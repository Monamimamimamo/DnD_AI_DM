# -*- coding: utf-8 -*-
"""
Модуль для работы с персонажами D&D
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Any
from enum import Enum


class CharacterClass(Enum):
    """Классы персонажей D&D 5e"""
    BARBARIAN = "barbarian"
    BARD = "bard"
    CLERIC = "cleric"
    DRUID = "druid"
    FIGHTER = "fighter"
    MONK = "monk"
    PALADIN = "paladin"
    RANGER = "ranger"
    ROGUE = "rogue"
    SORCERER = "sorcerer"
    WARLOCK = "warlock"
    WIZARD = "wizard"


class CharacterRace(Enum):
    """Расы персонажей D&D 5e"""
    DRAGONBORN = "dragonborn"
    DWARF = "dwarf"
    ELF = "elf"
    GNOME = "gnome"
    HALF_ELF = "half_elf"
    HALFLING = "halfling"
    HALF_ORC = "half_orc"
    HUMAN = "human"
    TIEFLING = "tiefling"


@dataclass
class AbilityScores:
    """Характеристики персонажа"""
    strength: int = 10
    dexterity: int = 10
    constitution: int = 10
    intelligence: int = 10
    wisdom: int = 10
    charisma: int = 10
    
    def get_modifier(self, ability: str) -> int:
        """Получить модификатор характеристики"""
        score = getattr(self, ability, 10)
        return (score - 10) // 2


@dataclass
class Character:
    """Персонаж D&D"""
    name: str
    character_class: CharacterClass
    race: CharacterRace
    level: int = 1
    ability_scores: AbilityScores = field(default_factory=AbilityScores)
    hit_points: int = 0
    max_hit_points: int = 0
    armor_class: int = 10
    speed: int = 30
    skills: Dict[str, int] = field(default_factory=dict)
    spells: List[str] = field(default_factory=list)
    equipment: List[str] = field(default_factory=list)
    background: str = ""
    alignment: str = "neutral"
    
    def __post_init__(self):
        """Инициализация после создания"""
        if self.hit_points == 0:
            self.hit_points = self._calculate_hit_points()
        if self.max_hit_points == 0:
            self.max_hit_points = self.hit_points
    
    def _calculate_hit_points(self) -> int:
        """Расчет хитов на основе класса и уровня"""
        # Базовые хиты для 1 уровня
        base_hp = {
            CharacterClass.BARBARIAN: 12,
            CharacterClass.FIGHTER: 10,
            CharacterClass.PALADIN: 10,
            CharacterClass.RANGER: 10,
            CharacterClass.CLERIC: 8,
            CharacterClass.DRUID: 8,
            CharacterClass.MONK: 8,
            CharacterClass.ROGUE: 8,
            CharacterClass.BARD: 8,
            CharacterClass.WARLOCK: 8,
            CharacterClass.SORCERER: 6,
            CharacterClass.WIZARD: 6,
        }
        
        con_mod = self.ability_scores.get_modifier("constitution")
        return base_hp.get(self.character_class, 8) + con_mod
    
    def get_skill_modifier(self, skill: str) -> int:
        """Получить модификатор навыка"""
        # Упрощенная версия - в реальной игре нужно учитывать
        # бонусы от класса, расы и т.д.
        base_ability = self._get_skill_ability(skill)
        ability_mod = self.ability_scores.get_modifier(base_ability)
        proficiency_bonus = self._get_proficiency_bonus()
        
        if skill in self.skills:
            return ability_mod + proficiency_bonus
        return ability_mod
    
    def _get_skill_ability(self, skill: str) -> str:
        """Получить основную характеристику для навыка"""
        skill_abilities = {
            "acrobatics": "dexterity",
            "animal_handling": "wisdom",
            "arcana": "intelligence",
            "athletics": "strength",
            "deception": "charisma",
            "history": "intelligence",
            "insight": "wisdom",
            "intimidation": "charisma",
            "investigation": "intelligence",
            "medicine": "wisdom",
            "nature": "intelligence",
            "perception": "wisdom",
            "performance": "charisma",
            "persuasion": "charisma",
            "religion": "intelligence",
            "sleight_of_hand": "dexterity",
            "stealth": "dexterity",
            "survival": "wisdom"
        }
        return skill_abilities.get(skill, "intelligence")
    
    def _get_proficiency_bonus(self) -> int:
        """Получить бонус мастерства"""
        return 2 + (self.level - 1) // 4
    
    def to_dict(self) -> Dict[str, Any]:
        """Преобразование в словарь для сериализации"""
        return {
            "name": self.name,
            "class": self.character_class.value,
            "race": self.race.value,
            "level": self.level,
            "ability_scores": {
                "strength": self.ability_scores.strength,
                "dexterity": self.ability_scores.dexterity,
                "constitution": self.ability_scores.constitution,
                "intelligence": self.ability_scores.intelligence,
                "wisdom": self.ability_scores.wisdom,
                "charisma": self.ability_scores.charisma,
            },
            "hit_points": self.hit_points,
            "max_hit_points": self.max_hit_points,
            "armor_class": self.armor_class,
            "speed": self.speed,
            "skills": self.skills,
            "spells": self.spells,
            "equipment": self.equipment,
            "background": self.background,
            "alignment": self.alignment
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Character':
        """Создание персонажа из словаря"""
        ability_scores = AbilityScores(**data["ability_scores"])
        character = cls(
            name=data["name"],
            character_class=CharacterClass(data["class"]),
            race=CharacterRace(data["race"]),
            level=data["level"],
            ability_scores=ability_scores,
            hit_points=data["hit_points"],
            max_hit_points=data["max_hit_points"],
            armor_class=data["armor_class"],
            speed=data["speed"],
            skills=data["skills"],
            spells=data["spells"],
            equipment=data["equipment"],
            background=data["background"],
            alignment=data["alignment"]
        )
        return character
