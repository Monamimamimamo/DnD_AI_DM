# -*- coding: utf-8 -*-
"""
Action Parser - LLM-слой для интерпретации свободных действий игрока
Использует данные SRD для определения возможности действия и параметров проверки
"""

from typing import Dict, Any, Optional, List
import json
from ai_engine.local_llm_client import LocalLLMClient, LocalLLMConfig
from .srd_data import SRDDataLoader
from prompts import DMPrompts


class ActionParser:
    """Парсер действий игрока в структурированный формат с использованием SRD данных"""
    
    def __init__(
        self, 
        llm_client: Optional[LocalLLMClient] = None, 
        parser_model: str = "llama3.1:8b",
        srd_loader: Optional[SRDDataLoader] = None
    ):
        """
        Инициализация парсера
        
        Args:
            llm_client: Клиент LLM для парсинга. Если None, создается новый.
            parser_model: Модель для парсинга. 
            srd_loader: Загрузчик SRD данных. Если None, создается новый.
        """
        if llm_client is None:
            config = LocalLLMConfig(
                model_name=parser_model,
                temperature=0.0,  # Максимальная детерминированность для парсинга
                max_tokens=400    # Увеличено для более детального ответа
            )
            self.llm_client = LocalLLMClient(config)
        else:
            self.llm_client = llm_client
        
        # Загружаем SRD данные для использования в промпте
        self.srd_loader = srd_loader or SRDDataLoader()
        # Данные будут загружаться динамически при парсинге
        self.skills_data = {}
        self.actions_data = {}
        self.dc_table = self.srd_loader.get_difficulty_table() or {}
    
    def parse_action(self, action_text: str, game_context: Dict[str, Any] = None) -> Dict[str, Any]:
        """
        Парсит действие игрока в структурированный JSON с использованием SRD данных
        Использует двухэтапный процесс:
        1. Модель выбирает нужные эндпоинты из SRD API
        2. Загружаются данные из выбранных эндпоинтов и передаются модели для финального парсинга
        
        Args:
            action_text: Текст действия игрока (например, "Я хочу перепрыгнуть реку")
            game_context: Контекст игры (локация, окружение, персонаж и т.д.)
        """
        try:
            # Этап 1: Выбор нужных эндпоинтов
            required_endpoints = self._select_required_endpoints(action_text)
            
            # Этап 2: Загрузка данных из выбранных эндпоинтов
            srd_data = self.srd_loader.load_multiple_endpoints(required_endpoints, limit_per_endpoint=20)
            
            # Обновляем кэш навыков для валидации
            if "skills" in srd_data:
                self.skills_data = {item.get("name", "").lower().replace("-", "_"): item for item in srd_data["skills"] if item.get("name")}
            
            # Этап 3: Финальный парсинг с данными из SRD
            system_prompt = self._get_parser_system_prompt()
            user_prompt = DMPrompts.get_action_parser_final_prompt(action_text, srd_data, game_context)
            
            messages = [{"role": "user", "content": user_prompt}]
            
            response = self.llm_client.generate_response(messages, system_prompt)
            parsed = self._extract_json_from_response(response, action_text)
            
            # Валидируем и дополняем результат
            parsed = self._validate_and_enrich_result(parsed, action_text)
            
            return parsed
        except Exception as e:
            # Возвращаем информацию об ошибке
            return {
                "is_possible": False,
                "error": f"Ошибка при парсинге действия: {str(e)}",
                "error_type": "exception",
                "intent": "unknown",
                "ability": "strength",
                "estimated_dc": 15,
                "reason": f"Не удалось обработать действие: {str(e)}. Попробуйте описать действие по-другому.",
                "action_text": action_text
            }
    
    def _select_required_endpoints(self, action_text: str) -> List[str]:
        """
        Этап 1: Выбор нужных эндпоинтов из SRD API
        
        Args:
            action_text: Текст действия игрока
            
        Returns:
            Список названий эндпоинтов, которые нужны для парсинга
        """
        # Получаем список доступных эндпоинтов
        available_endpoints = self.srd_loader.get_available_endpoints()
        
        # Промпт для выбора эндпоинтов
        system_prompt = """Ты — эксперт по правилам D&D 5e и структуре SRD API.

Твоя задача — проанализировать действие игрока и определить, какие эндпоинты из SRD API нужны для правильной интерпретации этого действия.

Отвечай ТОЛЬКО валидным JSON:
{
    "required_endpoints": ["skills", "ability-scores"]  // Список названий эндпоинтов
}"""
        
        user_prompt = DMPrompts.get_endpoint_selection_prompt(action_text, available_endpoints)
        messages = [{"role": "user", "content": user_prompt}]
        
        try:
            response = self.llm_client.generate_response(messages, system_prompt)
            parsed = self._extract_json_from_response(response, action_text)
            
            if parsed.get("error"):
                # Fallback: используем базовые эндпоинты
                return ["skills", "ability-scores", "rule-sections"]
            
            required_endpoints = parsed.get("required_endpoints", [])
            
            # Валидируем эндпоинты - проверяем, что они существуют
            valid_endpoints = []
            for endpoint in required_endpoints:
                if endpoint in available_endpoints.values() or endpoint in available_endpoints.keys():
                    valid_endpoints.append(endpoint)
            
            # Если нет валидных эндпоинтов, используем базовые
            if not valid_endpoints:
                return ["skills", "ability-scores", "rule-sections"]
            
            return valid_endpoints
        except Exception as e:
            print(f"⚠️ Ошибка при выборе эндпоинтов: {e}")
            # Fallback: используем базовые эндпоинты
            return ["skills", "ability-scores", "rule-sections"]

    def _get_parser_system_prompt(self) -> str:
        """Системный промпт для парсера действий с данными SRD"""
        # Формируем таблицу сложности (всегда нужна)
        if not self.dc_table:
            self.dc_table = self.srd_loader.get_difficulty_table() or {}
        dc_info = "\n".join([f"- {diff}: DC {dc}" for diff, dc in self.dc_table.items()]) if self.dc_table else ""
        
        # Используем промпт из DMPrompts (навыки будут в данных из эндпоинтов)
        return DMPrompts.get_action_parser_system_prompt(skills_list="", dc_info=dc_info)
    
    def _validate_and_enrich_result(self, parsed: Dict[str, Any], action_text: str) -> Dict[str, Any]:
        """
        Валидировать и обогатить результат парсинга
        
        Args:
            parsed: Результат парсинга от LLM
            action_text: Исходный текст действия
            
        Returns:
            Валидированный и обогащенный результат
        """
        # Если есть ошибка, возвращаем как есть
        if parsed.get("error"):
            return parsed
        
        # Убеждаемся, что все обязательные поля присутствуют
        result = {
            "is_possible": parsed.get("is_possible", True),
            "intent": parsed.get("intent", "unknown"),
            "ability": parsed.get("ability", "strength"),
            "skill": parsed.get("skill"),
            "estimated_dc": parsed.get("estimated_dc", "medium"),
            "estimated_difficulty": parsed.get("estimated_difficulty", "medium"),
            "modifiers": parsed.get("modifiers", []),
            "required_items": parsed.get("required_items", []),
            "reason": parsed.get("reason", ""),
            "base_action": parsed.get("base_action"),
            "action_text": action_text
        }
        
        # Валидируем навык - должен быть из SRD
        if result["skill"]:
            skill_normalized = result["skill"].lower().replace("-", "_")
            # Проверяем в кэше навыков (может быть словарь или список)
            skill_found = False
            if isinstance(self.skills_data, dict):
                if skill_normalized in self.skills_data:
                    skill_found = True
                else:
                    # Пробуем найти похожий навык
                    for skill_key in self.skills_data.keys():
                        if skill_key in skill_normalized or skill_normalized in skill_key:
                            result["skill"] = skill_key
                            skill_found = True
                            break
            elif isinstance(self.skills_data, list):
                # Если это список, ищем по name или index
                for skill_item in self.skills_data:
                    if isinstance(skill_item, dict):
                        skill_name = skill_item.get("name", "").lower().replace("-", "_")
                        skill_index = skill_item.get("index", "").lower().replace("-", "_")
                        if skill_normalized in skill_name or skill_normalized in skill_index:
                            result["skill"] = skill_index or skill_name
                            skill_found = True
                            break
            
            if not skill_found:
                # Навык не найден, но оставляем как есть (может быть null)
                result["skill"] = None
        
        # Валидируем DC - конвертируем строку в число если нужно
        if isinstance(result["estimated_dc"], str):
            if result["estimated_dc"] in self.dc_table:
                result["estimated_dc"] = self.dc_table[result["estimated_dc"]]
            else:
                # Используем среднюю сложность
                result["estimated_dc"] = self.dc_table.get("medium", 15)
        
        # Если действие невозможно, но нет reason, добавляем
        if not result["is_possible"] and not result["reason"]:
            result["reason"] = "Действие невозможно по правилам D&D 5e или нарушает законы физики/магии. Попробуйте описать другое действие."
        
        return result
    
    def _extract_json_from_response(self, response: str, action_text: str = "") -> Dict[str, Any]:
        """
        Извлечь JSON из ответа LLM
        
        Args:
            response: Ответ от LLM
            action_text: Исходный текст действия (для сообщения об ошибке)
            
        Returns:
            Словарь с распарсенными данными или словарь с ошибкой
        """
        if not response:
            return {
                "error": "Получен пустой ответ от LLM",
                "error_type": "empty_response"
            }
        
        response = response.strip()
        
        # Если ответ начинается с {, пытаемся распарсить
        if response.startswith('{'):
            try:
                parsed = json.loads(response)
                return parsed
            except json.JSONDecodeError as e:
                return {
                    "error": f"Ошибка парсинга JSON: {str(e)}",
                    "error_type": "json_decode_error"
                }
        
        # Ищем JSON в тексте
        start_idx = response.find('{')
        end_idx = response.rfind('}')
        
        if start_idx != -1 and end_idx != -1 and end_idx > start_idx:
            json_str = response[start_idx:end_idx + 1]
            try:
                parsed = json.loads(json_str)
                return parsed
            except json.JSONDecodeError as e:
                return {
                    "error": f"Ошибка парсинга JSON из текста: {str(e)}",
                    "error_type": "json_decode_error"
                }
        
        # JSON не найден в ответе
        return {
            "error": "Не удалось найти JSON в ответе LLM",
            "error_type": "json_not_found"
        }

