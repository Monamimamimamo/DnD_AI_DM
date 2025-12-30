package com.dnd.ai_engine;

import com.dnd.game_state.Character;
import com.dnd.game_rules.*;
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
            
            Map<String, Object> result = new HashMap<>();
            result.put("parsed_action", parsedAction);
            result.put("rule_result", ruleResult);
            result.put("dm_narrative", dmNarrative);
            result.put("success", false);
            result.put("requires_new_action", true);
            return result;
        }
        
        // Шаг 2: Проверяем правила
        Map<String, Object> ruleResult = ruleEngine.evaluateAction(parsedAction, character, gameContext);
        
        // Шаг 3: Генерируем нарратив
        String dmNarrative = generateNarrative(actionText, parsedAction, ruleResult, character, gameContext);
        
        Map<String, Object> result = new HashMap<>();
        result.put("parsed_action", parsedAction);
        result.put("rule_result", ruleResult);
        result.put("dm_narrative", dmNarrative);
        result.put("success", ruleResult.getOrDefault("result", "").toString().equals("success") ||
                             ruleResult.getOrDefault("result", "").toString().equals("partial_success"));
        return result;
    }

    private String generateNarrative(String actionText, Map<String, Object> parsedAction,
                                    Map<String, Object> ruleResult, Character character,
                                    Map<String, Object> gameContext) {
        String currentLocation = (String) gameContext.getOrDefault("current_location", "Неизвестно");
        String currentScene = (String) gameContext.getOrDefault("current_scene", "");
        String situation = (String) gameContext.getOrDefault("current_situation", "");
        
        String resultStatus = ruleResult.getOrDefault("result", "").toString();
        boolean isSuccess = resultStatus.equals("success") || resultStatus.equals("partial_success");
        
        String prompt = "Ты — опытный Dungeon Master для D&D 5e. Создай детальное, атмосферное описание действия игрока.\n\n" +
                       "Персонаж: " + character.getName() + " (" + character.getCharacterClass() + ", " + character.getRace() + ")\n" +
                       "Действие: \"" + actionText + "\"\n\n" +
                       "Результат проверки:\n" +
                       "- Навык: " + ruleResult.getOrDefault("skill", "N/A") + "\n" +
                       "- Характеристика: " + ruleResult.getOrDefault("ability", "N/A") + "\n" +
                       "- Сложность (DC): " + ruleResult.getOrDefault("dc", "N/A") + "\n" +
                       "- Бросок: " + ruleResult.getOrDefault("roll", "N/A") + " + модификаторы = " + 
                       ruleResult.getOrDefault("total", "N/A") + "\n" +
                       "- Результат: " + (isSuccess ? "УСПЕХ" : "НЕУДАЧА") + "\n\n" +
                       "Контекст:\n" +
                       "- Локация: " + currentLocation + "\n";
        
        if (!currentScene.isEmpty()) {
            prompt += "- Сцена: " + currentScene + "\n";
        }
        if (!situation.isEmpty()) {
            prompt += "- Ситуация: " + situation + "\n";
        }
        
        prompt += "\n" +
                 "Создай ДЕТАЛЬНОЕ описание (минимум 4-5 предложений) того, что происходит:\n" +
                 "1. Опиши, как персонаж выполняет действие\n" +
                 "2. Опиши, что он видит/чувствует/слышит\n" +
                 "3. Опиши результат действия (успех или неудача)\n" +
                 "4. Опиши последствия и что происходит дальше\n" +
                 "5. Если действие связано с перемещением, укажи новую локацию\n\n" +
                 "Будь конкретным и атмосферным. Используй детали окружения. Отвечай на русском языке.";
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        
        String systemPrompt = "Ты — опытный Dungeon Master для D&D 5e. " +
                             "Создавай детальные, атмосферные описания действий игроков. " +
                             "Всегда давай полные ответы (минимум 4-5 предложений). " +
                             "Отвечай на русском языке.";
        
        return dmClient.generateResponse(messages, systemPrompt);
    }
}

