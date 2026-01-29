package com.dnd.game_state;

/**
 * Длительность сессии кампании
 */
public enum SessionDuration {
    /**
     * Короткая сессия (~4 часа)
     * Маленький городок с несколькими ключевыми точками.
     * Мир должен быть прописан, но не огромен.
     */
    SHORT("short", "Короткая", "Маленький городок с несколькими ключевыми точками. Мир должен быть прописан, но не огромен. Сессия приблизительно на 4 часа."),
    
    /**
     * Средняя сессия (~30-40 часов)
     * Что-то среднее между короткой и длинной сессией.
     */
    MEDIUM("medium", "Средняя", "Что-то среднее между короткой и длинной сессией. Сессия приблизительно на 30-40 часов."),
    
    /**
     * Длинная сессия (не ограничена)
     * Огромный прописанный мир с глобальным квестом, к которому нужно идти шаг за шагом.
     * Уровень детализации максимален.
     */
    LONG("long", "Длинная", "Огромный прописанный мир с глобальным квестом, к которому нужно идти шаг за шагом. Сессия не ограничена временем. Уровень детализации максимален.");
    
    private final String value;
    private final String displayName;
    private final String description;
    
    SessionDuration(String value, String displayName, String description) {
        this.value = value;
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Получить SessionDuration по строковому значению
     */
    public static SessionDuration fromString(String value) {
        if (value == null) {
            return MEDIUM; // По умолчанию средняя
        }
        for (SessionDuration duration : values()) {
            if (duration.value.equalsIgnoreCase(value)) {
                return duration;
            }
        }
        return MEDIUM; // По умолчанию средняя
    }
}
