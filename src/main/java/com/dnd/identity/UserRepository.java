package com.dnd.identity;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы с пользователями в SQLite
 */
@Repository
public class UserRepository {
    private static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
        .create();
    private final String dbPath;
    
    public UserRepository() {
        this(getDbPathFromEnv());
    }
    
    public UserRepository(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }
    
    private static String getDbPathFromEnv() {
        String dbPath = System.getenv("IDENTITY_DB_PATH");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = System.getProperty("identity.db.path");
        }
        if (dbPath == null || dbPath.isEmpty()) {
            // По умолчанию используем /app/data в Docker или текущую директорию локально
            String dataDir = System.getenv("GAME_DATA_DIR");
            if (dataDir == null || dataDir.isEmpty()) {
                dataDir = System.getProperty("game.data.dir", ".");
            }
            dbPath = dataDir + "/identity.db";
        }
        return dbPath;
    }
    
    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    created_at TIMESTAMP,
                    last_login_at TIMESTAMP,
                    character_ids TEXT,
                    campaign_ids TEXT
                )
            """);
            
            conn.createStatement().execute("""
                CREATE INDEX IF NOT EXISTS idx_username ON users(username)
            """);
            
            conn.createStatement().execute("""
                CREATE INDEX IF NOT EXISTS idx_email ON users(email)
            """);
        } catch (SQLException e) {
            System.err.println("Ошибка инициализации БД пользователей: " + e.getMessage());
        }
    }
    
    public User createUser(String username, String email, String passwordHash) {
        String id = UUID.randomUUID().toString();
        User user = new User(username, email, passwordHash);
        user.setId(id);
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (id, username, email, password_hash, created_at, character_ids, campaign_ids) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, user.getId());
                stmt.setString(2, user.getUsername());
                stmt.setString(3, user.getEmail());
                stmt.setString(4, user.getPasswordHash());
                stmt.setString(5, user.getCreatedAt().toString());
                stmt.setString(6, gson.toJson(user.getCharacterIds()));
                stmt.setString(7, gson.toJson(user.getCampaignIds()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Ошибка создания пользователя: " + e.getMessage());
            throw new RuntimeException("Не удалось создать пользователя", e);
        }
        
        return user;
    }
    
    public User findByUsername(String username) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM users WHERE username = ?")) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка поиска пользователя: " + e.getMessage());
        }
        return null;
    }
    
    public User findByEmail(String email) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM users WHERE email = ?")) {
                stmt.setString(1, email);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка поиска пользователя: " + e.getMessage());
        }
        return null;
    }
    
    public User findById(String id) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM users WHERE id = ?")) {
                stmt.setString(1, id);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка поиска пользователя: " + e.getMessage());
        }
        return null;
    }
    
    public void updateUser(User user) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET username = ?, email = ?, password_hash = ?, last_login_at = ?, character_ids = ?, campaign_ids = ? WHERE id = ?")) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, user.getEmail());
                stmt.setString(3, user.getPasswordHash());
                stmt.setString(4, user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
                stmt.setString(5, gson.toJson(user.getCharacterIds()));
                stmt.setString(6, gson.toJson(user.getCampaignIds()));
                stmt.setString(7, user.getId());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Ошибка обновления пользователя: " + e.getMessage());
            throw new RuntimeException("Не удалось обновить пользователя", e);
        }
    }
    
    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        
        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            user.setCreatedAt(LocalDateTime.parse(createdAtStr));
        }
        
        String lastLoginAtStr = rs.getString("last_login_at");
        if (lastLoginAtStr != null) {
            user.setLastLoginAt(LocalDateTime.parse(lastLoginAtStr));
        }
        
        String characterIdsStr = rs.getString("character_ids");
        if (characterIdsStr != null && !characterIdsStr.isEmpty()) {
            List<String> characterIds = gson.fromJson(characterIdsStr, new TypeToken<List<String>>(){}.getType());
            user.setCharacterIds(characterIds != null ? characterIds : new ArrayList<>());
        }
        
        String campaignIdsStr = rs.getString("campaign_ids");
        if (campaignIdsStr != null && !campaignIdsStr.isEmpty()) {
            List<String> campaignIds = gson.fromJson(campaignIdsStr, new TypeToken<List<String>>(){}.getType());
            user.setCampaignIds(campaignIds != null ? campaignIds : new ArrayList<>());
        }
        
        return user;
    }
}

