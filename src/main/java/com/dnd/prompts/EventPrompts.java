package com.dnd.prompts;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Промпты для генерации событий (NPC, квесты, случайные события)
 */
public class EventPrompts {
    
    /**
     * Промпт для генерации случайного события
     */
    public static String getRandomEventPrompt(String eventType, String connectionText, 
                                             String gameContext, Map<String, Object> historyAnalysis) {
        String historyInfo = buildHistoryInfo(historyAnalysis);
        
        return String.format("""
            Создай случайное событие для D&D кампании.
            
            ТИП СОБЫТИЯ: %s
            
            ЖЕСТКИЕ ПРАВИЛА ГЕНЕРАЦИИ:
            
            1. СТРУКТУРА СОБЫТИЯ (ОБЯЗАТЕЛЬНО):
               - Начало: Что происходит прямо сейчас 
               - Развитие: Детали ситуации, что видит группа
               - Вызов: Что требует действий от игроков
               - Контекст: Как это связано с историей
            
            2. ТРЕБОВАНИЯ К СОДЕРЖАНИЮ:
               - Событие ДОЛЖНО происходить в текущей локации
               - Событие ДОЛЖНО быть логически связано с текущей ситуацией
               - Событие ДОЛЖНО требовать действий от группы (не просто описание)
               - Событие НЕ должно быть слишком масштабным (не битва, не катастрофа)
               - Событие ДОЛЖНО быть интересным и атмосферным
            
            3. СВЯЗИ С ИСТОРИЕЙ (ОБЯЗАТЕЛЬНО использовать если есть):
               %s
            
            4. КОНТЕКСТ ИГРЫ:
               %s
            
            5. АНАЛИЗ ИСТОРИИ:
               %s
            
            6. ЗАПРЕЩЕНО:
               - Создавать события, которые не связаны с историей
               - Генерировать события, которые противоречат текущей ситуации
               - Создавать события без четкого вызова для игроков
               - Использовать общие клише без связи с конкретной историей
            
            Верни ответ ТОЛЬКО в формате JSON (без дополнительного текста):
            {
                "message_type": "random_event",
                "content": "Описание события",
                "metadata": {
                    "event_type": "%s",
                    "connection_to_history": "Как событие связано с историей"
                }
            }
            
            ВАЖНО:
            - message_type должен быть "random_event"
            - content - детальное описание события
            - metadata.event_type - тип события из запроса (%s)
            """, eventType, 
            connectionText.isEmpty() ? "Нет явных связей с историей, но событие должно логически вытекать из текущей ситуации." : connectionText,
            gameContext,
            historyInfo);
    }
    
    /**
     * Промпт для генерации встречи с NPC
     */
    public static String getNPCEncounterPrompt(String connectionText, String gameContext,
                                              Map<String, Object> historyAnalysis) {
        String historyInfo = buildHistoryInfo(historyAnalysis);
        String npcGuidance = buildNPCGuidance(historyAnalysis);
        
        return String.format("""
            Создай встречу с NPC для D&D кампании.
            
            ЖЕСТКИЕ ПРАВИЛА ГЕНЕРАЦИИ NPC:
            
            1. СТРУКТУРА NPC (ОБЯЗАТЕЛЬНО):
               - Внешность: Детальное описание (возраст, одежда, особенности)
               - Поведение: Как NPC ведет себя, настроение, манера речи
               - Цель: Что NPC хочет от группы или что предлагает
               - Контекст: Как NPC связан с историей или локацией
            
            2. ТИПЫ NPC (выбери ОДИН и следуй его правилам):
               
               А) ТОРГОВЕЦ/КУПЕЦ:
                  - ОБЯЗАТЕЛЬНО: Предлагает товары, услуги или информацию за плату
                  - ОБЯЗАТЕЛЬНО: Имеет конкретный товар или услугу
                  - МОЖЕТ: Предложить побочный квест
                  - НЕ ДОЛЖЕН: Быть просто декорацией
               
               Б) МАГ/УЧЕНЫЙ:
                  - ОБЯЗАТЕЛЬНО: Имеет знания о магии, истории или мире
                  - ОБЯЗАТЕЛЬНО: Может помочь с информацией или обучением
                  - МОЖЕТ: Иметь задание, требующее магических знаний
                  - НЕ ДОЛЖЕН: Быть просто источником информации без цели
               
               В) СТРАЖНИК/ВОИН:
                  - ОБЯЗАТЕЛЬНО: Имеет отношение к безопасности или порядку
                  - ОБЯЗАТЕЛЬНО: Может дать информацию о локации или предупредить об опасности
                  - МОЖЕТ: Дать задание на защиту или расследование
                  - НЕ ДОЛЖЕН: Быть просто препятствием
               
               Г) МУДРЕЦ/ОТШЕЛЬНИК:
                  - ОБЯЗАТЕЛЬНО: Имеет древние знания или мудрость
                  - ОБЯЗАТЕЛЬНО: Может дать совет или раскрыть тайну
                  - МОЖЕТ: Связан с незавершенной сюжетной линией
                  - НЕ ДОЛЖЕН: Давать информацию без контекста
               
               Д) ВОР/РАЗВЕДЧИК:
                  - ОБЯЗАТЕЛЬНО: Имеет доступ к подпольной информации или услугам
                  - ОБЯЗАТЕЛЬНО: Предлагает что-то незаконное или рискованное
                  - МОЖЕТ: Дать задание на кражу или шпионаж
                  - НЕ ДОЛЖЕН: Быть просто криминальным элементом без цели
            
            3. СВЯЗИ С ИСТОРИЕЙ (ОБЯЗАТЕЛЬНО использовать если есть):
               %s
            
            4. КОНТЕКСТ ИГРЫ:
               %s
            
            5. АНАЛИЗ ИСТОРИИ:
               %s
            
            6. РЕКОМЕНДАЦИИ ПО NPC:
               %s
            
            7. ЗАПРЕЩЕНО:
               - Создавать NPC без четкой цели или предложения
               - Генерировать NPC, которые не связаны с историей или локацией
               - Создавать NPC, которые просто дают информацию без контекста
               - Использовать общие архетипы без связи с конкретной историей
            
            Верни ответ ТОЛЬКО в формате JSON (без дополнительного текста):
            {
                "message_type": "npc_encounter",
                "content": "Описание встречи с NPC",
                "metadata": {
                    "npc_name": "Имя NPC (если упоминается)",
                    "npc_type": "Тип NPC (торговец, маг, стражник и т.д.)",
                    "offers": "Что предлагает NPC",
                    "can_start_dialogue": true
                }
            }
            
            ВАЖНО:
            - message_type должен быть "npc_encounter"
            - content - детальное описание встречи
            - metadata.npc_name - имя NPC (если есть)
            - metadata.can_start_dialogue - может ли начаться диалог
            """, 
            connectionText.isEmpty() ? "NPC может быть новым персонажем, но должен логически вписываться в локацию и ситуацию." : connectionText,
            gameContext,
            historyInfo,
            npcGuidance);
    }
    
    /**
     * Промпт для генерации побочного квеста
     */
    public static String getSideQuestPrompt(String connectionText, String gameContext,
                                           Map<String, Object> historyAnalysis) {
        String historyInfo = buildHistoryInfo(historyAnalysis);
        String questGuidance = buildQuestGuidance(historyAnalysis);
        
        return String.format("""
            Создай побочный квест для D&D кампании.
            
            ЖЕСТКИЕ ПРАВИЛА ГЕНЕРАЦИИ КВЕСТА:
            
            1. СТРУКТУРА КВЕСТА (ОБЯЗАТЕЛЬНО):
               - Проблема: Что нужно решить или найти
               - Цель: Конкретная задача для группы
               - Контекст: Почему это важно и как связано с историей
               - Начало: Как квест начинается (кто дает, как предлагается)
            
            2. ТИПЫ КВЕСТОВ (выбери ОДИН и следуй его правилам):
               
               А) ПОИСК/ДОСТАВКА:
                  - ОБЯЗАТЕЛЬНО: Нужно найти конкретный предмет или доставить его
                  - ОБЯЗАТЕЛЬНО: Предмет должен быть связан с историей или локацией
                  - ОБЯЗАТЕЛЬНО: Есть конкретное место поиска или получатель
                  - МОЖЕТ: Предмет связан с упомянутыми ранее предметами или NPC
               
               Б) РАССЛЕДОВАНИЕ:
                  - ОБЯЗАТЕЛЬНО: Нужно раскрыть тайну или загадку
                  - ОБЯЗАТЕЛЬНО: Тайна связана с незавершенной сюжетной линией или упоминанием
                  - ОБЯЗАТЕЛЬНО: Есть конкретные улики или места для проверки
                  - МОЖЕТ: Развивает незавершенную историю из прошлого
               
               В) ЗАЩИТА/СПАСЕНИЕ:
                  - ОБЯЗАТЕЛЬНО: Нужно защитить кого-то или что-то
                  - ОБЯЗАТЕЛЬНО: Объект защиты связан с историей или локацией
                  - ОБЯЗАТЕЛЬНО: Есть конкретная угроза
                  - МОЖЕТ: Связан с последствиями прошлых действий
               
               Г) УСТРАНЕНИЕ УГРОЗЫ:
                  - ОБЯЗАТЕЛЬНО: Нужно устранить конкретную угрозу (монстр, враг, проблема)
                  - ОБЯЗАТЕЛЬНО: Угроза связана с текущей локацией или историей
                  - ОБЯЗАТЕЛЬНО: Есть конкретное место или способ устранения
                  - МОЖЕТ: Связан с упомянутыми ранее врагами или проблемами
            
            3. СВЯЗИ С ИСТОРИЕЙ (ОБЯЗАТЕЛЬНО использовать если есть):
               %s
            
            4. КОНТЕКСТ ИГРЫ:
               %s
            
            5. АНАЛИЗ ИСТОРИИ:
               %s
            
            6. РЕКОМЕНДАЦИИ ПО КВЕСТУ:
               %s
            
            7. ЗАПРЕЩЕНО:
               - Создавать квесты, не связанные с историей или текущей ситуацией
               - Генерировать квесты, которые отвлекают от основного сюжета без причины
               - Создавать квесты без четкой цели и структуры
               - Использовать общие шаблоны без связи с конкретной историей
            
            Верни ответ ТОЛЬКО в формате JSON (без дополнительного текста):
            {
                "message_type": "side_quest_intro",
                "content": "Описание начала квеста",
                "metadata": {
                    "quest_type": "Тип квеста (поиск, расследование, защита, устранение угрозы)",
                    "quest_giver": "Кто дает квест (если есть)",
                    "quest_goal": "Цель квеста"
                }
            }
            
            ВАЖНО:
            - message_type должен быть "side_quest_intro"
            - content - детальное описание начала квеста
            - metadata.quest_type - тип квеста
            - metadata.quest_goal - цель квеста
            """, 
            connectionText.isEmpty() ? "Квест должен логически вытекать из текущей ситуации или истории." : connectionText,
            gameContext,
            historyInfo,
            questGuidance);
    }
    
    /**
     * Строит информацию об истории для промпта
     */
    @SuppressWarnings("unchecked")
    private static String buildHistoryInfo(Map<String, Object> historyAnalysis) {
        if (historyAnalysis == null) {
            return "История не проанализирована.";
        }
        
        StringBuilder info = new StringBuilder();
        
        // Упоминания
        Map<String, List<String>> mentions = (Map<String, List<String>>) historyAnalysis.get("mentions");
        if (mentions != null) {
            if (!mentions.getOrDefault("npcs", Collections.emptyList()).isEmpty()) {
                info.append("Упомянутые NPC: ").append(String.join(", ", mentions.get("npcs"))).append("\n");
            }
            if (!mentions.getOrDefault("items", Collections.emptyList()).isEmpty()) {
                info.append("Упомянутые предметы: ").append(String.join(", ", mentions.get("items"))).append("\n");
            }
            if (!mentions.getOrDefault("locations", Collections.emptyList()).isEmpty()) {
                info.append("Упомянутые локации: ").append(String.join(", ", mentions.get("locations"))).append("\n");
            }
        }
        
        // Незавершенные сюжетные линии
        List<Map<String, Object>> unfinished = (List<Map<String, Object>>) historyAnalysis.get("unfinished_storylines");
        if (unfinished != null && !unfinished.isEmpty()) {
            info.append("Незавершенные истории: ");
            for (Map<String, Object> story : unfinished.subList(0, Math.min(2, unfinished.size()))) {
                info.append(story.get("description")).append("; ");
            }
            info.append("\n");
        }
        
        // Паттерны игроков
        Map<String, Object> patterns = (Map<String, Object>) historyAnalysis.get("player_patterns");
        if (patterns != null) {
            String dominantStyle = (String) patterns.get("dominant_style");
            if (dominantStyle != null) {
                info.append("Стиль игры группы: ").append(dominantStyle).append("\n");
            }
        }
        
        return info.toString().isEmpty() ? "История не содержит явных паттернов для связи." : info.toString();
    }
    
    /**
     * Строит рекомендации по NPC на основе истории
     */
    @SuppressWarnings("unchecked")
    private static String buildNPCGuidance(Map<String, Object> historyAnalysis) {
        if (historyAnalysis == null) {
            return "Создай NPC, который логически вписывается в текущую локацию.";
        }
        
        StringBuilder guidance = new StringBuilder();
        
        Map<String, List<String>> mentions = (Map<String, List<String>>) historyAnalysis.get("mentions");
        if (mentions != null && !mentions.getOrDefault("npcs", Collections.emptyList()).isEmpty()) {
            guidance.append("РЕКОМЕНДУЕТСЯ: Создать NPC, связанного с упомянутыми ранее: ")
                    .append(String.join(", ", mentions.get("npcs"))).append("\n");
        }
        
        List<Map<String, Object>> unfinished = (List<Map<String, Object>>) historyAnalysis.get("unfinished_storylines");
        if (unfinished != null && !unfinished.isEmpty()) {
            guidance.append("РЕКОМЕНДУЕТСЯ: NPC может помочь с незавершенной историей\n");
        }
        
        Map<String, Object> patterns = (Map<String, Object>) historyAnalysis.get("player_patterns");
        if (patterns != null) {
            String dominantStyle = (String) patterns.get("dominant_style");
            if ("social".equals(dominantStyle)) {
                guidance.append("РЕКОМЕНДУЕТСЯ: Создать NPC с социальным взаимодействием (торговец, информатор)\n");
            } else if ("combat".equals(dominantStyle)) {
                guidance.append("РЕКОМЕНДУЕТСЯ: Создать NPC, который может предложить боевое задание\n");
            } else if ("exploration".equals(dominantStyle)) {
                guidance.append("РЕКОМЕНДУЕТСЯ: Создать NPC, который может дать информацию о локации\n");
            }
        }
        
        return guidance.toString().isEmpty() ? "Создай NPC, который логически вписывается в текущую локацию и ситуацию." : guidance.toString();
    }
    
    /**
     * Строит рекомендации по квесту на основе истории
     */
    @SuppressWarnings("unchecked")
    private static String buildQuestGuidance(Map<String, Object> historyAnalysis) {
        if (historyAnalysis == null) {
            return "Создай квест, который логически вытекает из текущей ситуации.";
        }
        
        StringBuilder guidance = new StringBuilder();
        
        List<Map<String, Object>> unfinished = (List<Map<String, Object>>) historyAnalysis.get("unfinished_storylines");
        if (unfinished != null && !unfinished.isEmpty()) {
            guidance.append("РЕКОМЕНДУЕТСЯ: Создать квест, развивающий незавершенную историю\n");
        }
        
        Map<String, List<String>> mentions = (Map<String, List<String>>) historyAnalysis.get("mentions");
        if (mentions != null) {
            if (!mentions.getOrDefault("items", Collections.emptyList()).isEmpty()) {
                guidance.append("РЕКОМЕНДУЕТСЯ: Квест может быть связан с упомянутыми предметами: ")
                        .append(String.join(", ", mentions.get("items"))).append("\n");
            }
            if (!mentions.getOrDefault("npcs", Collections.emptyList()).isEmpty()) {
                guidance.append("РЕКОМЕНДУЕТСЯ: Квест может быть дан одним из упомянутых NPC\n");
            }
        }
        
        Map<String, Object> patterns = (Map<String, Object>) historyAnalysis.get("player_patterns");
        if (patterns != null) {
            String dominantStyle = (String) patterns.get("dominant_style");
            if ("combat".equals(dominantStyle)) {
                guidance.append("РЕКОМЕНДУЕТСЯ: Квест с боевыми элементами\n");
            } else if ("social".equals(dominantStyle)) {
                guidance.append("РЕКОМЕНДУЕТСЯ: Квест с социальным взаимодействием\n");
            } else if ("exploration".equals(dominantStyle)) {
                guidance.append("РЕКОМЕНДУЕТСЯ: Квест с исследованием и открытиями\n");
            }
        }
        
        return guidance.toString().isEmpty() ? "Создай квест, который логически вытекает из текущей ситуации." : guidance.toString();
    }
}

