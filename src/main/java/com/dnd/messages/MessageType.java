package com.dnd.messages;

/**
 * Типы сообщений, которые может генерировать Dungeon Master
 * Каждый тип имеет контекст, в котором он может быть использован
 */
public enum MessageType {
    /**
     * Продолжение текущей ситуации - основное повествование
     * Контекст: FREE_EXPLORATION, QUEST_FOCUSED
     */
    SITUATION_CONTINUATION("situation_continuation", "Продолжение ситуации"),
    
    /**
     * Продвижение основного квеста
     * Контекст: QUEST_FOCUSED, AFTER_ACTION
     */
    QUEST_PROGRESSION("quest_progression", "Продвижение квеста"),
    
    /**
     * Встреча с NPC
     * Контекст: FREE_EXPLORATION, QUEST_FOCUSED (но не во время диалога)
     */
    NPC_ENCOUNTER("npc_encounter", "Встреча с NPC"),
    
    /**
     * Продолжение диалога с NPC
     * Контекст: IN_DIALOGUE
     */
    DIALOGUE_CONTINUATION("dialogue_continuation", "Продолжение диалога"),
    
    /**
     * Результат действия игрока
     * Контекст: AFTER_ACTION
     */
    ACTION_RESULT("action_result", "Результат действия"),
    
    /**
     * Случайное событие (находка, препятствие и т.д.)
     * Контекст: FREE_EXPLORATION, QUEST_FOCUSED (но не во время диалога или боя)
     */
    RANDOM_EVENT("random_event", "Случайное событие"),
    
    /**
     * Описание локации
     * Контекст: FREE_EXPLORATION, QUEST_FOCUSED
     */
    LOCATION_DESCRIPTION("location_description", "Описание локации"),
    
    /**
     * Боевое событие
     * Контекст: IN_COMBAT, FREE_EXPLORATION (начало боя)
     */
    COMBAT_EVENT("combat_event", "Боевое событие"),
    
    /**
     * Событие исследования
     * Контекст: FREE_EXPLORATION, QUEST_FOCUSED
     */
    EXPLORATION_EVENT("exploration_event", "Событие исследования"),
    
    /**
     * Откровение/раскрытие тайны
     * Контекст: QUEST_FOCUSED, AFTER_ACTION, IN_DIALOGUE
     */
    REVELATION("revelation", "Откровение"),
    
    /**
     * Последствие прошлых действий
     * Контекст: AFTER_ACTION, QUEST_FOCUSED
     */
    CONSEQUENCE("consequence", "Последствие"),
    
    /**
     * Введение побочного квеста
     * Контекст: FREE_EXPLORATION, IN_DIALOGUE (от NPC)
     */
    SIDE_QUEST_INTRO("side_quest_intro", "Введение побочного квеста"),
    
    /**
     * Финальная сцена
     * Контекст: QUEST_COMPLETED
     */
    FINAL_SCENE("final_scene", "Финальная сцена"),
    
    /**
     * Системное сообщение (ошибки, предупреждения)
     * Контекст: любой
     */
    SYSTEM("system", "Системное сообщение");
    
    private final String code;
    private final String description;
    
    MessageType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Проверяет, является ли тип сообщения частью основного повествования
     */
    public boolean isNarrativeType() {
        return this == SITUATION_CONTINUATION || 
               this == QUEST_PROGRESSION || 
               this == LOCATION_DESCRIPTION ||
               this == FINAL_SCENE;
    }
    
    /**
     * Проверяет, является ли тип сообщения событием
     */
    public boolean isEventType() {
        return this == RANDOM_EVENT || 
               this == NPC_ENCOUNTER || 
               this == COMBAT_EVENT ||
               this == EXPLORATION_EVENT ||
               this == REVELATION ||
               this == CONSEQUENCE ||
               this == SIDE_QUEST_INTRO;
    }
    
    /**
     * Проверяет, является ли тип сообщения частью диалога
     */
    public boolean isDialogueType() {
        return this == DIALOGUE_CONTINUATION || 
               this == NPC_ENCOUNTER;
    }
}

