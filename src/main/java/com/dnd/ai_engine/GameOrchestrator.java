package com.dnd.ai_engine;

import com.dnd.game_state.Character;
import com.dnd.game_rules.*;
import com.dnd.prompts.DMPrompts;
import java.util.*;

/**
 * Координатор игровых компонентов
 */
public class GameOrchestrator {
    private final RuleEngine ruleEngine;
    private final ActionParser actionParser;
    private final LocalLLMClient dmClient;

    public GameOrchestrator(LocalLLMClient dmClient) {
        this.dmClient = dmClient;
        this.ruleEngine = new RuleEngine();
        
        // Создаем ActionParser с отдельным клиентом (использует тот же Ollama URL)
        LocalLLMClient.LocalLLMConfig parserConfig = new LocalLLMClient.LocalLLMConfig(
            "llama3.1:8b", 0.0, 400
        );
        // Используем тот же базовый URL, что и у dmClient
        String ollamaUrl = System.getenv("OLLAMA_BASE_URL");
        if (ollamaUrl == null || ollamaUrl.isEmpty()) {
            ollamaUrl = System.getProperty("ollama.base.url");
        }
        LocalLLMClient parserClient = new LocalLLMClient(parserConfig, ollamaUrl);
        this.actionParser = new ActionParser(parserClient, ruleEngine.getSrdLoader());
    }

    public Map<String, Object> processPlayerAction(String actionText, Character character, 
                                                   Map<String, Object> gameContext) {
        // Шаг 1: Парсим действие
        Map<String, Object> parsedAction = actionParser.parseAction(actionText, gameContext);
        
        // Проверяем, возможно ли действие
        if (!Boolean.TRUE.equals(parsedAction.getOrDefault("is_possible", true))) {
            String reason = (String) parsedAction.getOrDefault("reason", "Действие невозможно");
            String dmNarrative = character.getName() + " пытается: " + actionText + 
                               "\n\n" + reason + "\n\nПопробуйте описать другое действие.";
            
            Map<String, Object> ruleResult = new HashMap<>();
            ruleResult.put("is_possible", false);
            ruleResult.put("reason", reason);
            ruleResult.put("result", "impossible");
            ruleResult.put("requires_dice_roll", false); // Невозможные действия не требуют броска кубиков
            
            Map<String, Object> result = new HashMap<>();
            result.put("parsed_action", parsedAction);
            result.put("rule_result", ruleResult);
            result.put("dm_narrative", dmNarrative);
            result.put("success", false);
            result.put("requires_new_action", true);
            return result;
        }
        
        // Шаг 2: Проверяем, нужен ли бросок кубиков
        Object requiresDiceRollObj = parsedAction.get("requires_dice_roll");
        boolean requiresDiceRoll;
        if (requiresDiceRollObj instanceof Boolean) {
            requiresDiceRoll = (Boolean) requiresDiceRollObj;
        } else if (requiresDiceRollObj instanceof String) {
            requiresDiceRoll = Boolean.parseBoolean((String) requiresDiceRollObj);
        } else {
            // По умолчанию нужен бросок, если поле отсутствует или имеет неожиданный тип
            requiresDiceRoll = true;
        }
        
        Map<String, Object> ruleResult;
        
        if (requiresDiceRoll) {
            // Проверяем, что ability указан (обязателен для действий, требующих броска кубиков)
            Object abilityObj = parsedAction.get("ability");
            if (abilityObj == null || !(abilityObj instanceof String) || ((String) abilityObj).isEmpty()) {
                // Если requires_dice_roll: true, но ability не указан - это ошибка парсинга
                // Обрабатываем как тривиальное действие (автоматический успех)
                System.err.println("⚠️ [GameOrchestrator] Предупреждение: ability не указан для действия, требующего броска кубиков. Обрабатываем как тривиальное действие.");
                requiresDiceRoll = false;
            }
        }
        
        if (requiresDiceRoll) {
            // Проверяем правила и бросаем кубики
            ruleResult = ruleEngine.evaluateAction(parsedAction, character, gameContext);
        } else {
            // Тривиальное действие - автоматический успех без броска кубиков
            System.out.println("🎲 [GameOrchestrator] Действие тривиальное, бросок кубиков не требуется");
            ruleResult = new HashMap<>();
            ruleResult.put("result", "automatic_success");
            ruleResult.put("roll", null);
            ruleResult.put("total", null);
            ruleResult.put("dc", null);
            ruleResult.put("skill", parsedAction.get("skill"));
            ruleResult.put("ability", parsedAction.get("ability"));
            ruleResult.put("requires_dice_roll", false);
        }
        
        // Шаг 3: Генерируем нарратив
        String dmNarrative = generateNarrative(actionText, parsedAction, ruleResult, character, gameContext);
        
        Map<String, Object> result = new HashMap<>();
        result.put("parsed_action", parsedAction);
        result.put("rule_result", ruleResult);
        result.put("dm_narrative", dmNarrative);
        
        // Определяем успех: success, partial_success или automatic_success
        String resultStatus = ruleResult.getOrDefault("result", "").toString();
        boolean isSuccess = resultStatus.equals("success") || 
                           resultStatus.equals("partial_success") || 
                           resultStatus.equals("automatic_success");
        result.put("success", isSuccess);
        
        return result;
    }

    private String generateNarrative(String actionText, Map<String, Object> parsedAction,
                                    Map<String, Object> ruleResult, Character character,
                                    Map<String, Object> gameContext) {
        String currentLocation = (String) gameContext.getOrDefault("current_location", "Неизвестно");
        String situation = (String) gameContext.getOrDefault("current_situation", "");
        
        String prompt = DMPrompts.getActionNarrativePrompt(
            actionText,
            character.getName(),
            character.getCharacterClass().getValue(),
            character.getRace().getValue(),
            ruleResult,
            currentLocation,
            situation
        );
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        
        String systemPrompt = DMPrompts.getActionNarrativeSystemPrompt();
        
        return dmClient.generateResponse(messages, systemPrompt);
    }
}

