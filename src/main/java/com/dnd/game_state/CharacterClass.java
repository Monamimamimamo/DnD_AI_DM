package com.dnd.game_state;

/**
 * Классы персонажей D&D 5e
 */
public enum CharacterClass {
    BARBARIAN("barbarian"),
    BARD("bard"),
    CLERIC("cleric"),
    DRUID("druid"),
    FIGHTER("fighter"),
    MONK("monk"),
    PALADIN("paladin"),
    RANGER("ranger"),
    ROGUE("rogue"),
    SORCERER("sorcerer"),
    WARLOCK("warlock"),
    WIZARD("wizard");

    private final String value;

    CharacterClass(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CharacterClass fromString(String value) {
        for (CharacterClass cc : CharacterClass.values()) {
            if (cc.value.equals(value)) {
                return cc;
            }
        }
        throw new IllegalArgumentException("Unknown character class: " + value);
    }
}

