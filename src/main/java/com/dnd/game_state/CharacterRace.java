package com.dnd.game_state;

/**
 * Расы персонажей D&D 5e
 */
public enum CharacterRace {
    DRAGONBORN("dragonborn"),
    DWARF("dwarf"),
    ELF("elf"),
    GNOME("gnome"),
    HALF_ELF("half_elf"),
    HALFLING("halfling"),
    HALF_ORC("half_orc"),
    HUMAN("human"),
    TIEFLING("tiefling");

    private final String value;

    CharacterRace(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CharacterRace fromString(String value) {
        for (CharacterRace cr : CharacterRace.values()) {
            if (cr.value.equals(value)) {
                return cr;
            }
        }
        throw new IllegalArgumentException("Unknown character race: " + value);
    }
}

