package com.dnd.identity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Модель пользователя
 */
public class User {
    private String id;
    private String username;
    private String email;
    private String passwordHash; // bcrypt hash
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private List<String> characterIds; // ID персонажей пользователя
    private List<String> campaignIds; // ID кампаний, где пользователь участвует
    
    public User() {
        this.characterIds = new ArrayList<>();
        this.campaignIds = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }
    
    public User(String username, String email, String passwordHash) {
        this();
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
    public void setCharacterIds(List<String> characterIds) { this.characterIds = characterIds; }
    
    public List<String> getCampaignIds() { return campaignIds; }
    public void setCampaignIds(List<String> campaignIds) { this.campaignIds = campaignIds; }
    
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

