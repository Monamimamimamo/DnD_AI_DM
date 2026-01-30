package com.dnd.messages;

import com.dnd.game_state.GameState;
import com.dnd.entity.*;
import com.dnd.repository.CampaignRepository;
import com.dnd.service.EmbeddingService;
import com.dnd.service.VectorDBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Строитель релевантного контекста для LLM
 * Использует RAG для семантического поиска релевантных событий
 */
@Component
public class RelevantContextBuilder {
    
    @Autowired
    private CampaignRepository campaignRepository;
    
    @Autowired(required = false)
    private EmbeddingService embeddingService;
    
    @Autowired(required = false)
    private VectorDBService vectorDBService;
    
    // Минимальная похожесть для семантического поиска (0.0 - 1.0)
    private static final double MIN_SIMILARITY = 0.6;
    
    /**
     * Строит релевантный контекст для генерации сообщения
     * Использует RAG для семантического поиска релевантных событий
     */
    public RelevantContext buildRelevantContext(GameState gameState, String campaignId) {
        RelevantContext context = new RelevantContext();
        
        // Определяем активный квест
        Map<String, Object> mainQuest = gameState.getMainQuest();
        String currentQuestStage = gameState.getCurrentQuestStage();
        
        if (mainQuest != null && currentQuestStage != null) {
            context.setActiveQuest(mainQuest);
            context.setCurrentQuestStage(currentQuestStage);
            
            // Используем RAG для поиска релевантных событий
            List<GameState.GameEvent> relevantEvents = findRelevantEventsWithRAG(
                gameState.getGameHistory(),
                mainQuest,
                currentQuestStage,
                gameState.getCurrentLocation(),
                campaignId
            );
            context.setRelevantEvents(relevantEvents);
        } else {
            // Если нет активного квеста, берем события после последнего события квеста
            List<GameState.GameEvent> eventsAfterLastQuest = findEventsAfterLastQuest(gameState.getGameHistory());
            context.setRelevantEvents(eventsAfterLastQuest);
        }
        
        // NPC и локации текущей локации
        context.setRelevantNPCs(findNPCsInLocation(campaignId, gameState.getCurrentLocation()));
        context.setRelevantLocations(findLocationsNearby(campaignId, gameState.getCurrentLocation()));
        
        // Всегда добавляем текущую локацию и ситуацию
        context.setCurrentLocation(gameState.getCurrentLocation());
        context.setCurrentSituation(gameState.getCurrentSituation());
        
        return context;
    }
    
    /**
     * Находит релевантные события используя RAG
     * @throws IllegalStateException если RAG недоступен
     */
    private List<GameState.GameEvent> findRelevantEventsWithRAG(
        List<GameState.GameEvent> allEvents,
        Map<String, Object> quest,
        String currentQuestStage,
        String currentLocation,
        String campaignId
    ) {
        // Если RAG недоступен, выбрасываем ошибку
        if (embeddingService == null || vectorDBService == null) {
            throw new IllegalStateException("RAG сервисы не инициализированы. EmbeddingService или VectorDBService недоступны.");
        }
        
        if (!embeddingService.isAvailable()) {
            throw new IllegalStateException("RAG недоступен: EmbeddingService не может подключиться к Ollama или модель bge-m3 не загружена.");
        }
        
        // Получаем Campaign для получения ID
        Campaign campaign = campaignRepository.findBySessionId(campaignId).orElse(null);
        if (campaign == null || campaign.getId() == null) {
            throw new IllegalStateException("Не найдена кампания с sessionId: " + campaignId);
        }
        
        // Формируем запрос для RAG поиска
        String questTitle = (String) quest.getOrDefault("title", "");
        String questGoal = (String) quest.getOrDefault("goal", "");
        String queryText = embeddingService.buildEnhancedText(
            questTitle + " " + questGoal + " " + currentQuestStage,
            questTitle,
            currentLocation,
            null
        );
        
        // Получаем эмбеддинг запроса
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingService.embed(queryText);
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка при создании эмбеддинга для RAG поиска: " + e.getMessage(), e);
        }
        
        // Ищем похожие события через RAG (без ограничения по количеству)
        List<VectorDBService.SimilarEvent> ragEvents;
        try {
            ragEvents = vectorDBService.searchSimilar(
                queryEmbedding,
                campaign.getId(),
                null, // null означает получить все релевантные события без ограничения
                MIN_SIMILARITY
            );
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка при поиске в векторной БД: " + e.getMessage(), e);
        }
        
        // Создаем Map для быстрого поиска событий по описанию
        Map<String, GameState.GameEvent> eventsByDescription = allEvents.stream()
            .collect(Collectors.toMap(
                GameState.GameEvent::getDescription,
                e -> e,
                (e1, e2) -> e1 // При дубликатах берем первое
            ));
        
        // Собираем релевантные события из RAG результатов (фильтруем по похожести >= MIN_SIMILARITY)
        List<GameState.GameEvent> relevantEvents = new ArrayList<>();
        for (VectorDBService.SimilarEvent ragEvent : ragEvents) {
            if (ragEvent.getSimilarity() >= MIN_SIMILARITY) {
                GameState.GameEvent matchingEvent = eventsByDescription.get(ragEvent.getDescription());
                if (matchingEvent != null) {
                    relevantEvents.add(matchingEvent);
                }
            }
        }
        
        // Всегда добавляем последние 3 события для контекста
        int recentCount = Math.min(3, allEvents.size());
        if (recentCount > 0) {
            List<GameState.GameEvent> recentEvents = allEvents.subList(
                Math.max(0, allEvents.size() - recentCount),
                allEvents.size()
            );
            for (GameState.GameEvent recent : recentEvents) {
                if (!relevantEvents.contains(recent)) {
                    relevantEvents.add(recent);
                }
            }
        }
        
        // Сортируем по времени (исторический порядок: от старых к новым)
        relevantEvents.sort((e1, e2) -> {
            java.time.LocalDateTime t1 = e1.getTimestamp();
            java.time.LocalDateTime t2 = e2.getTimestamp();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1; // null в конец
            if (t2 == null) return -1;
            return t1.compareTo(t2); // Старые события первыми
        });
        
        // Возвращаем все релевантные события в историческом порядке
        return relevantEvents;
    }
    
    /**
     * Находит события после последнего события квеста
     * История загружается в порядке DESC (новые первыми), поэтому ищем с начала
     */
    private List<GameState.GameEvent> findEventsAfterLastQuest(List<GameState.GameEvent> allEvents) {
        if (allEvents == null || allEvents.isEmpty()) return new ArrayList<>();
        int lastQuestEventIndex = -1;
        for (int i = 0; i < allEvents.size(); i++) {
            GameState.GameEvent event = allEvents.get(i);
            String eventType = event.getType();
            if ("quest_completed".equals(eventType) || 
                "quest_progress".equals(eventType) ||
                "quest_started".equals(eventType)) {
                lastQuestEventIndex = i;
                break; // Берем первое найденное (самое новое событие квеста)
            }
        }
        // Если не нашли событий квеста, возвращаем все события
        if (lastQuestEventIndex == -1) return new ArrayList<>(allEvents);
        if (lastQuestEventIndex == 0) return new ArrayList<>();
        // Возвращаем все события до последнего события квеста (это события после квеста)
        return new ArrayList<>(allEvents.subList(0, lastQuestEventIndex));
    }
    
    /**
     * Находит NPC в конкретной локации
     */
    private List<Map<String, Object>> findNPCsInLocation(String campaignId, String locationName) {
        List<Map<String, Object>> npcs = new ArrayList<>();
        
        try {
            Campaign campaign = campaignRepository.findBySessionId(campaignId).orElse(null);
            if (campaign == null || locationName == null) {
                return npcs;
            }
            
            for (NPC npc : campaign.getNpcs()) {
                if (npc.getLocation() != null && 
                    npc.getLocation().getName().equalsIgnoreCase(locationName)) {
                    Map<String, Object> npcMap = new HashMap<>();
                    npcMap.put("name", npc.getName());
                    npcMap.put("description", npc.getDescription());
                    npcMap.put("home_location", npc.getHomeLocation());
                    npcs.add(npcMap);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при поиске NPC в локации: " + e.getMessage());
        }
        
        return npcs;
    }
    
    /**
     * Находит локации рядом с текущей
     */
    private List<Map<String, Object>> findLocationsNearby(String campaignId, String currentLocation) {
        // Упрощенная версия - возвращаем все открытые локации
        List<Map<String, Object>> locations = new ArrayList<>();
        
        try {
            Campaign campaign = campaignRepository.findBySessionId(campaignId).orElse(null);
            if (campaign == null) {
                return locations;
            }
            
            for (Location loc : campaign.getLocations()) {
                if (loc.getDiscovered() != null && loc.getDiscovered()) {
                    Map<String, Object> locMap = new HashMap<>();
                    locMap.put("name", loc.getName());
                    locMap.put("description", loc.getDescription());
                    locMap.put("discovered", true);
                    locations.add(locMap);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при поиске локаций: " + e.getMessage());
        }
        
        return locations;
    }
    
    /**
     * Класс для хранения релевантного контекста
     */
    public static class RelevantContext {
        private Map<String, Object> activeQuest;
        private String currentQuestStage;
        private List<GameState.GameEvent> relevantEvents;
        private List<Map<String, Object>> relevantNPCs;
        private List<Map<String, Object>> relevantLocations;
        private String currentLocation;
        private String currentSituation;
        
        // Getters and Setters
        public Map<String, Object> getActiveQuest() { return activeQuest; }
        public void setActiveQuest(Map<String, Object> activeQuest) { this.activeQuest = activeQuest; }
        
        public String getCurrentQuestStage() { return currentQuestStage; }
        public void setCurrentQuestStage(String currentQuestStage) { this.currentQuestStage = currentQuestStage; }
        
        public List<GameState.GameEvent> getRelevantEvents() { return relevantEvents; }
        public void setRelevantEvents(List<GameState.GameEvent> relevantEvents) { this.relevantEvents = relevantEvents; }
        
        public List<Map<String, Object>> getRelevantNPCs() { return relevantNPCs; }
        public void setRelevantNPCs(List<Map<String, Object>> relevantNPCs) { this.relevantNPCs = relevantNPCs; }
        
        public List<Map<String, Object>> getRelevantLocations() { return relevantLocations; }
        public void setRelevantLocations(List<Map<String, Object>> relevantLocations) { this.relevantLocations = relevantLocations; }
        
        public String getCurrentLocation() { return currentLocation; }
        public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }
        
        public String getCurrentSituation() { return currentSituation; }
        public void setCurrentSituation(String currentSituation) { this.currentSituation = currentSituation; }
        
        /**
         * Форматирует релевантный контекст в текст для промпта
         */
        public String formatForPrompt() {
            StringBuilder context = new StringBuilder();
            
            // Активный квест
            if (activeQuest != null) {
                context.append("=== АКТИВНЫЙ КВЕСТ ===\n");
                context.append("Название: ").append(activeQuest.getOrDefault("title", "Неизвестно")).append("\n");
                context.append("Цель: ").append(activeQuest.getOrDefault("goal", "Неизвестно")).append("\n");
                if (currentQuestStage != null) {
                    context.append("Текущий этап: ").append(currentQuestStage).append("\n");
                }
                context.append("\n");
            }
            
            // Релевантные события (найденные через RAG)
            if (relevantEvents != null && !relevantEvents.isEmpty()) {
                context.append("=== РЕЛЕВАНТНЫЕ СОБЫТИЯ ===\n");
                for (GameState.GameEvent event : relevantEvents) {
                    context.append("- [").append(event.getType()).append("] ")
                           .append(event.getDescription()).append("\n");
                }
                context.append("\n");
            }
            
            // NPC в текущей локации
            if (relevantNPCs != null && !relevantNPCs.isEmpty()) {
                context.append("=== NPC В ТЕКУЩЕЙ ЛОКАЦИИ ===\n");
                for (Map<String, Object> npc : relevantNPCs) {
                    context.append("- ").append(npc.get("name"));
                    if (npc.get("description") != null) {
                        String desc = (String) npc.get("description");
                        context.append(": ").append(desc);
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            // Локации
            if (relevantLocations != null && !relevantLocations.isEmpty()) {
                context.append("=== ДОСТУПНЫЕ ЛОКАЦИИ ===\n");
                for (Map<String, Object> loc : relevantLocations) {
                    context.append("- ").append(loc.get("name"));
                    if (loc.get("description") != null) {
                        String desc = (String) loc.get("description");
                        context.append(": ").append(desc);
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            return context.toString();
        }
    }
}
