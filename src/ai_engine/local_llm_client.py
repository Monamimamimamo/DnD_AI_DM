# -*- coding: utf-8 -*-
"""
Локальный LLM клиент для работы с моделями через Ollama
"""

import os
import sys
from typing import Optional, Dict, Any, List
from dataclasses import dataclass
import ollama

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from prompts import DMPrompts


@dataclass
class LocalLLMConfig:
    """Конфигурация для локальной модели Ollama"""
    model_name: str = "mistral:7b"
    temperature: float = 0.7
    max_tokens: int = 1000


class LocalLLMClient:
    """Клиент для работы с локальными языковыми моделями через Ollama"""
    
    def __init__(self, config: LocalLLMConfig):
        self.config = config
        self._initialize_model()
    
    def _initialize_model(self):
        """Инициализация модели Ollama"""
        self._initialize_ollama()
    
    def _initialize_ollama(self):
        """Инициализация Ollama модели"""
        try:
            # Проверяем доступные модели
            models = ollama.list()
            model_names = [model.model for model in models.models]
            
            if self.config.model_name not in model_names:
                print(f"Модель {self.config.model_name} не найдена. Загружаем...")
                ollama.pull(self.config.model_name)
            
            print(f"✅ Ollama модель {self.config.model_name} готова к использованию")
            
        except Exception as e:
            print(f"❌ Ошибка инициализации Ollama: {e}")
            print("💡 Убедитесь, что Ollama установлен и запущен")
            raise
    
    def generate_response(
        self, 
        messages: List[Dict[str, str]], 
        system_prompt: Optional[str] = None
    ) -> str:
        """Генерация ответа от локальной модели Ollama"""
        try:
            return self._generate_ollama_response(messages, system_prompt)
        except Exception as e:
            print(f"Ошибка при генерации ответа: {e}")
            return "Извините, произошла ошибка при генерации ответа."
    
    def _generate_ollama_response(self, messages: List[Dict[str, str]], system_prompt: Optional[str] = None) -> str:
        """Генерация ответа через Ollama"""
        # Формируем промпт
        prompt_parts = []
        
        if system_prompt:
            prompt_parts.append(f"System: {system_prompt}")
        
        for message in messages:
            role = message.get("role", "user")
            content = message.get("content", "")
            prompt_parts.append(f"{role.title()}: {content}")
        
        prompt = "\n\n".join(prompt_parts) + "\n\nAssistant:"
        
        # Генерируем ответ
        response = ollama.generate(
            model=self.config.model_name,
            prompt=prompt,
            options={
                "temperature": self.config.temperature,
                "num_predict": self.config.max_tokens
            }
        )
        
        return response['response'].strip()
    
    def generate_story_content(self, context: str, action: str, character_info: Dict[str, Any]) -> str:
        """Генерация контента для истории"""
        system_prompt = self._get_dm_system_prompt()
        
        
        messages = [
            {
                "role": "user", 
                "content": f"""
                    { DMPrompts.get_action_response_prompt(context, action, character_info) }
                """
            }
        ]
        
        return self.generate_response(messages, system_prompt)
    
    def _get_dm_system_prompt(self) -> str:
        """Системный промпт для Dungeon Master"""
        return DMPrompts.get_system_prompt(self.config.max_tokens)


class HybridLLMClient:
    """Гибридный клиент - сначала пробует локальную модель, потом API"""
    
    def __init__(self, local_config: LocalLLMConfig, api_key: Optional[str] = None):
        self.local_client = None
        self.api_client = None
        
        # Инициализируем локальную модель
        try:
            self.local_client = LocalLLMClient(local_config)
            print("✅ Локальная модель инициализирована")
        except Exception as e:
            print(f"⚠️ Локальная модель недоступна: {e}")
        
        # Инициализируем API клиент если есть ключ
        if api_key:
            try:
                from .llm_client import LLMClient, LLMConfig
                api_config = LLMConfig(api_key=api_key)
                self.api_client = LLMClient(api_config)
                print("✅ API клиент инициализирован")
            except Exception as e:
                print(f"⚠️ API клиент недоступен: {e}")
    
    def generate_story_content(self, context: str, action: str, character_info: Dict[str, Any]) -> str:
        """Генерация контента с fallback на API"""
        # Сначала пробуем локальную модель
        if self.local_client:
            try:
                return self.local_client.generate_story_content(context, action, character_info)
            except Exception as e:
                print(f"⚠️ Локальная модель недоступна: {e}")
        
        # Fallback на API
        if self.api_client:
            try:
                return self.api_client.generate_story_content(context, action, character_info)
            except Exception as e:
                print(f"⚠️ API недоступен: {e}")
        
        # Если ничего не работает, возвращаем заглушку
        return f"DM: Вы {action}. (Локальная модель и API недоступны)"
