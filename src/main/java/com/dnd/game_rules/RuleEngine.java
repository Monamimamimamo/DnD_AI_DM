package com.dnd.game_rules;

import com.dnd.game_state.Character;
import java.util.*;

/**
 * Детерминированный движок правил D&D 5e
 */
public class RuleEngine {
    private final SRDDataLoader srdLoader;
    private final Map<String, Integer> dcTable;

    public RuleEngine() {
        this.srdLoader = new SRDDataLoader();
        this.dcTable = srdLoader.getDifficultyTable();
    }

    public SRDDataLoader getSrdLoader() {
        return srdLoader;
    }

    public Map<String, Object> evaluateAction(Map<String, Object> parsedAction, 
                                              Character character, 
                                              Map<String, Object> gameContext) {
        String ability = (String) parsedAction.getOrDefault("ability", "strength");
        String skill = (String) parsedAction.getOrDefault("skill", null);
        Object estimatedDcObj = parsedAction.get("estimated_dc");
        
        int dc = 15; // По умолчанию
        if (estimatedDcObj instanceof Number) {
            dc = ((Number) estimatedDcObj).intValue();
        }
        
        // Получаем модификаторы
        int abilityMod = character.getAbilityScores().getModifier(ability);
        int proficiencyBonus = 0;
        if (skill != null && character.getSkills().containsKey(skill)) {
            proficiencyBonus = character.getProficiencyBonus();
        }
        
        // Бросаем кубик
        DiceRoller.AbilityCheckResult rollResult = DiceRoller.rollAbilityCheck(
            abilityMod, skill != null && character.getSkills().containsKey(skill), 
            proficiencyBonus
        );
        
        int total = rollResult.getTotal();
        String result;
        if (total >= dc) {
            result = "success";
        } else if (total >= dc - 3) {
            result = "partial_success";
        } else {
            result = "failure";
        }
        
        Map<String, Object> ruleResult = new HashMap<>();
        ruleResult.put("skill", skill);
        ruleResult.put("ability", ability);
        ruleResult.put("dc", dc);
        ruleResult.put("final_dc", dc);
        ruleResult.put("roll", rollResult.getRoll());
        ruleResult.put("total", total);
        ruleResult.put("result", result);
        ruleResult.put("is_possible", true);
        
        return ruleResult;
    }
}

