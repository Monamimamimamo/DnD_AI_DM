package com.dnd.events;

import com.dnd.ai_engine.LocalLLMClient;
import com.dnd.game_state.GameState;
import com.dnd.prompts.DMPrompts;
import java.util.*;

/**
 * LLM генератор событий с контекстом
 */
public class EventGenerator {
    private final LocalLLMClient llmClient;
    private final HistoryAnalyzer historyAnalyzer;
    private final ConnectionGenerator connectionGenerator;
    
    public EventGenerator(LocalLLMClient llmClient) {
        this.llmClient = llmClient;
        this.historyAnalyzer = new HistoryAnalyzer();
        this.connectionGenerator = new ConnectionGenerator();
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
        String generatedEvent = generateEventWithLLM(eventType, connections, updatedContext);
        
        // 5. Создаем объект GeneratedEvent
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("trigger", trigger);
        metadata.put("history_analysis", historyAnalysis);
        
        int priority = trigger.getPriority();
        
        return new GeneratedEvent(
            eventType,
            extractTitle(generatedEvent),
            generatedEvent,
            metadata,
            connections,
            priority
        );
    }
    
    /**
     * Генерирует событие через LLM
     */
    private String generateEventWithLLM(EventType eventType,
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
        
        // Извлекаем событие из ответа
        return extractEventFromResponse(response);
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
     * Строит контекст игры для промпта
     */
    private String buildGameContext(EventContext context) {
        StringBuilder gameContext = new StringBuilder();
        
        gameContext.append("Текущая локация: ").append(context.getCurrentLocation()).append("\n");
        gameContext.append("Текущая ситуация: ").append(context.getCurrentSituation()).append("\n");
        
        if (context.getMainQuest() != null) {
            String currentStage = context.getGameState().getCurrentQuestStage();
            gameContext.append("Текущий этап квеста: ").append(currentStage != null ? currentStage : "не определен").append("\n");
        }
        
        // История игры
        List<GameState.GameEvent> recentHistory = context.getRecentHistory();
        if (recentHistory != null && !recentHistory.isEmpty()) {
            gameContext.append("\nПоследние события:\n");
            int limit = Math.min(5, recentHistory.size());
            for (int i = recentHistory.size() - limit; i < recentHistory.size(); i++) {
                GameState.GameEvent event = recentHistory.get(i);
                gameContext.append("- [").append(event.getType()).append("] ").append(event.getDescription()).append("\n");
            }
        }
        
        // Упоминания из истории
        Map<String, Object> historyAnalysis = context.getHistoryAnalysis();
        if (historyAnalysis != null) {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> mentions = (Map<String, List<String>>) historyAnalysis.get("mentions");
            if (mentions != null && !mentions.isEmpty()) {
                gameContext.append("\nУпоминания из истории:\n");
                if (!mentions.get("npcs").isEmpty()) {
                    gameContext.append("NPC: ").append(String.join(", ", mentions.get("npcs"))).append("\n");
                }
                if (!mentions.get("items").isEmpty()) {
                    gameContext.append("Предметы: ").append(String.join(", ", mentions.get("items"))).append("\n");
                }
            }
        }
        
        return gameContext.toString();
    }
    
    /**
     * Извлекает событие из ответа LLM
     */
    private String extractEventFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "Произошло неожиданное событие...";
        }
        
        // Пытаемся извлечь JSON если есть
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");
        
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            String jsonStr = response.substring(jsonStart, jsonEnd + 1);
            try {
                // Можно распарсить JSON если нужно
                // Пока просто возвращаем весь ответ
            } catch (Exception e) {
                // Игнорируем ошибки парсинга
            }
        }
        
        // Очищаем от markdown разметки если есть
        response = response.replaceAll("```json", "").replaceAll("```", "").trim();
        
        return response;
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

