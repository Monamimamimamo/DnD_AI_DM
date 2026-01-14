package com.dnd.service;

import com.dnd.entity.*;
import com.dnd.game_state.AbilityScores;
import com.dnd.game_state.Character;
import com.dnd.game_state.CharacterClass;
import com.dnd.game_state.CharacterRace;
import com.dnd.game_state.GameState;
import com.dnd.repository.CampaignRepository;
import com.dnd.repository.CharacterRepository;
import com.dnd.repository.GameEventRepository;
import com.dnd.repository.WorldRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для синхронизации GameState с Campaign через JPA
 */
@Service
public class GameStateService {
    
    @Autowired
    private CampaignRepository campaignRepository;
    
    @Autowired
    private GameEventRepository gameEventRepository;
    
    @Autowired
    private CharacterRepository characterRepository;
    
    @Autowired
    private WorldRepository worldRepository;
    
    private static final Gson gson = new GsonBuilder().create();
    
    /**
     * Загружает GameState из Campaign
     */
    @Transactional(readOnly = true)
    public GameState loadGameState(String sessionId) {
        Optional<Campaign> campaignOpt = campaignRepository.findBySessionId(sessionId);
        if (campaignOpt.isEmpty()) {
            return null;
        }
        
        Campaign campaign = campaignOpt.get();
        return campaignToGameState(campaign);
    }
    
    /**
     * Сохраняет GameState в Campaign
     */
    @Transactional
    public void saveGameState(GameState gameState) {
        if (gameState == null || gameState.getSessionId() == null) {
            return;
        }
        
        Optional<Campaign> campaignOpt = campaignRepository.findBySessionId(gameState.getSessionId());
        Campaign campaign;
        
        if (campaignOpt.isPresent()) {
            campaign = campaignOpt.get();
        } else {
            // Создаем новую кампанию
            campaign = new Campaign(gameState.getSessionId());
            campaignRepository.save(campaign);
        }
        
        // Синхронизируем данные из GameState в Campaign
        syncGameStateToCampaign(gameState, campaign);
        
        campaignRepository.save(campaign);
    }
    
    /**
     * Проверяет существование игры
     */
    @Transactional(readOnly = true)
    public boolean gameExists(String sessionId) {
        return campaignRepository.existsBySessionId(sessionId);
    }
    
    /**
     * Создает новую игру
     */
    @Transactional
    public GameState createNewGame(String sessionId) {
        if (sessionId == null) {
            sessionId = "game_" + LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            );
        }
        
        // Проверяем, не существует ли уже кампания
        if (campaignRepository.existsBySessionId(sessionId)) {
            return loadGameState(sessionId);
        }
        
        Campaign campaign = new Campaign(sessionId);
        campaignRepository.save(campaign);
        
        GameState gameState = new GameState();
        gameState.setSessionId(sessionId);
        return gameState;
    }
    
    /**
     * Преобразует Campaign в GameState
     */
    private GameState campaignToGameState(Campaign campaign) {
        GameState gameState = new GameState();
        gameState.setSessionId(campaign.getSessionId());
        gameState.setCreatedAt(campaign.getCreatedAt());
        gameState.setCurrentLocation(campaign.getCurrentLocation());
        gameState.setCurrentSituation(campaign.getCurrentSituation());
        gameState.setCurrentScene(campaign.getCurrentScene());
        gameState.setGameMode(campaign.getGameMode());
        gameState.setStoryProgress(campaign.getStoryProgress() != null ? campaign.getStoryProgress() : 0);
        gameState.setStoryCompleted(campaign.getStoryCompleted() != null && campaign.getStoryCompleted());
        
        // Загружаем события из БД и преобразуем в GameState.GameEvent
        List<GameEvent> dbEvents = gameEventRepository.findByCampaignIdOrderByTimestampDesc(campaign.getId());
        List<GameState.GameEvent> gameEvents = dbEvents.stream()
            .map(dbEvent -> new GameState.GameEvent(
                dbEvent.getTimestamp(),
                dbEvent.getEventType(),
                dbEvent.getDescription(),
                dbEvent.getCharacterName()
            ))
            .collect(Collectors.toList());
        gameState.setGameHistory(gameEvents);
        
        // Загружаем основной квест
        Optional<Quest> mainQuest = campaign.getQuests().stream()
            .filter(q -> "main".equals(q.getQuestType()))
            .findFirst();
        
        if (mainQuest.isPresent()) {
            Quest quest = mainQuest.get();
            Map<String, Object> questMap = new HashMap<>();
            questMap.put("title", quest.getTitle());
            questMap.put("goal", quest.getGoal());
            questMap.put("description", quest.getDescription());
            questMap.put("current_stage_index", quest.getCurrentStageIndex());
            questMap.put("completed", quest.getCompleted());
            
            // Парсим stages из JSON строки
            if (quest.getStages() != null && !quest.getStages().isEmpty()) {
                try {
                    List<String> stages = gson.fromJson(quest.getStages(), 
                        new TypeToken<List<String>>(){}.getType());
                    questMap.put("stages", stages);
                } catch (Exception e) {
                    System.err.println("Ошибка парсинга stages: " + e.getMessage());
                }
            }
            
            gameState.setMainQuest(questMap);
        }
        
        // Загружаем мир
        if (campaign.getWorld() != null) {
            World world = campaign.getWorld();
            Map<String, Object> worldMap = new HashMap<>();
            worldMap.put("world_description", world.getWorldDescription());
            if (world.getWorldData() != null && !world.getWorldData().isEmpty()) {
                try {
                    Map<String, Object> worldData = gson.fromJson(world.getWorldData(), new TypeToken<Map<String, Object>>(){}.getType());
                    if (worldData != null) worldMap.putAll(worldData);
                } catch (Exception e) {
                    System.err.println("Ошибка парсинга world_data: " + e.getMessage());
                }
            }
            gameState.setWorld(worldMap);
        }
        
        // Загружаем персонажей
        List<Character> characters = new ArrayList<>();
        for (CharacterEntity entity : campaign.getCharacters()) {
            characters.add(entityToCharacter(entity));
        }
        gameState.setCharacters(characters);
        
        return gameState;
    }
    
    /**
     * Синхронизирует GameState в Campaign
     */
    private void syncGameStateToCampaign(GameState gameState, Campaign campaign) {
        campaign.setCurrentLocation(gameState.getCurrentLocation());
        campaign.setCurrentSituation(gameState.getCurrentSituation());
        campaign.setCurrentScene(gameState.getCurrentScene());
        campaign.setGameMode(gameState.getGameMode());
        campaign.setStoryProgress(gameState.getStoryProgress());
        campaign.setStoryCompleted(gameState.isStoryCompleted());
        
        // Синхронизируем персонажей
        syncCharacters(gameState, campaign);
        
        // Синхронизируем мир
        syncWorld(gameState, campaign);
        
        // Обновляем время обновления
        campaign.setUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * Синхронизирует персонажей из GameState в Campaign
     */
    private void syncCharacters(GameState gameState, Campaign campaign) {
        List<Character> gameStateCharacters = gameState.getCharacters();
        if (gameStateCharacters == null) {
            gameStateCharacters = new ArrayList<>();
        }
        
        // Создаем Map существующих персонажей по имени для быстрого поиска
        Map<String, CharacterEntity> existingChars = campaign.getCharacters().stream()
            .collect(Collectors.toMap(CharacterEntity::getName, ce -> ce));
        
        // Обновляем или создаем персонажей
        for (Character gameChar : gameStateCharacters) {
            CharacterEntity entity = existingChars.get(gameChar.getName());
            if (entity == null) {
                // Создаем нового персонажа
                entity = characterToEntity(gameChar, campaign);
                campaign.getCharacters().add(entity);
            } else {
                // Обновляем существующего персонажа
                updateEntityFromCharacter(entity, gameChar);
            }
        }
        
        // Удаляем персонажей, которых нет в GameState
        List<CharacterEntity> toRemove = new ArrayList<>();
        for (CharacterEntity entity : campaign.getCharacters()) {
            boolean existsInGameState = gameStateCharacters.stream()
                .anyMatch(c -> c.getName().equals(entity.getName()));
            if (!existsInGameState) {
                toRemove.add(entity);
            }
        }
        campaign.getCharacters().removeAll(toRemove);
        characterRepository.deleteAll(toRemove);
    }
    
    /**
     * Преобразует CharacterEntity в Character
     */
    private Character entityToCharacter(CharacterEntity entity) {
        AbilityScores abilityScores = new AbilityScores();
        abilityScores.setStrength(entity.getStrength());
        abilityScores.setDexterity(entity.getDexterity());
        abilityScores.setConstitution(entity.getConstitution());
        abilityScores.setIntelligence(entity.getIntelligence());
        abilityScores.setWisdom(entity.getWisdom());
        abilityScores.setCharisma(entity.getCharisma());
        
        Character character = new Character(
            entity.getName(),
            CharacterClass.valueOf(entity.getCharacterClass()),
            CharacterRace.valueOf(entity.getRace()),
            entity.getLevel(),
            abilityScores,
            entity.getBackground(),
            entity.getAlignment()
        );
        
        character.setHitPoints(entity.getHitPoints());
        character.setMaxHitPoints(entity.getMaxHitPoints());
        character.setArmorClass(entity.getArmorClass());
        character.setSpeed(entity.getSpeed());
        
        // Парсим JSON поля
        if (entity.getSkills() != null && !entity.getSkills().isEmpty()) {
            try {
                Map<String, Integer> skills = gson.fromJson(entity.getSkills(), 
                    new TypeToken<Map<String, Integer>>(){}.getType());
                character.setSkills(skills != null ? skills : new HashMap<>());
            } catch (Exception e) {
                System.err.println("Ошибка парсинга skills: " + e.getMessage());
            }
        }
        
        if (entity.getSpells() != null && !entity.getSpells().isEmpty()) {
            try {
                List<String> spells = gson.fromJson(entity.getSpells(), 
                    new TypeToken<List<String>>(){}.getType());
                character.setSpells(spells != null ? spells : new ArrayList<>());
            } catch (Exception e) {
                System.err.println("Ошибка парсинга spells: " + e.getMessage());
            }
        }
        
        if (entity.getEquipment() != null && !entity.getEquipment().isEmpty()) {
            try {
                List<String> equipment = gson.fromJson(entity.getEquipment(), 
                    new TypeToken<List<String>>(){}.getType());
                character.setEquipment(equipment != null ? equipment : new ArrayList<>());
            } catch (Exception e) {
                System.err.println("Ошибка парсинга equipment: " + e.getMessage());
            }
        }
        
        return character;
    }
    
    /**
     * Преобразует Character в CharacterEntity
     */
    private CharacterEntity characterToEntity(Character character, Campaign campaign) {
        CharacterEntity entity = new CharacterEntity();
        entity.setCampaign(campaign);
        updateEntityFromCharacter(entity, character);
        return entity;
    }
    
    /**
     * Обновляет CharacterEntity из Character
     */
    private void updateEntityFromCharacter(CharacterEntity entity, Character character) {
        entity.setName(character.getName());
        entity.setCharacterClass(character.getCharacterClass().toString());
        entity.setRace(character.getRace().toString());
        entity.setLevel(character.getLevel());
        entity.setStrength(character.getAbilityScores().getStrength());
        entity.setDexterity(character.getAbilityScores().getDexterity());
        entity.setConstitution(character.getAbilityScores().getConstitution());
        entity.setIntelligence(character.getAbilityScores().getIntelligence());
        entity.setWisdom(character.getAbilityScores().getWisdom());
        entity.setCharisma(character.getAbilityScores().getCharisma());
        entity.setHitPoints(character.getHitPoints());
        entity.setMaxHitPoints(character.getMaxHitPoints());
        entity.setArmorClass(character.getArmorClass());
        entity.setSpeed(character.getSpeed());
        entity.setBackground(character.getBackground());
        entity.setAlignment(character.getAlignment());
        
        // Сериализуем JSON поля
        if (character.getSkills() != null) {
            entity.setSkills(gson.toJson(character.getSkills()));
        }
        if (character.getSpells() != null) {
            entity.setSpells(gson.toJson(character.getSpells()));
        }
        if (character.getEquipment() != null) {
            entity.setEquipment(gson.toJson(character.getEquipment()));
        }
    }
    
    /**
     * Синхронизирует мир из GameState в Campaign
     */
    private void syncWorld(GameState gameState, Campaign campaign) {
        Map<String, Object> worldMap = gameState.getWorld();
        if (worldMap == null || worldMap.isEmpty()) {
            // Если мира нет в GameState, удаляем его из Campaign
            if (campaign.getWorld() != null) {
                worldRepository.delete(campaign.getWorld());
                campaign.setWorld(null);
            }
            return;
        }
        
        World world = campaign.getWorld();
        if (world == null) {
            // Создаем новый мир
            world = new World();
            world.setCampaign(campaign);
            campaign.setWorld(world);
        }
        
        // Обновляем описание мира
        String worldDescription = (String) worldMap.get("world_description");
        if (worldDescription != null) {
            world.setWorldDescription(worldDescription);
        }
        
        // Сохраняем все данные мира в JSON (кроме world_description, которое уже сохранено отдельно)
        Map<String, Object> worldDataToSave = new HashMap<>(worldMap);
        worldDataToSave.remove("world_description"); // Убираем, так как оно хранится отдельно
        
        if (!worldDataToSave.isEmpty()) {
            world.setWorldData(gson.toJson(worldDataToSave));
        } else {
            world.setWorldData(null);
        }
        
        worldRepository.save(world);
    }
}
