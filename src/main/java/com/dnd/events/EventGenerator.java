package com.dnd.events;

import com.dnd.ai_engine.LocalLLMClient;
import com.dnd.messages.RelevantContextBuilder;
import com.dnd.prompts.DMPrompts;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * LLM генератор событий с контекстом
 */
public class EventGenerator {
    private final LocalLLMClient llmClient;
    private final HistoryAnalyzer historyAnalyzer;
    private final ConnectionGenerator connectionGenerator;
    
    private RelevantContextBuilder relevantContextBuilder;
    
    public EventGenerator(LocalLLMClient llmClient) {
        this.llmClient = llmClient;
        this.historyAnalyzer = new HistoryAnalyzer();
        this.connectionGenerator = new ConnectionGenerator();
    }
    
    public void setRelevantContextBuilder(RelevantContextBuilder relevantContextBuilder) {
        this.relevantContextBuilder = relevantContextBuilder;
    }
    
    /**
     * Генерирует событие на основе триггера и контекста
     */
    public GeneratedEvent generateEvent(EventTrigger trigger, EventContext context) {
        if (trigger == null || context == null) {
            return null;
        }
        
        // 1. Определяем тип события
        EventTriggerManager triggerManager = new EventTriggerManager();
        EventType eventType = triggerManager.determineEventType(trigger, context.getGameState());
        
        // 2. Анализируем историю
        Map<String, Object> historyAnalysis = historyAnalyzer.analyzeHistory(
            context.getGameState(), 
            20 // Анализируем последние 20 событий
        );
        
        // Обновляем контекст с анализом истории
        EventContext updatedContext = new EventContext(
            context.getGameState(),
            context.getCurrentLocation(),
            context.getCurrentSituation(),
            context.getWorld(),
            context.getMainQuest(),
            context.getRecentHistory(),
            historyAnalysis,
            context.getConnections()
        );
        
        // 3. Генерируем связи с историей (на основе анализа, без шаблонов)
        Map<String, Object> connections = connectionGenerator.generateConnectionsFromHistory(updatedContext);
        
        // 4. Генерируем событие через LLM с жесткими правилами
        EventParseResult parseResult = generateEventWithLLM(eventType, connections, updatedContext);
        
        // 5. Создаем объект GeneratedEvent
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("trigger", trigger);
        metadata.put("history_analysis", historyAnalysis);
        
        // Добавляем связанные сущности из JSON ответа LLM
        if (parseResult.relatedNpcs != null) {
            metadata.put("related_npcs", parseResult.relatedNpcs);
        }
        if (parseResult.relatedQuests != null) {
            metadata.put("related_quests", parseResult.relatedQuests);
        }
        if (parseResult.relatedLocations != null) {
            metadata.put("related_locations", parseResult.relatedLocations);
        }
        
        int priority = trigger.getPriority();
        
        return new GeneratedEvent(
            eventType,
            extractTitle(parseResult.content),
            parseResult.content,
            metadata,
            connections,
            priority
        );
    }
    
    /**
     * Результат парсинга события из LLM
     */
    private static class EventParseResult {
        String content;
        List<String> relatedNpcs;
        List<String> relatedQuests;
        List<String> relatedLocations;
        
        EventParseResult(String content, List<String> relatedNpcs, 
                        List<String> relatedQuests, List<String> relatedLocations) {
            this.content = content;
            this.relatedNpcs = relatedNpcs;
            this.relatedQuests = relatedQuests;
            this.relatedLocations = relatedLocations;
        }
    }
    
    /**
     * Генерирует событие через LLM и парсит JSON ответ
     */
    private EventParseResult generateEventWithLLM(EventType eventType,
                                                   Map<String, Object> connections,
                                                   EventContext context) {
        // Создаем промпт для генерации события с жесткими правилами
        String prompt = buildEventPrompt(eventType, connections, context);
        
        // Системный промпт
        int maxTokens = llmClient.getConfig().getMaxTokens();
        String systemPrompt = DMPrompts.getSystemPrompt(maxTokens);
        
        // Генерируем ответ
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        
        String response = llmClient.generateResponse(messages, systemPrompt);
        
        // Парсим JSON и извлекаем событие и связи
        return parseEventFromResponse(response);
    }
    
    /**
     * Строит промпт для генерации события с жесткими правилами
     */
    private String buildEventPrompt(EventType eventType,
                                   Map<String, Object> connections,
                                   EventContext context) {
        String connectionText = connectionGenerator.createConnectionText(connections);
        String gameContext = buildGameContext(context);
        Map<String, Object> historyAnalysis = context.getHistoryAnalysis();
        
        // Используем специализированные промпты с жесткими правилами для каждого типа
        switch (eventType) {
            case NPC_ENCOUNTER:
                return DMPrompts.getNPCEncounterPrompt(connectionText, gameContext, historyAnalysis);
                
            case SIDE_QUEST:
            case QUEST_HOOK:
                return DMPrompts.getSideQuestPrompt(connectionText, gameContext, historyAnalysis);
                
            case RANDOM_EVENT:
            case LOCATION_EVENT:
            case REVELATION:
            case CONSEQUENCE:
            default:
                return DMPrompts.getRandomEventPrompt(eventType.name(), connectionText, gameContext, historyAnalysis);
        }
    }
    
    /**
     * Строит контекст игры для промпта (использует релевантный контекст)
     */
    private String buildGameContext(EventContext context) {
        StringBuilder gameContext = new StringBuilder();
        
        // Используем RelevantContextBuilder для получения только релевантной информации
        if (relevantContextBuilder != null) {
            try {
                RelevantContextBuilder.RelevantContext relevantContext = 
                    relevantContextBuilder.buildRelevantContext(
                        context.getGameState(),
                        context.getGameState().getSessionId()
                    );
                
                // Добавляем релевантный контекст
                String relevantContextText = relevantContext.formatForPrompt();
                if (!relevantContextText.isEmpty()) {
                    gameContext.append(relevantContextText);
                }
            } catch (Exception e) {
                System.err.println("Ошибка при построении релевантного контекста: " + e.getMessage());
            }
        } else return "";
        
        return gameContext.toString();
    }
    
    /**
     * Парсит событие из JSON ответа LLM и извлекает связанные сущности
     */
    private EventParseResult parseEventFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return new EventParseResult(
                "Произошло неожиданное событие...",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
            );
        }
        
        // Очищаем от markdown разметки если есть
        response = response.replaceAll("```json", "").replaceAll("```", "").trim();
        
        // Пытаемся извлечь JSON
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");
        
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            String jsonStr = response.substring(jsonStart, jsonEnd + 1);
            try {
                Gson gson = new Gson();
                JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
                
                // Извлекаем content
                String content = json.has("content") ? 
                    json.get("content").getAsString() : response;
                
                // Извлекаем связанные сущности из metadata
                List<String> relatedNpcs = new ArrayList<>();
                List<String> relatedQuests = new ArrayList<>();
                List<String> relatedLocations = new ArrayList<>();
                
                if (json.has("metadata")) {
                    JsonObject metadata = json.getAsJsonObject("metadata");
                    
                    // Извлекаем related_npcs
                    if (metadata.has("related_npcs")) {
                        JsonArray npcsArray = metadata.getAsJsonArray("related_npcs");
                        for (JsonElement element : npcsArray) {
                            if (element.isJsonPrimitive()) {
                                String npcName = element.getAsString();
                                if (npcName != null && !npcName.trim().isEmpty()) {
                                    relatedNpcs.add(npcName.trim());
                                }
                            }
                        }
                    }
                    
                    // Извлекаем related_quests
                    if (metadata.has("related_quests")) {
                        JsonArray questsArray = metadata.getAsJsonArray("related_quests");
                        for (JsonElement element : questsArray) {
                            if (element.isJsonPrimitive()) {
                                String questTitle = element.getAsString();
                                if (questTitle != null && !questTitle.trim().isEmpty()) {
                                    relatedQuests.add(questTitle.trim());
                                }
                            }
                        }
                    }
                    
                    // Извлекаем related_locations
                    if (metadata.has("related_locations")) {
                        JsonArray locationsArray = metadata.getAsJsonArray("related_locations");
                        for (JsonElement element : locationsArray) {
                            if (element.isJsonPrimitive()) {
                                String locationName = element.getAsString();
                                if (locationName != null && !locationName.trim().isEmpty()) {
                                    relatedLocations.add(locationName.trim());
                                }
                            }
                        }
                    }
                }
                
                return new EventParseResult(content, relatedNpcs, relatedQuests, relatedLocations);
                
            } catch (Exception e) {
                System.err.println("Ошибка парсинга JSON события: " + e.getMessage());
                // Возвращаем весь ответ как content, если парсинг не удался
                return new EventParseResult(
                    response,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
                );
            }
        }
        
        // Если JSON не найден, возвращаем весь ответ как content
        return new EventParseResult(
            response,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>()
        );
    }
    
    /**
     * Извлекает заголовок из сгенерированного события
     */
    private String extractTitle(String eventDescription) {
        if (eventDescription == null || eventDescription.isEmpty()) {
            return "Событие";
        }
        
        // Берем первое предложение как заголовок
        int firstSentenceEnd = eventDescription.indexOf('.');
        if (firstSentenceEnd > 0 && firstSentenceEnd < 100) {
            return eventDescription.substring(0, firstSentenceEnd).trim();
        }
        
        // Или первые 50 символов
        return eventDescription.length() > 50 ? 
               eventDescription.substring(0, 50).trim() + "..." : 
               eventDescription.trim();
    }
}

