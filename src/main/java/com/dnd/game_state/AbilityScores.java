package com.dnd.game_state;

/**
 * Характеристики персонажа D&D 5e
 */
public class AbilityScores {
    private int strength = 10;
    private int dexterity = 10;
    private int constitution = 10;
    private int intelligence = 10;
    private int wisdom = 10;
    private int charisma = 10;

    public AbilityScores() {
    }

    public AbilityScores(int strength, int dexterity, int constitution, 
                        int intelligence, int wisdom, int charisma) {
        this.strength = strength;
        this.dexterity = dexterity;
        this.constitution = constitution;
        this.intelligence = intelligence;
        this.wisdom = wisdom;
        this.charisma = charisma;
    }

    public int getModifier(String ability) {
        int score = getScore(ability);
        return (score - 10) / 2;
    }

    private int getScore(String ability) {
        return switch (ability.toLowerCase()) {
            case "strength" -> strength;
            case "dexterity" -> dexterity;
            case "constitution" -> constitution;
            case "intelligence" -> intelligence;
            case "wisdom" -> wisdom;
            case "charisma" -> charisma;
            default -> 10;
        };
    }

    // Getters and Setters
    public int getStrength() { return strength; }
    public void setStrength(int strength) { this.strength = strength; }

    public int getDexterity() { return dexterity; }
    public void setDexterity(int dexterity) { this.dexterity = dexterity; }

    public int getConstitution() { return constitution; }
    public void setConstitution(int constitution) { this.constitution = constitution; }

    public int getIntelligence() { return intelligence; }
    public void setIntelligence(int intelligence) { this.intelligence = intelligence; }

    public int getWisdom() { return wisdom; }
    public void setWisdom(int wisdom) { this.wisdom = wisdom; }

    public int getCharisma() { return charisma; }
    public void setCharisma(int charisma) { this.charisma = charisma; }
}

