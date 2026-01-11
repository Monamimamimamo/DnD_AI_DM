package com.dnd.events;

import com.dnd.game_state.GameState;
import java.util.*;

/**
 * Генератор связей между событиями и историей
 */
public class ConnectionGenerator {
    
    /**
     * Генерирует связи между выбранным элементом и историей (старый метод с шаблонами)
     */
    public Map<String, Object> generateConnections(Map<String, Object> selectedElement,
                                                   EventContext context) {
        Map<String, Object> connections = new HashMap<>();
        
        Map<String, Object> historyAnalysis = context.getHistoryAnalysis();
        if (historyAnalysis == null) {
            return connections;
        }
        
        // Типы связей
        connections.put("direct_reference", findDirectReferences(selectedElement, historyAnalysis));
        connections.put("hidden_connection", findHiddenConnections(selectedElement, historyAnalysis));
        connections.put("parallel_storyline", findParallelStorylines(selectedElement, historyAnalysis));
        connections.put("delayed_consequence", findDelayedConsequences(selectedElement, context));
        
        // Определяем основной тип связи
        connections.put("primary_connection_type", determinePrimaryConnectionType(connections));
        
        return connections;
    }
    
    /**
     * Генерирует связи с историей без использования шаблонов
     */
    public Map<String, Object> generateConnectionsFromHistory(EventContext context) {
        Map<String, Object> connections = new HashMap<>();
        
        Map<String, Object> historyAnalysis = context.getHistoryAnalysis();
        if (historyAnalysis == null) {
            return connections;
        }
        
        // Типы связей на основе анализа истории
        connections.put("direct_reference", findDirectReferencesFromHistory(historyAnalysis));
        connections.put("hidden_connection", findHiddenConnectionsFromHistory(historyAnalysis));
        connections.put("parallel_storyline", findParallelStorylinesFromHistory(historyAnalysis));
        connections.put("delayed_consequence", findDelayedConsequencesFromHistory(context));
        
        // Определяем основной тип связи
        connections.put("primary_connection_type", determinePrimaryConnectionType(connections));
        
        return connections;
    }
    
    /**
     * Находит прямые отсылки к прошлым событиям из истории
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findDirectReferencesFromHistory(Map<String, Object> historyAnalysis) {
        Map<String, Object> references = new HashMap<>();
        
        Map<String, List<String>> mentions = (Map<String, List<String>>) historyAnalysis.get("mentions");
        if (mentions == null) {
            return references;
        }
        
        // Ищем упоминания NPC для возможной встречи
        List<String> npcs = mentions.get("npcs");
        if (npcs != null && !npcs.isEmpty()) {
            references.put("mentioned_npcs", npcs);
            references.put("connection_text", "В истории упоминались NPC: " + String.join(", ", npcs) + 
                          ". Событие может быть связано с одним из них.");
        }
        
        // Ищем упоминания предметов
        List<String> items = mentions.get("items");
        if (items != null && !items.isEmpty()) {
            references.put("mentioned_items", items);
            if (references.containsKey("connection_text")) {
                references.put("connection_text", references.get("connection_text") + 
                              " Также упоминались предметы: " + String.join(", ", items));
            } else {
                references.put("connection_text", "В истории упоминались предметы: " + 
                              String.join(", ", items) + ". Событие может быть связано с ними.");
            }
        }
        
        return references;
    }
    
    /**
     * Находит скрытые связи через символы и детали из истории
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findHiddenConnectionsFromHistory(Map<String, Object> historyAnalysis) {
        Map<String, Object> connections = new HashMap<>();
        
        Map<String, Integer> mentionFrequency = (Map<String, Integer>) historyAnalysis.get("mention_frequency");
        if (mentionFrequency == null) {
            return connections;
        }
        
        // Ищем часто упоминаемые слова, которые могут быть символами
        List<String> symbolicWords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : mentionFrequency.entrySet()) {
            if (entry.getValue() >= 2) { // Упоминалось минимум 2 раза
                symbolicWords.add(entry.getKey());
            }
        }
        
        if (!symbolicWords.isEmpty()) {
            connections.put("symbolic_words", symbolicWords);
            connections.put("connection_text", "В истории часто упоминались: " + 
                           String.join(", ", symbolicWords.subList(0, Math.min(5, symbolicWords.size()))) + 
                           ". Событие может содержать эти символы или отсылки к ним.");
        }
        
        return connections;
    }
    
    /**
     * Находит параллельные сюжетные линии из истории
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findParallelStorylinesFromHistory(Map<String, Object> historyAnalysis) {
        Map<String, Object> storylines = new HashMap<>();
        
        List<Map<String, Object>> unfinished = (List<Map<String, Object>>) historyAnalysis.get("unfinished_storylines");
        if (unfinished != null && !unfinished.isEmpty()) {
            // Берем первую незавершенную сюжетную линию
            Map<String, Object> storyline = unfinished.get(0);
            storylines.put("parallel_storyline", storyline);
            storylines.put("connection_text", "В истории есть незавершенная сюжетная линия: " + 
                          storyline.get("description") + ". Событие может развить эту линию.");
        }
        
        return storylines;
    }
    
    /**
     * Находит отложенные последствия прошлых действий из истории
     */
    private Map<String, Object> findDelayedConsequencesFromHistory(EventContext context) {
        Map<String, Object> consequences = new HashMap<>();
        
        List<GameState.GameEvent> recentHistory = context.getRecentHistory();
        if (recentHistory == null || recentHistory.size() < 3) {
            return consequences;
        }
        
        // Ищем действия, которые могли иметь последствия
        List<String> consequenceSources = new ArrayList<>();
        for (int i = Math.max(0, recentHistory.size() - 5); i < recentHistory.size() - 1; i++) {
            GameState.GameEvent event = recentHistory.get(i);
            String description = event.getDescription().toLowerCase();
            
            // Проверяем, было ли действие, которое могло иметь последствия
            if (description.contains("убил") || description.contains("украл") || 
                description.contains("помог") || description.contains("спас") ||
                description.contains("обманул") || description.contains("предал") ||
                description.contains("разрушил") || description.contains("создал")) {
                
                consequenceSources.add(event.getDescription());
            }
        }
        
        if (!consequenceSources.isEmpty()) {
            consequences.put("consequence_sources", consequenceSources);
            consequences.put("connection_text", "Событие может быть последствием прошлых действий: " + 
                            String.join("; ", consequenceSources.subList(0, Math.min(2, consequenceSources.size()))));
        }
        
        return consequences;
    }
    
    /**
     * Находит прямые отсылки к прошлым событиям
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findDirectReferences(Map<String, Object> element,
                                                     Map<String, Object> historyAnalysis) {
        Map<String, Object> references = new HashMap<>();
        
        Map<String, List<String>> mentions = (Map<String, List<String>>) historyAnalysis.get("mentions");
        if (mentions == null) {
            return references;
        }
        
        String archetype = (String) element.get("archetype");
        String namePattern = (String) element.get("name_pattern");
        
        // Ищем упоминания NPC
        List<String> npcs = mentions.get("npcs");
        if (npcs != null && namePattern != null) {
            for (String npc : npcs) {
                String npcLower = npc.toLowerCase();
                String patternLower = namePattern.toLowerCase();
                
                // Проверяем, соответствует ли NPC паттерну
                if (patternLower.contains(npcLower) || npcLower.contains(patternLower.split("\\|")[0])) {
                    references.put("npc_reference", npc);
                    references.put("connection_text", "Вы встречаете " + npc + ", о котором слышали ранее");
                    break;
                }
            }
        }
        
        // Ищем упоминания предметов
        List<String> items = mentions.get("items");
        if (items != null && !items.isEmpty()) {
            // Берем первый упомянутый предмет как потенциальную связь
            String item = items.get(0);
            references.put("item_reference", item);
            references.put("item_connection_text", "Событие связано с " + item + ", который вы нашли ранее");
        }
        
        return references;
    }
    
    /**
     * Находит скрытые связи через символы и детали
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findHiddenConnections(Map<String, Object> element,
                                                     Map<String, Object> historyAnalysis) {
        Map<String, Object> connections = new HashMap<>();
        
        Map<String, Integer> mentionFrequency = (Map<String, Integer>) historyAnalysis.get("mention_frequency");
        if (mentionFrequency == null) {
            return connections;
        }
        
        // Ищем часто упоминаемые слова, которые могут быть символами
        List<String> symbolicWords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : mentionFrequency.entrySet()) {
            if (entry.getValue() >= 2) { // Упоминалось минимум 2 раза
                symbolicWords.add(entry.getKey());
            }
        }
        
        if (!symbolicWords.isEmpty()) {
            connections.put("symbolic_words", symbolicWords);
            connections.put("connection_text", "Событие содержит символы, которые вы видели ранее: " + 
                           String.join(", ", symbolicWords.subList(0, Math.min(3, symbolicWords.size()))));
        }
        
        return connections;
    }
    
    /**
     * Находит параллельные сюжетные линии
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findParallelStorylines(Map<String, Object> element,
                                                       Map<String, Object> historyAnalysis) {
        Map<String, Object> storylines = new HashMap<>();
        
        List<Map<String, Object>> unfinished = (List<Map<String, Object>>) historyAnalysis.get("unfinished_storylines");
        if (unfinished != null && !unfinished.isEmpty()) {
            // Берем первую незавершенную сюжетную линию
            Map<String, Object> storyline = unfinished.get(0);
            storylines.put("parallel_storyline", storyline);
            storylines.put("connection_text", "Это событие может быть связано с незавершенной историей: " + 
                          storyline.get("description"));
        }
        
        return storylines;
    }
    
    /**
     * Находит отложенные последствия прошлых действий
     */
    private Map<String, Object> findDelayedConsequences(Map<String, Object> element,
                                                       EventContext context) {
        Map<String, Object> consequences = new HashMap<>();
        
        List<GameState.GameEvent> recentHistory = context.getRecentHistory();
        if (recentHistory == null || recentHistory.size() < 3) {
            return consequences;
        }
        
        // Ищем действия, которые могли иметь последствия
        for (int i = Math.max(0, recentHistory.size() - 5); i < recentHistory.size() - 1; i++) {
            GameState.GameEvent event = recentHistory.get(i);
            String description = event.getDescription().toLowerCase();
            
            // Проверяем, было ли действие, которое могло иметь последствия
            if (description.contains("убил") || description.contains("украл") || 
                description.contains("помог") || description.contains("спас") ||
                description.contains("обманул") || description.contains("предал")) {
                
                consequences.put("consequence_source", event.getDescription());
                consequences.put("connection_text", "Это событие является последствием ваших прошлых действий");
                break;
            }
        }
        
        return consequences;
    }
    
    /**
     * Определяет основной тип связи
     */
    private String determinePrimaryConnectionType(Map<String, Object> connections) {
        if (!((Map<String, Object>) connections.get("direct_reference")).isEmpty()) {
            return "direct_reference";
        }
        if (!((Map<String, Object>) connections.get("delayed_consequence")).isEmpty()) {
            return "delayed_consequence";
        }
        if (!((Map<String, Object>) connections.get("parallel_storyline")).isEmpty()) {
            return "parallel_storyline";
        }
        if (!((Map<String, Object>) connections.get("hidden_connection")).isEmpty()) {
            return "hidden_connection";
        }
        return "none";
    }
    
    /**
     * Создает текст связи для промпта LLM
     */
    public String createConnectionText(Map<String, Object> connections) {
        StringBuilder text = new StringBuilder();
        
        String primaryType = (String) connections.get("primary_connection_type");
        
        switch (primaryType) {
            case "direct_reference":
                @SuppressWarnings("unchecked")
                Map<String, Object> directRef = (Map<String, Object>) connections.get("direct_reference");
                if (directRef.containsKey("connection_text")) {
                    text.append(directRef.get("connection_text"));
                }
                break;
                
            case "delayed_consequence":
                @SuppressWarnings("unchecked")
                Map<String, Object> consequence = (Map<String, Object>) connections.get("delayed_consequence");
                if (consequence.containsKey("connection_text")) {
                    text.append(consequence.get("connection_text"));
                }
                break;
                
            case "parallel_storyline":
                @SuppressWarnings("unchecked")
                Map<String, Object> parallel = (Map<String, Object>) connections.get("parallel_storyline");
                if (parallel.containsKey("connection_text")) {
                    text.append(parallel.get("connection_text"));
                }
                break;
                
            case "hidden_connection":
                @SuppressWarnings("unchecked")
                Map<String, Object> hidden = (Map<String, Object>) connections.get("hidden_connection");
                if (hidden.containsKey("connection_text")) {
                    text.append(hidden.get("connection_text"));
                }
                break;
        }
        
        return text.toString();
    }
}





