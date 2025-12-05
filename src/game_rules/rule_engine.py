# -*- coding: utf-8 -*-
"""
Детерминированный Rule Engine для проверки правил D&D 5e
Использует данные из SRD для правил и действий
"""

from typing import Dict, Any, Optional
from .dice import DiceRoller
from .srd_data import SRDDataLoader


class RuleEngine:
    """Детерминированный движок правил D&D 5e - координатор между компонентами"""
    
    def __init__(self, api_url: str = None, version: str = "2014"):
        """
        Инициализация Rule Engine
        
        Args:
            api_url: URL API (если None, используется локальный http://localhost:3000/api/{version})
            version: Версия SRD ("2014" или "2024"), по умолчанию "2014"
        """
        self.dice_roller = DiceRoller()
        self.srd_loader = SRDDataLoader(api_url=api_url, version=version)
        
        # Загружаем данные SRD
        self.skills_data = self.srd_loader.get_skills() or {}
        self.actions_data = self.srd_loader.get_actions() or {}
        self.dc_table = self.srd_loader.get_difficulty_table() or {}
    
    def evaluate_action(
        self,
        intent: str,
        context: Dict[str, Any],
        character_ability_mods: Dict[str, int],
        character_skills: Dict[str, bool] = None,
        character_proficiency_bonus: int = 2
    ) -> Dict[str, Any]:
        """
        Оценить действие и вернуть результат проверки
        
        Координирует работу между SRD данными и DiceRoller
        
        Args:
            intent: Тип действия (например, "jump", "sneak", "persuade")
            context: Контекст действия (environment, equipment, difficulty и т.д.)
            character_ability_mods: Модификаторы характеристик персонажа
            character_skills: Словарь навыков, которыми владеет персонаж
            character_proficiency_bonus: Бонус мастерства
            
        Returns:
            Словарь с результатами проверки
        """
        # Получаем информацию о навыке из SRD
        skill_info = self._get_skill_for_intent(intent)
        ability = skill_info.get("ability", "strength") if skill_info else "strength"
        skill = skill_info.get("skill") if skill_info else None
        
        # Получаем модификатор характеристики
        ability_mod = character_ability_mods.get(ability, 0)
        
        # Проверяем владение навыком
        has_proficiency = bool(character_skills and skill and character_skills.get(skill, False))
        
        # Определяем DC из контекста или SRD
        dc = self._get_dc(intent, context)
        
        # Делаем бросок через DiceRoller
        roll_result = self.dice_roller.roll_ability_check(
            ability_modifier=ability_mod,
            proficiency=has_proficiency,
            proficiency_bonus=character_proficiency_bonus
        )
        
        # Определяем результат
        success = roll_result["total"] >= dc
        partial_success = roll_result["total"] + 5 >= dc if not success else False
        
        return {
            "intent": intent,
            "ability": ability,
            "skill": skill,
            "dc": dc,
            "roll": roll_result["roll"],
            "total": roll_result["total"],
            "ability_modifier": ability_mod,
            "proficiency_bonus": character_proficiency_bonus if has_proficiency else 0,
            "result": "success" if success else ("partial_success" if partial_success else "fail"),
            "is_critical": roll_result["is_critical"],
            "is_critical_fail": roll_result["is_critical_fail"]
        }
    
    def _get_skill_for_intent(self, intent: str) -> Optional[Dict[str, str]]:
        """Получить навык и характеристику для намерения из SRD данных"""
        intent_lower = intent.lower()
        
        # Ищем в действиях
        if intent_lower in self.actions_data:
            action_data = self.actions_data[intent_lower]
            return {
                "ability": action_data.get("ability", "strength"),
                "skill": action_data.get("skill")
            }
        
        # Ищем по частичному совпадению
        for action_key, action_data in self.actions_data.items():
            if action_key in intent_lower or intent_lower in action_key:
                return {
                    "ability": action_data.get("ability", "strength"),
                    "skill": action_data.get("skill")
                }
        
        # Ищем в навыках
        if intent_lower in self.skills_data:
            skill_data = self.skills_data[intent_lower]
            return {
                "ability": skill_data.get("ability", "strength"),
                "skill": intent_lower
            }
        
        return None
    
    def _get_dc(self, intent: str, context: Dict[str, Any]) -> int:
        """Определить DC для действия из контекста или SRD"""
        # Если DC указан явно в контексте
        if "difficulty" in context:
            difficulty = context["difficulty"]
            if isinstance(difficulty, str) and difficulty in self.dc_table:
                return self.dc_table[difficulty]
            elif isinstance(difficulty, int):
                return difficulty
        
        # Ищем действие в SRD данных
        intent_lower = intent.lower()
        if intent_lower in self.actions_data:
            action_data = self.actions_data[intent_lower]
            if "base_dc" in action_data:
                base_dc = action_data["base_dc"]
                if isinstance(base_dc, int):
                    return base_dc
                elif isinstance(base_dc, dict):
                    # Если есть estimated_difficulty в контексте
                    if "estimated_difficulty" in context:
                        diff = context["estimated_difficulty"]
                        if diff in base_dc:
                            return base_dc[diff]
                        elif diff in self.dc_table:
                            return self.dc_table[diff]
                    # Берем первое значение или среднее
                    values = [v for v in base_dc.values() if isinstance(v, int)]
                    if values:
                        return sum(values) // len(values)
        
        # Используем среднюю сложность по умолчанию
        return self.dc_table.get("medium", 15)

