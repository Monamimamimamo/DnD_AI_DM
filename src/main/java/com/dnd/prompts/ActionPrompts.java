package com.dnd.prompts;

import java.util.List;
import java.util.Map;

/**
 * Промпты для парсинга и обработки действий игроков
 */
public class ActionPrompts {
    
    /**
     * Промпт для генерации нарратива действия игрока
     */
    public static String getActionNarrativePrompt(String actionText, String characterName, 
                                                   String characterClass, String characterRace,
                                                   Map<String, Object> ruleResult, 
                                                   String currentLocation, String situation) {
        String resultStatus = ruleResult.getOrDefault("result", "").toString();
        boolean isSuccess = resultStatus.equals("success") || resultStatus.equals("partial_success");
        
        String situationContext = situation != null && !situation.isEmpty()
            ? "- Ситуация: " + situation + "\n"
            : "";
        
        return String.format("""
            Ты — опытный Dungeon Master для D&D 5e. Создай детальное, атмосферное описание действия игрока.
            
            Персонаж: %s (%s, %s)
            Действие: "%s"
            
            Результат проверки:
            - Навык: %s
            - Характеристика: %s
            - Сложность (DC): %s
            - Бросок: %s + модификаторы = %s
            - Результат: %s
            
            Контекст:
            - Локация: %s
            %s
            Создай ДЕТАЛЬНОЕ описание того, что происходит:
            1. Опиши, как персонаж выполняет действие
            2. Опиши, что он видит/чувствует/слышит
            3. Опиши результат действия (успех или неудача)
            4. Опиши последствия и что происходит дальше
            5. Если действие связано с перемещением, укажи новую локацию
            
            Будь конкретным и атмосферным. Используй детали окружения. Отвечай на русском языке.
            
            Верни ответ ТОЛЬКО в формате JSON (без дополнительного текста):
            {
                "message_type": "action_result",
                "content": "Детальное описание действия и его результата",
                "location": "Название локации (если персонаж переместился, иначе текущая локация)",
                "metadata": {
                    "action": "Описание действия",
                    "success": true/false,
                    "skill_used": "Навык",
                    "dc": "Сложность"
                }
            }
            
            ВАЖНО:
            - message_type должен быть "action_result"
            - content - детальное описание результата действия
            - location - локация после действия
            - metadata - информация о действии (success, skill_used, dc и т.д.)
            """, 
            characterName, characterClass, characterRace, actionText,
            ruleResult.getOrDefault("skill", "N/A"),
            ruleResult.getOrDefault("ability", "N/A"),
            ruleResult.getOrDefault("dc", "N/A"),
            ruleResult.getOrDefault("roll", "N/A"),
            ruleResult.getOrDefault("total", "N/A"),
            isSuccess ? "УСПЕХ" : "НЕУДАЧА",
            currentLocation,
            situationContext);
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

