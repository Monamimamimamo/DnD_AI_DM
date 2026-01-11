package com.dnd.game_state;

import com.dnd.messages.GameContext;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Состояние текущей игры
 */
public class GameState {
    private List<Character> characters = new ArrayList<>();
    private String currentScene = "";
    private String currentLocation = "Неизвестная локация";
    private List<GameEvent> gameHistory = new ArrayList<>();
    private String currentSituation = "";
    private List<Map<String, Object>> npcs = new ArrayList<>();
    private List<Map<String, Object>> quests = new ArrayList<>();
    private String gameMode = "story"; // "story" или "combat"
    private String sessionId = "";
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Система сюжета
    private Map<String, Object> mainQuest = null;
    private int storyProgress = 0; // 0-100
    private boolean storyCompleted = false;
    
    // Информация о мире кампании
    private Map<String, Object> world = null;

    private List<User> users = new ArrayList<>();

    // Система флагов и состояний для отслеживания событий
    private Map<String, Object> campaignFlags = new HashMap<>(); // Глобальные флаги кампании
    private Map<String, Map<String, Object>> locationStates = new HashMap<>(); // Состояния локаций
    private Map<String, Integer> npcRelationships = new HashMap<>(); // Отношения с NPC (от -5 до +5)
    private Set<String> discoveredLocations = new HashSet<>(); // Открытые локации
    private Map<String, Map<String, Object>> sideQuests = new HashMap<>(); // Побочные квесты
    private Map<String, LocalDateTime> lastEventTimes = new HashMap<>(); // Время последних событий по типам
    private GameContext gameContext; // Контекст игры для валидации сообщений

    public void addCharacter(Character character) {
        characters.add(character);
    }

    public Character getCharacter(String name) {
        return characters.stream()
            .filter(c -> c.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public void addGameEvent(String eventType, String description, String characterName) {
        gameHistory.add(new GameEvent(LocalDateTime.now(), eventType, description, characterName));
    }

    public String getRecentContext(int limit) {
        int start = Math.max(0, gameHistory.size() - limit);
        List<GameEvent> recent = gameHistory.subList(start, gameHistory.size());
        
        StringBuilder sb = new StringBuilder();
        for (GameEvent event : recent) {
            sb.append("[").append(event.getType()).append("] ")
              .append(event.getDescription()).append("\n");
        }
        return sb.toString().trim();
    }

    public String getCurrentQuestStage() {
        if (mainQuest == null) return null;
        List<String> stages = (List<String>) mainQuest.get("stages");
        int currentStageIndex = getIntValue(mainQuest.get("current_stage_index"), 0);
        if (stages != null && currentStageIndex < stages.size()) {
            return stages.get(currentStageIndex);
        }
        return null;
    }

    public void advanceQuestStage() {
        if (mainQuest == null) return;
        
        List<String> stages = (List<String>) mainQuest.get("stages");
        int currentStageIndex = getIntValue(mainQuest.get("current_stage_index"), 0);
        
        if (stages != null && currentStageIndex < stages.size() - 1) {
            mainQuest.put("current_stage_index", currentStageIndex + 1);
            storyProgress = (int) ((currentStageIndex + 1.0) / stages.size() * 100);
        } else {
            mainQuest.put("completed", true);
            storyProgress = 100;
            storyCompleted = true;
        }
    }
    
    /**
     * Безопасное преобразование значения в int
     * Обрабатывает как Integer, так и Double (из JSON десериализации)
     */
    private int getIntValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    // Getters and Setters
    public List<Character> getCharacters() { return characters; }
    public void setCharacters(List<Character> characters) { this.characters = characters; }

    public String getCurrentScene() { return currentScene; }
    public void setCurrentScene(String currentScene) { this.currentScene = currentScene; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public List<GameEvent> getGameHistory() { return gameHistory; }
    public void setGameHistory(List<GameEvent> gameHistory) { this.gameHistory = gameHistory; }

    public String getCurrentSituation() { return currentSituation; }
    public void setCurrentSituation(String currentSituation) { this.currentSituation = currentSituation; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> getMainQuest() { return mainQuest; }
    public void setMainQuest(Map<String, Object> mainQuest) { 
        this.mainQuest = mainQuest;
        if (mainQuest != null && !mainQuest.containsKey("current_stage_index")) {
            mainQuest.put("current_stage_index", 0);
        }
        if (mainQuest != null && !mainQuest.containsKey("completed")) {
            mainQuest.put("completed", false);
        }
    }

    public int getStoryProgress() { return storyProgress; }
    public void setStoryProgress(int storyProgress) { 
        this.storyProgress = Math.max(0, Math.min(100, storyProgress));
        if (this.storyProgress >= 100) {
            storyCompleted = true;
        }
    }

    public boolean isStoryCompleted() {
        // Проверяем также флаг в mainQuest, так как он может быть установлен при десериализации
        if (mainQuest != null && mainQuest.containsKey("completed")) {
            Object completed = mainQuest.get("completed");
            if (completed instanceof Boolean) {
                storyCompleted = (Boolean) completed;
            } else if (completed instanceof String) {
                storyCompleted = Boolean.parseBoolean((String) completed);
            }
        }
        return storyCompleted;
    }
    
    public void setStoryCompleted(boolean storyCompleted) { 
        this.storyCompleted = storyCompleted;
        if (mainQuest != null) {
            mainQuest.put("completed", storyCompleted);
        }
    }

    public Map<String, Object> getWorld() { return world; }
    public void setWorld(Map<String, Object> world) { this.world = world; }

    public List<User> getUsers() {
        // Возвращаем список пользователей, участвующих в игре
        return users;
    }
    
    // Методы для работы с флагами кампании
    public void setCampaignFlag(String flag, Object value) {
        campaignFlags.put(flag, value);
    }
    
    public Object getCampaignFlag(String flag) {
        return campaignFlags.get(flag);
    }
    
    public boolean hasCampaignFlag(String flag) {
        return campaignFlags.containsKey(flag);
    }
    
    public Map<String, Object> getCampaignFlags() {
        return campaignFlags;
    }
    
    // Методы для работы с состояниями локаций
    public void setLocationState(String location, String stateKey, Object value) {
        locationStates.computeIfAbsent(location, k -> new HashMap<>()).put(stateKey, value);
    }
    
    public Object getLocationState(String location, String stateKey) {
        Map<String, Object> state = locationStates.get(location);
        return state != null ? state.get(stateKey) : null;
    }
    
    public Map<String, Object> getLocationState(String location) {
        return locationStates.getOrDefault(location, new HashMap<>());
    }
    
    // Методы для работы с отношениями NPC
    public void setNPCRelationship(String npcName, int relationship) {
        // Ограничиваем значения от -5 до +5
        npcRelationships.put(npcName, Math.max(-5, Math.min(5, relationship)));
    }
    
    public int getNPCRelationship(String npcName) {
        return npcRelationships.getOrDefault(npcName, 0);
    }
    
    public void modifyNPCRelationship(String npcName, int delta) {
        int current = getNPCRelationship(npcName);
        setNPCRelationship(npcName, current + delta);
    }
    
    // Методы для работы с открытыми локациями
    public void addDiscoveredLocation(String location) {
        discoveredLocations.add(location);
    }
    
    public boolean isLocationDiscovered(String location) {
        return discoveredLocations.contains(location);
    }
    
    public Set<String> getDiscoveredLocations() {
        return discoveredLocations;
    }
    
    // Методы для работы с побочными квестами
    public void addSideQuest(String questId, Map<String, Object> quest) {
        sideQuests.put(questId, quest);
    }
    
    public Map<String, Object> getSideQuest(String questId) {
        return sideQuests.get(questId);
    }
    
    public Map<String, Map<String, Object>> getSideQuests() {
        return sideQuests;
    }
    
    // Методы для отслеживания времени событий
    public void setLastEventTime(String eventType, LocalDateTime time) {
        lastEventTimes.put(eventType, time);
    }
    
    public LocalDateTime getLastEventTime(String eventType) {
        return lastEventTimes.get(eventType);
    }
    
    // Методы для работы с GameContext
    public GameContext getGameContext() {
        return gameContext;
    }
    
    public void setGameContext(GameContext gameContext) {
        this.gameContext = gameContext;
    }

    public static class GameEvent {
        private LocalDateTime timestamp;
        private String type;
        private String description;
        private String character;

        public GameEvent(LocalDateTime timestamp, String type, String description, String character) {
            this.timestamp = timestamp;
            this.type = type;
            this.description = description;
            this.character = character;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public String getCharacter() { return character; }
    }

    public static class User {
        private String name;
        private Character character;

        public User(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Character getCharacter() {
            return character;
        }

        public void setCharacter(Character character) {
            this.character = character;
        }
    }
}
