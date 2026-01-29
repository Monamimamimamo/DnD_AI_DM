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
        boolean isSuccess = resultStatus.equals("success") || resultStatus.equals("partial_success") || 
                           resultStatus.equals("automatic_success");
        boolean requiresDiceRoll = Boolean.TRUE.equals(ruleResult.getOrDefault("requires_dice_roll", true));
        
        String situationContext = situation != null && !situation.isEmpty()
            ? "- Ситуация: " + situation + "\n"
            : "";
        
        String diceRollInfo;
        if (requiresDiceRoll) {
            diceRollInfo = String.format("- Бросок: %s + модификаторы = %s\n", 
                ruleResult.getOrDefault("roll", "N/A"),
                ruleResult.getOrDefault("total", "N/A"));
        } else {
            diceRollInfo = "- Бросок кубиков не требуется (тривиальное действие)\n";
        }
        
        return String.format("""
            Ты — опытный Dungeon Master для D&D 5e. Создай детальное, атмосферное описание действия игрока.
            
            Персонаж: %s (%s, %s)
            Действие: "%s"
            
            Результат проверки:
            - Навык: %s
            - Характеристика: %s
            - Сложность (DC): %s
            %s- Результат: %s
            
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
                },
                "analysis": {
                    "npcs_mentioned": ["список имен NPC, которые упоминались или появились"],
                    "locations_mentioned": ["список локаций, к которым относится сообщение"],
                    "quests_mentioned": ["список названий квестов, которые были начаты или к которым относится сообщение"],
                    "key_events": ["список ключевых событий, которые произошли"],
                    "connections": ["список связей между событиями, персонажами, локациями"],
                    "new_information": {
                        "npcs": [
                            {
                                "name": "имя NPC",
                                "description": "описание NPC (если новый или обновлен)",
                                "location": "текущая локация NPC (где он находится сейчас)",
                                "home_location": "домашняя локация NPC (где он живет/базируется, может отличаться от текущей)"
                            }
                        ],
                        "locations": [
                            {
                                "name": "название локации",
                                "description": "описание локации (если новая или обновлена)"
                            }
                        ],
                        "quests": [
                            {
                                "title": "название квеста",
                                "description": "описание квеста (если новый или обновлен)",
                                "type": "main" | "side"
                            }
                        ]
                    }
                }
            }
            
            ВАЖНО:
            - message_type должен быть "action_result"
            - content - детальное описание результата действия
            - location - локация после действия
            - metadata - информация о действии (success, skill_used, dc и т.д.)
            - analysis - анализ сказанного (заполняй только если есть что анализировать, иначе можешь опустить или оставить пустым)
            - В analysis включай только релевантную информацию: новые NPC, локации, квесты или важные упоминания.
            - Уточняю: не нужно писать всё подряд, пишем только важных npc, продумывай их историю и значимость. Например не нужно писать рандомного стражника, если мы просто спросили у него дорогу (пиши только если он будет связан с сюжетом).
            - Если локация находится в другой локации. Например: город Лордран, страна Ильбум; или таверна "дикий крот", город Лордран
            - Если ничего нового не произошло, можешь опустить поле analysis или оставить его пустым
            """, 
            characterName, characterClass, characterRace, actionText,
            ruleResult.getOrDefault("skill", "N/A"),
            ruleResult.getOrDefault("ability", "N/A"),
            ruleResult.getOrDefault("dc", "N/A"),
            diceRollInfo,
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

ВАЖНО: Используй данные из SRD API для определения навыка и характеристики!

В данных из SRD API есть полный список навыков (skills). Каждый навык содержит:
- index: индекс навыка (используй в поле "skill")
- ability_score.index: индекс характеристики (str, dex, con, int, wis, cha)
- ability_score.name: название характеристики

Правила определения ability и skill:
1. Если действие требует навыка:
   - Найди нужный навык в списке skills из SRD API
   - Используй index навыка в поле "skill"
   - Используй ability_score.index из выбранного навыка в поле "ability"
   - Конвертируй ability_score.index в полное название:
     * str → strength
     * dex → dexterity
     * con → constitution
     * int → intelligence
     * wis → wisdom
     * cha → charisma

2. Если действие не требует навыка, но требует характеристики:
   - Выбери характеристику напрямую по типу действия:
     * Физическая сила → strength
     * Ловкость/рефлексы → dexterity
     * Выносливость → constitution
     * Логика/память → intelligence
     * Внимательность/интуиция → wisdom
     * Общение/влияние → charisma
   - skill = null

Формат ответа (ОБЯЗАТЕЛЬНО валидный JSON):
{
    "is_possible": true/false,  // Возможно ли действие по правилам D&D 5e
    "requires_dice_roll": true/false,  // Нужен ли бросок кубиков? false для тривиальных действий (открыть обычную дверь, взять предмет с пола, пройти по коридору)
    "intent": "jump",  // Тип действия (jump, climb, sneak, persuade, attack, cast, etc.)
    "ability": "strength",  // Используй ability_score.index из выбранного навыка и конвертируй в полное название.
    "skill": "athletics",  // Навык из SRD (если применимо, null если нет)
    "estimated_dc": 15,  // Сложность как число ИЛИ строка "medium" (если не можешь точно определить)
    "estimated_difficulty": "hard",  // Уровень сложности (very_easy, easy, medium, hard, very_hard, nearly_impossible)
    "modifiers": ["wide river", "muddy ground"],  // Факторы окружения, влияющие на действие
    "required_items": [],  // Необходимые предметы (если есть)
    "reason": "Перепрыгнуть реку требует проверки Athletics (Strength) из-за ширины и сложности"  // Объяснение
}

КРИТИЧЕСКИ ВАЖНО:
- Отвечай ТОЛЬКО валидным JSON, без дополнительного текста
- Используй ТОЛЬКО навыки из предоставленного списка skills из SRD API
- Если действие невозможно (нарушает законы физики/магии/правила), установи "is_possible": false, "requires_dice_roll": false и укажи "reason" с объяснением 
- Используй ТОЛЬКО навыки из предоставленного списка SRD
- "estimated_dc" может быть числом (10-30) или строкой уровня сложности
- "requires_dice_roll": false для:
  * Невозможных действий (is_possible: false) - не требуют броска кубиков
  * Тривиальных действий, которые автоматически успешны (открыть незапертую дверь, взять предмет, пройти по обычному коридору, сесть на стул и т.д.)
- "requires_dice_roll": true для действий с неопределенностью или сложностью (прыжок через пропасть, скрытное перемещение, убеждение NPC, атака и т.д.)
- Всегда возвращай полный JSON объект со всеми полями""";
        return String.format(template, skillsSection, dcSection);
    }
    
    /**
     * Системный промпт для выбора эндпоинтов
     */
    public static String getEndpointSelectionSystemPrompt() {
        return "Ты — эксперт по правилам D&D 5e и структуре SRD API.\n\n" +
            "Твоя задача — проанализировать действие игрока и определить:\n" +
            "1. Требует ли это действие проверки навыка/характеристики (броска кубиков)?\n" +
            "2. Если да, какие эндпоинты из SRD API нужны?\n\n" +
            "Отвечай ТОЛЬКО валидным JSON согласно формату, указанному в пользовательском промпте.";
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

Проанализируй действие и определи, требует ли оно проверки навыка/характеристики (броска кубиков).

ТРИВИАЛЬНЫЕ ДЕЙСТВИЯ (requires_check: false) - не требуют проверки:
- Простое перемещение: "пройти по коридору", "выйти на площадь", "зайти в комнату", "подойти к двери"
- Базовые действия: "открыть незапертую дверь", "взять предмет со стола", "сесть на стул", "лечь спать"
- Социальные действия без сложности: "спросить у NPC", "поздороваться", "сказать что-то", "подойти к торговцу"
- Наблюдение без скрытности: "посмотреть вокруг", "осмотреть комнату", "прочитать вывеску"
- И тому подобные простые вещи

ДЕЙСТВИЯ С ПРОВЕРКОЙ (requires_check: true) - требуют проверки:
- Физические вызовы: "прыгнуть через пропасть", "взобраться на стену", "поднять тяжелый камень"
- Скрытность и ловкость: "скрытно прокрасться", "взломать замок", "украсть кошелек"
- Социальные вызовы: "убедить стражника пропустить", "обмануть торговца", "запугать бандита"
- Магические действия: "прочитать древний свиток", "активировать магический артефакт"
- Боевые действия: "атаковать врага", "заблокировать удар"


ВАЖНО:
- Если действие тривиальное (простое перемещение, базовое действие, простой разговор), установи requires_check: false и required_endpoints: []
- Если действие требует проверки навыка/характеристики, установи requires_check: true и укажи нужные эндпоинты (обычно ["skills", "ability-scores"])

Отвечай ТОЛЬКО валидным JSON:
{
    "requires_check": true/false,  // Требует ли действие проверки навыка/характеристики?
    "required_endpoints": ["skills", "ability-scores"]  // Список эндпоинтов (пустой массив если requires_check: false)
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
            // Сначала обрабатываем skills отдельно, чтобы показать полную структуру
            if (srdData.containsKey("skills")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> skills = (List<Map<String, Object>>) srdData.get("skills");
                prompt.append("\n=== НАВЫКИ (SKILLS) ===\n");
                prompt.append("Выбери нужный навык из этого списка. Каждый навык содержит:\n");
                prompt.append("- index: индекс навыка (используй в поле 'skill')\n");
                prompt.append("- name: название навыка\n");
                prompt.append("- ability_score.index: индекс характеристики (str, dex, con, int, wis, cha)\n");
                prompt.append("- ability_score.name: название характеристики\n");
                prompt.append("- description: описание навыка\n\n");
                
                for (Map<String, Object> skill : skills) {
                    String index = (String) skill.getOrDefault("index", "");
                    String name = (String) skill.getOrDefault("name", "");
                    
                    // Извлекаем описание (может быть массивом строк)
                    String description = "";
                    Object descObj = skill.get("desc");
                    if (descObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> descList = (List<Object>) descObj;
                        if (!descList.isEmpty() && descList.get(0) instanceof String) {
                            description = (String) descList.get(0);
                        }
                    } else if (descObj instanceof String) {
                        description = (String) descObj;
                    }
                    
                    // Извлекаем ability_score
                    String abilityIndex = "unknown";
                    String abilityName = "unknown";
                    Object abilityScoreObj = skill.get("ability_score");
                    if (abilityScoreObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> abilityScore = (Map<String, Object>) abilityScoreObj;
                        abilityIndex = (String) abilityScore.getOrDefault("index", "unknown");
                        abilityName = (String) abilityScore.getOrDefault("name", "unknown");
                    }
                    
                    prompt.append(String.format("- %s (index: \"%s\")\n", name, index));
                    prompt.append(String.format("  Характеристика: %s (index: \"%s\")\n", abilityName, abilityIndex));
                    prompt.append(String.format("  Описание: %s\n", description));
                    prompt.append("\n");
                }
            }
            
            // Затем добавляем остальные данные
            for (Map.Entry<String, Object> entry : srdData.entrySet()) {
                // skills уже обработали выше
                if ("skills".equals(entry.getKey())) {
                    continue;
                }
                
                Object data = entry.getValue();
                if (data != null) {
                    String dataPreview;

                    dataPreview = data.toString();
                    prompt.append(String.format("\n%s:\n%s\n", entry.getKey(), dataPreview));
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

