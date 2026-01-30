package com.dnd.ai_engine;

import com.dnd.entity.Campaign;
import com.dnd.game_state.Character;
import com.dnd.game_state.GameManager;
import com.dnd.game_state.GameState;
import com.dnd.messages.*;
import com.dnd.prompts.DMPrompts;
import com.dnd.repository.CampaignRepository;
import com.dnd.service.MessageService;
import com.dnd.service.AnalysisProcessor;
import com.dnd.entity.Quest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.function.Consumer;

/**
 * AI Dungeon Master - основная система с мультиагентной архитектурой
 */
public class DungeonMasterAI {
    private static final Gson gson = new GsonBuilder().setLenient().create();
    private final GameManager gameManager;
    private GameState currentGame;
    private final LocalLLMClient llmClient;
    private final GameOrchestrator orchestrator;
    private RelevantContextBuilder relevantContextBuilder; // Опциональный, для фильтрации контекста
    
    @Autowired(required = false)
    private MessageService messageService; // Сервис для сохранения сообщений в БД
    
    @Autowired(required = false)
    private CampaignRepository campaignRepository; // Для поиска квестов по названиям
    
    @Autowired(required = false)
    private AnalysisProcessor analysisProcessor; // Обработчик анализа от LLM

    public DungeonMasterAI(String localModel) {
        this(new GameManager(), localModel);
    }
    
    public DungeonMasterAI(GameManager gameManager, String localModel) {
        this.gameManager = gameManager;
        
        // Инициализируем локальную модель (увеличено maxTokens для детальных описаний и проработанного мира)
        LocalLLMClient.LocalLLMConfig config = new LocalLLMClient.LocalLLMConfig(
            localModel, 0.7, 3000
        );
        this.llmClient = new LocalLLMClient(config);
        
        // Инициализируем Orchestrator
        this.orchestrator = new GameOrchestrator(llmClient);
    }
    
    /**
     * Устанавливает RelevantContextBuilder для фильтрации контекста
     */
    public void setRelevantContextBuilder(RelevantContextBuilder relevantContextBuilder) {
        this.relevantContextBuilder = relevantContextBuilder;
    }
    
    public GameState getCurrentGame() {
        return currentGame;
    }
    
    public void setCurrentGame(GameState game) {
        this.currentGame = game;
        if (game != null) {
            // Инициализируем GameContext, если его нет
            if (game.getGameContext() == null) {
                GameContext gameContext = new GameContext();
                gameContext.setCurrentLocation(game.getCurrentLocation());
                gameContext.setCurrentState(GameContext.ContextState.FREE_EXPLORATION);
                game.setGameContext(gameContext);
            }
            gameManager.setCurrentGame(game);
        }
    }

    public Map<String, Object> startNewCampaign(String sessionId, com.dnd.game_state.SessionDuration sessionDuration, Consumer<String> progressCallback) {
        if (!gameManager.haveAllUsersCreatedCharacters()) {
            throw new IllegalStateException("Все пользователи должны создать персонажей перед началом кампании.");
        }

        currentGame = gameManager.startNewGame(sessionId);
        
        // Устанавливаем длительность сессии
        if (sessionDuration != null) currentGame.setSessionDuration(sessionDuration);
        
        if (progressCallback != null) {
            progressCallback.accept("Кампания создана: " + currentGame.getSessionId());
            progressCallback.accept("⏳ Генерация мира кампании...");
        }
        
        // Сначала генерируем мир
        Map<String, Object> world = generateWorld();
        currentGame.setWorld(world);
        
        if (progressCallback != null) {
            progressCallback.accept("✅ Мир создан");
            progressCallback.accept("⏳ Генерация начальной сцены, квеста и ситуации...");
        }
        
        // Генерируем квест и начальную ситуацию с учетом мира
        Map<String, Object> questAndSituation = generateInitialSceneQuestAndSituation(world);
        
        Map<String, Object> mainQuest = (Map<String, Object>) questAndSituation.get("quest");
        if (mainQuest == null) {
            throw new RuntimeException("Не удалось сгенерировать основной квест");
        }
        
        // Получаем начальную сцену из нового поля "situation" или старого "initial_situation" для обратной совместимости
        String initialScene = (String) questAndSituation.get("situation");
        if (initialScene == null || initialScene.isEmpty()) {
            initialScene = (String) questAndSituation.get("initial_situation");
        }
        if (initialScene == null || initialScene.isEmpty()) {
            throw new RuntimeException("Не удалось сгенерировать начальную сцену");
        }
        
        // Извлекаем локацию из JSON ответа
        String initialLocation = (String) questAndSituation.get("initial_location");
        if (initialLocation == null || initialLocation.isEmpty() || initialLocation.equals("Неизвестная локация")) {
            System.err.println("⚠️ Не удалось получить локацию из JSON ответа");
            initialLocation = "Неизвестная локация";
        }
        currentGame.setCurrentLocation(initialLocation);
        
        // Устанавливаем квест
        if (!mainQuest.containsKey("current_stage_index")) {
            mainQuest.put("current_stage_index", 0);
        }
        if (!mainQuest.containsKey("completed")) {
            mainQuest.put("completed", false);
        }
        currentGame.setMainQuest(mainQuest);
        
        // Инициализируем GameContext
        GameContext gameContext = new GameContext();
        gameContext.setCurrentLocation(initialLocation);
        gameContext.setCurrentState(GameContext.ContextState.FREE_EXPLORATION);
        currentGame.setGameContext(gameContext);
        
        // Сохраняем начальную сцену в историю (для всей группы)
        currentGame.addGameEvent("initial_scene", initialScene, "Начальная сцена");
        
        // Сохраняем начальную сцену в БД
        if (messageService != null) {
            try {
                List<Long> locationIds = null;
                if (initialLocation != null) {
                    locationIds = messageService.findLocationIdsByName(
                        currentGame.getSessionId(), 
                        List.of(initialLocation)
                    );
                }
                
                List<Long> questIds = messageService.getActiveQuestIds(currentGame.getSessionId());
                
                messageService.saveDMMessage(
                    currentGame.getSessionId(),
                    "initial_scene",
                    initialScene,
                    initialScene,
                    null,
                    initialLocation,
                    null, // npcIds
                    questIds,
                    locationIds
                );
            } catch (Exception e) {
                System.err.println("Ошибка сохранения начальной сцены: " + e.getMessage());
            }
        }
        
        if (progressCallback != null) {
            progressCallback.accept("✅ Начальная сцена и квест созданы");
        }
        
        gameManager.saveGame();
        
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", currentGame.getSessionId());
        result.put("main_quest", mainQuest);
        result.put("initial_scene", initialScene);
        result.put("initial_location", initialLocation);
        return result;
    }

    public void addCharacter(Character character) {
        if (currentGame == null) {
            throw new IllegalStateException("Нет активной кампании");
        }
        gameManager.addCharacterToGame(character);
    }

    public Map<String, Object> processAction(String action, String characterName) {
        if (currentGame == null) {
            throw new IllegalStateException("Нет активной кампании");
        }
        
        Character character = currentGame.getCharacter(characterName);
        if (character == null) {
            throw new IllegalArgumentException("Персонаж " + characterName + " не найден");
        }
        
        // Получаем последнюю ситуацию из истории
        String lastSituation = "";
        List<com.dnd.game_state.GameState.GameEvent> history = currentGame.getGameHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            com.dnd.game_state.GameState.GameEvent event = history.get(i);
            if ("situation".equals(event.getType())) {
                lastSituation = event.getDescription();
                break;
            }
        }
        
        Map<String, Object> orchestratorContext = new HashMap<>();
        orchestratorContext.put("current_location", currentGame.getCurrentLocation());
        orchestratorContext.put("current_situation", lastSituation);
        orchestratorContext.put("environment", new ArrayList<>());
        orchestratorContext.put("game_mode", currentGame.getGameMode());
        
        try {
            // Получаем или создаем GameContext
            GameContext gameContext = currentGame.getGameContext();
            if (gameContext == null) {
                gameContext = new GameContext();
                gameContext.setCurrentLocation(currentGame.getCurrentLocation());
                currentGame.setGameContext(gameContext);
            }
            
            Map<String, Object> result = orchestrator.processPlayerAction(
                action, character, orchestratorContext
            );
            
            // Парсим ответ через MessageParser для получения StructuredMessage
            String dmResponseRaw = (String) result.get("dm_narrative");
            System.out.println("📥 [DungeonMasterAI] Полный ответ DM (нарратив действия):");
            System.out.println("   " + dmResponseRaw);
            StructuredMessage structuredMessage;
            try {
                structuredMessage = MessageParser.parseMessage(dmResponseRaw, characterName);
            } catch (Exception e) {
                // Fallback на старый формат для обратной совместимости
                System.err.println("⚠️ Ошибка парсинга через MessageParser, используем старый формат: " + e.getMessage());
                JsonObject jsonObj = extractJsonObject(dmResponseRaw);
                String content = jsonObj.has("narrative") ? jsonObj.get("narrative").getAsString() : 
                                jsonObj.has("content") ? jsonObj.get("content").getAsString() : "";
                String location = jsonObj.has("location") ? jsonObj.get("location").getAsString() : 
                                 currentGame.getCurrentLocation();
                Map<String, Object> metadata = new HashMap<>();
                if (location != null) metadata.put("location", location);
                structuredMessage = new StructuredMessage(MessageType.ACTION_RESULT, content, characterName, metadata);
            }
            
            // Валидируем тип сообщения через MessageTypeValidator
            MessageTypeValidator.ValidationResult validationResult = 
                MessageTypeValidator.validate(structuredMessage.getType(), gameContext);
            
            if (!validationResult.isValid()) {
                System.err.println("⚠️ Валидация не прошла: " + validationResult.getErrors());
            }
            
            // Обновляем GameContext на основе типа сообщения
            gameContext.updateFromMessage(structuredMessage.getType(), structuredMessage.getContent());
            
            // Обновляем локацию из метаданных или контента
            String newLocation = (String) structuredMessage.getMetadata().get("location");
            if (newLocation == null || newLocation.isEmpty()) {
                newLocation = currentGame.getCurrentLocation();
            }
            
            if (newLocation != null && !newLocation.isEmpty() && 
                !newLocation.equals(currentGame.getCurrentLocation()) && 
                !newLocation.equals("Неизвестная локация")) {
                currentGame.setCurrentLocation(newLocation);
                gameContext.setCurrentLocation(newLocation);
                gameManager.updateGameState(Map.of("current_location", newLocation));
            }
            
            String dmResponse = structuredMessage.getContent();
            
            // Проверяем прогресс квеста
            boolean questAdvanced = false;
            if (result.getOrDefault("success", false).equals(true) && !currentGame.isStoryCompleted()) {
                questAdvanced = checkAndAdvanceQuest();
            }
            
            // Сохраняем сообщение игрока в БД
            if (messageService != null) {
                try {
                    messageService.savePlayerMessage(currentGame.getSessionId(), characterName, action);
                } catch (Exception e) {
                    System.err.println("Ошибка сохранения сообщения игрока: " + e.getMessage());
                }
            }
            
            // Добавляем в GameState для совместимости
            currentGame.addGameEvent("player_action", action, characterName);
            
                // Сохраняем ответ DM в БД
                if (messageService != null) {
                    try {
                    // Определяем связанные сущности из анализа
                    List<Long> npcIds = null;
                        List<Long> questIds = messageService.getActiveQuestIds(currentGame.getSessionId());
                        List<Long> locationIds = null;
                    
                    // Извлекаем упоминания из анализа для связывания с событием
                    if (structuredMessage.getMetadata().containsKey("analysis")) {
                        Map<String, Object> analysis = (Map<String, Object>) structuredMessage.getMetadata().get("analysis");
                        
                        // Получаем ID упомянутых NPC
                        if (analysis.containsKey("npcs_mentioned")) {
                            List<String> npcNames = (List<String>) analysis.get("npcs_mentioned");
                            if (npcNames != null && !npcNames.isEmpty()) {
                                npcIds = messageService.findNpcIdsByName(currentGame.getSessionId(), npcNames);
                            }
                        }
                        
                        // Получаем ID упомянутых локаций
                        if (analysis.containsKey("locations_mentioned")) {
                            List<String> locationNames = (List<String>) analysis.get("locations_mentioned");
                            if (locationNames != null && !locationNames.isEmpty()) {
                            locationIds = messageService.findLocationIdsByName(
                                currentGame.getSessionId(), 
                                    locationNames
                            );
                        }
                        }
                        
                        // Получаем ID упомянутых квестов
                        if (analysis.containsKey("quests_mentioned")) {
                            List<String> questTitles = (List<String>) analysis.get("quests_mentioned");
                            if (questTitles != null && !questTitles.isEmpty()) {
                                // Используем MessageService для получения ID квестов по названиям (работает внутри транзакции)
                                questIds = messageService.findQuestIdsByTitles(currentGame.getSessionId(), questTitles);
                            }
                        }
                    }
                    
                    // Если локация не указана в analysis, используем текущую
                    if (locationIds == null || locationIds.isEmpty()) {
                        if (currentGame.getCurrentLocation() != null) {
                            locationIds = messageService.findLocationIdsByName(
                                currentGame.getSessionId(), 
                                List.of(currentGame.getCurrentLocation())
                            );
                        }
                        }
                        
                    // Сохраняем событие и получаем его ID
                    com.dnd.entity.GameEvent savedEvent = messageService.saveDMMessage(
                            currentGame.getSessionId(),
                        "dm_response",
                        dmResponse,
                        dmResponse,
                        characterName,
                            currentGame.getCurrentLocation(),
                        npcIds,
                            questIds,
                            locationIds
                        );
                    
                    Long lastEventId = savedEvent.getId();
                    
                    // Обрабатываем анализ с привязкой к событию
                    if (analysisProcessor != null && structuredMessage.getMetadata().containsKey("analysis")) {
                        try {
                            Map<String, Object> analysis = (Map<String, Object>) structuredMessage.getMetadata().get("analysis");
                            if (analysis != null && !analysis.isEmpty()) {
                                System.out.println("📊 [DungeonMasterAI] Обработка анализа от LLM...");
                                System.out.println("📋 [DungeonMasterAI] Анализ: " + analysis);
                                analysisProcessor.processAnalysis(currentGame.getSessionId(), analysis, lastEventId);
                            } else {
                                System.out.println("ℹ️ [DungeonMasterAI] Анализ пустой или отсутствует, пропускаем обработку");
                            }
                    } catch (Exception e) {
                            System.err.println("⚠️ Ошибка обработки анализа: " + e.getMessage());
                            e.printStackTrace();
                    }
                } else {
                    System.out.println("ℹ️ [DungeonMasterAI] Поле 'analysis' отсутствует в метаданных сообщения");
                }
                } catch (Exception e) {
                    System.err.println("Ошибка сохранения ответа DM: " + e.getMessage());
                }
            }
            
            currentGame.addGameEvent("dm_response", dmResponse, characterName);
            
            // Финальная сцена
            if (currentGame.isStoryCompleted() && questAdvanced) {
                String finalScene = generateFinalScene();
                dmResponse = dmResponse + "\n\n" + finalScene;
                
                // Сохраняем финальную сцену в БД
                if (messageService != null) {
                    try {
                        List<Long> questIds = messageService.getActiveQuestIds(currentGame.getSessionId());
                        List<Long> locationIds = null;
                        if (currentGame.getCurrentLocation() != null) {
                            locationIds = messageService.findLocationIdsByName(
                                currentGame.getSessionId(), 
                                List.of(currentGame.getCurrentLocation())
                            );
                        }
                        
                        messageService.saveDMMessage(
                            currentGame.getSessionId(),
                            "final_scene",
                            finalScene,
                            finalScene,
                            null,
                            currentGame.getCurrentLocation(),
                            null, // npcIds
                            questIds,
                            locationIds
                        );
                    } catch (Exception e) {
                        System.err.println("Ошибка сохранения финальной сцены: " + e.getMessage());
                    }
                }
                
                currentGame.addGameEvent("final_scene", finalScene, "");
            } else if (!currentGame.isStoryCompleted() && result.getOrDefault("success", false).equals(true)) {
                // Если квест не завершен и действие успешно - генерируем продолжение истории
                try {
                    String storyContinuation = generateStoryContinuation(action, dmResponse, character);
                    if (storyContinuation != null && !storyContinuation.trim().isEmpty()) {
                        dmResponse = dmResponse + "\n\n" + storyContinuation;
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка генерации продолжения истории: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Синхронизируем GameContext обратно в GameState
            currentGame.setGameContext(gameContext);
            
            gameManager.saveGame();
            
            Map<String, Object> response = new HashMap<>();
            response.put("dm_response", dmResponse);
            response.put("character_name", characterName);
            response.put("current_location", currentGame.getCurrentLocation());
            response.put("game_mode", currentGame.getGameMode());
            response.put("rule_result", result.getOrDefault("rule_result", new HashMap<>()));
            response.put("success", result.getOrDefault("success", false));
            response.put("requires_new_action", result.getOrDefault("requires_new_action", false));
            response.put("quest_advanced", questAdvanced);
            response.put("story_completed", currentGame.isStoryCompleted());
            return response;
            
        } catch (Exception e) {
            throw new RuntimeException("Ошибка обработки действия в Orchestrator: " + e.getMessage(), e);
        }
    }

    /**
     * Генерирует продолжение истории после действия игрока
     * DM продолжает сюжет: развивает квест, организует встречу с NPC, создает событие и т.д.
     */
    private String generateStoryContinuation(String playerAction, String dmResponse, Character character) {
        if (currentGame == null) {
            throw new IllegalStateException("Нет активной кампании для генерации продолжения истории");
        }
        
        if (currentGame.isStoryCompleted()) {
            return null; // Не генерируем продолжение, если квест завершен
        }
        
        long startTime = System.currentTimeMillis();
        System.out.println("⏳ Генерация продолжения истории для " + character.getName() + "...");
        
        int maxTokens = llmClient.getConfig().getMaxTokens();
        String systemPrompt = DMPrompts.getSystemPrompt(maxTokens);
        
        // Подготавливаем информацию о квесте
        Map<String, Object> questInfo = null;
        if (currentGame.getMainQuest() != null) {
            String currentStage = currentGame.getCurrentQuestStage();
            Map<String, Object> quest = currentGame.getMainQuest();
            questInfo = new HashMap<>();
            questInfo.put("title", quest.getOrDefault("title", ""));
            questInfo.put("goal", quest.getOrDefault("goal", ""));
            questInfo.put("current_stage", currentStage != null ? currentStage : "");
            questInfo.put("progress", currentGame.getStoryProgress());
        }
        
        // Получаем релевантный контекст, если доступен RelevantContextBuilder
        String relevantContextText = "";
        if (relevantContextBuilder != null) {
            try {
                RelevantContextBuilder.RelevantContext relevantContext = 
                    relevantContextBuilder.buildRelevantContext(currentGame, currentGame.getSessionId());
                relevantContextText = relevantContext.formatForPrompt();
            } catch (Exception e) {
                System.err.println("Ошибка при построении релевантного контекста для продолжения истории: " + e.getMessage());
            }
        }
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", DMPrompts.getStoryContinuationPrompt(
            playerAction,
            dmResponse,
            character.getName(),
            character.getCharacterClass().getValue(),
            character.getRace().getValue(),
            currentGame.getCurrentLocation(),
            questInfo,
            relevantContextText
        )));
        
        String response = llmClient.generateResponse(messages, systemPrompt);
        long generationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Генерация продолжения истории завершена за " + (generationTime / 1000.0) + " секунд");
        
        if (response == null || response.trim().isEmpty()) {
            System.err.println("⚠️ LLM вернул пустой ответ при генерации продолжения истории");
            return null;
        }
        
        // Получаем или создаем GameContext
        GameContext gameContext = currentGame.getGameContext();
        if (gameContext == null) {
            gameContext = new GameContext();
            gameContext.setCurrentLocation(currentGame.getCurrentLocation());
            currentGame.setGameContext(gameContext);
        }
        
        // Парсим JSON ответ через MessageParser
        StructuredMessage structuredMessage;
        try {
            structuredMessage = MessageParser.parseMessage(response, character.getName());
        } catch (Exception e) {
            // Fallback на старый формат
            System.err.println("⚠️ Ошибка парсинга через MessageParser, используем старый формат: " + e.getMessage());
            JsonObject jsonObj = extractJsonObject(response);
            String content = jsonObj.has("content") ? jsonObj.get("content").getAsString() : "";
            String location = jsonObj.has("location") ? jsonObj.get("location").getAsString() : 
                             currentGame.getCurrentLocation();
            Map<String, Object> metadata = new HashMap<>();
            if (location != null) metadata.put("location", location);
            structuredMessage = new StructuredMessage(MessageType.SITUATION_CONTINUATION, content, character.getName(), metadata);
        }
        
        // Валидируем тип сообщения
        MessageTypeValidator.ValidationResult validationResult = 
            MessageTypeValidator.validate(structuredMessage.getType(), gameContext);
        
        if (!validationResult.isValid()) {
            System.err.println("⚠️ Валидация продолжения истории не прошла: " + validationResult.getErrors());
        }
        
        // Обновляем GameContext
        gameContext.updateFromMessage(structuredMessage.getType(), structuredMessage.getContent());
        
        String continuation = structuredMessage.getContent();
        String newLocation = (String) structuredMessage.getMetadata().get("location");
        if (newLocation == null || newLocation.isEmpty()) {
            newLocation = currentGame.getCurrentLocation();
        }
        
        // Обновляем локацию, если она указана
        if (newLocation != null && !newLocation.isEmpty() && !newLocation.equals("Неизвестная локация")) {
            currentGame.setCurrentLocation(newLocation);
            gameContext.setCurrentLocation(newLocation);
            gameManager.updateGameState(Map.of("current_location", newLocation));
        }
        
        // Определяем тип события для сохранения
        String eventType = structuredMessage.getType().getCode();
        
        // Сохраняем продолжение истории в историю
        currentGame.addGameEvent(eventType, continuation, character.getName());
        
        // Сохраняем продолжение истории в БД
        if (messageService != null) {
            try {
                List<Long> locationIds = null;
                if (newLocation != null) {
                    locationIds = messageService.findLocationIdsByName(
                        currentGame.getSessionId(), 
                        List.of(newLocation)
                    );
                }
                
                List<Long> questIds = messageService.getActiveQuestIds(currentGame.getSessionId());
                List<Long> npcIds = null;
                
                // Извлекаем упоминания из анализа для связывания с событием
                if (structuredMessage.getMetadata().containsKey("analysis")) {
                    Map<String, Object> analysis = (Map<String, Object>) structuredMessage.getMetadata().get("analysis");
                    
                    // Получаем ID упомянутых NPC
                    if (analysis.containsKey("npcs_mentioned")) {
                        List<String> npcNames = (List<String>) analysis.get("npcs_mentioned");
                        if (npcNames != null && !npcNames.isEmpty()) {
                            npcIds = messageService.findNpcIdsByName(currentGame.getSessionId(), npcNames);
                        }
                    }
                    
                    // Получаем ID упомянутых локаций
                    if (analysis.containsKey("locations_mentioned")) {
                        List<String> locationNames = (List<String>) analysis.get("locations_mentioned");
                        if (locationNames != null && !locationNames.isEmpty()) {
                            List<Long> mentionedLocationIds = messageService.findLocationIdsByName(
                                currentGame.getSessionId(), 
                                locationNames
                            );
                            if (mentionedLocationIds != null && !mentionedLocationIds.isEmpty()) {
                                if (locationIds == null) locationIds = new ArrayList<>();
                                locationIds.addAll(mentionedLocationIds);
                            }
                        }
                    }
                    
                    // Получаем ID упомянутых квестов
                    if (analysis.containsKey("quests_mentioned")) {
                        List<String> questTitles = (List<String>) analysis.get("quests_mentioned");
                        if (questTitles != null && !questTitles.isEmpty()) {
                            questIds = messageService.findQuestIdsByTitles(currentGame.getSessionId(), questTitles);
                        }
                    }
                    
                    // Сохраняем событие и обрабатываем анализ
                    com.dnd.entity.GameEvent savedEvent = messageService.saveDMMessage(
                        currentGame.getSessionId(),
                        eventType,
                        continuation,
                        continuation,
                        character.getName(),
                        newLocation,
                        npcIds,
                        questIds,
                        locationIds
                    );
                    
                    // Обрабатываем анализ с привязкой к событию
                    if (analysisProcessor != null && !analysis.isEmpty()) {
                        try {
                            System.out.println("📊 [DungeonMasterAI] Обработка анализа продолжения истории...");
                            if (savedEvent != null && savedEvent.getId() != null) {
                                analysisProcessor.processAnalysis(currentGame.getSessionId(), analysis, savedEvent.getId());
                            }
                        } catch (Exception e) {
                            System.err.println("⚠️ Ошибка обработки анализа продолжения истории: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                } else {
                    // Сохраняем без анализа
                    messageService.saveDMMessage(
                        currentGame.getSessionId(),
                        eventType,
                        continuation,
                        continuation,
                        character.getName(),
                        newLocation,
                        npcIds,
                        questIds,
                        locationIds
                    );
                }
            } catch (Exception e) {
                System.err.println("Ошибка сохранения продолжения истории: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        currentGame.setGameContext(gameContext);
        
        return continuation;
    }



    public Map<String, Object> getGameStatus() {
        if (currentGame == null) {
            throw new IllegalStateException("Нет активной кампании");
        }
        
        Map<String, Object> questInfo = null;
        if (currentGame.getMainQuest() != null) {
            String currentStage = currentGame.getCurrentQuestStage();
            Map<String, Object> quest = currentGame.getMainQuest();
            questInfo = new HashMap<>();
            questInfo.put("title", quest.getOrDefault("title", ""));
            questInfo.put("goal", quest.getOrDefault("goal", ""));
            questInfo.put("current_stage", currentStage != null ? currentStage : "");
            questInfo.put("progress", currentGame.getStoryProgress());
            questInfo.put("completed", currentGame.isStoryCompleted());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", currentGame.getSessionId());
        result.put("current_location", currentGame.getCurrentLocation());
        result.put("game_mode", currentGame.getGameMode());
        result.put("recent_events", currentGame.getRecentContext(3));
        result.put("quest", questInfo);
        result.put("characters", currentGame.getCharacters());
        result.put("world", currentGame.getWorld());
        return result;
    }

    // Вспомогательные методы
    private Map<String, Object> generateWorld() {
        long startTime = System.currentTimeMillis();
        System.out.println("⏳ Начало генерации мира...");
        
        int maxTokens = llmClient.getConfig().getMaxTokens();
        String systemPrompt = DMPrompts.getSystemPrompt(maxTokens);
        
        List<Map<String, String>> messages = new ArrayList<>();
        com.dnd.game_state.SessionDuration sessionDuration = currentGame != null ? currentGame.getSessionDuration() : com.dnd.game_state.SessionDuration.MEDIUM;
        messages.add(Map.of("role", "user", "content", DMPrompts.getWorldBuildingPrompt(sessionDuration)));
        
        String response = llmClient.generateResponse(messages, systemPrompt);
        long generationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Генерация мира завершена за " + (generationTime / 1000.0) + " секунд");
        
        Map<String, Object> parsedData = extractWorldFromResponse(response);
        
        if (!parsedData.containsKey("world_description")) {
            throw new RuntimeException("Не удалось распарсить ответ LLM для генерации мира. Ответ: " + response);
        }
        
        return parsedData;
    }
    
    private Map<String, Object> generateInitialSceneQuestAndSituation(Map<String, Object> world) {
        long startTime = System.currentTimeMillis();
        int maxTokens = llmClient.getConfig().getMaxTokens();
        String systemPrompt = DMPrompts.getSystemPrompt(maxTokens);
        List<Map<String, String>> messages = new ArrayList<>();
        com.dnd.game_state.SessionDuration sessionDuration = currentGame != null ? currentGame.getSessionDuration() : com.dnd.game_state.SessionDuration.MEDIUM;
        String prompt = DMPrompts.getInitialSceneQuestAndSituationPrompt(world, sessionDuration);
        messages.add(Map.of("role", "user", "content", prompt));
        
        String response = llmClient.generateResponse(messages, systemPrompt);
        long generationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Генерация начальной сцены, квеста и ситуации завершена за " + (generationTime / 1000.0) + " секунд");
        
        Map<String, Object> parsedData = extractJsonFromResponseWithSituation(response);
        
        // Проверяем наличие квеста и ситуации (поддерживаем оба формата: "situation" и "initial_situation")
        if (!parsedData.containsKey("quest") || 
            (!parsedData.containsKey("situation") && !parsedData.containsKey("initial_situation"))) {
            throw new RuntimeException("Не удалось распарсить ответ LLM для генерации начальной сцены, квеста и ситуации. Ответ: " + response);
        }
        
        return parsedData;
    }

    private boolean checkAndAdvanceQuest() {
        if (currentGame == null || currentGame.getMainQuest() == null) {
            return false;
        }
        
        // Упрощенная логика - после успешных действий переходим к следующему этапу
        List<GameState.GameEvent> recentEvents = currentGame.getGameHistory();
        long successCount = recentEvents.stream()
            .filter(e -> e.getType().equals("dm_response"))
            .limit(5)
            .count();
        
        if (successCount >= 2) {
            String oldStage = currentGame.getCurrentQuestStage();
            currentGame.advanceQuestStage();
            String newStage = currentGame.getCurrentQuestStage();
            
            if (oldStage != null && !oldStage.equals(newStage)) {
                String progressMessage = "Прогресс квеста: " + oldStage + " → " + newStage;
                currentGame.addGameEvent("quest_progress", progressMessage, "");
                
                // Сохраняем прогресс квеста в БД
                if (messageService != null) {
                    try {
                        List<Long> questIds = messageService.getActiveQuestIds(currentGame.getSessionId());
                        List<Long> locationIds = null;
                        if (currentGame.getCurrentLocation() != null) {
                            locationIds = messageService.findLocationIdsByName(
                                currentGame.getSessionId(), 
                                List.of(currentGame.getCurrentLocation())
                            );
                        }
                        
                        messageService.saveDMMessage(
                            currentGame.getSessionId(),
                            "quest_progress",
                            progressMessage,
                            progressMessage,
                            null,
                            currentGame.getCurrentLocation(),
                            null, // npcIds
                            questIds,
                            locationIds
                        );
                    } catch (Exception e) {
                        System.err.println("Ошибка сохранения прогресса квеста: " + e.getMessage());
                    }
                }
                
                return true;
            }
        }
        
        return false;
    }

    private String generateFinalScene() {
        if (currentGame == null) {
            throw new IllegalStateException("Нет активной кампании для генерации финальной сцены");
        }
        if (currentGame.getMainQuest() == null) {
            throw new IllegalStateException("Нет основного квеста для генерации финальной сцены");
        }
        
        Map<String, Object> quest = currentGame.getMainQuest();
        int maxTokens = llmClient.getConfig().getMaxTokens();
        String systemPrompt = DMPrompts.getSystemPrompt(maxTokens);
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", DMPrompts.getFinalScenePrompt(
            (String) quest.getOrDefault("title", "Квест"),
            (String) quest.getOrDefault("goal", "")
        )));
        
        String finalScene = llmClient.generateResponse(messages, systemPrompt);
        
        if (finalScene == null || finalScene.trim().isEmpty()) {
            throw new RuntimeException("LLM вернул пустой ответ при генерации финальной сцены");
        }
        
        currentGame.addGameEvent("final_scene", finalScene, "");
        return finalScene;
    }
    
    private JsonObject extractJsonObject(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new RuntimeException("Получен пустой ответ от LLM");
        }
        
        int startIdx = response.indexOf("{");
        int endIdx = response.lastIndexOf("}") + 1;
        
        if (startIdx == -1 || endIdx <= startIdx) {
            throw new RuntimeException("Не удалось найти JSON в ответе: " + response);
        }
        
        String jsonStr = response.substring(startIdx, endIdx);
        
        // Очищаем JSON от распространенных ошибок LLM
        jsonStr = cleanJsonString(jsonStr);
        
        try {
            // Сначала пробуем обычный парсинг
            return gson.fromJson(jsonStr, JsonObject.class);
        } catch (Exception e) {
            // Если не получилось, пробуем с lenient режимом (разрешает trailing commas и другие ошибки)
            try {
                com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(jsonStr));
                reader.setLenient(true);
                return gson.fromJson(reader, JsonObject.class);
            } catch (Exception e2) {
                throw new RuntimeException("Ошибка парсинга JSON: " + e.getMessage() + ". JSON: " + jsonStr, e);
            }
        }
    }
    
    /**
     * Очищает JSON строку от распространенных ошибок, которые может генерировать LLM
     */
    private String cleanJsonString(String json) {
        json = json.replaceAll(",\\s*}", "}");
        json = json.replaceAll(",\\s*]", "]");
        
        json = json.replaceAll(",\\s*\\n\\s*}", "\n}");
        json = json.replaceAll(",\\s*\\n\\s*]", "\n]");
        
        return json;
    }
    

    private Map<String, Object> extractJsonFromResponseWithSituation(String response) {
        JsonObject jsonObj = extractJsonObject(response);
        
        Map<String, Object> result = new HashMap<>();
        
        JsonObject questObj = jsonObj.getAsJsonObject("quest");
        Map<String, Object> quest = new HashMap<>();
        quest.put("title", questObj.get("title").getAsString());
        quest.put("goal", questObj.get("goal").getAsString());
        if (questObj.has("description")) {
            quest.put("description", questObj.get("description").getAsString());
        }
        // Добавляем quest_summary если есть
        if (questObj.has("quest_summary")) {
            quest.put("quest_summary", questObj.get("quest_summary").getAsString());
        }
        
        List<String> stages = new ArrayList<>();
        questObj.getAsJsonArray("stages").forEach(e -> stages.add(e.getAsString()));
        quest.put("stages", stages);
        
        result.put("quest", quest);
        
        // Поддерживаем оба формата: новый "situation" и старый "initial_situation" для обратной совместимости
        if (jsonObj.has("situation")) {
            result.put("situation", jsonObj.get("situation").getAsString());
        } else if (jsonObj.has("initial_situation")) {
            result.put("initial_situation", jsonObj.get("initial_situation").getAsString());
        }
        if (jsonObj.has("initial_location")) {
            result.put("initial_location", jsonObj.get("initial_location").getAsString());
        }
        
        return result;
    }
    
    private Map<String, Object> extractWorldFromResponse(String response) {
        JsonObject jsonObj = extractJsonObject(response);
        // Просто преобразуем весь JSON объект в Map
        return jsonObjectToMap(jsonObj);
    }
    
    /**
     * Преобразует JsonObject в Map<String, Object>
     */
    private Map<String, Object> jsonObjectToMap(JsonObject jsonObj) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            
            if (value.isJsonNull()) {
                map.put(key, null);
            } else if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isString()) {
                    map.put(key, primitive.getAsString());
                } else if (primitive.isNumber()) {
                    map.put(key, primitive.getAsNumber());
                } else if (primitive.isBoolean()) {
                    map.put(key, primitive.getAsBoolean());
                }
            } else if (value.isJsonArray()) {
                List<Object> list = new ArrayList<>();
                value.getAsJsonArray().forEach(e -> {
                    if (e.isJsonObject()) {
                        list.add(jsonObjectToMap(e.getAsJsonObject()));
                    } else if (e.isJsonPrimitive()) {
                        JsonPrimitive p = e.getAsJsonPrimitive();
                        if (p.isString()) {
                            list.add(p.getAsString());
                        } else if (p.isNumber()) {
                            list.add(p.getAsNumber());
                        } else if (p.isBoolean()) {
                            list.add(p.getAsBoolean());
                        }
                    }
                });
                map.put(key, list);
            } else if (value.isJsonObject()) {
                map.put(key, jsonObjectToMap(value.getAsJsonObject()));
            }
        }
        return map;
    }
    
    
}
