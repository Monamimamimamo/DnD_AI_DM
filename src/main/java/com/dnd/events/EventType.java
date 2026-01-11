package com.dnd.events;

/**
 * Типы событий, которые могут быть сгенерированы
 */
public enum EventType {
    NPC_ENCOUNTER,      // Встреча с NPC
    SIDE_QUEST,         // Побочный квест
    RANDOM_EVENT,       // Случайное событие (находка, препятствие и т.д.)
    LOCATION_EVENT,     // Событие, связанное с локацией
    QUEST_HOOK,         // Зацепка для квеста
    REVELATION,         // Откровение/поворот сюжета
    CONSEQUENCE         // Последствие прошлых действий
}

