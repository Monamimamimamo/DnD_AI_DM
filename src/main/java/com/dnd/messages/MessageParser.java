package com.dnd.messages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Утилита для парсинга структурированных сообщений из JSON ответов LLM
 */
public class MessageParser {
    
    /**
     * Парсит JSON ответ от LLM в StructuredMessage
     */
    public static StructuredMessage parseMessage(String jsonResponse, String characterName) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("Пустой ответ от LLM");
        }
        
        // Извлекаем JSON из ответа (может быть обернут в текст)
        String jsonStr = extractJson(jsonResponse);
        
        // Парсим JSON
        JsonObject jsonObj = JsonParser.parseString(jsonStr).getAsJsonObject();
        
        // Извлекаем тип сообщения
        String messageTypeCode = getStringValue(jsonObj, "message_type", null);
        if (messageTypeCode == null) {
            // Попытка определить тип по старым полям (для обратной совместимости)
            messageTypeCode = inferMessageType(jsonObj);
        }
        
        MessageType messageType = parseMessageType(messageTypeCode);
        
        // Извлекаем контент
        String content = getStringValue(jsonObj, "content", null);
        if (content == null) {
            // Попытка найти контент в старых полях
            content = getStringValue(jsonObj, "situation", 
                    getStringValue(jsonObj, "narrative", 
                    getStringValue(jsonObj, "description", "")));
        }
        
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Не найден контент сообщения в JSON");
        }
        
        // Извлекаем метаданные
        Map<String, Object> metadata = new HashMap<>();
        if (jsonObj.has("metadata") && jsonObj.get("metadata").isJsonObject()) {
            JsonObject metadataObj = jsonObj.getAsJsonObject("metadata");
            for (Map.Entry<String, JsonElement> entry : metadataObj.entrySet()) {
                metadata.put(entry.getKey(), extractValue(entry.getValue()));
            }
        }
        
        // Добавляем локацию в метаданные, если есть
        String location = getStringValue(jsonObj, "location", null);
        if (location != null && !location.isEmpty()) {
            metadata.put("location", location);
        }
        
        // Извлекаем анализ, если есть
        if (jsonObj.has("analysis") && jsonObj.get("analysis").isJsonObject()) {
            JsonObject analysisObj = jsonObj.getAsJsonObject("analysis");
            Map<String, Object> analysis = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : analysisObj.entrySet()) {
                analysis.put(entry.getKey(), extractValue(entry.getValue()));
            }
            metadata.put("analysis", analysis);
        }
        
        // Создаем структурированное сообщение
        StructuredMessage message = new StructuredMessage(messageType, content, characterName, metadata);
        
        return message;
    }
    
    /**
     * Извлекает JSON из ответа (может быть обернут в текст)
     */
    private static String extractJson(String response) {
        int startIdx = response.indexOf("{");
        int endIdx = response.lastIndexOf("}") + 1;
        
        if (startIdx == -1 || endIdx <= startIdx) {
            throw new IllegalArgumentException("Не найден JSON в ответе: " + response);
        }
        
        String jsonStr = response.substring(startIdx, endIdx);
        
        // Очищаем JSON от распространенных ошибок
        jsonStr = cleanJsonString(jsonStr);
        
        return jsonStr;
    }
    
    /**
     * Очищает JSON строку от распространенных ошибок
     */
    private static String cleanJsonString(String json) {
        json = json.replaceAll(",\\s*}", "}");
        json = json.replaceAll(",\\s*]", "]");
        json = json.replaceAll(",\\s*\\n\\s*}", "\n}");
        json = json.replaceAll(",\\s*\\n\\s*]", "\n]");
        return json;
    }
    
    /**
     * Определяет тип сообщения по старым полям (для обратной совместимости)
     */
    private static String inferMessageType(JsonObject jsonObj) {
        if (jsonObj.has("situation")) {
            return "situation_continuation";
        }
        if (jsonObj.has("narrative")) {
            return "action_result";
        }
        if (jsonObj.has("final_scene")) {
            return "final_scene";
        }
        // По умолчанию
        return "situation_continuation";
    }
    
    /**
     * Парсит строковый код типа сообщения в MessageType
     */
    private static MessageType parseMessageType(String code) {
        if (code == null || code.isEmpty()) {
            return MessageType.SITUATION_CONTINUATION; // По умолчанию
        }
        
        // Ищем соответствующий тип
        for (MessageType type : MessageType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        
        // Если не найден, пытаемся найти по частичному совпадению
        String lowerCode = code.toLowerCase();
        for (MessageType type : MessageType.values()) {
            if (type.getCode().toLowerCase().contains(lowerCode) || 
                lowerCode.contains(type.getCode().toLowerCase())) {
                return type;
            }
        }
        
        // По умолчанию
        return MessageType.SITUATION_CONTINUATION;
    }
    
    /**
     * Безопасно извлекает строковое значение из JsonObject
     */
    private static String getStringValue(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key)) {
            return defaultValue;
        }
        JsonElement element = obj.get(key);
        if (element.isJsonNull()) {
            return defaultValue;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray() && element.getAsJsonArray().size() > 0) {
            return element.getAsJsonArray().get(0).getAsString();
        }
        return defaultValue;
    }
    
    /**
     * Извлекает значение из JsonElement
     */
    private static Object extractValue(JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsNumber();
            }
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
        }
        if (element.isJsonArray()) {
            // Преобразуем JsonArray в List рекурсивно
            List<Object> list = new ArrayList<>();
            for (JsonElement arrayElement : element.getAsJsonArray()) {
                list.add(extractValue(arrayElement));
            }
            return list;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), extractValue(entry.getValue()));
            }
            return map;
        }
        return element.toString();
    }
}

