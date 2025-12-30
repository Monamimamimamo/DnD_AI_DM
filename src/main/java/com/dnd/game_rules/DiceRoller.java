package com.dnd.game_rules;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Утилита для бросков кубиков D&D
 */
public class DiceRoller {
    private static final Random random = new Random();
    private static final Pattern DICE_PATTERN = Pattern.compile("(\\d+)d(\\d+)([+-]\\d+)?");

    /**
     * Бросок кубиков по выражению (например, "1d20+3", "2d6", "4d8+1")
     */
    public static DiceResult roll(String expression) {
        expression = expression.toLowerCase().replace(" ", "");
        Matcher matcher = DICE_PATTERN.matcher(expression);
        
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Неверный формат броска: " + expression);
        }
        
        int numDice = Integer.parseInt(matcher.group(1));
        int sides = Integer.parseInt(matcher.group(2));
        String modifierStr = matcher.group(3);
        int modifier = modifierStr != null ? Integer.parseInt(modifierStr) : 0;
        
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < numDice; i++) {
            rolls.add(random.nextInt(sides) + 1);
        }
        
        int total = rolls.stream().mapToInt(Integer::intValue).sum() + modifier;
        
        return new DiceResult(total, rolls, modifier, expression, numDice, sides);
    }

    /**
     * Бросок d20 с модификатором
     */
    public static D20Result rollD20(int modifier) {
        int roll = random.nextInt(20) + 1;
        int total = roll + modifier;
        
        return new D20Result(roll, total, modifier, roll == 20, roll == 1);
    }

    /**
     * Бросок проверки характеристики
     */
    public static AbilityCheckResult rollAbilityCheck(int abilityModifier, boolean proficiency, int proficiencyBonus) {
        D20Result d20Result = rollD20(0);
        int totalModifier = abilityModifier + (proficiency ? proficiencyBonus : 0);
        
        return new AbilityCheckResult(
            d20Result.getRoll(),
            d20Result.getRoll() + totalModifier,
            abilityModifier,
            proficiency ? proficiencyBonus : 0,
            d20Result.isCritical(),
            d20Result.isCriticalFail()
        );
    }

    // Result classes
    public static class DiceResult {
        private final int total;
        private final List<Integer> rolls;
        private final int modifier;
        private final String expression;
        private final int numDice;
        private final int sides;

        public DiceResult(int total, List<Integer> rolls, int modifier, 
                         String expression, int numDice, int sides) {
            this.total = total;
            this.rolls = rolls;
            this.modifier = modifier;
            this.expression = expression;
            this.numDice = numDice;
            this.sides = sides;
        }

        public int getTotal() { return total; }
        public List<Integer> getRolls() { return rolls; }
        public int getModifier() { return modifier; }
        public String getExpression() { return expression; }
        public int getNumDice() { return numDice; }
        public int getSides() { return sides; }
    }

    public static class D20Result {
        private final int roll;
        private final int total;
        private final int modifier;
        private final boolean critical;
        private final boolean criticalFail;

        public D20Result(int roll, int total, int modifier, boolean critical, boolean criticalFail) {
            this.roll = roll;
            this.total = total;
            this.modifier = modifier;
            this.critical = critical;
            this.criticalFail = criticalFail;
        }

        public int getRoll() { return roll; }
        public int getTotal() { return total; }
        public int getModifier() { return modifier; }
        public boolean isCritical() { return critical; }
        public boolean isCriticalFail() { return criticalFail; }
    }

    public static class AbilityCheckResult {
        private final int roll;
        private final int total;
        private final int abilityModifier;
        private final int proficiencyBonus;
        private final boolean critical;
        private final boolean criticalFail;

        public AbilityCheckResult(int roll, int total, int abilityModifier, 
                                 int proficiencyBonus, boolean critical, boolean criticalFail) {
            this.roll = roll;
            this.total = total;
            this.abilityModifier = abilityModifier;
            this.proficiencyBonus = proficiencyBonus;
            this.critical = critical;
            this.criticalFail = criticalFail;
        }

        public int getRoll() { return roll; }
        public int getTotal() { return total; }
        public int getAbilityModifier() { return abilityModifier; }
        public int getProficiencyBonus() { return proficiencyBonus; }
        public boolean isCritical() { return critical; }
        public boolean isCriticalFail() { return criticalFail; }
    }
}

