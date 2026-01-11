package com.dnd.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "characters")
public class Character {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "character_class", nullable = false)
    private String characterClass; // Enum как строка
    
    @Column(name = "race", nullable = false)
    private String race; // Enum как строка
    
    @Column(name = "level")
    private Integer level = 1;
    
    // Ability Scores
    @Column(name = "strength")
    private Integer strength = 10;
    
    @Column(name = "dexterity")
    private Integer dexterity = 10;
    
    @Column(name = "constitution")
    private Integer constitution = 10;
    
    @Column(name = "intelligence")
    private Integer intelligence = 10;
    
    @Column(name = "wisdom")
    private Integer wisdom = 10;
    
    @Column(name = "charisma")
    private Integer charisma = 10;
    
    @Column(name = "hit_points")
    private Integer hitPoints = 0;
    
    @Column(name = "max_hit_points")
    private Integer maxHitPoints = 0;
    
    @Column(name = "armor_class")
    private Integer armorClass = 10;
    
    @Column(name = "speed")
    private Integer speed = 30;
    
    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills; // JSON Map<String, Integer>
    
    @Column(name = "spells", columnDefinition = "TEXT")
    private String spells; // JSON List<String>
    
    @Column(name = "equipment", columnDefinition = "TEXT")
    private String equipment; // JSON List<String>
    
    @Column(name = "background")
    private String background = "";
    
    @Column(name = "alignment")
    private String alignment = "neutral";
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public Character() {
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCharacterClass() {
        return characterClass;
    }
    
    public void setCharacterClass(String characterClass) {
        this.characterClass = characterClass;
    }
    
    public String getRace() {
        return race;
    }
    
    public void setRace(String race) {
        this.race = race;
    }
    
    public Integer getLevel() {
        return level;
    }
    
    public void setLevel(Integer level) {
        this.level = level;
    }
    
    public Integer getStrength() {
        return strength;
    }
    
    public void setStrength(Integer strength) {
        this.strength = strength;
    }
    
    public Integer getDexterity() {
        return dexterity;
    }
    
    public void setDexterity(Integer dexterity) {
        this.dexterity = dexterity;
    }
    
    public Integer getConstitution() {
        return constitution;
    }
    
    public void setConstitution(Integer constitution) {
        this.constitution = constitution;
    }
    
    public Integer getIntelligence() {
        return intelligence;
    }
    
    public void setIntelligence(Integer intelligence) {
        this.intelligence = intelligence;
    }
    
    public Integer getWisdom() {
        return wisdom;
    }
    
    public void setWisdom(Integer wisdom) {
        this.wisdom = wisdom;
    }
    
    public Integer getCharisma() {
        return charisma;
    }
    
    public void setCharisma(Integer charisma) {
        this.charisma = charisma;
    }
    
    public Integer getHitPoints() {
        return hitPoints;
    }
    
    public void setHitPoints(Integer hitPoints) {
        this.hitPoints = hitPoints;
    }
    
    public Integer getMaxHitPoints() {
        return maxHitPoints;
    }
    
    public void setMaxHitPoints(Integer maxHitPoints) {
        this.maxHitPoints = maxHitPoints;
    }
    
    public Integer getArmorClass() {
        return armorClass;
    }
    
    public void setArmorClass(Integer armorClass) {
        this.armorClass = armorClass;
    }
    
    public Integer getSpeed() {
        return speed;
    }
    
    public void setSpeed(Integer speed) {
        this.speed = speed;
    }
    
    public String getSkills() {
        return skills;
    }
    
    public void setSkills(String skills) {
        this.skills = skills;
    }
    
    public String getSpells() {
        return spells;
    }
    
    public void setSpells(String spells) {
        this.spells = spells;
    }
    
    public String getEquipment() {
        return equipment;
    }
    
    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }
    
    public String getBackground() {
        return background;
    }
    
    public void setBackground(String background) {
        this.background = background;
    }
    
    public String getAlignment() {
        return alignment;
    }
    
    public void setAlignment(String alignment) {
        this.alignment = alignment;
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

