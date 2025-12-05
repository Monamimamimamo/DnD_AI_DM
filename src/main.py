# -*- coding: utf-8 -*-
"""
Главный файл для запуска AI Dungeon Master
"""

import sys
import os

# Добавляем путь к модулям
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from ai_engine.dungeon_master import DungeonMasterAI
from game_state.character import Character, CharacterClass, CharacterRace, AbilityScores


def create_sample_character() -> Character:
    """Создать пример персонажа для тестирования"""
    ability_scores = AbilityScores(
        strength=16,
        dexterity=14,
        constitution=15,
        intelligence=10,
        wisdom=12,
        charisma=8
    )
    
    character = Character(
        name="Арагорн",
        character_class=CharacterClass.FIGHTER,
        race=CharacterRace.HUMAN,
        level=3,
        ability_scores=ability_scores,
        background="Странствующий рыцарь",
        alignment="lawful_good"
    )
    
    return character


def main():
    """Главная функция"""
    print("=== AI Dungeon Master ===")
    print("Система замены DM для D&D 5e")
    print("Мультиагентная архитектура:")
    print("  - DM Agent (нарратив)")
    print("  - Rule Engine (детерминированные правила)")
    print("  - Action Parser (интерпретация действий)")
    print("Используется локальная модель Mistral 7B")
    print()
    
    try:
        # Инициализируем AI DM с локальной моделью
        print("Инициализация AI Dungeon Master с локальной моделью...")
        dm = DungeonMasterAI(local_model="mistral:7b")
        
        # Начинаем новую кампанию
        print("Создание новой кампании...")
        campaign = dm.start_new_campaign()
        print(f"Кампания создана: {campaign['session_id']}")
        print()
        
        # Показываем начальную сцену
        print("=== НАЧАЛЬНАЯ СЦЕНА ===")
        print(campaign['initial_scene'])
        print()
        
        # Создаем тестового персонажа
        print("Создание тестового персонажа...")
        character = create_sample_character()
        dm.add_character(character)
        print(f"Персонаж {character.name} добавлен в кампанию")
        print()
        
        # Интерактивный цикл
        print("=== ИНТЕРАКТИВНЫЙ РЕЖИМ ===")
        print("Введите действия персонажа (или 'quit' для выхода):")
        print("💡 Используется локальная модель Mistral 7B")
        print()
        
        while True:
            try:
                action = input(f"{character.name}> ").strip()
                
                if action.lower() in ['quit', 'exit', 'выход']:
                    print("Завершение игры...")
                    break
                
                if not action:
                    continue
                
                # Обрабатываем действие
                result = dm.process_action(action, character.name)
                
                if "error" in result:
                    print(f"Ошибка: {result['error']}")
                    continue
                
                # Показываем ответ DM
                print(f"\nDM: {result['dm_response']}")
                
                # Показываем информацию о проверке правил (если есть)
                if 'rule_result' in result:
                    rule = result['rule_result']
                    print(f"\n[Правила] {rule.get('skill', 'N/A')} DC {rule.get('final_dc', 'N/A')} "
                          f"→ Бросок: {rule.get('roll', 'N/A')} + модификаторы = {rule.get('total', 'N/A')} "
                          f"({rule.get('result', 'N/A')})")
                print()
                
            except KeyboardInterrupt:
                print("\n\nЗавершение игры...")
                break
            except Exception as e:
                print(f"Ошибка: {e}")
                continue
    
    except Exception as e:
        print(f"Критическая ошибка: {e}")
        return


if __name__ == "__main__":
    main()
