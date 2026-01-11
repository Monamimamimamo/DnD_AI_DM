package com.dnd.events;

import com.dnd.game_state.GameState;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Анализатор истории игры для извлечения паттернов и зацепок
 */
public class HistoryAnalyzer {
    
    /**
     * Анализирует историю игры и извлекает паттерны
     */
    public Map<String, Object> analyzeHistory(GameState gameState, int recentEventsLimit) {
        List<GameState.GameEvent> history = gameState.getGameHistory();
        
        // Берем последние N событий для анализа
        int start = Math.max(0, history.size() - recentEventsLimit);
        List<GameState.GameEvent> recentHistory = history.subList(start, history.size());
        
        Map<String, Object> analysis = new HashMap<>();
        
        // Извлекаем упоминания (персонажи, предметы, локации)
        analysis.put("mentions", extractMentions(recentHistory));
        
        // Находим незавершенные сюжетные линии
        analysis.put("unfinished_storylines", findUnfinishedStorylines(recentHistory));
        
        // Определяем паттерны действий игроков
        analysis.put("player_patterns", analyzePlayerPatterns(recentHistory));
        
        // Выявляем эмоциональные моменты
        analysis.put("emotional_moments", findEmotionalMoments(recentHistory));
        
        // Создаем список зацепок для будущих событий
        analysis.put("hooks", generateHooks(recentHistory, analysis));
        
        // Анализируем частоту упоминаний
        analysis.put("mention_frequency", calculateMentionFrequency(recentHistory));
        
        return analysis;
    }
    
    /**
     * Извлекает упоминания из истории (NPC, предметы, локации)
     */
    private Map<String, List<String>> extractMentions(List<GameState.GameEvent> history) {
        Map<String, List<String>> mentions = new HashMap<>();
        mentions.put("npcs", new ArrayList<>());
        mentions.put("items", new ArrayList<>());
        mentions.put("locations", new ArrayList<>());
        mentions.put("organizations", new ArrayList<>());
        
        // Паттерны для поиска упоминаний
        Pattern npcPattern = Pattern.compile("(торговец|маг|стражник|купец|старик|старуха|воин|жрец|бард|вор|аристократ|крестьянин|дворянин|король|королева|принц|принцесса|лорд|леди|капитан|командир|мастер|ученик|незнакомец|незнакомка)", Pattern.CASE_INSENSITIVE);
        Pattern itemPattern = Pattern.compile("(меч|кинжал|щит|доспех|кольцо|амулет|ключ|свиток|книга|карта|монета|сокровище|артефакт|реликвия|оружие|предмет)", Pattern.CASE_INSENSITIVE);
        Pattern locationPattern = Pattern.compile("(таверна|храм|замок|дворец|лес|пещера|подземелье|город|деревня|порт|рынок|площадь|улица|дом|башня|мост|река|гора|долина)", Pattern.CASE_INSENSITIVE);
        Pattern orgPattern = Pattern.compile("(гильдия|орден|братство|купеческая|гильдия|церковь|храм|королевство|империя|республика|союз|лига)", Pattern.CASE_INSENSITIVE);
        
        Set<String> uniqueNPCs = new HashSet<>();
        Set<String> uniqueItems = new HashSet<>();
        Set<String> uniqueLocations = new HashSet<>();
        Set<String> uniqueOrgs = new HashSet<>();
        
        for (GameState.GameEvent event : history) {
            String text = event.getDescription().toLowerCase();
            
            // Ищем NPC
            if (npcPattern.matcher(text).find()) {
                String npc = extractEntity(text, npcPattern);
                if (npc != null && !uniqueNPCs.contains(npc)) {
                    uniqueNPCs.add(npc);
                    mentions.get("npcs").add(npc);
                }
            }
            
            // Ищем предметы
            if (itemPattern.matcher(text).find()) {
                String item = extractEntity(text, itemPattern);
                if (item != null && !uniqueItems.contains(item)) {
                    uniqueItems.add(item);
                    mentions.get("items").add(item);
                }
            }
            
            // Ищем локации
            if (locationPattern.matcher(text).find()) {
                String location = extractEntity(text, locationPattern);
                if (location != null && !uniqueLocations.contains(location)) {
                    uniqueLocations.add(location);
                    mentions.get("locations").add(location);
                }
            }
            
            // Ищем организации
            if (orgPattern.matcher(text).find()) {
                String org = extractEntity(text, orgPattern);
                if (org != null && !uniqueOrgs.contains(org)) {
                    uniqueOrgs.add(org);
                    mentions.get("organizations").add(org);
                }
            }
        }
        
        return mentions;
    }
    
    /**
     * Извлекает сущность из текста по паттерну
     */
    private String extractEntity(String text, Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            // Пытаемся извлечь контекст вокруг найденного слова
            int start = Math.max(0, matcher.start() - 20);
            int end = Math.min(text.length(), matcher.end() + 20);
            String context = text.substring(start, end);
            
            // Упрощенная логика - возвращаем найденное слово
            return matcher.group();
        }
        return null;
    }
    
    /**
     * Находит незавершенные сюжетные линии
     */
    private List<Map<String, Object>> findUnfinishedStorylines(List<GameState.GameEvent> history) {
        List<Map<String, Object>> unfinished = new ArrayList<>();
        
        // Ищем события, которые упоминают что-то, но не завершают
        Set<String> mentionedButNotResolved = new HashSet<>();
        
        for (GameState.GameEvent event : history) {
            String text = event.getDescription().toLowerCase();
            
            // Ищем упоминания загадок, тайн, обещаний
            if (text.contains("загадк") || text.contains("тайна") || text.contains("обещан") ||
                text.contains("позже") || text.contains("в будущем") || text.contains("когда-нибудь")) {
                
                Map<String, Object> storyline = new HashMap<>();
                storyline.put("type", "unresolved_mystery");
                storyline.put("description", event.getDescription());
                storyline.put("timestamp", event.getTimestamp());
                unfinished.add(storyline);
            }
            
            // Ищем упоминания предметов без объяснения
            if (text.contains("наход") || text.contains("обнаруж") || text.contains("найден")) {
                if (!text.contains("использован") && !text.contains("применен") && !text.contains("объяснен")) {
                    Map<String, Object> storyline = new HashMap<>();
                    storyline.put("type", "unexplained_item");
                    storyline.put("description", event.getDescription());
                    storyline.put("timestamp", event.getTimestamp());
                    unfinished.add(storyline);
                }
            }
        }
        
        return unfinished;
    }
    
    /**
     * Анализирует паттерны действий игроков
     */
    private Map<String, Object> analyzePlayerPatterns(List<GameState.GameEvent> history) {
        Map<String, Object> patterns = new HashMap<>();
        
        int combatActions = 0;
        int socialActions = 0;
        int explorationActions = 0;
        int magicActions = 0;
        
        for (GameState.GameEvent event : history) {
            if (!"player_action".equals(event.getType())) continue;
            
            String text = event.getDescription().toLowerCase();
            
            if (text.contains("атак") || text.contains("бьет") || text.contains("стрел") || text.contains("удар")) {
                combatActions++;
            }
            if (text.contains("говорит") || text.contains("спрашивает") || text.contains("убеждает") || text.contains("обманывает")) {
                socialActions++;
            }
            if (text.contains("исследует") || text.contains("осматривает") || text.contains("ищет") || text.contains("проверяет")) {
                explorationActions++;
            }
            if (text.contains("заклинание") || text.contains("магия") || text.contains("колдует") || text.contains("кастует")) {
                magicActions++;
            }
        }
        
        patterns.put("combat_frequency", combatActions);
        patterns.put("social_frequency", socialActions);
        patterns.put("exploration_frequency", explorationActions);
        patterns.put("magic_frequency", magicActions);
        
        // Определяем доминирующий стиль игры
        int max = Math.max(Math.max(combatActions, socialActions), Math.max(explorationActions, magicActions));
        if (max == combatActions) {
            patterns.put("dominant_style", "combat");
        } else if (max == socialActions) {
            patterns.put("dominant_style", "social");
        } else if (max == explorationActions) {
            patterns.put("dominant_style", "exploration");
        } else {
            patterns.put("dominant_style", "magic");
        }
        
        return patterns;
    }
    
    /**
     * Выявляет эмоциональные моменты
     */
    private List<Map<String, Object>> findEmotionalMoments(List<GameState.GameEvent> history) {
        List<Map<String, Object>> moments = new ArrayList<>();
        
        Pattern emotionalPattern = Pattern.compile("(ужас|страх|радость|печаль|гнев|удивление|надежда|отчаяние|триумф|поражение|победа|потеря|находка|открытие|тайна|загадка)", Pattern.CASE_INSENSITIVE);
        
        for (GameState.GameEvent event : history) {
            if (emotionalPattern.matcher(event.getDescription()).find()) {
                Map<String, Object> moment = new HashMap<>();
                moment.put("description", event.getDescription());
                moment.put("timestamp", event.getTimestamp());
                moment.put("type", event.getType());
                moments.add(moment);
            }
        }
        
        return moments;
    }
    
    /**
     * Генерирует зацепки для будущих событий
     */
    private List<Map<String, Object>> generateHooks(List<GameState.GameEvent> history, Map<String, Object> analysis) {
        List<Map<String, Object>> hooks = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        Map<String, List<String>> mentions = (Map<String, List<String>>) analysis.get("mentions");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unfinished = (List<Map<String, Object>>) unfinishedStorylines(history);
        
        // Зацепки на основе упоминаний NPC
        for (String npc : mentions.get("npcs")) {
            Map<String, Object> hook = new HashMap<>();
            hook.put("type", "npc_encounter");
            hook.put("entity", npc);
            hook.put("description", "Встреча с " + npc + ", который упоминался ранее");
            hook.put("priority", 70);
            hooks.add(hook);
        }
        
        // Зацепки на основе незавершенных сюжетных линий
        for (Map<String, Object> storyline : unfinished) {
            Map<String, Object> hook = new HashMap<>();
            hook.put("type", "storyline_continuation");
            hook.put("description", storyline.get("description"));
            hook.put("priority", 80);
            hooks.add(hook);
        }
        
        // Зацепки на основе упоминаний предметов
        for (String item : mentions.get("items")) {
            Map<String, Object> hook = new HashMap<>();
            hook.put("type", "item_quest");
            hook.put("entity", item);
            hook.put("description", "Квест, связанный с " + item);
            hook.put("priority", 60);
            hooks.add(hook);
        }
        
        return hooks;
    }
    
    /**
     * Вычисляет частоту упоминаний
     */
    private Map<String, Integer> calculateMentionFrequency(List<GameState.GameEvent> history) {
        Map<String, Integer> frequency = new HashMap<>();
        
        for (GameState.GameEvent event : history) {
            String[] words = event.getDescription().toLowerCase().split("\\s+");
            for (String word : words) {
                // Убираем короткие слова и служебные
                if (word.length() > 4 && !isStopWord(word)) {
                    frequency.put(word, frequency.getOrDefault(word, 0) + 1);
                }
            }
        }
        
        return frequency;
    }
    
    /**
     * Проверяет, является ли слово служебным
     */
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("это", "как", "что", "который", "когда", "где", "куда", "откуда",
                                       "кто", "чем", "чем", "для", "при", "над", "под", "перед", "после");
        return stopWords.contains(word);
    }
    
    // Вспомогательный метод для незавершенных сюжетных линий
    private List<Map<String, Object>> unfinishedStorylines(List<GameState.GameEvent> history) {
        return findUnfinishedStorylines(history);
    }
}



