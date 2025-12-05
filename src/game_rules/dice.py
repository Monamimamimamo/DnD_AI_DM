# -*- coding: utf-8 -*-
"""
Утилита для бросков кубиков D&D
"""

import random
import re
from typing import Dict, Any, List


class DiceRoller:
    """Класс для бросков кубиков D&D"""
    
    @staticmethod
    def roll(expression: str) -> Dict[str, Any]:
        """
        Бросок кубиков по выражению (например, "1d20+3", "2d6", "4d8+1")
        
        Args:
            expression: Строка с выражением броска (например, "1d20+5")
            
        Returns:
            Словарь с результатами: {"total": int, "rolls": List[int], "modifier": int}
        """
        # Парсим выражение: (\d+)d(\d+)([+-]\d+)?
        pattern = r'(\d+)d(\d+)([+-]\d+)?'
        match = re.match(pattern, expression.lower().replace(' ', ''))
        
        if not match:
            raise ValueError(f"Неверный формат броска: {expression}")
        
        num_dice = int(match.group(1))
        sides = int(match.group(2))
        modifier_str = match.group(3) or "0"
        modifier = int(modifier_str)
        
        # Бросаем кубики
        rolls = [random.randint(1, sides) for _ in range(num_dice)]
        total = sum(rolls) + modifier
        
        return {
            "total": total,
            "rolls": rolls,
            "modifier": modifier,
            "expression": expression,
            "num_dice": num_dice,
            "sides": sides
        }
    
    @staticmethod
    def roll_d20(modifier: int = 0) -> Dict[str, Any]:
        """
        Бросок d20 с модификатором
        
        Args:
            modifier: Модификатор к броску
            
        Returns:
            Словарь с результатами
        """
        roll = random.randint(1, 20)
        total = roll + modifier
        
        return {
            "roll": roll,
            "total": total,
            "modifier": modifier,
            "is_critical": roll == 20,
            "is_critical_fail": roll == 1
        }
    
    @staticmethod
    def roll_ability_check(ability_modifier: int, proficiency: bool = False, proficiency_bonus: int = 2) -> Dict[str, Any]:
        """
        Бросок проверки характеристики
        
        Args:
            ability_modifier: Модификатор характеристики
            proficiency: Есть ли владение навыком
            proficiency_bonus: Бонус мастерства
            
        Returns:
            Словарь с результатами
        """
        roll_result = DiceRoller.roll_d20()
        total_modifier = ability_modifier + (proficiency_bonus if proficiency else 0)
        
        return {
            "roll": roll_result["roll"],
            "total": roll_result["roll"] + total_modifier,
            "ability_modifier": ability_modifier,
            "proficiency_bonus": proficiency_bonus if proficiency else 0,
            "is_critical": roll_result["is_critical"],
            "is_critical_fail": roll_result["is_critical_fail"]
        }

