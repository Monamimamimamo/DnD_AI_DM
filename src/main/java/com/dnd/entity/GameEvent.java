package com.dnd.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "game_events")
public class GameEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;
    
    @Column(name = "character_name")
    private String characterName;
    
    @Column(name = "location_name")
    private String locationName; // Название локации для быстрого доступа
    
    // Множественные связи с NPC
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "game_event_npcs",
        joinColumns = @JoinColumn(name = "game_event_id"),
        inverseJoinColumns = @JoinColumn(name = "npc_id")
    )
    private List<NPC> npcs = new ArrayList<>();
    
    // Множественные связи с квестами
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "game_event_quests",
        joinColumns = @JoinColumn(name = "game_event_id"),
        inverseJoinColumns = @JoinColumn(name = "quest_id")
    )
    private List<Quest> quests = new ArrayList<>();
    
    // Множественные связи с локациями
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "game_event_locations",
        joinColumns = @JoinColumn(name = "game_event_id"),
        inverseJoinColumns = @JoinColumn(name = "location_id")
    )
    private List<Location> locations = new ArrayList<>();
    
    @Column(name = "full_text", columnDefinition = "TEXT")
    private String fullText; // Полный текст сообщения от LLM
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }
    
    public GameEvent() {
    }
    
    public GameEvent(String eventType, String description, String characterName, Campaign campaign) {
        this.eventType = eventType;
        this.description = description;
        this.characterName = characterName;
        this.campaign = campaign;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Campaign getCampaign() {
        return campaign;
    }
    
    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getCharacterName() {
        return characterName;
    }
    
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
    
    public String getFullText() {
        return fullText;
    }
    
    public void setFullText(String fullText) {
        this.fullText = fullText;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // Getters and Setters для множественных связей
    public List<NPC> getNpcs() {
        return npcs;
    }
    
    public void setNpcs(List<NPC> npcs) {
        this.npcs = npcs != null ? npcs : new ArrayList<>();
    }
    
    public void addNpc(NPC npc) {
        if (npc != null && !this.npcs.contains(npc)) {
            this.npcs.add(npc);
        }
    }
    
    public void removeNpc(NPC npc) {
        this.npcs.remove(npc);
    }
    
    public List<Quest> getQuests() {
        return quests;
    }
    
    public void setQuests(List<Quest> quests) {
        this.quests = quests != null ? quests : new ArrayList<>();
    }
    
    public void addQuest(Quest quest) {
        if (quest != null && !this.quests.contains(quest)) {
            this.quests.add(quest);
        }
    }
    
    public void removeQuest(Quest quest) {
        this.quests.remove(quest);
    }
    
    public List<Location> getLocations() {
        return locations;
    }
    
    public void setLocations(List<Location> locations) {
        this.locations = locations != null ? locations : new ArrayList<>();
    }
    
    public void addLocation(Location location) {
        if (location != null && !this.locations.contains(location)) {
            this.locations.add(location);
        }
    }
    
    public void removeLocation(Location location) {
        this.locations.remove(location);
    }
}

