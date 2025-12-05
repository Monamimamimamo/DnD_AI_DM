# -*- coding: utf-8 -*-
"""
Orchestrator - координатор между DM Agent, Rule Engine и Action Parser
"""

from typing import Dict, Any, Optional
from game_rules.rule_engine import RuleEngine
from game_rules.action_parser import ActionParser
from game_state.character import Character
from .local_llm_client import LocalLLMClient, LocalLLMConfig


class GameOrchestrator:
    """Координатор игровых компонентов"""
    
    def __init__(
        self,
        dm_llm_client: Optional[LocalLLMClient] = None,
        parser_llm_client: Optional[LocalLLMClient] = None
    ):
        """
        Инициализация оркестратора
        
        Args:
            dm_llm_client: Клиент LLM для DM Agent (нарратив)
            parser_llm_client: Клиент LLM для Action Parser (можно использовать тот же)
        """
        # Rule Engine - детерминированный, не требует LLM
        self.rule_engine = RuleEngine()
        
        # Используем тот же SRD loader для Action Parser (чтобы не дублировать загрузку)
        srd_loader = self.rule_engine.srd_loader
        
        # Action Parser - использует LLM для интерпретации действий
        if parser_llm_client:
            self.action_parser = ActionParser(parser_llm_client, srd_loader=srd_loader)
        else:
            # Создаем отдельный клиент для парсера с максимальной детерминированностью
            parser_config = LocalLLMConfig(
                model_name="llama3.1:8b",  # Детерминированная модель для структурированного вывода
                temperature=0.0,  # Максимальная детерминированность для парсинга
                max_tokens=400    # Увеличено для более детального ответа с SRD данными
            )
            self.action_parser = ActionParser(
                LocalLLMClient(parser_config), 
                parser_model="llama3.1:8b",
                srd_loader=srd_loader
            )
        
        # DM Agent - использует LLM для нарратива
        if dm_llm_client:
            self.dm_client = dm_llm_client
        else:
            dm_config = LocalLLMConfig(
                model_name="mistral:7b",
                temperature=0.7,  # Высокая температура для креативности
                max_tokens=1000
            )
            self.dm_client = LocalLLMClient(dm_config)
    
    def process_player_action(
        self,
        action_text: str,
        character: Character,
        game_context: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Обработать действие игрока через всю цепочку
        
        Args:
            action_text: Текст действия игрока
            character: Персонаж, выполняющий действие
            game_context: Контекст игры (локация, окружение и т.д.)
            
        Returns:
            Словарь с результатом обработки:
            {
                "parsed_action": {...},  # Результат парсинга
                "rule_result": {...},     # Результат проверки правил
                "dm_narrative": str,      # Нарративное описание от DM
                "success": bool
            }
        """
        # Шаг 1: Парсим действие через Action Parser
        parsed_action = self.action_parser.parse_action(action_text, game_context)
        
        # Проверяем, возможно ли действие
        if not parsed_action.get("is_possible", True):
            # Действие невозможно - возвращаем ответ без проверки правил
            reason = parsed_action.get("reason", "Действие невозможно по правилам D&D 5e")
            dm_narrative = f"{character.name} пытается: {action_text}\n\n{reason}\n\nПопробуйте описать другое действие."
            
            return {
                "parsed_action": parsed_action,
                "rule_result": {
                    "is_possible": False,
                    "reason": reason,
                    "result": "impossible"
                },
                "dm_narrative": dm_narrative,
                "success": False,
                "requires_new_action": True  # Флаг, что нужно новое действие
            }
        
        # Шаг 2: Проверяем правила через Rule Engine (только если действие возможно)
        rule_result = self._evaluate_with_rule_engine(parsed_action, character, game_context)
        
        # Шаг 3: Генерируем нарратив через DM Agent
        dm_narrative = self._generate_narrative(action_text, parsed_action, rule_result, character, game_context)
        
        return {
            "parsed_action": parsed_action,
            "rule_result": rule_result,
            "dm_narrative": dm_narrative,
            "success": rule_result.get("result") in ["success", "partial_success"]
        }
    
    def _evaluate_with_rule_engine(
        self,
        parsed_action: Dict[str, Any],
        character: Character,
        game_context: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Оценить действие через Rule Engine, используя данные из Action Parser"""
        intent = parsed_action.get("intent", "unknown")
        
        # Используем данные напрямую из Action Parser (он уже определил ability, skill, DC)
        ability = parsed_action.get("ability", "strength")
        skill = parsed_action.get("skill")
        estimated_dc = parsed_action.get("estimated_dc", 15)
        
        # Подготавливаем контекст для Rule Engine
        rule_context = {
            "difficulty": parsed_action.get("estimated_difficulty", "medium"),
            "estimated_difficulty": parsed_action.get("estimated_difficulty", "medium"),
            "dc": estimated_dc if isinstance(estimated_dc, int) else None,  # Явный DC из парсера
            "environment": parsed_action.get("modifiers", []),  # Модификаторы из парсера
            "equipment": character.equipment,
            "factors": parsed_action.get("modifiers", [])
        }
        
        # Добавляем контекст из игры
        if "environment" in game_context:
            env = game_context.get("environment", [])
            if isinstance(env, list):
                rule_context["environment"].extend(env)
        
        # Получаем модификаторы характеристик персонажа
        ability_mods = {
            "strength": character.ability_scores.get_modifier("strength"),
            "dexterity": character.ability_scores.get_modifier("dexterity"),
            "constitution": character.ability_scores.get_modifier("constitution"),
            "intelligence": character.ability_scores.get_modifier("intelligence"),
            "wisdom": character.ability_scores.get_modifier("wisdom"),
            "charisma": character.ability_scores.get_modifier("charisma")
        }
        
        # Получаем навыки персонажа
        character_skills = {skill: True for skill in character.skills.keys()}
        
        # Если Action Parser определил конкретную ability и skill, используем их
        # Иначе Rule Engine сам определит по intent
        if ability and skill:
            # Используем данные из Action Parser напрямую
            ability_mod = ability_mods.get(ability, 0)
            has_proficiency = skill in character_skills
            
            # Используем DC из Action Parser или определяем по difficulty
            if isinstance(estimated_dc, int):
                dc = estimated_dc
            else:
                # Если DC строка, используем таблицу сложности
                difficulty = parsed_action.get("estimated_difficulty", "medium")
                dc = self.rule_engine.dc_table.get(difficulty, 15)
            
            # Делаем бросок
            roll_result = self.rule_engine.dice_roller.roll_ability_check(
                ability_modifier=ability_mod,
                proficiency=has_proficiency,
                proficiency_bonus=character._get_proficiency_bonus()
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
                "proficiency_bonus": character._get_proficiency_bonus() if has_proficiency else 0,
                "result": "success" if success else ("partial_success" if partial_success else "fail"),
                "is_critical": roll_result["is_critical"],
                "is_critical_fail": roll_result["is_critical_fail"],
                "is_possible": True,
                "reason": parsed_action.get("reason", "")
            }
        else:
            # Fallback: используем Rule Engine для определения ability/skill по intent
            rule_result = self.rule_engine.evaluate_action(
                intent=intent,
                context=rule_context,
                character_ability_mods=ability_mods,
                character_skills=character_skills,
                character_proficiency_bonus=character._get_proficiency_bonus()
            )
            rule_result["is_possible"] = True
            rule_result["reason"] = parsed_action.get("reason", "")
            return rule_result
    
    def _generate_narrative(
        self,
        action_text: str,
        parsed_action: Dict[str, Any],
        rule_result: Dict[str, Any],
        character: Character,
        game_context: Dict[str, Any]
    ) -> str:
        """Генерировать нарративное описание через DM Agent"""
        system_prompt = self._get_dm_system_prompt()
        
        # Формируем промпт для DM
        user_prompt = f"""Игрок ({character.name}) выполняет действие: "{action_text}"

Результат проверки правил:
- Навык: {rule_result.get('skill', 'N/A')}
- Характеристика: {rule_result.get('ability', 'N/A')}
- Сложность (DC): {rule_result.get('dc', 'N/A')}
- Бросок: {rule_result.get('roll', 'N/A')} + модификаторы = {rule_result.get('total', 'N/A')}
- Результат: {rule_result.get('result', 'N/A')}

Контекст:
- Локация: {game_context.get('current_location', 'Неизвестно')}
- Окружение: {', '.join(game_context.get('environment', []))}

Создай краткое, атмосферное описание того, что происходит. Учитывай результат проверки и сделай описание логичным и интересным."""

        messages = [{"role": "user", "content": user_prompt}]
        
        try:
            return self.dm_client.generate_response(messages, system_prompt)
        except Exception as e:
            # Fallback описание
            result = rule_result.get("result", "unknown")
            if result == "success":
                return f"{character.name} успешно выполняет действие: {action_text}"
            elif result == "partial_success":
                return f"{character.name} частично выполняет действие: {action_text}, но с осложнениями."
            else:
                return f"{character.name} не удается выполнить действие: {action_text}"
    
    def _get_dm_system_prompt(self) -> str:
        """Системный промпт для DM Agent"""
        return """Ты — опытный Dungeon Master для D&D 5e.

Твоя задача — создавать атмосферные, краткие описания действий игроков на основе результатов проверок правил.

Важно:
- Будь кратким но выразительным
- Учитывай результаты бросков (успех/провал)
- Создавай логичные последствия действий
- Поддерживай атмосферу приключения
- Отвечай на русском языке"""

