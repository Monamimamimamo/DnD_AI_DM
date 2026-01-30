package com.dnd.messages;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Контекст текущего состояния игры для определения допустимых типов сообщений
 */
public class GameContext {
    
    /**
     * Состояния игры, определяющие, какие типы сообщений могут быть сгенерированы
     */
    public enum ContextState {
        /**
         * Свободное исследование - игроки могут исследовать мир
         * Допустимые типы: SITUATION_CONTINUATION, RANDOM_EVENT, NPC_ENCOUNTER, 
         * LOCATION_DESCRIPTION, EXPLORATION_EVENT, SIDE_QUEST_INTRO
         */
        FREE_EXPLORATION,
        
        /**
         * В диалоге с NPC
         * Допустимые типы: DIALOGUE_CONTINUATION, REVELATION, SIDE_QUEST_INTRO
         */
        IN_DIALOGUE,
        
        /**
         * В бою
         * Допустимые типы: COMBAT_EVENT, ACTION_RESULT
         */
        IN_COMBAT,
        
        /**
         * Фокус на основном квесте
         * Допустимые типы: QUEST_PROGRESSION, SITUATION_CONTINUATION, REVELATION, 
         * CONSEQUENCE, LOCATION_DESCRIPTION
         */
        QUEST_FOCUSED,
        
        /**
         * После действия игрока
         * Допустимые типы: ACTION_RESULT, CONSEQUENCE, QUEST_PROGRESSION, SITUATION_CONTINUATION, REVELATION
         */
        AFTER_ACTION,
        
        /**
         * Квест завершен
         * Допустимые типы: FINAL_SCENE
         */
        QUEST_COMPLETED
    }
    
    private ContextState currentState;
    private String currentLocation;
    private String lastMessageType; // Последний тип сообщения
    private LocalDateTime lastStateChange;
    private boolean inDialogue; // В диалоге ли сейчас
    private String dialogueNPC; // С кем диалог (если есть)
    private int actionCount; // Счетчик действий подряд
    private LocalDateTime lastEventTime; // Время последнего события
    
    public GameContext() {
        this.currentState = ContextState.FREE_EXPLORATION;
        this.lastStateChange = LocalDateTime.now();
        this.inDialogue = false;
        this.actionCount = 0;
        this.lastEventTime = LocalDateTime.now();
    }
    
    /**
     * Обновляет контекст на основе последнего сообщения
     */
    public void updateFromMessage(MessageType messageType, String content) {
        this.lastMessageType = messageType.getCode();
        
        // Определяем новое состояние на основе типа сообщения
        switch (messageType) {
            case DIALOGUE_CONTINUATION:
            case NPC_ENCOUNTER:
                if (!inDialogue) {
                    setState(ContextState.IN_DIALOGUE);
                    // Извлекаем имя NPC из контента (если возможно)
                    this.dialogueNPC = extractNPCName(content);
                }
                break;
                
            case COMBAT_EVENT:
                if (currentState != ContextState.IN_COMBAT) {
                    setState(ContextState.IN_COMBAT);
                }
                break;
                
            case ACTION_RESULT:
                setState(ContextState.AFTER_ACTION);
                actionCount++;
                break;
                
            case QUEST_PROGRESSION:
                if (currentState == ContextState.AFTER_ACTION) {
                    setState(ContextState.QUEST_FOCUSED);
                }
                break;
                
            case FINAL_SCENE:
                setState(ContextState.QUEST_COMPLETED);
                break;
                
            case RANDOM_EVENT:
            case EXPLORATION_EVENT:
                lastEventTime = LocalDateTime.now();
                // После события возвращаемся к свободному исследованию (если не в диалоге/бою)
                if (currentState == ContextState.AFTER_ACTION && !inDialogue) {
                    setState(ContextState.FREE_EXPLORATION);
                }
                break;
                
            case SITUATION_CONTINUATION:
                // После ситуации возвращаемся к свободному исследованию (если не в диалоге/бою)
                if (currentState == ContextState.AFTER_ACTION && !inDialogue && currentState != ContextState.IN_COMBAT) {
                    setState(ContextState.FREE_EXPLORATION);
                }
                break;
        }
    }
    
    /**
     * Завершает диалог
     */
    public void endDialogue() {
        this.inDialogue = false;
        this.dialogueNPC = null;
        if (currentState == ContextState.IN_DIALOGUE) {
            setState(ContextState.FREE_EXPLORATION);
        }
    }
    
    /**
     * Завершает бой
     */
    public void endCombat() {
        if (currentState == ContextState.IN_COMBAT) {
            setState(ContextState.AFTER_ACTION);
        }
    }
    
    /**
     * Устанавливает новое состояние
     */
    private void setState(ContextState newState) {
        if (this.currentState != newState) {
            this.currentState = newState;
            this.lastStateChange = LocalDateTime.now();
        }
    }
    
    /**
     * Пытается извлечь имя NPC из текста (простая эвристика)
     */
    private String extractNPCName(String content) {
        // Простая эвристика: ищем паттерны типа "NPC говорит", "встречаете NPC"
        // В реальной реализации можно использовать более сложный парсинг
        if (content != null && content.length() > 0) {
            // Это упрощенная версия, в реальности нужен более сложный парсинг
            return null; // Пока возвращаем null, можно улучшить позже
        }
        return null;
    }
    
    // Getters and Setters
    public ContextState getCurrentState() {
        return currentState;
    }
    
    public void setCurrentState(ContextState currentState) {
        setState(currentState);
    }
    
    public String getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }
    
    public String getLastMessageType() {
        return lastMessageType;
    }
    
    public LocalDateTime getLastStateChange() {
        return lastStateChange;
    }
    
    public boolean isInDialogue() {
        return inDialogue;
    }
    
    public void setInDialogue(boolean inDialogue) {
        this.inDialogue = inDialogue;
        if (!inDialogue) {
            this.dialogueNPC = null;
        }
    }
    
    public String getDialogueNPC() {
        return dialogueNPC;
    }
    
    public void setDialogueNPC(String dialogueNPC) {
        this.dialogueNPC = dialogueNPC;
        this.inDialogue = dialogueNPC != null;
    }
    
    public int getActionCount() {
        return actionCount;
    }
    
    public void resetActionCount() {
        this.actionCount = 0;
    }
    
    public LocalDateTime getLastEventTime() {
        return lastEventTime;
    }
    
    public void setLastEventTime(LocalDateTime lastEventTime) {
        this.lastEventTime = lastEventTime;
    }
}

