package com.dnd.events;

import com.dnd.game_state.GameState;
import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Менеджер триггеров для определения когда генерировать события
 * Использует контекстные триггеры вместо фиксированных временных интервалов
 */
public class EventTriggerManager {
    private Map<String, Set<String>> visitedLocations = new HashMap<>(); // Посещенные локации по сессии
    private Map<String, LocalDateTime> lastEventTimes = new HashMap<>(); // Время последних событий по типам
    private HistoryAnalyzer historyAnalyzer = new HistoryAnalyzer();
    
    // Минимальное время между событиями одного типа (в минутах)
    private static final int MIN_MINUTES_BETWEEN_EVENTS = 5;
    // Минимальное количество действий для проверки паттернов
    private static final int MIN_ACTIONS_FOR_PATTERN_ANALYSIS = 3;
    
    /**
     * Проверяет, нужно ли генерировать событие
     */
    public EventTrigger checkTriggers(GameState gameState, String sessionId) {
        List<EventTrigger> activeTriggers = new ArrayList<>();
        
        // 1. Проверка триггера на основе локации
        EventTrigger locationTrigger = checkLocationTrigger(gameState, sessionId);
        if (locationTrigger != null) {
            activeTriggers.add(locationTrigger);
        }
        
        // 2. Проверка триггера на основе квеста
        EventTrigger questTrigger = checkQuestTrigger(gameState);
        if (questTrigger != null) {
            activeTriggers.add(questTrigger);
        }
        
        // 3. Проверка триггера на основе паттернов в истории
        EventTrigger patternTrigger = checkPatternBasedTrigger(gameState, sessionId);
        if (patternTrigger != null) {
            activeTriggers.add(patternTrigger);
        }
        
        // 4. Проверка контекстного триггера (незавершенные сюжетные линии, долгое отсутствие событий)
        EventTrigger contextTrigger = checkContextBasedTrigger(gameState, sessionId);
        if (contextTrigger != null) {
            activeTriggers.add(contextTrigger);
        }
        
        // 5. Проверка триггера на основе флагов
        EventTrigger flagTrigger = checkFlagTrigger(gameState);
        if (flagTrigger != null) {
            activeTriggers.add(flagTrigger);
        }
        
        // Выбираем триггер с наивысшим приоритетом
        if (activeTriggers.isEmpty()) {
            return null;
        }
        
        return activeTriggers.stream()
                .max(Comparator.comparingInt(EventTrigger::getPriority))
                .orElse(null);
    }
    
    /**
     * Проверяет триггер на основе локации
     */
    private EventTrigger checkLocationTrigger(GameState gameState, String sessionId) {
        String currentLocation = gameState.getCurrentLocation();
        
        if (currentLocation == null || currentLocation.isEmpty()) {
            return null;
        }
        
        // Инициализируем множество посещенных локаций для сессии
        if (!visitedLocations.containsKey(sessionId)) {
            visitedLocations.put(sessionId, new HashSet<>());
        }
        
        Set<String> visited = visitedLocations.get(sessionId);
        
        // Если локация посещается впервые
        if (!visited.contains(currentLocation)) {
            visited.add(currentLocation);
            
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("location", currentLocation);
            conditions.put("first_visit", true);
            
            // Записываем событие локации
            recordLocationEvent(currentLocation);
            
            return new EventTrigger(
                EventTrigger.TriggerType.LOCATION_BASED,
                conditions,
                80, // Высокий приоритет для первого посещения
                "Первое посещение локации: " + currentLocation
            );
        }
        
        return null;
    }
    
    /**
     * Проверяет триггер на основе квеста
     */
    private EventTrigger checkQuestTrigger(GameState gameState) {
        Map<String, Object> mainQuest = gameState.getMainQuest();
        
        if (mainQuest == null) {
            return null;
        }
        
        // Проверяем, завершен ли этап квеста недавно
        String currentStage = gameState.getCurrentQuestStage();
        int currentStageIndex = getIntValue(mainQuest.get("current_stage_index"), 0);
        
        // Если квест только начался или перешел на новый этап
        // (можно отслеживать через флаги, когда этап был завершен)
        
        // Проверяем прогресс квеста
        int storyProgress = gameState.getStoryProgress();
        
        // Генерируем событие после завершения этапа (если прогресс изменился)
        if (storyProgress > 0 && storyProgress < 100) {
            Map<String, Object> conditions = new HashMap<>();
            conditions.put("quest_stage", currentStage);
            conditions.put("quest_progress", storyProgress);
            conditions.put("stage_index", currentStageIndex);
            
            return new EventTrigger(
                EventTrigger.TriggerType.QUEST_BASED,
                conditions,
                75, // Высокий приоритет после этапа квеста
                "Прогресс квеста: этап " + currentStageIndex + " (" + currentStage + ")"
            );
        }
        
        return null;
    }
    
    /**
     * Проверяет триггер на основе паттернов в истории игры
     */
    private EventTrigger checkPatternBasedTrigger(GameState gameState, String sessionId) {
        List<GameState.GameEvent> history = gameState.getGameHistory();
        
        // Нужно минимум несколько действий для анализа паттернов
        long actionCount = history.stream()
                .filter(e -> "player_action".equals(e.getType()))
                .count();
        
        if (actionCount < MIN_ACTIONS_FOR_PATTERN_ANALYSIS) {
            return null;
        }
        
        // Анализируем историю для выявления паттернов
        Map<String, Object> analysis = historyAnalyzer.analyzeHistory(gameState, 15);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> playerPatterns = (Map<String, Object>) analysis.get("player_patterns");
        
        if (playerPatterns == null) {
            return null;
        }
        
        String dominantStyle = (String) playerPatterns.get("dominant_style");
        
        // Проверяем, прошло ли достаточно времени с последнего события этого типа
        String eventTypeKey = "pattern_" + dominantStyle;
        if (!canGenerateEvent(eventTypeKey)) {
            return null;
        }
        
        // Определяем тип события на основе доминирующего стиля
        EventType suggestedEventType = determineEventTypeByPattern(dominantStyle);
        
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("pattern", dominantStyle);
        conditions.put("analysis", analysis);
        conditions.put("suggested_event_type", suggestedEventType.name());
        
        // Обновляем время последнего события
        lastEventTimes.put(eventTypeKey, LocalDateTime.now());
        
        return new EventTrigger(
            EventTrigger.TriggerType.PATTERN_BASED,
            conditions,
            60, // Средний приоритет для паттернов
            "Обнаружен паттерн: " + dominantStyle + " стиль игры"
        );
    }
    
    /**
     * Проверяет контекстный триггер (незавершенные сюжетные линии, долгое отсутствие событий)
     */
    private EventTrigger checkContextBasedTrigger(GameState gameState, String sessionId) {
        List<GameState.GameEvent> history = gameState.getGameHistory();
        
        if (history.isEmpty()) {
            return null;
        }
        
        // Анализируем историю для поиска незавершенных сюжетных линий
        Map<String, Object> analysis = historyAnalyzer.analyzeHistory(gameState, 20);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unfinishedStorylines = (List<Map<String, Object>>) analysis.get("unfinished_storylines");
        
        // Проверяем незавершенные сюжетные линии
        if (unfinishedStorylines != null && !unfinishedStorylines.isEmpty()) {
            // Берем самую старую незавершенную сюжетную линию
            Map<String, Object> oldestStoryline = unfinishedStorylines.get(0);
            
            // Проверяем, прошло ли достаточно времени с последнего события этого типа
            if (canGenerateEvent("storyline_continuation")) {
                Map<String, Object> conditions = new HashMap<>();
                conditions.put("storyline", oldestStoryline);
                conditions.put("storyline_type", oldestStoryline.get("type"));
                
                lastEventTimes.put("storyline_continuation", LocalDateTime.now());
                
                return new EventTrigger(
                    EventTrigger.TriggerType.STORYLINE_BASED,
                    conditions,
                    85, // Высокий приоритет для продолжения сюжетных линий
                    "Незавершенная сюжетная линия: " + oldestStoryline.get("type")
                );
            }
        }
        
        // Проверяем долгое отсутствие событий в текущей локации
        String currentLocation = gameState.getCurrentLocation();
        if (currentLocation != null && !currentLocation.isEmpty()) {
            String locationEventKey = "location_" + currentLocation;
            LocalDateTime lastLocationEvent = lastEventTimes.get(locationEventKey);
            
            if (lastLocationEvent == null || 
                ChronoUnit.MINUTES.between(lastLocationEvent, LocalDateTime.now()) > MIN_MINUTES_BETWEEN_EVENTS * 2) {
                
                // Проверяем, есть ли события в этой локации
                long eventsInLocation = history.stream()
                        .filter(e -> e.getDescription().toLowerCase().contains(currentLocation.toLowerCase()))
                        .count();
                
                // Если событий мало или их давно не было
                if (eventsInLocation < 2 || lastLocationEvent == null) {
                    Map<String, Object> conditions = new HashMap<>();
                    conditions.put("location", currentLocation);
                    conditions.put("events_count", eventsInLocation);
                    conditions.put("reason", "долгое отсутствие событий в локации");
                    
                    return new EventTrigger(
                        EventTrigger.TriggerType.CONTEXT_BASED,
                        conditions,
                        55, // Средний приоритет
                        "Долгое отсутствие событий в локации: " + currentLocation
                    );
                }
            }
        }
        
        return null;
    }
    
    /**
     * Проверяет, можно ли генерировать событие данного типа (rate limiting)
     */
    private boolean canGenerateEvent(String eventTypeKey) {
        LocalDateTime lastTime = lastEventTimes.get(eventTypeKey);
        if (lastTime == null) {
            return true;
        }
        
        long minutesSince = ChronoUnit.MINUTES.between(lastTime, LocalDateTime.now());
        return minutesSince >= MIN_MINUTES_BETWEEN_EVENTS;
    }
    
    /**
     * Определяет тип события на основе паттерна игры
     */
    private EventType determineEventTypeByPattern(String pattern) {
        switch (pattern) {
            case "combat":
                return EventType.RANDOM_EVENT; // Боевое событие
            case "social":
                return EventType.NPC_ENCOUNTER; // Встреча с NPC
            case "exploration":
                return EventType.LOCATION_EVENT; // Событие локации
            case "magic":
                return EventType.RANDOM_EVENT; // Магическое событие
            default:
                return EventType.RANDOM_EVENT;
        }
    }
    
    /**
     * Проверяет триггер на основе флагов
     */
    private EventTrigger checkFlagTrigger(GameState gameState) {
        // Здесь можно добавить проверку флагов из расширенного GameState
        // Пока возвращаем null, так как система флагов еще не расширена
        
        // Пример логики:
        // if (gameState.hasFlag("npc_mentioned_trader")) {
        //     return new EventTrigger(...);
        // }
        
        return null;
    }
    
    /**
     * Определяет тип события на основе триггера
     */
    public EventType determineEventType(EventTrigger trigger, GameState gameState) {
        if (trigger == null) {
            return EventType.RANDOM_EVENT;
        }
        
        EventTrigger.TriggerType triggerType = trigger.getType();
        Map<String, Object> conditions = trigger.getConditions();
        
        switch (triggerType) {
            case LOCATION_BASED:
                Boolean firstVisit = (Boolean) conditions.getOrDefault("first_visit", false);
                if (firstVisit) {
                    return EventType.LOCATION_EVENT;
                }
                return EventType.RANDOM_EVENT;
                
            case QUEST_BASED:
                return EventType.SIDE_QUEST;
                
            case PATTERN_BASED:
                // Используем предложенный тип из анализа паттернов
                String suggestedType = (String) conditions.get("suggested_event_type");
                if (suggestedType != null) {
                    try {
                        return EventType.valueOf(suggestedType);
                    } catch (IllegalArgumentException e) {
                        return EventType.RANDOM_EVENT;
                    }
                }
                return EventType.RANDOM_EVENT;
                
            case CONTEXT_BASED:
                // Контекстные события могут быть разными
                String reason = (String) conditions.getOrDefault("reason", "");
                if (reason.contains("локации")) {
                    return EventType.LOCATION_EVENT;
                }
                return EventType.RANDOM_EVENT;
                
            case STORYLINE_BASED:
                // Продолжение сюжетной линии может быть откровением или последствием
                String storylineType = (String) conditions.getOrDefault("storyline_type", "");
                if (storylineType.contains("mystery")) {
                    return EventType.REVELATION;
                }
                return EventType.CONSEQUENCE;
                
            case FLAG_BASED:
                return EventType.QUEST_HOOK;
                
            default:
                return EventType.RANDOM_EVENT;
        }
    }
    
    /**
     * Безопасное преобразование значения в int
     */
    private int getIntValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * Сбрасывает счетчики для новой сессии
     */
    public void resetSession(String sessionId) {
        visitedLocations.remove(sessionId);
        // Не сбрасываем lastEventTimes, так как они глобальные для игры
    }
    
    /**
     * Обновляет время последнего события для локации
     */
    public void recordLocationEvent(String location) {
        if (location != null && !location.isEmpty()) {
            lastEventTimes.put("location_" + location, LocalDateTime.now());
        }
    }
}







