package com.dnd.game_state;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Менеджер для управления состоянием игры
 */
public class GameManager {
    private static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
        .create();
    private final String dbPath;
    private GameState currentGame;

    public GameManager() {
        this(getDbPathFromEnv());
    }

    public GameManager(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }
    
    private static String getDbPathFromEnv() {
        String dbPath = System.getenv("GAME_DB_PATH");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = System.getProperty("game.db.path");
        }
        if (dbPath == null || dbPath.isEmpty()) {
            // По умолчанию используем /app/data в Docker или текущую директорию локально
            String dataDir = System.getenv("GAME_DATA_DIR");
            if (dataDir == null || dataDir.isEmpty()) {
                dataDir = System.getProperty("game.data.dir", ".");
            }
            dbPath = dataDir + "/game_data.db";
        }
        return dbPath;
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // Таблица для сохранения игр
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS games (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT UNIQUE,
                    created_at TIMESTAMP,
                    game_data TEXT
                )
            """);
            
            // Таблица для персонажей
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS characters (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT,
                    character_data TEXT,
                    FOREIGN KEY (session_id) REFERENCES games (session_id)
                )
            """);
        } catch (SQLException e) {
            System.err.println("Ошибка инициализации БД: " + e.getMessage());
        }
    }

    public GameState startNewGame(String sessionId) {
        if (sessionId == null) {
            sessionId = "game_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        }
        
        currentGame = new GameState();
        currentGame.setSessionId(sessionId);
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
        if (updates.containsKey("current_scene")) {
            currentGame.setCurrentScene((String) updates.get("current_scene"));
        }
        if (updates.containsKey("game_mode")) {
            currentGame.setGameMode((String) updates.get("game_mode"));
        }
        
        saveGame();
    }

    public void saveGame() {
        if (currentGame == null) return;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            String gameData = gson.toJson(currentGame);
            
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO games (session_id, created_at, game_data) VALUES (?, ?, ?)")) {
                stmt.setString(1, currentGame.getSessionId());
                stmt.setString(2, currentGame.getCreatedAt().toString());
                stmt.setString(3, gameData);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Ошибка сохранения игры: " + e.getMessage());
        }
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
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT game_data FROM games WHERE session_id = ?")) {
                stmt.setString(1, sessionId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String gameData = rs.getString("game_data");
                    currentGame = gson.fromJson(gameData, GameState.class);
                    return currentGame;
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка загрузки игры: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Проверить существование игры по session_id
     */
    public boolean gameExists(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM games WHERE session_id = ?")) {
                stmt.setString(1, sessionId);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Ошибка проверки игры: " + e.getMessage());
        }
        
        return false;
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
