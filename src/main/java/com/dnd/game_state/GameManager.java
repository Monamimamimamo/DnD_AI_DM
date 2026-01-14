package com.dnd.game_state;

import com.dnd.service.GameStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Менеджер для управления состоянием игры (работает через JPA/PostgreSQL)
 */
@Component
public class GameManager {
    private GameState currentGame;
    
    @Autowired
    private GameStateService gameStateService;

    public GameManager() {
    }

    public GameState startNewGame(String sessionId) {
        currentGame = gameStateService.createNewGame(sessionId);
        return currentGame;
    }

    public void addCharacterToGame(Character character) {
        if (currentGame != null) {
            currentGame.addCharacter(character);
            saveGame();
        }
    }

    public Map<String, Object> processPlayerAction(String action, String characterName) {
        if (currentGame == null) {
            return Map.of("error", "Нет активной игры");
        }
        
        currentGame.addGameEvent("player_action", action, characterName);
        
        String context = currentGame.getRecentContext(5);
        Map<String, Object> characterInfo = new HashMap<>();
        
        if (characterName != null && !characterName.isEmpty()) {
            Character character = currentGame.getCharacter(characterName);
            if (character != null) {
                // Преобразуем Character в Map (упрощенно)
                characterInfo.put("name", character.getName());
            }
        }
        
        return Map.of(
            "context", context,
            "character_info", characterInfo,
            "current_location", currentGame.getCurrentLocation(),
            "game_mode", currentGame.getGameMode()
        );
    }

    public void updateGameState(Map<String, Object> updates) {
        if (currentGame == null) return;
        
        if (updates.containsKey("current_location")) {
            currentGame.setCurrentLocation((String) updates.get("current_location"));
        }
        if (updates.containsKey("game_mode")) {
            currentGame.setGameMode((String) updates.get("game_mode"));
        }
        
        saveGame();
    }

    public void saveGame() {
        if (currentGame == null) return;
        gameStateService.saveGameState(currentGame);
    }

    public GameState getCurrentGame() {
        return currentGame;
    }
    
    public void setCurrentGame(GameState game) {
        this.currentGame = game;
    }
    
    /**
     * Загрузить игру по session_id
     */
    public GameState loadGame(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        
        currentGame = gameStateService.loadGameState(sessionId);
        return currentGame;
    }
    
    /**
     * Проверить существование игры по session_id
     */
    public boolean gameExists(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return gameStateService.gameExists(sessionId);
    }

    public List<Character> getCharacters() {
        if (currentGame == null) {
            return Collections.emptyList();
        }
        return currentGame.getCharacters();
    }

    public boolean haveAllUsersCreatedCharacters() {
        if (currentGame == null) {
            return false;
        }
        // Проверяем, что у всех пользователей есть персонажи
        return currentGame.getUsers().stream()
            .allMatch(user -> user.getCharacter() != null);
    }
}
