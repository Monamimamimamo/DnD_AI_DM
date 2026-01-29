package com.dnd;

import com.dnd.game_state.Character;
import com.dnd.game_state.CharacterClass;
import com.dnd.game_state.CharacterRace;
import com.dnd.game_state.AbilityScores;
import com.dnd.ai_engine.DungeonMasterAI;
import java.util.Scanner;

/**
 * Главный класс для запуска AI Dungeon Master
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=== AI Dungeon Master ===");
        System.out.println("Система замены DM для D&D 5e");
        System.out.println("Мультиагентная архитектура:");
        System.out.println("  - DM Agent (нарратив)");
        System.out.println("  - Rule Engine (детерминированные правила)");
        System.out.println("  - Action Parser (интерпретация действий)");
        System.out.println("Используется локальная модель Mistral 7B");
        System.out.println();
        
        try {
            // Инициализируем AI DM с локальной моделью
            System.out.println("Инициализация AI Dungeon Master с локальной моделью...");
            DungeonMasterAI dm = new DungeonMasterAI("mistral:7b");
            
            // Начинаем новую кампанию
            System.out.println("Создание новой кампании...");
            var campaign = dm.startNewCampaign(null, com.dnd.game_state.SessionDuration.MEDIUM, message -> System.out.println("  " + message));
            System.out.println();
            
            // Показываем начальную сцену
            System.out.println("=== НАЧАЛЬНАЯ СЦЕНА ===");
            System.out.println(campaign.get("initial_scene"));
            System.out.println();
            
            // Показываем основной квест
            if (campaign.containsKey("main_quest") && campaign.get("main_quest") != null) {
                var quest = (java.util.Map<String, Object>) campaign.get("main_quest");
                System.out.println("=== ОСНОВНОЙ КВЕСТ ===");
                System.out.println("📜 " + quest.getOrDefault("title", "Квест"));
                System.out.println("🎯 Цель: " + quest.getOrDefault("goal", ""));
                System.out.println("📝 " + quest.getOrDefault("description", ""));
                System.out.println();
            }
            
            // Создаем тестового персонажа
            System.out.println("Создание тестового персонажа...");
            Character character = createSampleCharacter();
            dm.addCharacter(character);
            System.out.println("  ✅ Персонаж " + character.getName() + " добавлен в кампанию");
            System.out.println();
            
            // Генерируем ситуацию
            System.out.println("=== СИТУАЦИЯ ===");
            String situation = dm.generateSituation(character.getName(), 
                message -> System.out.println("  " + message));
            System.out.println("DM: " + situation);
            System.out.println();
            
            // Интерактивный цикл
            System.out.println("=== ИНТЕРАКТИВНЫЙ РЕЖИМ ===");
            System.out.println("💡 Используется локальная модель Mistral 7B");
            System.out.println();
            
            Scanner scanner = new Scanner(System.in);
            while (true) {
                try {
                    var gameStatus = dm.getGameStatus();
                    String currentLocation = (String) gameStatus.getOrDefault("current_location", "Неизвестно");
                    System.out.println("📍 Локация: " + currentLocation);
                    
                    System.out.print(character.getName() + "> ");
                    String action = scanner.nextLine().trim();
                    
                    if (action.equalsIgnoreCase("quit") || 
                        action.equalsIgnoreCase("exit") || 
                        action.equalsIgnoreCase("выход")) {
                        System.out.println("Завершение игры...");
                        break;
                    }
                    
                    if (action.isEmpty()) {
                        continue;
                    }
                    
                    // Обрабатываем действие
                    var result = dm.processAction(action, character.getName());
                    
                    if (result.containsKey("error")) {
                        System.out.println("Ошибка: " + result.get("error"));
                        continue;
                    }
                    
                    // Показываем результат
                    gameStatus = dm.getGameStatus();
                    currentLocation = (String) gameStatus.getOrDefault("current_location", "Неизвестно");
                    System.out.println("\n📍 Локация: " + currentLocation);
                    System.out.println("\nDM: " + result.get("dm_response"));
                    
                    if (result.containsKey("rule_result")) {
                        var rule = (java.util.Map<String, Object>) result.get("rule_result");
                        System.out.println("\n[Правила] " + 
                            rule.getOrDefault("skill", "N/A") + " DC " + 
                            rule.getOrDefault("final_dc", "N/A") + " → Бросок: " + 
                            rule.getOrDefault("roll", "N/A") + " + модификаторы = " + 
                            rule.getOrDefault("total", "N/A") + " (" + 
                            rule.getOrDefault("result", "N/A") + ")");
                    }
                    
                    // Проверяем завершение сюжета
                    if (result.getOrDefault("story_completed", false).equals(true)) {
                        System.out.println("\n" + "=".repeat(50));
                        System.out.println("🎉 СЮЖЕТ ЗАВЕРШЕН! 🎉");
                        System.out.println("=".repeat(50));
                        System.out.println("\nСпасибо за игру! Приключение подошло к концу.");
                        break;
                    }
                    
                    // Если требуется новое действие
                    if (result.getOrDefault("requires_new_action", false).equals(true)) {
                        System.out.println();
                        System.out.println("=== СИТУАЦИЯ ===");
                        situation = dm.generateSituation(character.getName(), 
                            message -> System.out.println("  " + message));
                        System.out.println("DM: " + situation);
                        System.out.println();
                    }
                    
                } catch (Exception e) {
                    System.out.println("Ошибка: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            scanner.close();
            
        } catch (Exception e) {
            System.out.println("Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static Character createSampleCharacter() {
        AbilityScores abilityScores = new AbilityScores(16, 14, 15, 10, 12, 8);
        return new Character(
            "Арагорн",
            CharacterClass.FIGHTER,
            CharacterRace.HUMAN,
            3,
            abilityScores,
            "Странствующий рыцарь",
            "lawful_good"
        );
    }
}

