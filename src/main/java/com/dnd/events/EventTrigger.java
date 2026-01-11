package com.dnd.events;

import java.util.Map;

/**
 * Триггер для генерации события
 */
public class EventTrigger {
    public enum TriggerType {
        LOCATION_BASED,      // При посещении локации
        QUEST_BASED,        // После этапа квеста
        FLAG_BASED,         // При установке флага
        PATTERN_BASED,      // При обнаружении паттерна в истории
        CONTEXT_BASED,      // На основе контекста игры (незавершенные сюжетные линии, долгое отсутствие событий)
        STORYLINE_BASED     // На основе незавершенных сюжетных линий
    }
    
    private TriggerType type;
    private Map<String, Object> conditions; // Условия активации
    private int priority; // Приоритет (0-100)
    private String description; // Описание триггера
    
    public EventTrigger(TriggerType type, Map<String, Object> conditions, int priority, String description) {
        this.type = type;
        this.conditions = conditions;
        this.priority = priority;
        this.description = description;
    }
    
    public TriggerType getType() { return type; }
    public Map<String, Object> getConditions() { return conditions; }
    public int getPriority() { return priority; }
    public String getDescription() { return description; }
}

