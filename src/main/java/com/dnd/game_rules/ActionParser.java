package com.dnd.game_rules;

import com.dnd.ai_engine.LocalLLMClient;
import com.dnd.prompts.DMPrompts;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Парсер действий игрока в структурированный формат с использованием SRD данных
 * Использует двухэтапный процесс:
 * 1. Модель выбирает нужные эндпоинты из SRD API
 * 2. Загружаются данные из выбранных эндпоинтов и передаются модели для финального парсинга
 */
public class ActionParser {
    private static final Gson gson = new GsonBuilder().setLenient().create();
    private final LocalLLMClient llmClient;
    private final SRDDataLoader srdLoader;
    private Map<String, Object> skillsData = new HashMap<>();
    private Map<String, Integer> dcTable = new HashMap<>();

    public ActionParser(LocalLLMClient llmClient, SRDDataLoader srdLoader) {
        this.llmClient = llmClient;
        this.srdLoader = srdLoader;
        this.dcTable = srdLoader.getDifficultyTable();
    }

    public Map<String, Object> parseAction(String actionText, Map<String, Object> gameContext) {
        // Этап 1: Проверяем, требует ли действие проверки
        Map<String, Object> endpointSelection = selectRequiredEndpoints(actionText);
        
        Boolean requiresCheck = (Boolean) endpointSelection.get("requires_check");
        if (requiresCheck == null) requiresCheck = true;
        
        // Если действие не требует проверки, возвращаем результат без парсинга через SRD
        if (!requiresCheck) {
            System.out.println("✅ [ActionParser] Действие не требует проверки, пропускаем парсинг через SRD");
            Map<String, Object> result = new HashMap<>();
            result.put("is_possible", true);
            result.put("requires_dice_roll", false);
            result.put("intent", "trivial");
            result.put("ability", null);
            result.put("skill", null);
            result.put("estimated_dc", null);
            result.put("estimated_difficulty", null);
            result.put("modifiers", new ArrayList<>());
            result.put("required_items", new ArrayList<>());
            result.put("reason", "Тривиальное действие, не требует проверки навыка или характеристики");
            return result;
        }
        
        // Этап 2: Загрузка данных из выбранных эндпоинтов
        @SuppressWarnings("unchecked")
        List<String> requiredEndpoints = (List<String>) endpointSelection.get("required_endpoints");
        if (requiredEndpoints == null || requiredEndpoints.isEmpty()) {
            throw new RuntimeException("Действие требует проверки, но не указаны эндпоинты");
        }
        
        Map<String, List<Map<String, Object>>> srdData = srdLoader.loadMultipleEndpoints(requiredEndpoints);
        
        // Обновляем кэш навыков для валидации
        if (srdData.containsKey("skills")) {
            List<Map<String, Object>> skills = srdData.get("skills");
            skillsData.clear();
            for (Map<String, Object> skill : skills) {
                String name = (String) skill.getOrDefault("name", "");
                if (!name.isEmpty()) {
                    String normalized = name.toLowerCase().replace("-", "_");
                    skillsData.put(normalized, skill);
                }
            }
        }
        
        // Этап 3: Финальный парсинг с данными из SRD
        String systemPrompt = getParserSystemPrompt();
        String userPrompt = DMPrompts.getActionParserFinalPrompt(actionText, convertSRDData(srdData), gameContext);
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userPrompt));
        
        System.out.println("🤖 [ActionParser] Финальный парсинг действия: " + actionText);
        System.out.println("📤 [ActionParser] User prompt (первые 400 символов): " + userPrompt);
        
        String response = llmClient.generateResponse(messages, systemPrompt);
        
        System.out.println("📥 [ActionParser] Полный ответ LLM для парсинга действия:");
        System.out.println("   " + response);
        
        Map<String, Object> parsed = extractJsonFromResponse(response, actionText);
        
        // Валидируем и дополняем результат
        parsed = validateAndEnrichResult(parsed, actionText);
        
        return parsed;
    }
    
    private Map<String, Object> convertSRDData(Map<String, List<Map<String, Object>>> srdData) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : srdData.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private Map<String, Object> selectRequiredEndpoints(String actionText) {
        // Получаем список доступных эндпоинтов
        Map<String, String> availableEndpoints = srdLoader.getAvailableEndpoints();
        
        // Используем промпты из ActionPrompts
        String systemPrompt = DMPrompts.getEndpointSelectionSystemPrompt();
        String userPrompt = DMPrompts.getEndpointSelectionPrompt(actionText, availableEndpoints);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userPrompt));
        
        System.out.println("🤖 [ActionParser] Запрос выбора эндпоинтов для действия: " + actionText);
        
        String response = llmClient.generateResponse(messages, systemPrompt);
        
        System.out.println("📥 [ActionParser] Полный ответ LLM для выбора эндпоинтов:");
        System.out.println("   " + response);
        
        Map<String, Object> parsed = extractJsonFromResponse(response, actionText);
        
        if (parsed.containsKey("error")) {
            throw new RuntimeException("Ошибка при выборе эндпоинтов: " + parsed.get("error"));
        }
        
        // Проверяем requires_check
        Boolean requiresCheck = null;
        if (parsed.containsKey("requires_check")) {
            Object reqCheckObj = parsed.get("requires_check");
            System.out.println("🔍 [ActionParser] requires_check найден в ответе: " + reqCheckObj + " (тип: " + (reqCheckObj != null ? reqCheckObj.getClass().getSimpleName() : "null") + ")");
            if (reqCheckObj instanceof Boolean) {
                requiresCheck = (Boolean) reqCheckObj;
            } else if (reqCheckObj instanceof String) {
                requiresCheck = Boolean.parseBoolean((String) reqCheckObj);
            } else {
                System.err.println("⚠️ [ActionParser] Неожиданный тип для requires_check: " + reqCheckObj.getClass());
            }
        } else {
            System.out.println("⚠️ [ActionParser] requires_check отсутствует в ответе LLM");
        }
        
        if (requiresCheck == null) {
            // По умолчанию считаем, что требуется проверка
            System.out.println("⚠️ [ActionParser] requires_check = null, устанавливаем по умолчанию: true");
            requiresCheck = true;
        } else {
            System.out.println("✅ [ActionParser] requires_check = " + requiresCheck);
        }
        
        parsed.put("requires_check", requiresCheck);
        
        // Валидируем эндпоинты только если требуется проверка
        if (requiresCheck) {
            @SuppressWarnings("unchecked")
            List<String> requiredEndpoints = (List<String>) parsed.get("required_endpoints");
            
            if (requiredEndpoints == null || requiredEndpoints.isEmpty()) {
                throw new RuntimeException("Действие требует проверки, но LLM не вернул список требуемых эндпоинтов. Ответ: " + response);
            }
            
            // Валидируем эндпоинты - проверяем, что они существуют
            List<String> validEndpoints = new ArrayList<>();
            for (String endpoint : requiredEndpoints) {
                if (availableEndpoints.containsKey(endpoint) || availableEndpoints.containsValue(endpoint)) {
                    validEndpoints.add(endpoint);
                }
            }
            
            if (validEndpoints.isEmpty()) {
                throw new RuntimeException("Не найдено ни одного валидного эндпоинта из запрошенных: " + requiredEndpoints);
            }
            
            parsed.put("required_endpoints", validEndpoints);
        } else {
            // Если не требуется проверка, эндпоинты не нужны
            parsed.put("required_endpoints", new ArrayList<>());
        }
        
        return parsed;
    }
    
    private String getParserSystemPrompt() {
        // Формируем таблицу сложности (всегда нужна)
        if (dcTable.isEmpty()) {
            dcTable = srdLoader.getDifficultyTable();
        }
        StringBuilder dcInfo = new StringBuilder();
        for (Map.Entry<String, Integer> entry : dcTable.entrySet()) {
            dcInfo.append(String.format("- %s: DC %d\n", entry.getKey(), entry.getValue()));
        }
        
        // Используем промпт из DMPrompts (навыки будут в данных из эндпоинтов)
        return DMPrompts.getActionParserSystemPrompt("", dcInfo.toString());
    }

    private Map<String, Object> validateAndEnrichResult(Map<String, Object> parsed, String actionText) {
        // Если есть ошибка, выбрасываем исключение
        if (parsed.containsKey("error")) {
            throw new RuntimeException("Ошибка в результате парсинга: " + parsed.get("error"));
        }
        
        // Убеждаемся, что все обязательные поля присутствуют
        Map<String, Object> result = new HashMap<>();
        result.put("is_possible", parsed.getOrDefault("is_possible", true));
        // requires_dice_roll: если не указан, по умолчанию нужен бросок
        // Но если указан явно (даже как false), используем его значение
        if (parsed.containsKey("requires_dice_roll")) {
            result.put("requires_dice_roll", parsed.get("requires_dice_roll"));
        } else {
            result.put("requires_dice_roll", true); // По умолчанию нужен бросок
        }
        result.put("intent", parsed.getOrDefault("intent", "unknown"));
        result.put("ability", parsed.getOrDefault("ability", "strength"));
        result.put("skill", parsed.get("skill"));
        result.put("estimated_dc", parsed.getOrDefault("estimated_dc", "medium"));
        result.put("estimated_difficulty", parsed.getOrDefault("estimated_difficulty", "medium"));
        result.put("modifiers", parsed.getOrDefault("modifiers", new ArrayList<>()));
        result.put("required_items", parsed.getOrDefault("required_items", new ArrayList<>()));
        result.put("reason", parsed.getOrDefault("reason", ""));
        result.put("action_text", actionText);
        
        // Валидируем навык - должен быть из SRD
        if (result.get("skill") != null) {
            String skill = (String) result.get("skill");
            String skillNormalized = skill.toLowerCase().replace("-", "_");
            
            // Проверяем в кэше навыков
            boolean skillFound = false;
            if (skillsData.containsKey(skillNormalized)) {
                skillFound = true;
            } else {
                // Пробуем найти похожий навык
                for (String skillKey : skillsData.keySet()) {
                    if (skillKey.contains(skillNormalized) || skillNormalized.contains(skillKey)) {
                        result.put("skill", skillKey);
                        skillFound = true;
                        break;
                    }
                }
            }
            
            if (!skillFound) {
                // Навык не найден, но оставляем как есть (может быть null)
                result.put("skill", null);
            }
        }
        
        // Валидируем DC - конвертируем строку в число если нужно
        Object estimatedDc = result.get("estimated_dc");
        if (estimatedDc instanceof String) {
            String dcStr = (String) estimatedDc;
            if (dcTable.containsKey(dcStr)) {
                result.put("estimated_dc", dcTable.get(dcStr));
            } else {
                // Используем среднюю сложность
                result.put("estimated_dc", dcTable.getOrDefault("medium", 15));
            }
        }
        
        // Если действие невозможно, но нет reason, добавляем
        if (!(Boolean) result.get("is_possible") && ((String) result.get("reason")).isEmpty()) {
            result.put("reason", "Действие невозможно по правилам D&D 5e или нарушает законы физики/магии. Попробуйте описать другое действие.");
        }
        
        return result;
    }
    
    private Map<String, Object> extractJsonFromResponse(String response, String actionText) {
        if (response == null || response.trim().isEmpty()) {
            System.err.println("❌ [ActionParser] Получен пустой ответ от LLM для действия: " + actionText);
            throw new RuntimeException("Получен пустой ответ от LLM при парсинге действия: " + actionText);
        }
        
        response = response.trim();
        
        // Если ответ начинается с {, пытаемся распарсить
        if (response.startsWith("{")) {
            try {
                JsonObject jsonObj = gson.fromJson(response, JsonObject.class);
                Map<String, Object> result = parseJsonObject(jsonObj);
                return result;
            } catch (Exception e) {
                System.err.println("❌ [ActionParser] Ошибка парсинга JSON: " + e.getMessage());
                System.err.println("   Полный ответ: " + response);
                throw new RuntimeException("Ошибка парсинга JSON из ответа LLM: " + e.getMessage() + ". Ответ: " + response, e);
            }
        }
        
        // Ищем JSON в тексте
        System.out.println("🔍 [ActionParser] Поиск JSON в тексте ответа");
        int startIdx = response.indexOf('{');
        int endIdx = response.lastIndexOf('}');
        
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            System.err.println("❌ [ActionParser] Не удалось найти JSON в ответе");
            System.err.println("   Полный ответ: " + response);
            throw new RuntimeException("Не удалось найти JSON в ответе LLM. Ответ: " + response);
        }
        
        String jsonStr = response.substring(startIdx, endIdx + 1);
        System.out.println("🔍 [ActionParser] Извлеченный JSON: " + jsonStr);
        try {
            JsonObject jsonObj = gson.fromJson(jsonStr, JsonObject.class);
            Map<String, Object> result = parseJsonObject(jsonObj);
            System.out.println("✅ [ActionParser] JSON успешно распарсен из текста");
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ [ActionParser] Ошибка парсинга извлеченного JSON: " + e.getMessage());
            System.err.println("   Извлеченный JSON: " + jsonStr);
            throw new RuntimeException("Ошибка парсинга JSON из текста: " + e.getMessage() + ". JSON: " + jsonStr, e);
        }
    }
    
    private Map<String, Object> parseJsonObject(JsonObject jsonObj) {
        Map<String, Object> result = new HashMap<>();
        
        if (jsonObj.has("is_possible")) {
            result.put("is_possible", jsonObj.get("is_possible").getAsBoolean());
        }
        
        if (jsonObj.has("requires_check")) {
            if (jsonObj.get("requires_check").isJsonNull()) {
                result.put("requires_check", true); // По умолчанию требуется проверка
            } else {
                result.put("requires_check", jsonObj.get("requires_check").getAsBoolean());
            }
        }
        
        if (jsonObj.has("requires_dice_roll")) {
            if (jsonObj.get("requires_dice_roll").isJsonNull()) {
                result.put("requires_dice_roll", true); // По умолчанию нужен бросок
            } else {
                result.put("requires_dice_roll", jsonObj.get("requires_dice_roll").getAsBoolean());
            }
        }
        
        if (jsonObj.has("intent")) {
            if (jsonObj.get("intent").isJsonNull()) {
                result.put("intent", null);
            } else {
                result.put("intent", jsonObj.get("intent").getAsString());
            }
        }
        
        if (jsonObj.has("ability")) {
            if (jsonObj.get("ability").isJsonNull()) {
                result.put("ability", null);
        } else {
                result.put("ability", jsonObj.get("ability").getAsString());
            }
        }
        
        if (jsonObj.has("skill")) {
            if (jsonObj.get("skill").isJsonNull()) {
            result.put("skill", null);
            } else {
                result.put("skill", jsonObj.get("skill").getAsString());
            }
        }
        
        if (jsonObj.has("estimated_dc")) {
            if (jsonObj.get("estimated_dc").isJsonNull()) {
                result.put("estimated_dc", null);
            } else if (jsonObj.get("estimated_dc").isJsonPrimitive()) {
                if (jsonObj.get("estimated_dc").getAsJsonPrimitive().isNumber()) {
                    result.put("estimated_dc", jsonObj.get("estimated_dc").getAsInt());
                } else {
                    result.put("estimated_dc", jsonObj.get("estimated_dc").getAsString());
                }
            }
        }
        
        if (jsonObj.has("estimated_difficulty")) {
            if (jsonObj.get("estimated_difficulty").isJsonNull()) {
                result.put("estimated_difficulty", null);
            } else {
                result.put("estimated_difficulty", jsonObj.get("estimated_difficulty").getAsString());
            }
        }
        
        if (jsonObj.has("modifiers")) {
            if (jsonObj.get("modifiers").isJsonNull()) {
        result.put("modifiers", new ArrayList<>());
            } else {
                List<String> modifiers = new ArrayList<>();
                jsonObj.getAsJsonArray("modifiers").forEach(e -> {
                    if (!e.isJsonNull()) {
                        modifiers.add(e.getAsString());
                    }
                });
                result.put("modifiers", modifiers);
            }
        }
        
        if (jsonObj.has("required_items")) {
            if (jsonObj.get("required_items").isJsonNull()) {
        result.put("required_items", new ArrayList<>());
            } else {
                List<String> items = new ArrayList<>();
                jsonObj.getAsJsonArray("required_items").forEach(e -> {
                    if (!e.isJsonNull()) {
                        items.add(e.getAsString());
                    }
                });
                result.put("required_items", items);
            }
        }
        
        if (jsonObj.has("reason")) {
            if (jsonObj.get("reason").isJsonNull()) {
                result.put("reason", "");
            } else {
                result.put("reason", jsonObj.get("reason").getAsString());
            }
        }
        
        if (jsonObj.has("required_endpoints")) {
            if (jsonObj.get("required_endpoints").isJsonNull()) {
                result.put("required_endpoints", new ArrayList<>());
            } else {
                List<String> endpoints = new ArrayList<>();
                jsonObj.getAsJsonArray("required_endpoints").forEach(e -> {
                    if (!e.isJsonNull()) {
                        endpoints.add(e.getAsString());
                    }
                });
                result.put("required_endpoints", endpoints);
            }
        }
        
        return result;
    }


}

