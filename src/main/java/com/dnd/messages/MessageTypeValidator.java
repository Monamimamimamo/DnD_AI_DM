package com.dnd.messages;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Валидатор для проверки, может ли определенный тип сообщения быть сгенерирован в текущем контексте
 */
public class MessageTypeValidator {
    
    /**
     * Проверяет, допустим ли тип сообщения в текущем контексте
     */
    public static ValidationResult validate(MessageType messageType, GameContext context) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Проверка базовой допустимости типа в текущем состоянии
        if (!isAllowedInState(messageType, context.getCurrentState())) {
            errors.add(String.format(
                "Тип сообщения '%s' не допустим в состоянии '%s'",
                messageType.getDescription(),
                context.getCurrentState()
            ));
            return new ValidationResult(false, errors, warnings);
        }
        
        // Проверка специальных правил
        
        // 1. Нельзя генерировать случайные события во время диалога
        if (messageType == MessageType.RANDOM_EVENT && context.isInDialogue()) {
            errors.add("Нельзя генерировать случайное событие во время диалога");
        }
        
        // 2. Нельзя генерировать встречу с NPC во время диалога
        if (messageType == MessageType.NPC_ENCOUNTER && context.isInDialogue()) {
            errors.add("Нельзя генерировать встречу с NPC во время другого диалога");
        }
        
        // 3. Нельзя генерировать диалог, если уже в диалоге с другим NPC
        if (messageType == MessageType.DIALOGUE_CONTINUATION && 
            context.isInDialogue() && 
            context.getDialogueNPC() != null) {
            // Проверяем, что это продолжение того же диалога
            // (это можно улучшить, добавив проверку имени NPC в метаданных)
        }
        
        // 4. Ограничение частоты случайных событий (не чаще раза в 2 минуты)
        if (messageType == MessageType.RANDOM_EVENT || 
            messageType == MessageType.EXPLORATION_EVENT) {
            LocalDateTime lastEvent = context.getLastEventTime();
            if (lastEvent != null) {
                Duration timeSinceLastEvent = Duration.between(lastEvent, LocalDateTime.now());
                if (timeSinceLastEvent.toMinutes() < 2) {
                    warnings.add("Случайное событие генерируется слишком часто после предыдущего");
                }
            }
        }
        
        // 5. После действия игрока должен быть ACTION_RESULT, CONSEQUENCE, QUEST_PROGRESSION или SITUATION_CONTINUATION
        if (context.getCurrentState() == GameContext.ContextState.AFTER_ACTION) {
            if (messageType != MessageType.ACTION_RESULT && 
                messageType != MessageType.CONSEQUENCE &&
                messageType != MessageType.QUEST_PROGRESSION &&
                messageType != MessageType.SITUATION_CONTINUATION &&
                messageType != MessageType.REVELATION &&
                messageType != MessageType.SYSTEM) {
                warnings.add("После действия игрока ожидается ACTION_RESULT, CONSEQUENCE, QUEST_PROGRESSION или SITUATION_CONTINUATION");
            }
        }
        
        // 6. В бою допустимы только боевые события и результаты действий
        if (context.getCurrentState() == GameContext.ContextState.IN_COMBAT) {
            if (messageType != MessageType.COMBAT_EVENT && 
                messageType != MessageType.ACTION_RESULT &&
                messageType != MessageType.SYSTEM) {
                errors.add("В бою допустимы только боевые события и результаты действий");
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Проверяет, допустим ли тип сообщения в указанном состоянии
     */
    private static boolean isAllowedInState(MessageType messageType, GameContext.ContextState state) {
        switch (state) {
            case FREE_EXPLORATION:
                return messageType == MessageType.SITUATION_CONTINUATION ||
                       messageType == MessageType.RANDOM_EVENT ||
                       messageType == MessageType.NPC_ENCOUNTER ||
                       messageType == MessageType.LOCATION_DESCRIPTION ||
                       messageType == MessageType.EXPLORATION_EVENT ||
                       messageType == MessageType.SIDE_QUEST_INTRO ||
                       messageType == MessageType.ACTION_RESULT ||
                       messageType == MessageType.SYSTEM;
                       
            case IN_DIALOGUE:
                return messageType == MessageType.DIALOGUE_CONTINUATION ||
                       messageType == MessageType.REVELATION ||
                       messageType == MessageType.SIDE_QUEST_INTRO ||
                       messageType == MessageType.SYSTEM;
                       
            case IN_COMBAT:
                return messageType == MessageType.COMBAT_EVENT ||
                       messageType == MessageType.ACTION_RESULT ||
                       messageType == MessageType.SYSTEM;
                       
            case QUEST_FOCUSED:
                return messageType == MessageType.QUEST_PROGRESSION ||
                       messageType == MessageType.SITUATION_CONTINUATION ||
                       messageType == MessageType.REVELATION ||
                       messageType == MessageType.CONSEQUENCE ||
                       messageType == MessageType.LOCATION_DESCRIPTION ||
                       messageType == MessageType.SYSTEM;
                       
            case AFTER_ACTION:
                return messageType == MessageType.ACTION_RESULT ||
                       messageType == MessageType.CONSEQUENCE ||
                       messageType == MessageType.QUEST_PROGRESSION ||
                       messageType == MessageType.SITUATION_CONTINUATION ||
                       messageType == MessageType.REVELATION ||
                       messageType == MessageType.SYSTEM;
                       
            case QUEST_COMPLETED:
                return messageType == MessageType.FINAL_SCENE ||
                       messageType == MessageType.SYSTEM;
                       
            default:
                return false;
        }
    }
    
    /**
     * Получает список допустимых типов сообщений для текущего состояния
     */
    public static List<MessageType> getAllowedTypes(GameContext context) {
        List<MessageType> allowed = new ArrayList<>();
        for (MessageType type : MessageType.values()) {
            if (isAllowedInState(type, context.getCurrentState())) {
                // Дополнительные проверки
                if (type == MessageType.RANDOM_EVENT && context.isInDialogue()) {
                    continue;
                }
                if (type == MessageType.NPC_ENCOUNTER && context.isInDialogue()) {
                    continue;
                }
                allowed.add(type);
            }
        }
        return allowed;
    }
    
    /**
     * Результат валидации
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}


