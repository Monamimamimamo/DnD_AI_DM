package com.dnd.messages;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Структурированное сообщение от Dungeon Master с типом и метаданными
 */
public class StructuredMessage {
    private MessageType type;
    private String content;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata; // Дополнительные данные (локация, NPC, квест и т.д.)
    private String characterName; // Имя персонажа, к которому относится сообщение
    
    public StructuredMessage(MessageType type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }
    
    public StructuredMessage(MessageType type, String content, String characterName) {
        this(type, content);
        this.characterName = characterName;
    }
    
    public StructuredMessage(MessageType type, String content, Map<String, Object> metadata) {
        this(type, content);
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    public StructuredMessage(MessageType type, String content, String characterName, Map<String, Object> metadata) {
        this(type, content, characterName);
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    /**
     * Создает JSON-представление сообщения для передачи клиенту
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("type", type.getCode());
        result.put("type_description", type.getDescription());
        result.put("content", content);
        result.put("timestamp", timestamp.toString());
        if (characterName != null) {
            result.put("character", characterName);
        }
        if (!metadata.isEmpty()) {
            result.put("metadata", metadata);
        }
        return result;
    }
    
    // Getters and Setters
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public String getCharacterName() {
        return characterName;
    }
    
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
}

