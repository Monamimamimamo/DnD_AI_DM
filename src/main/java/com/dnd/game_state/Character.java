package com.dnd.game_state;

import java.util.*;

/**
 * Персонаж D&D 5e
 */
public class Character {
    private String name;
    private CharacterClass characterClass;
    private CharacterRace race;
    private int level = 1;
    private AbilityScores abilityScores;
    private int hitPoints = 0;
    private int maxHitPoints = 0;
    private int armorClass = 10;
    private int speed = 30;
    private Map<String, Integer> skills = new HashMap<>();
    private List<String> spells = new ArrayList<>();
    private List<String> equipment = new ArrayList<>();
    private String background = "";
    private String alignment = "neutral";

    public Character(String name, CharacterClass characterClass, CharacterRace race) {
        this.name = name;
        this.characterClass = characterClass;
        this.race = race;
        this.abilityScores = new AbilityScores();
        initializeHitPoints();
    }

    public Character(String name, CharacterClass characterClass, CharacterRace race, 
                    int level, AbilityScores abilityScores, String background, String alignment) {
        this.name = name;
        this.characterClass = characterClass;
        this.race = race;
        this.level = level;
        this.abilityScores = abilityScores;
        this.background = background;
        this.alignment = alignment;
        initializeHitPoints();
    }

    private void initializeHitPoints() {
        if (hitPoints == 0) {
            hitPoints = calculateHitPoints();
        }
        if (maxHitPoints == 0) {
            maxHitPoints = hitPoints;
        }
    }

    private int calculateHitPoints() {
        Map<CharacterClass, Integer> baseHp = new HashMap<>();
        baseHp.put(CharacterClass.BARBARIAN, 12);
        baseHp.put(CharacterClass.FIGHTER, 10);
        baseHp.put(CharacterClass.PALADIN, 10);
        baseHp.put(CharacterClass.RANGER, 10);
        baseHp.put(CharacterClass.CLERIC, 8);
        baseHp.put(CharacterClass.DRUID, 8);
        baseHp.put(CharacterClass.MONK, 8);
        baseHp.put(CharacterClass.ROGUE, 8);
        baseHp.put(CharacterClass.BARD, 8);
        baseHp.put(CharacterClass.WARLOCK, 8);
        baseHp.put(CharacterClass.SORCERER, 6);
        baseHp.put(CharacterClass.WIZARD, 6);
        
        int base = baseHp.getOrDefault(characterClass, 8);
        int conMod = abilityScores.getModifier("constitution");
        return base + conMod;
    }

    public int getSkillModifier(String skill) {
        String baseAbility = getSkillAbility(skill);
        int abilityMod = abilityScores.getModifier(baseAbility);
        int proficiencyBonus = getProficiencyBonus();
        
        if (skills.containsKey(skill)) {
            return abilityMod + proficiencyBonus;
        }
        return abilityMod;
    }

    private String getSkillAbility(String skill) {
        Map<String, String> skillAbilities = new HashMap<>();
        skillAbilities.put("acrobatics", "dexterity");
        skillAbilities.put("animal_handling", "wisdom");
        skillAbilities.put("arcana", "intelligence");
        skillAbilities.put("athletics", "strength");
        skillAbilities.put("deception", "charisma");
        skillAbilities.put("history", "intelligence");
        skillAbilities.put("insight", "wisdom");
        skillAbilities.put("intimidation", "charisma");
        skillAbilities.put("investigation", "intelligence");
        skillAbilities.put("medicine", "wisdom");
        skillAbilities.put("nature", "intelligence");
        skillAbilities.put("perception", "wisdom");
        skillAbilities.put("performance", "charisma");
        skillAbilities.put("persuasion", "charisma");
        skillAbilities.put("religion", "intelligence");
        skillAbilities.put("sleight_of_hand", "dexterity");
        skillAbilities.put("stealth", "dexterity");
        skillAbilities.put("survival", "wisdom");
        return skillAbilities.getOrDefault(skill, "intelligence");
    }

    public int getProficiencyBonus() {
        return 2 + (level - 1) / 4;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CharacterClass getCharacterClass() { return characterClass; }
    public void setCharacterClass(CharacterClass characterClass) { this.characterClass = characterClass; }

    public CharacterRace getRace() { return race; }
    public void setRace(CharacterRace race) { this.race = race; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public AbilityScores getAbilityScores() { return abilityScores; }
    public void setAbilityScores(AbilityScores abilityScores) { this.abilityScores = abilityScores; }

    public int getHitPoints() { return hitPoints; }
    public void setHitPoints(int hitPoints) { this.hitPoints = hitPoints; }

    public int getMaxHitPoints() { return maxHitPoints; }
    public void setMaxHitPoints(int maxHitPoints) { this.maxHitPoints = maxHitPoints; }

    public int getArmorClass() { return armorClass; }
    public void setArmorClass(int armorClass) { this.armorClass = armorClass; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public Map<String, Integer> getSkills() { return skills; }
    public void setSkills(Map<String, Integer> skills) { this.skills = skills; }

    public List<String> getSpells() { return spells; }
    public void setSpells(List<String> spells) { this.spells = spells; }

    public List<String> getEquipment() { return equipment; }
    public void setEquipment(List<String> equipment) { this.equipment = equipment; }

    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }

    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }
}

