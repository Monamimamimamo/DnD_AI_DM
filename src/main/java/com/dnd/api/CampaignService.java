package com.dnd.api;

import com.dnd.ai_engine.DungeonMasterAI;
import com.dnd.entity.*;
import com.dnd.game_state.Character;
import com.dnd.game_state.GameManager;
import com.dnd.game_state.GameState;
import com.dnd.repository.CampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * Сервис для управления кампаниями
 */
@Service
public class CampaignService {
    
    @Autowired
    private DungeonMasterAI dungeonMasterAI;
    
    @Autowired
    private GameManager gameManager;
    
    @Autowired
    private CampaignRepository campaignRepository;
    
    /**
     * Создать новую кампанию (только структура, без генерации сцены)
     */
    public Map<String, Object> createCampaign(String sessionId) {
        // Если сессия уже существует, загружаем ее
        if (sessionId != null && gameManager.gameExists(sessionId)) {
            GameState existingGame = gameManager.loadGame(sessionId);
            if (existingGame != null) {
                gameManager.setCurrentGame(existingGame);
                dungeonMasterAI.setCurrentGame(existingGame);
                Map<String, Object> result = new HashMap<>();
                result.put("session_id", existingGame.getSessionId());
                result.put("current_location", existingGame.getCurrentLocation());
                result.put("main_quest", existingGame.getMainQuest());
                return result;
            }
        }
        
        // Создаем новую игру без генерации сцены
        GameState newGame = gameManager.startNewGame(sessionId);
        gameManager.setCurrentGame(newGame);
        dungeonMasterAI.setCurrentGame(newGame);
        gameManager.saveGame();
        
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", newGame.getSessionId());
        result.put("main_quest", null); // Квест будет сгенерирован при начале
        return result;
    }
    
    /**
     * Начать кампанию (генерирует начальную сцену и квест)
     */
    public Map<String, Object> startCampaign(String sessionId, com.dnd.game_state.SessionDuration sessionDuration, Consumer<String> progressCallback) {
        GameState game = gameManager.loadGame(sessionId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + sessionId);
        }
        
        gameManager.setCurrentGame(game);
        dungeonMasterAI.setCurrentGame(game);
        
        // Устанавливаем длительность сессии
        game.setSessionDuration(sessionDuration);
        gameManager.saveGame();
        
        // Генерируем начальную сцену и квест
        return dungeonMasterAI.startNewCampaign(sessionId, sessionDuration, progressCallback);
    }
    
    /**
     * Добавить персонажа в кампанию
     */
    public void addCharacter(String campaignId, Character character) {
        GameState game = gameManager.loadGame(campaignId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
        }
        gameManager.setCurrentGame(game);
        dungeonMasterAI.setCurrentGame(game);
        dungeonMasterAI.addCharacter(character);
        gameManager.saveGame();
    }
    
    /**
     * Обработать действие игрока
     */
    public Map<String, Object> processAction(String campaignId, String action, String characterName) {
        GameState game = gameManager.loadGame(campaignId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
        }
        gameManager.setCurrentGame(game);
        dungeonMasterAI.setCurrentGame(game);
        Map<String, Object> result = dungeonMasterAI.processAction(action, characterName);
        gameManager.saveGame();
        return result;
    }
    
    /**
     * Сгенерировать ситуацию
     */
    public String generateSituation(String campaignId, String characterName, Consumer<String> progressCallback) {
        GameState game = gameManager.loadGame(campaignId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
        }
        gameManager.setCurrentGame(game);
        dungeonMasterAI.setCurrentGame(game);
        String situation = dungeonMasterAI.generateSituation(characterName, progressCallback);
        gameManager.saveGame();
        return situation;
    }
    
    /**
     * Получить статус игры
     */
    public Map<String, Object> getGameStatus(String campaignId) {
        GameState game = gameManager.loadGame(campaignId);
        if (game == null) {
            throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
        }
        dungeonMasterAI.setCurrentGame(game);
        return dungeonMasterAI.getGameStatus();
    }
    
    /**
     * Убедиться, что кампания загружена в DungeonMasterAI
     */
    public void ensureCampaignLoaded(String campaignId) {
        if (campaignId == null || campaignId.isEmpty()) {
            throw new IllegalArgumentException("Campaign ID не может быть пустым");
        }
        
        // Загружаем игру в GameManager
        GameState currentGame = gameManager.getCurrentGame();
        if (currentGame == null || !campaignId.equals(currentGame.getSessionId())) {
            GameState loadedGame = gameManager.loadGame(campaignId);
            if (loadedGame == null) {
                throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
            }
        }
        
        GameState loadedGame = gameManager.getCurrentGame();
        if (loadedGame == null) {
            loadedGame = gameManager.loadGame(campaignId);
            if (loadedGame == null) {
                throw new IllegalArgumentException("Кампания не найдена: " + campaignId);
            }
        }
        dungeonMasterAI.setCurrentGame(loadedGame);
    }
    
    /**
     * Проверить существование кампании
     */
    public boolean campaignExists(String campaignId) {
        return gameManager.gameExists(campaignId);
    }
    
    /**
     * Получить GameState для кампании
     */
    public GameState getGameState(String campaignId) {
        ensureCampaignLoaded(campaignId);
        return gameManager.getCurrentGame();
    }
    
    /**
     * Получить полную информацию о кампании (все поля, квесты, NPC, локации и т.д.)
     */
    public Map<String, Object> getFullCampaignInfo(String campaignId) {
        // Загружаем GameState
        GameState gameState = getGameState(campaignId);
        
        // Загружаем Campaign из БД
        Campaign campaign = campaignRepository.findBySessionId(campaignId)
            .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена: " + campaignId));
        
        Map<String, Object> result = new HashMap<>();
        
        // Основная информация о кампании
        result.put("campaign_id", campaignId);
        result.put("session_id", gameState.getSessionId());
        result.put("created_at", campaign.getCreatedAt());
        result.put("updated_at", campaign.getUpdatedAt());
        result.put("current_location", gameState.getCurrentLocation());
        result.put("current_situation", gameState.getCurrentSituation());
        result.put("current_scene", gameState.getCurrentScene());
        result.put("game_mode", gameState.getGameMode());
        result.put("story_progress", gameState.getStoryProgress());
        result.put("story_completed", gameState.isStoryCompleted());
        
        // Персонажи
        List<Map<String, Object>> characters = new ArrayList<>();
        for (Character character : gameState.getCharacters()) {
            Map<String, Object> charMap = new HashMap<>();
            charMap.put("name", character.getName());
            charMap.put("class", character.getCharacterClass().toString());
            charMap.put("race", character.getRace().toString());
            charMap.put("level", character.getLevel());
            charMap.put("hit_points", character.getHitPoints());
            charMap.put("max_hit_points", character.getMaxHitPoints());
            charMap.put("armor_class", character.getArmorClass());
            charMap.put("speed", character.getSpeed());
            charMap.put("ability_scores", Map.of(
                "strength", character.getAbilityScores().getStrength(),
                "dexterity", character.getAbilityScores().getDexterity(),
                "constitution", character.getAbilityScores().getConstitution(),
                "intelligence", character.getAbilityScores().getIntelligence(),
                "wisdom", character.getAbilityScores().getWisdom(),
                "charisma", character.getAbilityScores().getCharisma()
            ));
            charMap.put("background", character.getBackground());
            charMap.put("alignment", character.getAlignment());
            characters.add(charMap);
        }
        result.put("characters", characters);
        
        // Основной квест
        Map<String, Object> mainQuest = gameState.getMainQuest();
        if (mainQuest != null) {
            Map<String, Object> questInfo = new HashMap<>();
            questInfo.put("title", mainQuest.get("title"));
            questInfo.put("goal", mainQuest.get("goal"));
            questInfo.put("description", mainQuest.get("description"));
            questInfo.put("stages", mainQuest.get("stages"));
            questInfo.put("current_stage_index", mainQuest.get("current_stage_index"));
            questInfo.put("current_stage", gameState.getCurrentQuestStage());
            questInfo.put("completed", mainQuest.get("completed"));
            result.put("main_quest", questInfo);
        } else {
            result.put("main_quest", null);
        }
        
        // Побочные квесты
        Map<String, Map<String, Object>> sideQuests = gameState.getSideQuests();
        result.put("side_quests", sideQuests);
        
        // Мир кампании
        Map<String, Object> world = gameState.getWorld();
        result.put("world", world);
        
        // NPC из БД
        List<Map<String, Object>> npcsFromDb = new ArrayList<>();
        for (NPC npc : campaign.getNpcs()) {
            Map<String, Object> npcMap = new HashMap<>();
            npcMap.put("id", npc.getId());
            npcMap.put("name", npc.getName());
            npcMap.put("description", npc.getDescription());
            npcMap.put("home_location", npc.getHomeLocation());
            npcMap.put("metadata", npc.getMetadata());
            npcMap.put("created_at", npc.getCreatedAt());
            npcMap.put("updated_at", npc.getUpdatedAt());
            if (npc.getLocation() != null) {
                npcMap.put("location_id", npc.getLocation().getId());
                npcMap.put("location_name", npc.getLocation().getName());
            }
            npcsFromDb.add(npcMap);
        }
        result.put("npcs_from_db", npcsFromDb);
        
        // Квесты из БД
        List<Map<String, Object>> questsFromDb = new ArrayList<>();
        for (Quest quest : campaign.getQuests()) {
            Map<String, Object> questMap = new HashMap<>();
            questMap.put("id", quest.getId());
            questMap.put("title", quest.getTitle());
            questMap.put("goal", quest.getGoal());
            questMap.put("description", quest.getDescription());
            questMap.put("quest_type", quest.getQuestType());
            questMap.put("stages", quest.getStages());
            questMap.put("current_stage_index", quest.getCurrentStageIndex());
            questMap.put("completed", quest.getCompleted());
            questMap.put("created_at", quest.getCreatedAt());
            questMap.put("updated_at", quest.getUpdatedAt());
            questsFromDb.add(questMap);
        }
        result.put("quests_from_db", questsFromDb);
        
        // Локации
        List<Map<String, Object>> locations = new ArrayList<>();
        for (Location location : campaign.getLocations()) {
            Map<String, Object> locMap = new HashMap<>();
            locMap.put("id", location.getId());
            locMap.put("name", location.getName());
            locMap.put("description", location.getDescription());
            locMap.put("discovered", location.getDiscovered());
            locMap.put("created_at", location.getCreatedAt());
            locMap.put("updated_at", location.getUpdatedAt());
            locations.add(locMap);
        }
        result.put("locations", locations);
        
        // Открытые локации
        result.put("discovered_locations", new ArrayList<>(gameState.getDiscoveredLocations()));
        
        // История игры
        List<Map<String, Object>> gameHistory = new ArrayList<>();
        for (GameState.GameEvent event : gameState.getGameHistory()) {
            Map<String, Object> eventMap = new HashMap<>();
            eventMap.put("timestamp", event.getTimestamp());
            eventMap.put("type", event.getType());
            eventMap.put("description", event.getDescription());
            eventMap.put("character", event.getCharacter());
            gameHistory.add(eventMap);
        }
        result.put("game_history", gameHistory);
        
        // События из БД
        List<Map<String, Object>> eventsFromDb = new ArrayList<>();
        for (GameEvent event : campaign.getGameEvents()) {
            Map<String, Object> eventMap = new HashMap<>();
            eventMap.put("id", event.getId());
            eventMap.put("event_type", event.getEventType());
            eventMap.put("description", event.getDescription());
            eventMap.put("full_text", event.getFullText());
            eventMap.put("character_name", event.getCharacterName());
            eventMap.put("location_name", event.getLocationName());
            eventMap.put("timestamp", event.getTimestamp());
            eventMap.put("created_at", event.getCreatedAt());
            
            // Множественные связи
            List<Long> npcIds = event.getNpcs().stream()
                .map(NPC::getId)
                .collect(java.util.stream.Collectors.toList());
            eventMap.put("npc_ids", npcIds);
            
            List<Long> questIds = event.getQuests().stream()
                .map(Quest::getId)
                .collect(java.util.stream.Collectors.toList());
            eventMap.put("quest_ids", questIds);
            
            List<Long> locationIds = event.getLocations().stream()
                .map(Location::getId)
                .collect(java.util.stream.Collectors.toList());
            eventMap.put("location_ids", locationIds);
            
            eventsFromDb.add(eventMap);
        }
        result.put("events_from_db", eventsFromDb);
        
        // Флаги кампании
        result.put("campaign_flags", gameState.getCampaignFlags());
        
        // Флаги из БД
        List<Map<String, Object>> flagsFromDb = new ArrayList<>();
        for (CampaignFlag flag : campaign.getCampaignFlags()) {
            Map<String, Object> flagMap = new HashMap<>();
            flagMap.put("id", flag.getId());
            flagMap.put("flag_key", flag.getFlagKey());
            flagMap.put("flag_value", flag.getFlagValue());
            flagMap.put("created_at", flag.getCreatedAt());
            flagMap.put("updated_at", flag.getUpdatedAt());
            flagsFromDb.add(flagMap);
        }
        result.put("campaign_flags_from_db", flagsFromDb);
        
        // Состояния локаций
        Map<String, Map<String, Object>> locationStates = new HashMap<>();
        for (LocationState ls : campaign.getLocationStates()) {
            locationStates.computeIfAbsent(ls.getLocationName(), k -> new HashMap<>())
                .put(ls.getStateKey(), ls.getStateValue());
        }
        result.put("location_states", locationStates);
        
        // Отношения с NPC
        Map<String, Integer> npcRelationships = new HashMap<>();
        for (NPCRelationship rel : campaign.getNpcRelationships()) {
            npcRelationships.put(rel.getNpcName(), rel.getRelationship());
        }
        result.put("npc_relationships", npcRelationships);
        
        // Побочные квесты из БД
        List<Map<String, Object>> sideQuestsFromDb = new ArrayList<>();
        for (SideQuest sq : campaign.getSideQuests()) {
            Map<String, Object> sqMap = new HashMap<>();
            sqMap.put("id", sq.getId());
            sqMap.put("quest_id", sq.getQuestId());
            sqMap.put("quest_data", sq.getQuestData());
            sqMap.put("created_at", sq.getCreatedAt());
            sqMap.put("updated_at", sq.getUpdatedAt());
            sideQuestsFromDb.add(sqMap);
        }
        result.put("side_quests_from_db", sideQuestsFromDb);
        
        // Время последних событий
        Map<String, String> lastEventTimes = new HashMap<>();
        for (LastEventTime let : campaign.getLastEventTimes()) {
            lastEventTimes.put(let.getEventType(), let.getLastTime().toString());
        }
        result.put("last_event_times", lastEventTimes);
        
        // GameContext
        if (gameState.getGameContext() != null) {
            Map<String, Object> gameContextMap = new HashMap<>();
            gameContextMap.put("current_state", gameState.getGameContext().getCurrentState().toString());
            gameContextMap.put("current_location", gameState.getGameContext().getCurrentLocation());
            gameContextMap.put("last_message_type", gameState.getGameContext().getLastMessageType());
            gameContextMap.put("in_dialogue", gameState.getGameContext().isInDialogue());
            gameContextMap.put("dialogue_npc", gameState.getGameContext().getDialogueNPC());
            gameContextMap.put("action_count", gameState.getGameContext().getActionCount());
            result.put("game_context", gameContextMap);
        }
        
        // Мир из БД
        if (campaign.getWorld() != null) {
            World worldEntity = campaign.getWorld();
            Map<String, Object> worldMap = new HashMap<>();
            worldMap.put("id", worldEntity.getId());
            worldMap.put("world_description", worldEntity.getWorldDescription());
            worldMap.put("world_data", worldEntity.getWorldData());
            worldMap.put("created_at", worldEntity.getCreatedAt());
            worldMap.put("updated_at", worldEntity.getUpdatedAt());
            result.put("world_from_db", worldMap);
        }
        
        return result;
    }
}

