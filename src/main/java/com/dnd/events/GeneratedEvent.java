package com.dnd.events;

import java.util.Map;

/**
 * Сгенерированное событие
 */
public class GeneratedEvent {
    private EventType type;
    private String title;
    private String description;
    private Map<String, Object> metadata; // Дополнительные данные (NPC, квест, локация и т.д.)
    private Map<String, Object> connections; // Связи с историей
    private int priority;
    
    public GeneratedEvent(EventType type, String title, String description,
                         Map<String, Object> metadata, Map<String, Object> connections,
                         int priority) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.metadata = metadata;
        this.connections = connections;
        this.priority = priority;
    }
    
    // Getters and Setters
    public EventType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Map<String, Object> getConnections() { return connections; }
    public int getPriority() { return priority; }
}







