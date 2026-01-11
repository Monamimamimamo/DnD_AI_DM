package com.dnd.api;

import com.dnd.ai_engine.DungeonMasterAI;
import com.dnd.game_state.Character;
import com.dnd.game_state.GameManager;
import com.dnd.game_state.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Сервис для управления кампаниями
 */
@Service
public class CampaignService {
    
    @Autowired
    private DungeonMasterAI dungeonMasterAI;
    
    @Autowired
    private GameManager gameManager;
    
    /**
     * Создать новую кампанию (только структура, без генерации сцены)
     */
    public Map<String, Object> createCampaign(String sessionId) {
        // Если сессия уже существует, загружаем ее
        if (sessionId != null && gameManager.gameExists(sessionId)) {
            GameState existingGame = gameManager.loadGame(sessionId);
            if (existingGame != null) {
                gameManager.setCurrentGame(existingGame);
                dungeonMasterAI.setCurrentGame(existingGame);
                Map<String, Object> result = new HashMap<>();
                result.put("session_id", existingGame.getSessionId());
                result.put("current_location", existingGame.getCurrentLocation());
                result.put("main_quest", existingGame.getMainQuest());
                return result;
            }
        }
        
        // Создаем новую игру без генерации сцены
        GameState newGame = gameManager.startNewGame(sessionId);
        gameManager.setCurrentGame(newGame);
        dungeonMasterAI.setCurrentGame(newGame);
        gameManager.saveGame();
        
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", newGame.getSessionId());
        result.put("main_quest", null); // Квест будет сгенерирован при начале
        return result;
    }
    
    /**
     * Начать кампанию (генерирует начальную сцену и квест)
     */
    public Map<String, Object> startCampaign(String sessionId, Consumer<String> progressCallback) {
        GameState game = gameManager.loadGame(sessionId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + sessionId);
        }
        
        gameManager.setCurrentGame(game);
        dungeonMasterAI.setCurrentGame(game);
        
        // Генерируем начальную сцену и квест
        return dungeonMasterAI.startNewCampaign(sessionId, progressCallback);
    }
    
    /**
     * Создать новую кампанию (legacy метод для совместимости)
     */
    public Map<String, Object> startNewCampaign(String sessionId, Consumer<String> progressCallback) {
        return startCampaign(sessionId, progressCallback);
    }
    
    /**
     * Добавить персонажа в кампанию
     */
    public void addCharacter(String campaignId, Character character) {
        GameState game = gameManager.loadGame(campaignId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
        }
        gameManager.setCurrentGame(game);
        dungeonMasterAI.setCurrentGame(game);
        dungeonMasterAI.addCharacter(character);
        gameManager.saveGame();
    }
    
    /**
     * Обработать действие игрока
     */
    public Map<String, Object> processAction(String campaignId, String action, String characterName) {
        GameState game = gameManager.loadGame(campaignId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
        }
        gameManager.setCurrentGame(game);
        dungeonMasterAI.setCurrentGame(game);
        Map<String, Object> result = dungeonMasterAI.processAction(action, characterName);
        gameManager.saveGame();
        return result;
    }
    
    /**
     * Сгенерировать ситуацию
     */
    public String generateSituation(String campaignId, String characterName, Consumer<String> progressCallback) {
        GameState game = gameManager.loadGame(campaignId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
        }
        gameManager.setCurrentGame(game);
        dungeonMasterAI.setCurrentGame(game);
        String situation = dungeonMasterAI.generateSituation(characterName, progressCallback);
        gameManager.saveGame();
        return situation;
    }
    
    /**
     * Получить статус игры
     */
    public Map<String, Object> getGameStatus(String campaignId) {
        GameState game = gameManager.loadGame(campaignId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
        }
        dungeonMasterAI.setCurrentGame(game);
        return dungeonMasterAI.getGameStatus();
    }
    
    /**
     * Убедиться, что кампания загружена в DungeonMasterAI
     */
    public void ensureCampaignLoaded(String campaignId) {
        if (campaignId == null || campaignId.isEmpty()) {
            throw new IllegalArgumentException("Campaign ID не может быть пустым");
        }
        
        // Загружаем игру в GameManager
        GameState currentGame = gameManager.getCurrentGame();
        if (currentGame == null || !campaignId.equals(currentGame.getSessionId())) {
            GameState loadedGame = gameManager.loadGame(campaignId);
            if (loadedGame == null) {
                throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
            }
        }
        
        GameState loadedGame = gameManager.getCurrentGame();
        if (loadedGame == null) {
            loadedGame = gameManager.loadGame(campaignId);
            if (loadedGame == null) {
                throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
            }
        }
        dungeonMasterAI.setCurrentGame(loadedGame);
    }
    
    /**
     * Проверить существование кампании
     */
    public boolean campaignExists(String campaignId) {
        return gameManager.gameExists(campaignId);
    }
    
    /**
     * Получить GameState для кампании
     */
    public GameState getGameState(String campaignId) {
        ensureCampaignLoaded(campaignId);
        return gameManager.getCurrentGame();
    }
}

