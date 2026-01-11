package com.dnd.prompts;

import java.util.Map;

/**
 * Промпты для генерации ситуаций
 */
public class SituationPrompts {
    
    /**
     * Промпт для генерации ситуации, требующей действия
     */
    public static String getSituationPrompt(String previousSituation, String characterName, String currentLocation, Map<String, Object> questInfo) {
        StringBuilder questContext = new StringBuilder();
        if (questInfo != null) {
            String currentStage = (String) questInfo.getOrDefault("current_stage", "");
            String goal = (String) questInfo.getOrDefault("goal", "");
            questContext.append(String.format("""
            
            ТЕКУЩИЙ КВЕСТ:
            Цель: %s
            Текущий этап: %s
            
            Ситуация должна быть связана с текущим этапом квеста и продвигать сюжет к цели.
            """, goal, currentStage));
        }
        
        String locationContext = currentLocation != null && !currentLocation.isEmpty() 
            ? String.format("\nТекущая локация: %s", currentLocation) 
            : "";
        
        String situationContext = previousSituation != null && !previousSituation.isEmpty()
            ? String.format("\nПредыдущая ситуация: %s\n\nНовая ситуация должна логически вытекать из предыдущей.", previousSituation)
            : "";
        
        return String.format("""
            %s
            
            Персонаж: %s
            %s
            %s
            
            Создай ДЕТАЛЬНОЕ и КОНКРЕТНОЕ описание ситуации, которая требует от персонажа действия.
            
            ОБЯЗАТЕЛЬНО включи:
            1. ТОЧНУЮ ЛОКАЦИЮ - где именно находится персонаж (детальное описание места)
            2. ДЕТАЛЬНОЕ ОПИСАНИЕ ОКРУЖЕНИЯ - что персонаж видит вокруг (конкретные объекты, препятствия, детали, атмосфера)
            3. СЕНСОРНЫЕ ДЕТАЛИ - что персонаж слышит, чувствует, ощущает
            4. КОНКРЕТНЫЕ ВАРИАНТЫ - что можно сделать (например: "Перед вами три двери: северная, восточная и западная", "Река шириной 5 метров, через которую перекинут шаткий мост", "Замок на двери, рядом лежит ключ")
            5. ЧЕТКИЕ УСЛОВИЯ - что нужно преодолеть или решить
            6. КОНТЕКСТ МИРА - как эта ситуация связана с большим миром и квестом
            
            Это может быть:
            - Препятствие (река, дверь, пропасть, враги)
            - Выбор пути (несколько дорог, двери, направления)
            - Событие, требующее реакции (крик, шум, встреча с NPC)
            - Задача, которую нужно решить (замок, головоломка, переговоры)
            
            Опиши ситуацию ДЕТАЛЬНО, атмосферно, и так, чтобы было понятно, что персонажу нужно что-то сделать.
            НЕ проси персонажа действовать напрямую - просто опиши ситуацию, которая естественным образом требует действия.
            
            Если есть текущий квест, ситуация должна логически вести к выполнению текущего этапа.
            
            Верни ответ ТОЛЬКО в формате JSON (без дополнительного текста):
            {
                "message_type": "situation_continuation",
                "content": "Детальное описание ситуации",
                "location": "Название локации",
                "metadata": {
                    "quest_stage": "Текущий этап квеста (если есть)",
                    "requires_action": true
                }
            }
            
            ВАЖНО: 
            - message_type должен быть "situation_continuation"
            - content - это детальное описание ситуации
            - location - название локации
            - metadata - дополнительные данные (может быть пустым объектом)
            """, situationContext, characterName, locationContext, questContext);
    }
}

