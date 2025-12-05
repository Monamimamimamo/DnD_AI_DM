# -*- coding: utf-8 -*-
"""
Промпты для AI Dungeon Master
"""

import json
from typing import Dict, Any


class DMPrompts:
    """Коллекция промптов для Dungeon Master"""
    
    @staticmethod
    def get_system_prompt(max_tokens: int = 1000) -> str:
        """Системный промпт для Dungeon Master"""
        return f"""
        Ты опытный Dungeon Master для D&D 5e. Твоя задача:

        1. Создавать увлекательные описания и сюжеты
        2. Следовать правилам D&D 5e
        3. Реагировать на действия игроков логично и интересно
        4. Поддерживать атмосферу приключения
        5. Проверять возможность действий согласно правилам

        КРИТИЧЕСКИ ВАЖНО: Твой ответ должен быть КРАТКИМ и укладываться в {max_tokens} токенов.
        - Будь лаконичным но атмосферным
        - Завершай ответ логически
        - Не обрывай на середине предложения
        - Если нужно, сократи описание, но сохрани суть

        Стиль ответа:
        - Описательный и атмосферный
        - Краткий но информативный
        - Учитывающий характеристики персонажей
        - Следующий правилам D&D

        Всегда отвечай на русском языке.
        """
    
    @staticmethod
    def get_initial_scene_prompt() -> str:
        """Промпт для генерации начальной сцены"""
        return """
        Создай увлекательное начало приключения.

        Создай краткое описание локации, где начинается приключение. 
        Сделай это атмосферно и интригующе.
        """
    
    @staticmethod
    def get_action_response_prompt(context: str, action: str, character_info: Dict[str, Any]) -> str:
        """Промпт для ответа на действие игрока"""
        return f"""
        Контекст: {context}
        Действие игрока: {action}
        Информация о персонаже: {character_info}

        Сгенерируй ответ Dungeon Master'а на это действие.
        """

    @staticmethod
    def get_action_parser_system_prompt(skills_list: str = "", dc_info: str = "") -> str:
        """
        Системный промпт для парсера действий с данными SRD
        
        Args:
            skills_list: Список доступных навыков из SRD
            dc_info: Таблица сложности (DC)
        """
        skills_section = f"""
Доступные навыки из SRD (используй только эти):
{skills_list}""" if skills_list else """
Доступные навыки из SRD (используй только навыки из SRD D&D 5e)"""
        
        dc_section = f"""
Таблица сложности (DC):
{dc_info}""" if dc_info else """
Таблица сложности (DC):
- very_easy: DC 5
- easy: DC 10
- medium: DC 15
- hard: DC 20
- very_hard: DC 25
- nearly_impossible: DC 30"""
        
        return f"""Ты — эксперт по правилам D&D 5e, который интерпретирует действия игроков.

Твоя задача:
1. Определить, возможно ли действие по правилам D&D 5e
2. Определить, какая характеристика (ability) и навык (skill) из SRD используются
3. Оценить сложность (DC) на основе контекста
4. Указать модификаторы окружения
{skills_section}
{dc_section}

Характеристики D&D 5e:
- strength (сила)
- dexterity (ловкость)
- constitution (телосложение)
- intelligence (интеллект)
- wisdom (мудрость)
- charisma (харизма)

Формат ответа (ОБЯЗАТЕЛЬНО валидный JSON):
{{
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
}}

КРИТИЧЕСКИ ВАЖНО:
- Отвечай ТОЛЬКО валидным JSON, без дополнительного текста
- Если действие невозможно (нарушает законы физики/магии/правила), установи "is_possible": false и укажи "reason" с объяснением, почему действие невозможно
- В "reason" для невозможных действий обязательно укажи, что игрок должен попробовать другое действие
- Используй ТОЛЬКО навыки из предоставленного списка SRD
- "estimated_dc" может быть числом (10-30) или строкой уровня сложности
- Всегда возвращай полный JSON объект со всеми полями"""
    
    @staticmethod
    def get_endpoint_selection_prompt(action_text: str, available_endpoints: Dict[str, str]) -> str:
        """
        Промпт для первого этапа - выбор нужных эндпоинтов
        
        Args:
            action_text: Текст действия игрока
            available_endpoints: Словарь доступных эндпоинтов
        """
        endpoints_list = "\n".join([f"- {key}: {value}" for key, value in available_endpoints.items()])
        
        return f"""Действие игрока: "{action_text}"

Доступные эндпоинты SRD API:
{endpoints_list}

Проанализируй действие и определи, какие эндпоинты из SRD API тебе нужны для правильной интерпретации этого действия.

Например:
- Для физических действий (прыжок, лазание) → skills, ability-scores
- Для магических действий → spells, magic-schools
- Для использования предметов → equipment, magic-items
- Для социальных действий → skills (persuasion, deception, etc.)

Отвечай ТОЛЬКО валидным JSON:
{{
    "required_endpoints": ["skills", "ability-scores"]  // Список названий эндпоинтов, которые нужны
}}"""

    @staticmethod
    def get_action_parser_user_prompt(action_text: str, game_context: Dict[str, Any] = None) -> str:
        """
        Пользовательский промпт для парсера действий
        
        Args:
            action_text: Текст действия игрока
            game_context: Контекст игры (локация, окружение, снаряжение)
        """
        prompt = f"""Действие игрока: "{action_text}"

"""
        
        if game_context:
            if "current_location" in game_context:
                prompt += f"Локация: {game_context['current_location']}\n"
            if "environment" in game_context:
                env = game_context.get('environment', [])
                if isinstance(env, list):
                    prompt += f"Окружение: {', '.join(env)}\n"
                else:
                    prompt += f"Окружение: {env}\n"
            if "equipment" in game_context:
                equip = game_context.get('equipment', [])
                if isinstance(equip, list):
                    prompt += f"Снаряжение: {', '.join(equip)}\n"
                else:
                    prompt += f"Снаряжение: {equip}\n"
        
        prompt += """
Проанализируй это действие по правилам D&D 5e:
1. Определи, возможно ли это действие
2. Определи, какая характеристика и навык из SRD нужны
3. Оцени сложность (DC) на основе контекста
4. Укажи модификаторы окружения

Отвечай ТОЛЬКО валидным JSON согласно формату."""
        
        return prompt
    
    @staticmethod
    def get_action_parser_final_prompt(action_text: str, srd_data: Dict[str, Any], game_context: Dict[str, Any] = None) -> str:
        """
        Промпт для второго этапа - финальный парсинг с данными из SRD
        
        Args:
            action_text: Текст действия игрока
            srd_data: Данные из выбранных эндпоинтов SRD
            game_context: Контекст игры
        """
        prompt = f"""Действие игрока: "{action_text}"

"""
        
        if game_context:
            if "current_location" in game_context:
                prompt += f"Локация: {game_context['current_location']}\n"
            if "environment" in game_context:
                env = game_context.get('environment', [])
                if isinstance(env, list):
                    prompt += f"Окружение: {', '.join(env)}\n"
                else:
                    prompt += f"Окружение: {env}\n"
            if "equipment" in game_context:
                equip = game_context.get('equipment', [])
                if isinstance(equip, list):
                    prompt += f"Снаряжение: {', '.join(equip)}\n"
                else:
                    prompt += f"Снаряжение: {equip}\n"
        
        # Добавляем данные из SRD
        prompt += "\nДанные из SRD API:\n"
        for endpoint, data in srd_data.items():
            if data:
                # Ограничиваем количество данных для промпта
                data_preview = json.dumps(data[:10], ensure_ascii=False, indent=2) if isinstance(data, list) else json.dumps(data, ensure_ascii=False, indent=2)
                prompt += f"\n{endpoint}:\n{data_preview[:500]}...\n"  # Ограничиваем длину
        
        prompt += """
Используя эти данные из SRD, проанализируй действие по правилам D&D 5e:
1. Определи, возможно ли это действие
2. Определи, какая характеристика и навык из SRD нужны
3. Оцени сложность (DC) на основе контекста и данных SRD
4. Укажи модификаторы окружения

Отвечай ТОЛЬКО валидным JSON согласно формату."""
        
        return prompt