package com.dnd.identity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity для пользователя
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true),
    @Index(name = "idx_email", columnList = "email", unique = true)
})
public class User {
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Column(name = "username", unique = true, nullable = false, length = 100)
    private String username;
    
    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;
    
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "character_ids", columnDefinition = "jsonb")
    private List<String> characterIds = new ArrayList<>();
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "campaign_ids", columnDefinition = "jsonb")
    private List<String> campaignIds = new ArrayList<>();
    
    public User() {
        this.createdAt = LocalDateTime.now();
    }
    
    public User(String username, String email, String passwordHash) {
        this();
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    
    public List<String> getCharacterIds() { return characterIds; }
    public void setCharacterIds(List<String> characterIds) { this.characterIds = characterIds != null ? characterIds : new ArrayList<>(); }
    
    public List<String> getCampaignIds() { return campaignIds; }
    public void setCampaignIds(List<String> campaignIds) { this.campaignIds = campaignIds != null ? campaignIds : new ArrayList<>(); }
    
    public void addCharacterId(String characterId) {
        if (!characterIds.contains(characterId)) {
            characterIds.add(characterId);
        }
    }
    
    public void addCampaignId(String campaignId) {
        if (!campaignIds.contains(campaignId)) {
            campaignIds.add(campaignId);
        }
    }
}
