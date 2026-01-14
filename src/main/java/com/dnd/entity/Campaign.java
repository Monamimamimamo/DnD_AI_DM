package com.dnd.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.dnd.entity.PlayerMessage;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "campaigns")
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", unique = true, nullable = false)
    private String sessionId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "current_location")
    private String currentLocation;
    
    @Column(name = "current_situation", columnDefinition = "TEXT")
    private String currentSituation;
    
    @Column(name = "current_scene")
    private String currentScene;
    
    @Column(name = "game_mode")
    private String gameMode = "story";
    
    @Column(name = "story_progress")
    private Integer storyProgress = 0;
    
    @Column(name = "story_completed")
    private Boolean storyCompleted = false;
    
    // Связи
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterEntity> characters = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameEvent> gameEvents = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerMessage> playerMessages = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Location> locations = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NPC> npcs = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Quest> quests = new ArrayList<>();
    
    @OneToOne(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private World world;
    
    @OneToOne(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private GameContextEntity gameContext;
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampaignFlag> campaignFlags = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LocationState> locationStates = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NPCRelationship> npcRelationships = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiscoveredLocation> discoveredLocations = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SideQuest> sideQuests = new ArrayList<>();
    
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LastEventTime> lastEventTimes = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    public Campaign() {
    }
    
    public Campaign(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }
    
    public String getCurrentSituation() {
        return currentSituation;
    }
    
    public void setCurrentSituation(String currentSituation) {
        this.currentSituation = currentSituation;
    }
    
    public String getCurrentScene() {
        return currentScene;
    }
    
    public void setCurrentScene(String currentScene) {
        this.currentScene = currentScene;
    }
    
    public String getGameMode() {
        return gameMode;
    }
    
    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }
    
    public Integer getStoryProgress() {
        return storyProgress;
    }
    
    public void setStoryProgress(Integer storyProgress) {
        this.storyProgress = storyProgress;
    }
    
    public Boolean getStoryCompleted() {
        return storyCompleted;
    }
    
    public void setStoryCompleted(Boolean storyCompleted) {
        this.storyCompleted = storyCompleted;
    }
    
    public List<CharacterEntity> getCharacters() {
        return characters;
    }
    
    public void setCharacters(List<CharacterEntity> characters) {
        this.characters = characters;
    }
    
    public List<GameEvent> getGameEvents() {
        return gameEvents;
    }
    
    public void setGameEvents(List<GameEvent> gameEvents) {
        this.gameEvents = gameEvents;
    }
    
    public List<Location> getLocations() {
        return locations;
    }
    
    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }
    
    public List<NPC> getNpcs() {
        return npcs;
    }
    
    public void setNpcs(List<NPC> npcs) {
        this.npcs = npcs;
    }
    
    public List<Quest> getQuests() {
        return quests;
    }
    
    public void setQuests(List<Quest> quests) {
        this.quests = quests;
    }
    
    public World getWorld() {
        return world;
    }
    
    public void setWorld(World world) {
        this.world = world;
    }
    
    public GameContextEntity getGameContext() {
        return gameContext;
    }
    
    public void setGameContext(GameContextEntity gameContext) {
        this.gameContext = gameContext;
    }
    
    public List<CampaignFlag> getCampaignFlags() {
        return campaignFlags;
    }
    
    public void setCampaignFlags(List<CampaignFlag> campaignFlags) {
        this.campaignFlags = campaignFlags;
    }
    
    public List<LocationState> getLocationStates() {
        return locationStates;
    }
    
    public void setLocationStates(List<LocationState> locationStates) {
        this.locationStates = locationStates;
    }
    
    public List<NPCRelationship> getNpcRelationships() {
        return npcRelationships;
    }
    
    public void setNpcRelationships(List<NPCRelationship> npcRelationships) {
        this.npcRelationships = npcRelationships;
    }
    
    public List<DiscoveredLocation> getDiscoveredLocations() {
        return discoveredLocations;
    }
    
    public void setDiscoveredLocations(List<DiscoveredLocation> discoveredLocations) {
        this.discoveredLocations = discoveredLocations;
    }
    
    public List<SideQuest> getSideQuests() {
        return sideQuests;
    }
    
    public void setSideQuests(List<SideQuest> sideQuests) {
        this.sideQuests = sideQuests;
    }
    
    public List<LastEventTime> getLastEventTimes() {
        return lastEventTimes;
    }
    
    public void setLastEventTimes(List<LastEventTime> lastEventTimes) {
        this.lastEventTimes = lastEventTimes;
    }
    
    public List<PlayerMessage> getPlayerMessages() {
        return playerMessages;
    }
    
    public void setPlayerMessages(List<PlayerMessage> playerMessages) {
        this.playerMessages = playerMessages;
    }
}

