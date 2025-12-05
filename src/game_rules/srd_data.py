# -*- coding: utf-8 -*-
"""
Модуль для загрузки и работы с SRD данными D&D 5e
Использует 5e-srd-api (локальный или удаленный)
Кэширование данных выполняется через Redis
"""

import re
import requests
from typing import Dict, Any, Optional, List


class SRDDataLoader:
    """Загрузчик данных SRD из 5e-srd-api (локальный или удаленный)"""
    
    # Локальный API базовый URL
    LOCAL_API_BASE = "http://localhost:3000/api"
    
    # Поддерживаемые версии
    SUPPORTED_VERSIONS = ["2014", "2024"]
    
    # Маппинг коротких индексов ability scores в полные названия
    ABILITY_SCORE_MAP = {
        "str": "strength",
        "dex": "dexterity",
        "con": "constitution",
        "int": "intelligence",
        "wis": "wisdom",
        "cha": "charisma"
    }
    
    def __init__(self, api_url: str = None, version: str = "2014"):
        """
        Инициализация загрузчика
        
        Args:
            api_url: Полный URL API (если None, используется LOCAL_API_BASE + версия)
            version: Версия SRD ("2014" или "2024"), по умолчанию "2014"
        """
        self.version = version if version in self.SUPPORTED_VERSIONS else "2014"
        self.api_url = self._build_api_url(api_url)
    
    def _build_api_url(self, api_url: Optional[str]) -> str:
        """Построить URL API с учетом версии"""
        if api_url:
            if api_url.endswith(("/2014", "/2024")):
                return api_url
            return f"{api_url.rstrip('/')}/{self.version}"
        return f"{self.LOCAL_API_BASE}/{self.version}"
    
    def _make_request(self, endpoint: str, timeout: int = 10) -> Optional[Dict[str, Any]]:
        """
        Выполнить запрос к API
        
        Args:
            endpoint: Endpoint API (без базового URL)
            timeout: Таймаут запроса в секундах
            
        Returns:
            JSON данные или None при ошибке
        """
        try:
            response = requests.get(f"{self.api_url}/{endpoint}", timeout=timeout)
            if response.status_code == 200:
                return response.json()
        except requests.exceptions.RequestException as e:
            print(f"⚠️ Ошибка запроса к API ({endpoint}): {e}")
        except Exception as e:
            print(f"⚠️ Ошибка обработки ответа API ({endpoint}): {e}")
        return None
    
    @staticmethod
    def _normalize_index(index: str) -> str:
        """Нормализовать индекс (заменить дефисы на подчеркивания)"""
        return index.replace("-", "_")
    
    @staticmethod
    def _extract_description(desc: Any) -> str:
        """Извлечь описание из различных форматов API"""
        if isinstance(desc, list):
            return desc[0] if desc else ""
        return desc if isinstance(desc, str) else ""
    
    def download_all_data(self):
        """Скачать все данные SRD и сохранить в кэш"""
        print("📥 Загрузка данных SRD...")
        print(f"Версия: {self.version}")
        print()
        
        # Загружаем навыки
        print("Загрузка навыков...")
        skills = self._load_skills_from_api()
        print(f"✅ Загружено {len(skills)} навыков")
        
        # Загружаем действия
        print("Загрузка действий...")
        actions = self._load_actions_from_api()
        print(f"✅ Загружено {len(actions)} действий")
        
        # Пробуем загрузить дополнительные данные из API
        print("Загрузка дополнительных данных из API...")
        self._download_api_extras()
        
        print()
    
    def _load_skills_from_api(self) -> Dict[str, Any]:
        """Загрузить навыки из 5e-srd-api"""
        data = self._make_request("skills")
        if not data or "results" not in data:
            return {}
        
        skills_dict = {}
        for skill_ref in data["results"]:
            skill_index = skill_ref.get("index", "")
            if not skill_index:
                continue
            
            skill_detail = self._load_skill_detail(skill_index)
            normalized_index = self._normalize_index(skill_index)
            
            if skill_detail:
                skills_dict[normalized_index] = skill_detail
            else:
                # Fallback на базовую информацию
                skills_dict[normalized_index] = {
                    "name": skill_ref.get("name", skill_index),
                    "ability": "strength",
                    "description": ""
                }
        
        return skills_dict
    
    def _load_skill_detail(self, skill_index: str) -> Optional[Dict[str, Any]]:
        """Загрузить детальную информацию о навыке"""
        data = self._make_request(f"skills/{skill_index}", timeout=5)
        if not data:
            return None
        
        # Преобразуем короткий индекс ability score в полное название
        ability_short = data.get("ability_score", {}).get("index", "str")
        ability_full = self.ABILITY_SCORE_MAP.get(ability_short, "strength")
        
        return {
            "name": data.get("name", skill_index),
            "ability": ability_full,
            "description": self._extract_description(data.get("desc", ""))
        }
    
    def _load_actions_from_api(self) -> Dict[str, Any]:
        """
        Загрузить действия из API
        
        Примечание: API может не иметь прямого endpoint для действий.
        В этом случае возвращается пустой словарь.
        """
        data = self._make_request("actions")
        if not data or "results" not in data:
            return {}
        
        actions = {}
        for item in data["results"]:
            action_key = item.get("index") or item.get("name", "").lower().replace(" ", "_")
            if not action_key:
                continue
            
            ability_score = item.get("ability_score", {})
            skill_data = item.get("skill", {})
            
            actions[action_key] = {
                "name": item.get("name", ""),
                "type": item.get("type", "ability_check"),
                "ability": ability_score.get("index", "strength") if isinstance(ability_score, dict) else ability_score,
                "skill": skill_data.get("index") if isinstance(skill_data, dict) else skill_data,
                "description": self._extract_description(item.get("desc", ""))
            }
        
        return actions
    
    def _download_api_extras(self):
        """Загрузить дополнительные данные из API (заклинания, классы и т.д.)"""
        endpoints = ["spells", "classes", "races", "monsters", "equipment"]
        
        for endpoint in endpoints:
            data = self._make_request(endpoint, timeout=15)
            if data:
                count = self._count_results(data)
                print(f"  ✅ {endpoint}: {count} записей")
            else:
                print(f"  ⚠️ {endpoint}: не удалось загрузить из API")
    
    @staticmethod
    def _count_results(data: Any) -> int:
        """Подсчитать количество результатов в ответе API"""
        if isinstance(data, dict) and "results" in data:
            return len(data["results"])
        if isinstance(data, list):
            return len(data)
        if isinstance(data, dict):
            return len(data)
        return 0
    
    def _get_list_data(self, endpoint: str) -> List[Dict[str, Any]]:
        """Общий метод для получения списка данных из API"""
        data = self._make_request(endpoint, timeout=15)
        if not data:
            return []
        
        if isinstance(data, dict) and "results" in data:
            return data["results"]
        if isinstance(data, list):
            return data
        return []
    
    def get_spells(self) -> List[Dict[str, Any]]:
        """Получить список заклинаний из API"""
        return self._get_list_data("spells")
    
    def get_classes(self) -> List[Dict[str, Any]]:
        """Получить список классов из API"""
        return self._get_list_data("classes")
    
    def get_skills(self) -> Dict[str, Any]:
        """Получить словарь навыков из API"""
        return self._load_skills_from_api()
    
    def get_actions(self) -> Dict[str, Any]:
        """Получить словарь действий из API"""
        return self._load_actions_from_api()
    
    def get_difficulty_table(self) -> Dict[str, int]:
        """Получить таблицу сложности DC (Difficulty Class) из API"""
        data = self._make_request("rule-sections/ability-checks")
        if not data:
            return {}
        
        desc = data.get("desc", "")
        pattern = r'\| (Very easy|Easy|Medium|Hard|Very hard|Nearly impossible)\s+\| (\d+)\s+\|'
        matches = re.findall(pattern, desc)
        
        dc_table = {}
        for difficulty, dc in matches:
            key = difficulty.lower().replace(' ', '_')
            dc_table[key] = int(dc)
        
        return dc_table
    
    def get_available_endpoints(self) -> Dict[str, str]:
        """Получить список всех доступных эндпоинтов API"""
        endpoints = {}
        try:
            # Пробуем получить список эндпоинтов из корневого пути API
            response = requests.get(self.api_url.rstrip('/'), timeout=5)
            if response.status_code == 200:
                data = response.json()
                # Возвращаем словарь эндпоинтов (убираем версию из пути)
                for key, path in data.items():
                    # Извлекаем название эндпоинта из пути (например, /api/2014/skills -> skills)
                    endpoint_name = path.split('/')[-1] if '/' in path else key
                    endpoints[key] = endpoint_name
                return endpoints
        except Exception as e:
            print(f"⚠️ Не удалось получить список эндпоинтов: {e}")
        
        return endpoints
    
    def load_endpoint_data(self, endpoint: str) -> List[Dict[str, Any]]:
        """Загрузить данные из конкретного эндпоинта"""
        data = self._make_request(endpoint, timeout=15)
        if not data:
            return []
        
        # Извлекаем результаты
        if isinstance(data, dict) and "results" in data:
            results = data["results"]
            return results
        elif isinstance(data, list):
            return data
        return []
    
    def load_multiple_endpoints(self, endpoints: List[str]) -> Dict[str, List[Dict[str, Any]]]:
        """Загрузить данные из нескольких эндпоинтов"""
        result = {}
        for endpoint in endpoints:
            result[endpoint] = self.load_endpoint_data(endpoint)
        return result
