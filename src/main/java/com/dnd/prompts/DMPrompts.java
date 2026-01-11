package com.dnd.prompts;

import java.util.Map;

/**
 * Фасад для всех промптов Dungeon Master
 * Делегирует вызовы к специализированным классам промптов
 * 
 * @deprecated Используйте напрямую специализированные классы:
 * - SystemPrompts для системных промптов
 * - WorldPrompts для генерации мира и квестов
 * - SituationPrompts для генерации ситуаций
 * - EventPrompts для генерации событий
 * - ActionPrompts для парсинга действий
 */
@Deprecated
public class DMPrompts {
    
    // ========== Системные промпты ==========
    
    /**
     * Системный промпт для Dungeon Master
     */
    public static String getSystemPrompt(int maxTokens) {
        return SystemPrompts.getSystemPrompt(maxTokens);
    }
    
    /**
     * Системный промпт для генерации нарратива действий
     */
    public static String getActionNarrativeSystemPrompt() {
        return SystemPrompts.getActionNarrativeSystemPrompt();
    }
    
    // ========== Промпты для генерации мира и квестов ==========
    
    /**
     * Промпт для генерации мира кампании
     */
    public static String getWorldBuildingPrompt() {
        return WorldPrompts.getWorldBuildingPrompt();
    }
    
    /**
     * Промпт для генерации начальной сцены, квеста и начальной ситуации
     */
    public static String getInitialSceneQuestAndSituationPrompt(Map<String, Object> world) {
        return WorldPrompts.getInitialSceneQuestAndSituationPrompt(world);
        }
        
    /**
     * Промпт для генерации финальной сцены при завершении квеста
     */
    public static String getFinalScenePrompt(String questTitle, String questGoal) {
        return WorldPrompts.getFinalScenePrompt(questTitle, questGoal);
    }
    
    // ========== Промпты для генерации ситуаций ==========
    
    /**
     * Промпт для генерации ситуации, требующей действия
     */
    public static String getSituationPrompt(String previousSituation, String characterName, 
                                           String currentLocation, Map<String, Object> questInfo) {
        return SituationPrompts.getSituationPrompt(previousSituation, characterName, currentLocation, questInfo);
    }
    
    // ========== Промпты для генерации событий ==========
    
    /**
     * Промпт для генерации случайного события
     */
    public static String getRandomEventPrompt(String eventType, String connectionText, 
                                             String gameContext, Map<String, Object> historyAnalysis) {
        return EventPrompts.getRandomEventPrompt(eventType, connectionText, gameContext, historyAnalysis);
    }
    
    /**
     * Промпт для генерации встречи с NPC
     */
    public static String getNPCEncounterPrompt(String connectionText, String gameContext,
                                              Map<String, Object> historyAnalysis) {
        return EventPrompts.getNPCEncounterPrompt(connectionText, gameContext, historyAnalysis);
    }
    
    /**
     * Промпт для генерации побочного квеста
     */
    public static String getSideQuestPrompt(String connectionText, String gameContext,
                                           Map<String, Object> historyAnalysis) {
        return EventPrompts.getSideQuestPrompt(connectionText, gameContext, historyAnalysis);
    }
    
    // ========== Промпты для парсинга действий ==========
    
    /**
     * Промпт для генерации нарратива действия игрока
     */
    public static String getActionNarrativePrompt(String actionText, String characterName, 
                                                   String characterClass, String characterRace,
                                                   Map<String, Object> ruleResult, 
                                                   String currentLocation, String situation) {
        return ActionPrompts.getActionNarrativePrompt(actionText, characterName, characterClass, characterRace,
                                                      ruleResult, currentLocation, situation);
    }
    
    /**
     * Промпт для ответа на действие игрока
     */
    public static String getActionResponsePrompt(String context, String action, Map<String, Object> characterInfo) {
        return ActionPrompts.getActionResponsePrompt(context, action, characterInfo);
    }
    
    /**
     * Системный промпт для парсера действий с данными SRD
     */
    public static String getActionParserSystemPrompt(String skillsList, String dcInfo) {
        return ActionPrompts.getActionParserSystemPrompt(skillsList, dcInfo);
    }
    
    /**
     * Промпт для первого этапа - выбор нужных эндпоинтов
     */
    public static String getEndpointSelectionPrompt(String actionText, Map<String, String> availableEndpoints) {
        return ActionPrompts.getEndpointSelectionPrompt(actionText, availableEndpoints);
    }
    
    /**
     * Пользовательский промпт для парсера действий
     */
    public static String getActionParserUserPrompt(String actionText, Map<String, Object> gameContext) {
        return ActionPrompts.getActionParserUserPrompt(actionText, gameContext);
    }
    
    /**
     * Промпт для второго этапа - финальный парсинг с данными из SRD
     */
    public static String getActionParserFinalPrompt(String actionText, Map<String, Object> srdData, 
                                                    Map<String, Object> gameContext) {
        return ActionPrompts.getActionParserFinalPrompt(actionText, srdData, gameContext);
                    }
}
