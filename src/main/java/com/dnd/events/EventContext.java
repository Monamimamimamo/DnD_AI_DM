package com.dnd.events;

import com.dnd.game_state.GameState;
import java.util.List;
import java.util.Map;

/**
 * Контекст для генерации события
 */
public class EventContext {
    private GameState gameState;
    private String currentLocation;
    private String currentSituation;
    private Map<String, Object> world;
    private Map<String, Object> mainQuest;
    private List<GameState.GameEvent> recentHistory;
    private Map<String, Object> historyAnalysis; // Результаты анализа истории
    private Map<String, Object> connections; // Связи с прошлыми событиями
    
    public EventContext(GameState gameState, String currentLocation, String currentSituation,
                       Map<String, Object> world, Map<String, Object> mainQuest,
                       List<GameState.GameEvent> recentHistory,
                       Map<String, Object> historyAnalysis, Map<String, Object> connections) {
        this.gameState = gameState;
        this.currentLocation = currentLocation;
        this.currentSituation = currentSituation;
        this.world = world;
        this.mainQuest = mainQuest;
        this.recentHistory = recentHistory;
        this.historyAnalysis = historyAnalysis;
        this.connections = connections;
    }
    
    // Getters
    public GameState getGameState() { return gameState; }
    public String getCurrentLocation() { return currentLocation; }
    public String getCurrentSituation() { return currentSituation; }
    public Map<String, Object> getWorld() { return world; }
    public Map<String, Object> getMainQuest() { return mainQuest; }
    public List<GameState.GameEvent> getRecentHistory() { return recentHistory; }
    public Map<String, Object> getHistoryAnalysis() { return historyAnalysis; }
    public Map<String, Object> getConnections() { return connections; }
}

