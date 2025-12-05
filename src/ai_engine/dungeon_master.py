# -*- coding: utf-8 -*-
"""
Основной AI Dungeon Master - мультиагентная архитектура
"""

from typing import Dict, Any
from .local_llm_client import LocalLLMClient, LocalLLMConfig
from .orchestrator import GameOrchestrator
from game_state.game_manager import GameManager
from game_state.character import Character
from prompts import DMPrompts


class DungeonMasterAI:
    """AI Dungeon Master - основная система с мультиагентной архитектурой"""
    
    def __init__(self, local_model: str = "mistral:7b"):
        self.game_manager = GameManager()
        self.current_game = None
        
        # Инициализируем локальную модель для DM Agent
        try:
            local_config = LocalLLMConfig(
                model_name=local_model,
                temperature=0.7,
                max_tokens=1000
            )
            self.llm_client = LocalLLMClient(local_config)
            print(f"✅ Инициализирован локальный клиент с моделью {local_model}")
        except Exception as e:
            print(f"❌ Не удалось инициализировать локальную модель: {e}")
            raise ValueError(f"Локальная модель {local_model} недоступна. Убедитесь, что Ollama запущен и модель загружена.")
        
        # Инициализируем Orchestrator (координирует DM Agent, Rule Engine, Action Parser)
        try:
            self.orchestrator = GameOrchestrator(dm_llm_client=self.llm_client)
            print("✅ Инициализирован Game Orchestrator")
        except Exception as e:
            print(f"⚠️ Предупреждение: Orchestrator инициализирован с ограничениями: {e}")
            self.orchestrator = None
    
    def start_new_campaign(self, session_id: str = None) -> Dict[str, Any]:
        """Начать новую кампанию"""
        self.current_game = self.game_manager.start_new_game(session_id)
        
        # Генерируем начальную сцену
        initial_scene = self._generate_initial_scene()
        self.current_game.current_scene = initial_scene
        self.current_game.current_location = "Начальная локация"
        
        self.game_manager.save_game()
        
        return {
            "session_id": self.current_game.session_id,
            "initial_scene": initial_scene,
            "message": "Новая кампания начата! Создайте персонажей и начните приключение."
        }
    
    def add_character(self, character: Character) -> Dict[str, Any]:
        """Добавить персонажа в кампанию"""
        if not self.current_game:
            return {"error": "Нет активной кампании"}
        
        self.game_manager.add_character_to_game(character)
        
        return {
            "message": f"Персонаж {character.name} добавлен в кампанию",
            "character": character.to_dict()
        }
    
    def process_action(self, action: str, character_name: str = "") -> Dict[str, Any]:
        """Обработать действие игрока через мультиагентную систему"""
        if not self.current_game:
            return {"error": "Нет активной кампании"}
        
        # Получаем персонажа
        character = self.current_game.get_character(character_name) if character_name else None
        if not character:
            return {"error": f"Персонаж {character_name} не найден"}
        
        # Получаем контекст игры
        game_context_data = self.game_manager.process_player_action(action, character_name)
        
        # Подготавливаем контекст для Orchestrator
        orchestrator_context = {
            "current_location": self.current_game.current_location,
            "environment": self._extract_environment_from_context(game_context_data),
            "game_mode": self.current_game.game_mode
        }
        
        # Обрабатываем действие через Orchestrator (если доступен)
        if self.orchestrator:
            try:
                result = self.orchestrator.process_player_action(
                    action_text=action,
                    character=character,
                    game_context=orchestrator_context
                )
                
                dm_response = result["dm_narrative"]
                rule_result = result.get("rule_result", {})
                
                # Добавляем событие в историю
                self.current_game.add_game_event("player_action", action, character_name)
                self.current_game.add_game_event("dm_response", dm_response)
                
                # Сохраняем игру
                self.game_manager.save_game()
                
                return {
                    "dm_response": dm_response,
                    "character_name": character_name,
                    "current_location": self.current_game.current_location,
                    "game_mode": self.current_game.game_mode,
                    "rule_result": rule_result,
                    "success": result.get("success", False)
                }
            except Exception as e:
                print(f"⚠️ Ошибка в Orchestrator, используем fallback: {e}")
                # Fallback на старый метод
                return self._process_action_fallback(action, character_name, game_context_data)
        else:
            # Fallback на старый метод если Orchestrator недоступен
            return self._process_action_fallback(action, character_name, game_context_data)
    
    def _process_action_fallback(self, action: str, character_name: str, game_context: Dict[str, Any]) -> Dict[str, Any]:
        """Fallback метод обработки действий (старая логика)"""
        # Генерируем ответ от DM
        dm_response = self.llm_client.generate_story_content(
            context=game_context["context"],
            action=action,
            character_info=game_context["character_info"]
        )
        
        # Добавляем ответ DM в историю
        self.current_game.add_game_event("dm_response", dm_response)
        
        # Сохраняем игру
        self.game_manager.save_game()
        
        return {
            "dm_response": dm_response,
            "character_name": character_name,
            "current_location": self.current_game.current_location,
            "game_mode": self.current_game.game_mode
        }
    
    def _extract_environment_from_context(self, game_context: Dict[str, Any]) -> list:
        """Извлечь информацию об окружении из контекста"""
        environment = []
        
        # Можно добавить логику извлечения окружения из контекста игры
        # Пока возвращаем пустой список
        return environment
    
    def get_game_status(self) -> Dict[str, Any]:
        """Получить текущий статус игры"""
        if not self.current_game:
            return {"error": "Нет активной кампании"}
        
        return {
            "session_id": self.current_game.session_id,
            "characters": [char.to_dict() for char in self.current_game.characters],
            "current_location": self.current_game.current_location,
            "current_scene": self.current_game.current_scene,
            "game_mode": self.current_game.game_mode,
            "recent_events": self.current_game.get_recent_context(3)
        }
    
    def _generate_initial_scene(self) -> str:
        """Генерация начальной сцены"""
        system_prompt = DMPrompts.get_system_prompt(self.llm_client.config.max_tokens)
        
        messages = [{
            "role": "user",
            "content": DMPrompts.get_initial_scene_prompt()
        }]
        
        try:
            return self.llm_client.generate_response(messages, system_prompt)
        except Exception as e:
            return f"Добро пожаловать в таверну 'Золотой дракон'! Здесь начинается ваше приключение... (Ошибка генерации: {e})"
    
    def switch_to_combat_mode(self) -> Dict[str, Any]:
        """Переключиться в режим боя"""
        if not self.current_game:
            return {"error": "Нет активной кампании"}
        
        self.current_game.game_mode = "combat"
        self.game_manager.update_game_state({"game_mode": "combat"})
        
        return {
            "message": "Переключение в режим боя",
            "game_mode": "combat"
        }
    
    def switch_to_story_mode(self) -> Dict[str, Any]:
        """Переключиться в режим истории"""
        if not self.current_game:
            return {"error": "Нет активной кампании"}
        
        self.current_game.game_mode = "story"
        self.game_manager.update_game_state({"game_mode": "story"})
        
        return {
            "message": "Переключение в режим истории",
            "game_mode": "story"
        }
