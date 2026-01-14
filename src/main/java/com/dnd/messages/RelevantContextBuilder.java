package com.dnd.messages;

import com.dnd.game_state.GameState;
import com.dnd.entity.*;
import com.dnd.repository.CampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Строитель релевантного контекста для LLM
 * Фильтрует информацию, оставляя только то, что связано с текущим квестом и ситуацией
 */
@Component
public class RelevantContextBuilder {
    
    @Autowired
    private CampaignRepository campaignRepository;
    
    /**
     * Строит релевантный контекст для генерации сообщения
     * Включает только события, NPC, локации и информацию, связанную с текущим квестом
     */
    public RelevantContext buildRelevantContext(GameState gameState, String campaignId) {
        RelevantContext context = new RelevantContext();
        
        // Определяем активный квест
        Map<String, Object> mainQuest = gameState.getMainQuest();
        String currentQuestStage = gameState.getCurrentQuestStage();
        
        if (mainQuest != null && currentQuestStage != null) {
            context.setActiveQuest(mainQuest);
            context.setCurrentQuestStage(currentQuestStage);
            
            // Извлекаем ключевые слова из квеста для фильтрации
            Set<String> questKeywords = extractQuestKeywords(mainQuest, currentQuestStage);
            context.setQuestKeywords(questKeywords);
            
            // Фильтруем события истории
            List<GameState.GameEvent> relevantEvents = filterRelevantEvents(
                gameState.getGameHistory(), 
                questKeywords,
                mainQuest
            );
            context.setRelevantEvents(relevantEvents);
            
            // Находим связанных NPC
            List<Map<String, Object>> relevantNPCs = findRelevantNPCs(
                campaignId, 
                questKeywords, 
                gameState.getCurrentLocation()
            );
            context.setRelevantNPCs(relevantNPCs);
            
            // Находим связанные локации
            List<Map<String, Object>> relevantLocations = findRelevantLocations(
                campaignId,
                questKeywords,
                gameState.getCurrentLocation()
            );
            context.setRelevantLocations(relevantLocations);
            
            // Находим связанные предметы из истории
            Set<String> relevantItems = extractRelevantItems(relevantEvents);
            context.setRelevantItems(relevantItems);
            
            // Информация о мире, связанная с квестом
            Map<String, Object> world = gameState.getWorld();
            if (world != null) {
                context.setRelevantWorldInfo(extractRelevantWorldInfo(world, questKeywords));
            }
            
            // Побочные квесты, связанные с основным
            Map<String, Map<String, Object>> sideQuests = gameState.getSideQuests();
            Map<String, Map<String, Object>> relevantSideQuests = filterRelevantSideQuests(
                sideQuests, 
                questKeywords
            );
            context.setRelevantSideQuests(relevantSideQuests);
        } else {
            // Если нет активного квеста, берем последние события и текущую локацию
            List<GameState.GameEvent> recentEvents = gameState.getGameHistory();
            int limit = Math.min(10, recentEvents.size());
            context.setRelevantEvents(
                recentEvents.subList(Math.max(0, recentEvents.size() - limit), recentEvents.size())
            );
            
            // NPC и локации текущей локации
            context.setRelevantNPCs(findNPCsInLocation(campaignId, gameState.getCurrentLocation()));
            context.setRelevantLocations(findLocationsNearby(campaignId, gameState.getCurrentLocation()));
        }
        
        // Всегда добавляем текущую локацию и ситуацию
        context.setCurrentLocation(gameState.getCurrentLocation());
        context.setCurrentSituation(gameState.getCurrentSituation());
        
        return context;
    }
    
    /**
     * Извлекает ключевые слова из квеста для фильтрации
     */
    private Set<String> extractQuestKeywords(Map<String, Object> quest, String currentStage) {
        Set<String> keywords = new HashSet<>();
        
        // Из названия квеста
        String title = (String) quest.getOrDefault("title", "");
        if (title != null) {
            keywords.addAll(extractWords(title));
        }
        
        // Из цели квеста
        String goal = (String) quest.getOrDefault("goal", "");
        if (goal != null) {
            keywords.addAll(extractWords(goal));
        }
        
        // Из описания
        String description = (String) quest.getOrDefault("description", "");
        if (description != null) {
            keywords.addAll(extractWords(description));
        }
        
        // Из текущего этапа
        if (currentStage != null) {
            keywords.addAll(extractWords(currentStage));
        }
        
        // Из всех этапов
        @SuppressWarnings("unchecked")
        List<String> stages = (List<String>) quest.getOrDefault("stages", Collections.emptyList());
        if (stages != null) {
            for (String stage : stages) {
                keywords.addAll(extractWords(stage));
            }
        }
        
        return keywords;
    }
    
    /**
     * Извлекает значимые слова из текста
     */
    private Set<String> extractWords(String text) {
        Set<String> words = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return words;
        }
        
        // Убираем знаки препинания и разбиваем на слова
        String[] tokens = text.toLowerCase()
            .replaceAll("[^а-яёa-z0-9\\s]", " ")
            .split("\\s+");
        
        // Фильтруем стоп-слова и короткие слова
        Set<String> stopWords = Set.of(
            "и", "в", "на", "с", "по", "для", "от", "до", "из", "к", "о", "у", "за", "под", "над",
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by"
        );
        
        for (String token : tokens) {
            if (token.length() > 3 && !stopWords.contains(token)) {
                words.add(token);
            }
        }
        
        return words;
    }
    
    /**
     * Фильтрует события истории, оставляя только релевантные к квесту
     */
    private List<GameState.GameEvent> filterRelevantEvents(
        List<GameState.GameEvent> allEvents,
        Set<String> questKeywords,
        Map<String, Object> quest
    ) {
        List<GameState.GameEvent> relevant = new ArrayList<>();
        
        // Всегда включаем последние 3 события для контекста
        int recentCount = Math.min(3, allEvents.size());
        if (recentCount > 0) {
            relevant.addAll(allEvents.subList(
                Math.max(0, allEvents.size() - recentCount),
                allEvents.size()
            ));
        }
        
        // Ищем события, связанные с квестом
        String questTitle = (String) quest.getOrDefault("title", "");
        String questGoal = (String) quest.getOrDefault("goal", "");
        
        for (GameState.GameEvent event : allEvents) {
            String description = event.getDescription().toLowerCase();
            
            // Проверяем, содержит ли событие ключевые слова квеста
            boolean isRelevant = questKeywords.stream().anyMatch(description::contains) ||
                                description.contains(questTitle.toLowerCase()) ||
                                description.contains(questGoal.toLowerCase()) ||
                                "quest_progress".equals(event.getType()) ||
                                "quest_completed".equals(event.getType());
            
            if (isRelevant && !relevant.contains(event)) {
                relevant.add(event);
            }
        }
        
        // Сортируем по времени (новые первыми)
        relevant.sort((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()));
        
        // Ограничиваем количество (максимум 15 событий)
        return relevant.size() > 15 ? relevant.subList(0, 15) : relevant;
    }
    
    /**
     * Находит NPC, связанных с квестом
     */
    private List<Map<String, Object>> findRelevantNPCs(
        String campaignId,
        Set<String> questKeywords,
        String currentLocation
    ) {
        List<Map<String, Object>> relevantNPCs = new ArrayList<>();
        
        try {
            Campaign campaign = campaignRepository.findBySessionId(campaignId).orElse(null);
            if (campaign == null) {
                return relevantNPCs;
            }
            
            for (NPC npc : campaign.getNpcs()) {
                boolean isRelevant = false;
                
                // Проверяем по имени
                String name = npc.getName().toLowerCase();
                if (questKeywords.stream().anyMatch(name::contains)) {
                    isRelevant = true;
                }
                
                // Проверяем по описанию
                if (npc.getDescription() != null) {
                    String description = npc.getDescription().toLowerCase();
                    if (questKeywords.stream().anyMatch(description::contains)) {
                        isRelevant = true;
                    }
                }
                
                // Проверяем по локации
                if (npc.getLocation() != null && 
                    npc.getLocation().getName().equalsIgnoreCase(currentLocation)) {
                    isRelevant = true;
                }
                
                if (isRelevant) {
                    Map<String, Object> npcMap = new HashMap<>();
                    npcMap.put("name", npc.getName());
                    npcMap.put("description", npc.getDescription());
                    npcMap.put("home_location", npc.getHomeLocation());
                    if (npc.getLocation() != null) {
                        npcMap.put("current_location", npc.getLocation().getName());
                    }
                    relevantNPCs.add(npcMap);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при поиске релевантных NPC: " + e.getMessage());
        }
        
        return relevantNPCs;
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
     * Находит локации, связанные с квестом
     */
    private List<Map<String, Object>> findRelevantLocations(
        String campaignId,
        Set<String> questKeywords,
        String currentLocation
    ) {
        List<Map<String, Object>> relevantLocations = new ArrayList<>();
        
        try {
            Campaign campaign = campaignRepository.findBySessionId(campaignId).orElse(null);
            if (campaign == null) {
                return relevantLocations;
            }
            
            // Всегда добавляем текущую локацию
            if (currentLocation != null) {
                for (Location loc : campaign.getLocations()) {
                    if (loc.getName().equalsIgnoreCase(currentLocation)) {
                        Map<String, Object> locMap = new HashMap<>();
                        locMap.put("name", loc.getName());
                        locMap.put("description", loc.getDescription());
                        locMap.put("discovered", loc.getDiscovered());
                        relevantLocations.add(locMap);
                        break;
                    }
                }
            }
            
            // Ищем другие релевантные локации
            for (Location loc : campaign.getLocations()) {
                if (loc.getName().equalsIgnoreCase(currentLocation)) {
                    continue; // Уже добавлена
                }
                
                String name = loc.getName().toLowerCase();
                String description = loc.getDescription() != null ? loc.getDescription().toLowerCase() : "";
                
                boolean isRelevant = questKeywords.stream().anyMatch(name::contains) ||
                                   questKeywords.stream().anyMatch(description::contains);
                
                if (isRelevant) {
                    Map<String, Object> locMap = new HashMap<>();
                    locMap.put("name", loc.getName());
                    locMap.put("description", loc.getDescription());
                    locMap.put("discovered", loc.getDiscovered());
                    relevantLocations.add(locMap);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при поиске релевантных локаций: " + e.getMessage());
        }
        
        return relevantLocations;
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
     * Извлекает релевантные предметы из событий
     */
    private Set<String> extractRelevantItems(List<GameState.GameEvent> events) {
        Set<String> items = new HashSet<>();
        
        // Паттерны для поиска предметов
        String[] itemPatterns = {
            "меч", "кинжал", "щит", "доспех", "кольцо", "амулет", "ключ", "свиток",
            "книга", "карта", "монета", "сокровище", "артефакт", "реликвия", "оружие", "предмет"
        };
        
        for (GameState.GameEvent event : events) {
            String description = event.getDescription().toLowerCase();
            for (String pattern : itemPatterns) {
                if (description.contains(pattern)) {
                    // Пытаемся извлечь название предмета
                    int index = description.indexOf(pattern);
                    if (index > 0) {
                        String context = description.substring(Math.max(0, index - 20), 
                                                             Math.min(description.length(), index + pattern.length() + 20));
                        items.add(context.trim());
                    } else {
                        items.add(pattern);
                    }
                }
            }
        }
        
        return items;
    }
    
    /**
     * Извлекает релевантную информацию о мире
     */
    private Map<String, Object> extractRelevantWorldInfo(Map<String, Object> world, Set<String> keywords) {
        Map<String, Object> relevant = new HashMap<>();
        
        String worldDescription = (String) world.getOrDefault("world_description", "");
        if (worldDescription != null && !worldDescription.isEmpty()) {
            // Проверяем, содержит ли описание мира ключевые слова квеста
            String descLower = worldDescription.toLowerCase();
            boolean isRelevant = keywords.stream().anyMatch(descLower::contains);
            
            if (isRelevant) {
                relevant.put("world_description", worldDescription);
            }
        }
        
        // Добавляем другие релевантные поля мира
        for (Map.Entry<String, Object> entry : world.entrySet()) {
            if (!entry.getKey().equals("world_description") && entry.getValue() != null) {
                String valueStr = entry.getValue().toString().toLowerCase();
                if (keywords.stream().anyMatch(valueStr::contains)) {
                    relevant.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        return relevant;
    }
    
    /**
     * Фильтрует побочные квесты, связанные с основным
     */
    private Map<String, Map<String, Object>> filterRelevantSideQuests(
        Map<String, Map<String, Object>> sideQuests,
        Set<String> questKeywords
    ) {
        Map<String, Map<String, Object>> relevant = new HashMap<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : sideQuests.entrySet()) {
            Map<String, Object> quest = entry.getValue();
            String questStr = quest.toString().toLowerCase();
            
            boolean isRelevant = questKeywords.stream().anyMatch(questStr::contains);
            
            if (isRelevant) {
                relevant.put(entry.getKey(), quest);
            }
        }
        
        return relevant;
    }
    
    /**
     * Класс для хранения релевантного контекста
     */
    public static class RelevantContext {
        private Map<String, Object> activeQuest;
        private String currentQuestStage;
        private Set<String> questKeywords;
        private List<GameState.GameEvent> relevantEvents;
        private List<Map<String, Object>> relevantNPCs;
        private List<Map<String, Object>> relevantLocations;
        private Set<String> relevantItems;
        private Map<String, Object> relevantWorldInfo;
        private Map<String, Map<String, Object>> relevantSideQuests;
        private String currentLocation;
        private String currentSituation;
        
        // Getters and Setters
        public Map<String, Object> getActiveQuest() { return activeQuest; }
        public void setActiveQuest(Map<String, Object> activeQuest) { this.activeQuest = activeQuest; }
        
        public String getCurrentQuestStage() { return currentQuestStage; }
        public void setCurrentQuestStage(String currentQuestStage) { this.currentQuestStage = currentQuestStage; }
        
        public Set<String> getQuestKeywords() { return questKeywords; }
        public void setQuestKeywords(Set<String> questKeywords) { this.questKeywords = questKeywords; }
        
        public List<GameState.GameEvent> getRelevantEvents() { return relevantEvents; }
        public void setRelevantEvents(List<GameState.GameEvent> relevantEvents) { this.relevantEvents = relevantEvents; }
        
        public List<Map<String, Object>> getRelevantNPCs() { return relevantNPCs; }
        public void setRelevantNPCs(List<Map<String, Object>> relevantNPCs) { this.relevantNPCs = relevantNPCs; }
        
        public List<Map<String, Object>> getRelevantLocations() { return relevantLocations; }
        public void setRelevantLocations(List<Map<String, Object>> relevantLocations) { this.relevantLocations = relevantLocations; }
        
        public Set<String> getRelevantItems() { return relevantItems; }
        public void setRelevantItems(Set<String> relevantItems) { this.relevantItems = relevantItems; }
        
        public Map<String, Object> getRelevantWorldInfo() { return relevantWorldInfo; }
        public void setRelevantWorldInfo(Map<String, Object> relevantWorldInfo) { this.relevantWorldInfo = relevantWorldInfo; }
        
        public Map<String, Map<String, Object>> getRelevantSideQuests() { return relevantSideQuests; }
        public void setRelevantSideQuests(Map<String, Map<String, Object>> relevantSideQuests) { this.relevantSideQuests = relevantSideQuests; }
        
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
            
            // Релевантные события (только ключевые)
            if (relevantEvents != null && !relevantEvents.isEmpty()) {
                context.append("=== КЛЮЧЕВЫЕ СОБЫТИЯ КВЕСТА ===\n");
                int count = Math.min(10, relevantEvents.size());
                for (int i = 0; i < count; i++) {
                    GameState.GameEvent event = relevantEvents.get(i);
                    context.append("- [").append(event.getType()).append("] ")
                           .append(event.getDescription()).append("\n");
                }
                context.append("\n");
            }
            
            // Релевантные NPC
            if (relevantNPCs != null && !relevantNPCs.isEmpty()) {
                context.append("=== NPC, СВЯЗАННЫЕ С КВЕСТОМ ===\n");
                for (Map<String, Object> npc : relevantNPCs) {
                    context.append("- ").append(npc.get("name"));
                    if (npc.get("description") != null) {
                        String desc = (String) npc.get("description");
                        if (desc.length() > 100) {
                            desc = desc.substring(0, 100) + "...";
                        }
                        context.append(": ").append(desc);
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            // Релевантные локации
            if (relevantLocations != null && !relevantLocations.isEmpty()) {
                context.append("=== ЛОКАЦИИ, СВЯЗАННЫЕ С КВЕСТОМ ===\n");
                for (Map<String, Object> loc : relevantLocations) {
                    context.append("- ").append(loc.get("name"));
                    if (loc.get("description") != null) {
                        String desc = (String) loc.get("description");
                        if (desc.length() > 80) {
                            desc = desc.substring(0, 80) + "...";
                        }
                        context.append(": ").append(desc);
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            // Релевантные предметы
            if (relevantItems != null && !relevantItems.isEmpty()) {
                context.append("=== ПРЕДМЕТЫ, СВЯЗАННЫЕ С КВЕСТОМ ===\n");
                int count = 0;
                for (String item : relevantItems) {
                    if (count++ >= 5) break; // Ограничиваем количество
                    context.append("- ").append(item).append("\n");
                }
                context.append("\n");
            }
            
            // Релевантная информация о мире
            if (relevantWorldInfo != null && !relevantWorldInfo.isEmpty()) {
                context.append("=== ИНФОРМАЦИЯ О МИРЕ, СВЯЗАННАЯ С КВЕСТОМ ===\n");
                if (relevantWorldInfo.containsKey("world_description")) {
                    String worldDesc = (String) relevantWorldInfo.get("world_description");
                    if (worldDesc.length() > 300) {
                        worldDesc = worldDesc.substring(0, 300) + "...";
                    }
                    context.append(worldDesc).append("\n");
                }
                context.append("\n");
            }
            
            // Побочные квесты
            if (relevantSideQuests != null && !relevantSideQuests.isEmpty()) {
                context.append("=== СВЯЗАННЫЕ ПОБОЧНЫЕ КВЕСТЫ ===\n");
                for (Map<String, Object> sq : relevantSideQuests.values()) {
                    if (sq.containsKey("title")) {
                        context.append("- ").append(sq.get("title")).append("\n");
                    }
                }
                context.append("\n");
            }
            
            return context.toString();
        }
    }
}
