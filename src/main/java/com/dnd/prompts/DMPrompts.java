package com.dnd.prompts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Коллекция промптов для Dungeon Master
 */
public class DMPrompts {
    
    /**
     * Системный промпт для Dungeon Master
     */
    public static String getSystemPrompt(int maxTokens) {
        return String.format("""
        Ты опытный Dungeon Master для D&D 5e. Твоя задача:

        1. Создавать увлекательные, АТМОСФЕРНЫЕ описания и сюжеты
        2. Следовать правилам D&D 5e
        3. Реагировать на действия игроков логично и интересно
        4. Поддерживать атмосферу приключения
        5. Проверять возможность действий согласно правилам
        6. Создавать ПРОРАБОТАННЫЙ МИР с деталями и контекстом
    
        ВАЖНО: Твой ответ должен быть ДЕТАЛЬНЫМ и АТМОСФЕРНЫМ.
        - Будь описательным и развернутым
        - Включай детали окружения, ощущения, звуки, запахи
        - Создавай живой и проработанный мир
        - Завершай ответ логически
        - Не обрывай на середине предложения
        - Максимальная длина: %d токенов (но используй их для деталей, а не краткости)
    
        Стиль ответа:
        - Описательный и атмосферный
        - Детальный и информативный
        - Учитывающий характеристики персонажей
        - Следующий правилам D&D
        - Создающий ощущение масштабного мира
    
        Всегда отвечай на русском языке.
        """, maxTokens);
    }
    
    /**
     * Промпт для генерации мира кампании
     */
    public static String getWorldBuildingPrompt() {
        return """
        Создай детальный и проработанный мир для кампании D&D 5e.
        
        ОБЯЗАТЕЛЬНО включи:
        
        1. ОПИСАНИЕ МИРА:
           - География: континенты, регионы, климат
           - Политическая система: королевства, города, правители
           - Магическая система: как работает магия в этом мире
           - История: ключевые события, войны, легенды
           - Культура: расы, народы, традиции, религии
        
        2. ОСНОВНАЯ ЛОКАЦИЯ (где начинается приключение):
           - Детальное описание места (город, деревня, замок и т.д.)
           - Важные NPC и их роли
           - Проблемы и конфликты в этой локации
           - Интересные места для исследования
        
        3. АТМОСФЕРА И ТОН:
           - Общий настрой кампании (мрачный, героический, загадочный и т.д.)
           - Стиль приключения
        
        Верни ответ ТОЛЬКО в формате JSON (без дополнительного текста):
        {
            "world_description": "Детальное описание мира",
            "main_location": {
                "name": "Название основной локации",
                "description": "Детальное описание локации",
                "important_npcs": ["NPC 1 и его роль", "NPC 2 и его роль"],
                "problems": ["Проблема 1", "Проблема 2"],
                "points_of_interest": ["Место 1", "Место 2"]
            },
            "atmosphere": "Описание атмосферы и тона кампании",
            "magic_system": "Как работает магия в этом мире",
            "history": "Ключевые исторические события"
        }
        
        КРИТИЧЕСКИ ВАЖНО:
        - Мир должен быть детальным и проработанным
        - Описания должны быть развернутыми (не краткие!)
        - Мир должен создавать основу для масштабного приключения
        - Отвечай ТОЛЬКО валидным JSON, без дополнительного текста
        """;
    }
    
    /**
     * Промпт для генерации начальной сцены и квеста вместе
     */
    public static String getInitialSceneAndQuestPrompt() {
        return """
        Создай увлекательное начало приключения D&D 5e с начальной сценой и основным квестом.
        
        ВАЖНО: Начальная сцена и квест должны быть ЛОГИЧЕСКИ СВЯЗАНЫ и создавать единую МАСШТАБНУЮ историю.
        
        Структура:
        
        1. НАЧАЛЬНАЯ СЦЕНА:
           - ТОЧНАЯ ЛОКАЦИЯ - где именно начинается приключение (детальное описание)
           - Атмосферное и ДЕТАЛЬНОЕ описание окружения (что видно, слышно, чувствуется)
           - Описание важных объектов, NPC, деталей окружения
           - Что-то интригующее, что намекает на квест (проблема, тайна, угроза)
           - Контекст мира - как эта сцена связана с большим миром
        
        2. ОСНОВНОЙ КВЕСТ:
           - Цель должна логически вытекать из начальной сцены
           - Квест должен быть МАСШТАБНЫМ (не простой задачей, а полноценным приключением)
           - Минимум 7-10 этапов, каждый этап должен быть значимым
           - Этапы должны быть связаны с элементами, упомянутыми в начальной сцене
           - Квест должен решать проблему или раскрывать тайну из начальной сцены
           - Квест должен вести через разные локации и ситуации
        
        Верни ответ ТОЛЬКО в формате JSON (без дополнительного текста):
        {
            "initial_scene": "Описание сцены",
            "quest": {
                "title": "Название квеста",
                "goal": "Главная цель квеста",
                "stages": ["Этапы", "квеста"],
                "description": "Развернутое описание квеста"
            }
        }
        
        КРИТИЧЕСКИ ВАЖНО:
        - Начальная сцена должна быть ДЕТАЛЬНОЙ
        - Квест должен быть МАСШТАБНЫМ с множеством этапов
        - Элементы из начальной сцены должны использоваться в квесте
        - Квест должен логически вытекать из проблем/тайны начальной сцены
        - Отвечай ТОЛЬКО валидным JSON, без дополнительного текста
        """;
    }
    
    /**
     * Промпт для генерации начальной сцены и квеста с учетом мира
     */
    public static String getInitialSceneAndQuestPromptWithWorld(Map<String, Object> world) {
        StringBuilder worldContext = new StringBuilder();
        if (world != null) {
            String worldDesc = (String) world.getOrDefault("world_description", "");
            String atmosphere = (String) world.getOrDefault("atmosphere", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> mainLocation = (Map<String, Object>) world.getOrDefault("main_location", new HashMap<>());
            String locationName = (String) mainLocation.getOrDefault("name", "");
            String locationDesc = (String) mainLocation.getOrDefault("description", "");
            
            worldContext.append(String.format("""
            
            КОНТЕКСТ МИРА:
            Описание мира: %s
            Атмосфера: %s
            Основная локация: %s
            Описание локации: %s
            
            Начальная сцена должна логически вписываться в этот мир и использовать информацию о локации.
            """, worldDesc, atmosphere, locationName, locationDesc));
        }
        
        return String.format("""
        Создай увлекательное начало приключения D&D 5e с начальной сценой и основным квестом.
        %s
        
        ВАЖНО: Начальная сцена и квест должны быть ЛОГИЧЕСКИ СВЯЗАНЫ и создавать единую МАСШТАБНУЮ историю.
        
        Структура:
        
        1. НАЧАЛЬНАЯ СЦЕНА:
           - ТОЧНАЯ ЛОКАЦИЯ - где именно начинается приключение (детальное описание, используй информацию о локации из мира)
           - Атмосферное и ДЕТАЛЬНОЕ описание окружения (что видно, слышно, чувствуется)
           - Описание важных объектов, NPC, деталей окружения
           - Что-то интригующее, что намекает на квест (проблема, тайна, угроза)
           - Контекст мира - как эта сцена связана с большим миром
        
        2. ОСНОВНОЙ КВЕСТ (масштабный и многоэтапный):
           - Цель должна логически вытекать из начальной сцены
           - Квест должен быть МАСШТАБНЫМ (не простой задачей, а полноценным приключением)
           - Минимум 7-10 этапов, каждый этап должен быть значимым
           - Этапы должны быть связаны с элементами, упомянутыми в начальной сцене
           - Квест должен решать проблему или раскрывать тайну из начальной сцены
           - Квест должен вести через разные локации и ситуации
        
        Верни ответ ТОЛЬКО в формате JSON (без дополнительного текста):
        {
            "initial_scene": "Детальное описание начальной сцены",
            "quest": {
                "title": "Название квеста",
                "goal": "Главная цель квеста (должна быть связана с начальной сценой)",
                "stages": ["Этап 1", "Этап 2", "Этап 3", "Этап 4", "Этап 5", "Этап 6", "Этап 7"],
                "description": "Развернутое описание квеста (как он связан с начальной сценой и миром)"
            }
        }
        
        КРИТИЧЕСКИ ВАЖНО:
        - Начальная сцена должна быть ДЕТАЛЬНОЙ)
        - Квест должен быть МАСШТАБНЫМ с множеством этапов
        - Элементы из начальной сцены должны использоваться в квесте
        - Квест должен логически вытекать из проблем/тайны начальной сцены
        - Начальная сцена должна использовать информацию о мире и локации
        - Отвечай ТОЛЬКО валидным JSON, без дополнительного текста
        """, worldContext.toString());
    }
    
    /**
     * Промпт для генерации ситуации, требующей действия
     */
    public static String getSituationPrompt(String initialScene, String characterName, String currentLocation, Map<String, Object> questInfo) {
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
        
            return String.format("""
                Начальная сцена: %s
                
                Персонаж: %s
                %s
                %s
                
                Создай ДЕТАЛЬНОЕ и КОНКРЕТНОЕ описание ситуации, которая требует от персонажа действия.
                
                ОБЯЗАТЕЛЬНО включи (минимум 5-7 предложений):
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
                
                Опиши ситуацию ДЕТАЛЬНО (минимум 5-7 предложений), атмосферно, и так, чтобы было понятно, что персонажу нужно что-то сделать.
                НЕ проси персонажа действовать напрямую - просто опиши ситуацию, которая естественным образом требует действия.
                
                Если есть текущий квест, ситуация должна логически вести к выполнению текущего этапа.
                """, initialScene, characterName, locationContext, questContext);
    }
    
    /**
     * Промпт для генерации финальной сцены при завершении квеста
     */
    public static String getFinalScenePrompt(String questTitle, String questGoal) {
        return String.format("""
        Квест "%s" завершен!
        Цель квеста: %s
        
        Создай эпическую финальную сцену, которая:
        1. Показывает последствия выполнения квеста
        2. Подводит итоги приключения
        3. Дает ощущение завершенности истории
        4. Может намекать на возможные продолжения
        
        Опиши финальную сцену атмосферно и эмоционально (3-5 предложений).
        """, questTitle, questGoal);
    }
    
    /**
     * Промпт для ответа на действие игрока
     */
    public static String getActionResponsePrompt(String context, String action, Map<String, Object> characterInfo) {
        return String.format("""
        Контекст: %s
        Действие игрока: %s
        Информация о персонаже: %s

        Сгенерируй ответ Dungeon Master'а на это действие.
        """, context, action, characterInfo);
    }
    
    /**
     * Системный промпт для парсера действий с данными SRD
     */
    public static String getActionParserSystemPrompt(String skillsList, String dcInfo) {
        String skillsSection = skillsList != null && !skillsList.isEmpty()
            ? String.format("""
Доступные навыки из SRD (используй только эти):
%s""", skillsList)
            : """
Доступные навыки из SRD (используй только навыки из SRD D&D 5e)""";
        
        String dcSection = dcInfo != null && !dcInfo.isEmpty()
            ? String.format("""
Таблица сложности (DC):
%s""", dcInfo)
            : """
Таблица сложности (DC):
- very_easy: DC 5
- easy: DC 10
- medium: DC 15
- hard: DC 20
- very_hard: DC 25
- nearly_impossible: DC 30""";
        
        String template = """
Ты — эксперт по правилам D&D 5e, который интерпретирует действия игроков.

Твоя задача:
1. Определить, возможно ли действие по правилам D&D 5e
2. Определить, какая характеристика (ability) и навык (skill) из SRD используются
3. Оценить сложность (DC) на основе контекста
4. Указать модификаторы окружения
%s
%s

Характеристики D&D 5e:
- strength (сила)
- dexterity (ловкость)
- constitution (телосложение)
- intelligence (интеллект)
- wisdom (мудрость)
- charisma (харизма)

Формат ответа (ОБЯЗАТЕЛЬНО валидный JSON):
{
    "is_possible": true/false,  // Возможно ли действие по правилам D&D 5e
    "intent": "jump",  // Тип действия (jump, climb, sneak, persuade, attack, cast, etc.)
    "ability": "strength",  // Характеристика для проверки (strength, dexterity, etc.)
    "skill": "athletics",  // Навык из SRD (если применимо, null если нет)
    "estimated_dc": 15,  // Сложность как число ИЛИ строка "medium" (если не можешь точно определить)
    "estimated_difficulty": "hard",  // Уровень сложности (very_easy, easy, medium, hard, very_hard, nearly_impossible)
    "modifiers": ["wide river", "muddy ground"],  // Факторы окружения, влияющие на действие
    "required_items": [],  // Необходимые предметы (если есть)
    "reason": "Перепрыгнуть реку требует проверки Athletics (Strength) из-за ширины и сложности",  // Объяснение
    "base_action": "jump"  // Базовое действие из SRD (если применимо, null если нет)
}

КРИТИЧЕСКИ ВАЖНО:
- Отвечай ТОЛЬКО валидным JSON, без дополнительного текста
- Если действие невозможно (нарушает законы физики/магии/правила), установи "is_possible": false и укажи "reason" с объяснением, почему действие невозможно
- В "reason" для невозможных действий обязательно укажи, что игрок должен попробовать другое действие
- Используй ТОЛЬКО навыки из предоставленного списка SRD
- "estimated_dc" может быть числом (10-30) или строкой уровня сложности
- Всегда возвращай полный JSON объект со всеми полями""";
        return String.format(template, skillsSection, dcSection);
    }
    
    /**
     * Промпт для первого этапа - выбор нужных эндпоинтов
     */
    public static String getEndpointSelectionPrompt(String actionText, Map<String, String> availableEndpoints) {
        StringBuilder endpointsList = new StringBuilder();
        if (availableEndpoints != null) {
            for (Map.Entry<String, String> entry : availableEndpoints.entrySet()) {
                endpointsList.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
            }
        }
        
        String template = """
Действие игрока: "%s"

Доступные эндпоинты SRD API:
%s

Проанализируй действие и определи, какие эндпоинты из SRD API тебе нужны для правильной интерпретации этого действия.

Например:
- Для физических действий (прыжок, лазание) → skills, ability-scores
- Для магических действий → spells, magic-schools
- Для использования предметов → equipment, magic-items
- Для социальных действий → skills (persuasion, deception, etc.)

Отвечай ТОЛЬКО валидным JSON:
{
    "required_endpoints": ["skills", "ability-scores"]  // Список названий эндпоинтов, которые нужны
}""";
        return String.format(template, actionText, endpointsList);
    }
    
    /**
     * Пользовательский промпт для парсера действий
     */
    public static String getActionParserUserPrompt(String actionText, Map<String, Object> gameContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("Действие игрока: \"%s\"\n\n", actionText));
        
        if (gameContext != null) {
            if (gameContext.containsKey("current_location")) {
                prompt.append(String.format("Локация: %s\n", gameContext.get("current_location")));
            }
            if (gameContext.containsKey("environment")) {
                Object env = gameContext.get("environment");
                if (env instanceof List) {
                    prompt.append(String.format("Окружение: %s\n", String.join(", ", 
                        ((List<?>) env).stream().map(Object::toString).toArray(String[]::new))));
                } else {
                    prompt.append(String.format("Окружение: %s\n", env));
                }
            }
            if (gameContext.containsKey("equipment")) {
                Object equip = gameContext.get("equipment");
                if (equip instanceof List) {
                    prompt.append(String.format("Снаряжение: %s\n", String.join(", ", 
                        ((List<?>) equip).stream().map(Object::toString).toArray(String[]::new))));
                } else {
                    prompt.append(String.format("Снаряжение: %s\n", equip));
                }
            }
        }
        
        prompt.append("""
Проанализируй это действие по правилам D&D 5e:
1. Определи, возможно ли это действие
2. Определи, какая характеристика и навык из SRD нужны
3. Оцени сложность (DC) на основе контекста
4. Укажи модификаторы окружения

Отвечай ТОЛЬКО валидным JSON согласно формату.""");
        
        return prompt.toString();
    }
    
    /**
     * Промпт для второго этапа - финальный парсинг с данными из SRD
     */
    public static String getActionParserFinalPrompt(String actionText, Map<String, Object> srdData, 
                                                    Map<String, Object> gameContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("Действие игрока: \"%s\"\n\n", actionText));
        
        if (gameContext != null) {
            if (gameContext.containsKey("current_location")) {
                prompt.append(String.format("Локация: %s\n", gameContext.get("current_location")));
            }
            if (gameContext.containsKey("environment")) {
                Object env = gameContext.get("environment");
                if (env instanceof List) {
                    prompt.append(String.format("Окружение: %s\n", String.join(", ", 
                        ((List<?>) env).stream().map(Object::toString).toArray(String[]::new))));
                } else {
                    prompt.append(String.format("Окружение: %s\n", env));
                }
            }
            if (gameContext.containsKey("equipment")) {
                Object equip = gameContext.get("equipment");
                if (equip instanceof List) {
                    prompt.append(String.format("Снаряжение: %s\n", String.join(", ", 
                        ((List<?>) equip).stream().map(Object::toString).toArray(String[]::new))));
                } else {
                    prompt.append(String.format("Снаряжение: %s\n", equip));
                }
            }
        }
        
        // Добавляем данные из SRD
        prompt.append("\nДанные из SRD API:\n");
        if (srdData != null) {
            for (Map.Entry<String, Object> entry : srdData.entrySet()) {
                Object data = entry.getValue();
                if (data != null) {
                    String dataPreview;
                    if (data instanceof List) {
                        List<?> dataList = (List<?>) data;
                        int limit = Math.min(10, dataList.size());
                        dataPreview = dataList.subList(0, limit).toString();
                    } else {
                        dataPreview = data.toString();
                    }
                    // Ограничиваем длину
                    if (dataPreview.length() > 500) {
                        dataPreview = dataPreview.substring(0, 500) + "...";
                    }
                    prompt.append(String.format("\n%s:\n%s...\n", entry.getKey(), dataPreview));
                }
            }
        }
        
        prompt.append("""
Используя эти данные из SRD, проанализируй действие по правилам D&D 5e:
1. Определи, возможно ли это действие
2. Определи, какая характеристика и навык из SRD нужны
3. Оцени сложность (DC) на основе контекста и данных SRD
4. Укажи модификаторы окружения

Отвечай ТОЛЬКО валидным JSON согласно формату.""");
        
        return prompt.toString();
    }
}

