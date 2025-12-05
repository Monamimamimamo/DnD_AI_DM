# -*- coding: utf-8 -*-
"""
Менеджер состояния игры D&D
"""

import json
import sqlite3
from typing import List, Dict, Any, Optional
from datetime import datetime
from .character import Character


class GameState:
    """Состояние текущей игры"""
    
    def __init__(self):
        self.characters: List[Character] = []
        self.current_scene: str = ""
        self.game_history: List[Dict[str, Any]] = []
        self.current_location: str = ""
        self.npcs: List[Dict[str, Any]] = []
        self.quests: List[Dict[str, Any]] = []
        self.game_mode: str = "story"  # "story" или "combat"
        self.session_id: str = ""
        self.created_at: datetime = datetime.now()
    
    def add_character(self, character: Character):
        """Добавить персонажа в игру"""
        self.characters.append(character)
    
    def get_character(self, name: str) -> Optional[Character]:
        """Получить персонажа по имени"""
        for char in self.characters:
            if char.name.lower() == name.lower():
                return char
        return None
    
    def add_game_event(self, event_type: str, description: str, character_name: str = ""):
        """Добавить событие в историю игры"""
        event = {
            "timestamp": datetime.now().isoformat(),
            "type": event_type,
            "description": description,
            "character": character_name
        }
        self.game_history.append(event)
    
    def get_recent_context(self, limit: int = 5) -> str:
        """Получить недавний контекст для AI"""
        recent_events = self.game_history[-limit:]
        context_parts = []
        
        for event in recent_events:
            context_parts.append(f"[{event['type']}] {event['description']}")
        
        return "\\n".join(context_parts)


class GameManager:
    """Менеджер для управления состоянием игры"""
    
    def __init__(self, db_path: str = "game_data.db"):
        self.db_path = db_path
        self.current_game: Optional[GameState] = None
        self._init_database()
    
    def _init_database(self):
        """Инициализация базы данных"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # Таблица для сохранения игр
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS games (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT UNIQUE,
                created_at TIMESTAMP,
                game_data TEXT
            )
        ''')
        
        # Таблица для персонажей
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS characters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT,
                character_data TEXT,
                FOREIGN KEY (session_id) REFERENCES games (session_id)
            )
        ''')
        
        conn.commit()
        conn.close()
    
    def start_new_game(self, session_id: str = None) -> GameState:
        """Начать новую игру"""
        if session_id is None:
            session_id = f"game_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
        
        self.current_game = GameState()
        self.current_game.session_id = session_id
        return self.current_game
    
    def load_game(self, session_id: str) -> Optional[GameState]:
        """Загрузить существующую игру"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute(
            "SELECT game_data FROM games WHERE session_id = ?",
            (session_id,)
        )
        
        result = cursor.fetchone()
        conn.close()
        
        if result:
            game_data = json.loads(result[0])
            self.current_game = self._deserialize_game_state(game_data)
            return self.current_game
        
        return None
    
    def save_game(self):
        """Сохранить текущую игру"""
        if not self.current_game:
            return
        
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        game_data = self._serialize_game_state(self.current_game)
        
        cursor.execute('''
            INSERT OR REPLACE INTO games (session_id, created_at, game_data)
            VALUES (?, ?, ?)
        ''', (self.current_game.session_id, self.current_game.created_at, game_data))
        
        conn.commit()
        conn.close()
    
    def _serialize_game_state(self, game_state: GameState) -> str:
        """Сериализация состояния игры"""
        data = {
            "session_id": game_state.session_id,
            "current_scene": game_state.current_scene,
            "game_history": game_state.game_history,
            "current_location": game_state.current_location,
            "npcs": game_state.npcs,
            "quests": game_state.quests,
            "game_mode": game_state.game_mode,
            "created_at": game_state.created_at.isoformat(),
            "characters": [char.to_dict() for char in game_state.characters]
        }
        return json.dumps(data, ensure_ascii=False)
    
    def _deserialize_game_state(self, data: Dict[str, Any]) -> GameState:
        """Десериализация состояния игры"""
        game_state = GameState()
        game_state.session_id = data["session_id"]
        game_state.current_scene = data["current_scene"]
        game_state.game_history = data["game_history"]
        game_state.current_location = data["current_location"]
        game_state.npcs = data["npcs"]
        game_state.quests = data["quests"]
        game_state.game_mode = data["game_mode"]
        game_state.created_at = datetime.fromisoformat(data["created_at"])
        
        # Восстанавливаем персонажей
        for char_data in data["characters"]:
            character = Character.from_dict(char_data)
            game_state.characters.append(character)
        
        return game_state
    
    def add_character_to_game(self, character: Character):
        """Добавить персонажа в текущую игру"""
        if self.current_game:
            self.current_game.add_character(character)
            self.save_game()
    
    def process_player_action(self, action: str, character_name: str = "") -> Dict[str, Any]:
        """Обработать действие игрока"""
        if not self.current_game:
            return {"error": "Нет активной игры"}
        
        # Добавляем событие в историю
        self.current_game.add_game_event("player_action", action, character_name)
        
        # Получаем контекст для AI
        context = self.current_game.get_recent_context()
        
        # Информация о персонаже
        character_info = {}
        if character_name:
            character = self.current_game.get_character(character_name)
            if character:
                character_info = character.to_dict()
        
        return {
            "context": context,
            "character_info": character_info,
            "current_location": self.current_game.current_location,
            "game_mode": self.current_game.game_mode
        }
    
    def update_game_state(self, updates: Dict[str, Any]):
        """Обновить состояние игры"""
        if not self.current_game:
            return
        
        if "current_location" in updates:
            self.current_game.current_location = updates["current_location"]
        
        if "current_scene" in updates:
            self.current_game.current_scene = updates["current_scene"]
        
        if "game_mode" in updates:
            self.current_game.game_mode = updates["game_mode"]
        
        self.save_game()
