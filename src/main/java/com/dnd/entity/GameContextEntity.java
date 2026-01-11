package com.dnd.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "game_contexts")
public class GameContextEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false, unique = true)
    private Campaign campaign;
    
    @Column(name = "current_state")
    private String currentState;
    
    @Column(name = "current_location")
    private String currentLocation;
    
    @Column(name = "last_message_type")
    private String lastMessageType;
    
    @Column(name = "last_state_change")
    private LocalDateTime lastStateChange;
    
    @Column(name = "in_dialogue")
    private Boolean inDialogue = false;
    
    @Column(name = "dialogue_npc")
    private String dialogueNPC;
    
    @Column(name = "action_count")
    private Integer actionCount = 0;
    
    @Column(name = "last_event_time")
    private LocalDateTime lastEventTime;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastStateChange == null) {
            lastStateChange = LocalDateTime.now();
        }
        if (lastEventTime == null) {
            lastEventTime = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public GameContextEntity() {
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
    
    public String getCurrentState() {
        return currentState;
    }
    
    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }
    
    public String getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }
    
    public String getLastMessageType() {
        return lastMessageType;
    }
    
    public void setLastMessageType(String lastMessageType) {
        this.lastMessageType = lastMessageType;
    }
    
    public LocalDateTime getLastStateChange() {
        return lastStateChange;
    }
    
    public void setLastStateChange(LocalDateTime lastStateChange) {
        this.lastStateChange = lastStateChange;
    }
    
    public Boolean getInDialogue() {
        return inDialogue;
    }
    
    public void setInDialogue(Boolean inDialogue) {
        this.inDialogue = inDialogue;
    }
    
    public String getDialogueNPC() {
        return dialogueNPC;
    }
    
    public void setDialogueNPC(String dialogueNPC) {
        this.dialogueNPC = dialogueNPC;
    }
    
    public Integer getActionCount() {
        return actionCount;
    }
    
    public void setActionCount(Integer actionCount) {
        this.actionCount = actionCount;
    }
    
    public LocalDateTime getLastEventTime() {
        return lastEventTime;
    }
    
    public void setLastEventTime(LocalDateTime lastEventTime) {
        this.lastEventTime = lastEventTime;
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
}

