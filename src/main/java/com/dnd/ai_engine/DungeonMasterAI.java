package com.dnd.ai_engine;

import com.dnd.events.*;
import com.dnd.entity.Campaign;
import com.dnd.game_state.Character;
import com.dnd.game_state.GameManager;
import com.dnd.game_state.GameState;
import com.dnd.messages.*;
import com.dnd.prompts.DMPrompts;
import com.dnd.repository.CampaignRepository;
import com.dnd.service.MessageService;
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
    private final EventGenerator eventGenerator;
    private final EventTriggerManager triggerManager;
    private RelevantContextBuilder relevantContextBuilder; // Опциональный, для фильтрации контекста
    
    @Autowired(required = false)
    private MessageService messageService; // Сервис для сохранения сообщений в БД
    
    @Autowired(required = false)
    private CampaignRepository campaignRepository; // Для поиска квестов по названиям

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
        
        // Инициализируем систему событий
        this.eventGenerator = new EventGenerator(llmClient);
        this.triggerManager = new EventTriggerManager();
    }
    
    /**
     * Устанавливает RelevantContextBuilder для фильтрации контекста
     */
    public void setRelevantContextBuilder(RelevantContextBuilder relevantContextBuilder) {
        this.relevantContextBuilder = relevantContextBuilder;
        // Также устанавливаем в EventGenerator
        if (eventGenerator != null && relevantContextBuilder != null) {
            eventGenerator.setRelevantContextBuilder(relevantContextBuilder);
        }
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

    public Map<String, Object> startNewCampaign(String sessionId, Consumer<String> progressCallback) {
        if (!gameManager.haveAllUsersCreatedCharacters()) {
            throw new IllegalStateException("Все пользователи должны создать персонажей перед началом кампании.");
        }

        currentGame = gameManager.startNewGame(sessionId);

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
        
        // Получаем ситуацию из нового поля "situation" или старого "initial_situation" для обратной совместимости
        String initialSituation = (String) questAndSituation.get("situation");
        if (initialSituation == null || initialSituation.isEmpty()) {
            initialSituation = (String) questAndSituation.get("initial_situation");
        }
        if (initialSituation == null || initialSituation.isEmpty()) {
            throw new RuntimeException("Не удалось сгенерировать начальную ситуацию");
        }
        currentGame.setCurrentSituation(initialSituation);
        
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
        
        // Сохраняем начальную ситуацию в историю (для всей группы)
        currentGame.addGameEvent("situation", initialSituation, "Начальная ситуация");
        
        // Сохраняем начальную ситуацию в БД
        if (messageService != null) {
            try {
                List<Long> locationIds = null;
                if (initialLocation != null) {
                    locationIds = messageService.findLocationIdsByName(
                        currentGame.getSessionId(), 
                        List.of(initialLocation)
                    );
                }
                
                messageService.saveDMMessage(
                    currentGame.getSessionId(),
                    "situation",
                    initialSituation,
                    initialSituation,
                    null,
                    initialLocation,
                    null, // npcIds
                    null, // questIds
                    locationIds
                );
            } catch (Exception e) {
                System.err.println("Ошибка сохранения начальной ситуации: " + e.getMessage());
            }
        }
        
        if (progressCallback != null) {
            progressCallback.accept("✅ Начальная сцена, квест и ситуация созданы");
        }
        
        gameManager.saveGame();
        
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", currentGame.getSessionId());
        result.put("main_quest", mainQuest);
        result.put("initial_situation", initialSituation);
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
                // Используем сообщение, но логируем предупреждение
                // В будущем можно добавить логику исправления или отклонения
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
                        // Определяем связанные сущности
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
                            "dm_response",
                            dmResponse,
                            dmResponse,
                            characterName,
                            currentGame.getCurrentLocation(),
                            null, // npcIds - можно определить из контекста
                            questIds,
                            locationIds
                        );
                    } catch (Exception e) {
                        System.err.println("Ошибка сохранения ответа DM: " + e.getMessage());
                    }
                }
            
            currentGame.addGameEvent("dm_response", dmResponse, characterName);
            
            // Проверяем триггеры для генерации случайных событий
            String randomEvent = checkAndGenerateRandomEvent(gameContext);
            if (randomEvent != null && !randomEvent.isEmpty()) {
                dmResponse = dmResponse + "\n\n" + randomEvent;
                
                // Сохраняем случайное событие в БД
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
                            "random_event",
                            randomEvent,
                            randomEvent,
                            null, // characterName
                            currentGame.getCurrentLocation(),
                            null, // npcIds
                            questIds,
                            locationIds
                        );
                    } catch (Exception e) {
                        System.err.println("Ошибка сохранения случайного события: " + e.getMessage());
                    }
                }
                
                currentGame.addGameEvent("random_event", randomEvent, "");
            }
            
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

    public String generateSituation(String characterName, Consumer<String> progressCallback) {
        if (currentGame == null) {
            throw new IllegalStateException("Нет активной кампании для генерации ситуации");
        }
        
        if (currentGame.isStoryCompleted()) {
            throw new IllegalStateException("Кампания уже завершена, нельзя генерировать новые ситуации");
        }
        
        if (progressCallback != null) {
            progressCallback.accept("⏳ Генерация ситуации...");
        }
        
        long startTime = System.currentTimeMillis();
        System.out.println("⏳ Начало генерации ситуации для " + characterName + "...");
        
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
        
        List<Map<String, String>> messages = new ArrayList<>();
        String contextSituation = lastSituation;
        if (contextSituation == null || contextSituation.isEmpty()) {
            contextSituation = currentGame.getCurrentSituation();
        }
        
        // Получаем релевантный контекст, если доступен RelevantContextBuilder
        String relevantContextText = "";
        if (relevantContextBuilder != null) {
            try {
                RelevantContextBuilder.RelevantContext relevantContext = 
                    relevantContextBuilder.buildRelevantContext(currentGame, currentGame.getSessionId());
                relevantContextText = relevantContext.formatForPrompt();
            } catch (Exception e) {
                System.err.println("Ошибка при построении релевантного контекста для ситуации: " + e.getMessage());
            }
        }
        
        messages.add(Map.of("role", "user", "content", DMPrompts.getSituationPrompt(
            contextSituation,
            characterName,
            currentGame.getCurrentLocation(),
            questInfo,
            relevantContextText
        )));
        
        String response = llmClient.generateResponse(messages, systemPrompt);
        long generationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Генерация ситуации завершена за " + (generationTime / 1000.0) + " секунд");
        
        if (response == null || response.trim().isEmpty()) {
            throw new RuntimeException("LLM вернул пустой ответ при генерации ситуации");
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
            structuredMessage = MessageParser.parseMessage(response, characterName);
        } catch (Exception e) {
            // Fallback на старый формат
            System.err.println("⚠️ Ошибка парсинга через MessageParser, используем старый формат: " + e.getMessage());
            JsonObject jsonObj = extractJsonObject(response);
            String content = jsonObj.has("situation") ? jsonObj.get("situation").getAsString() : 
                            jsonObj.has("content") ? jsonObj.get("content").getAsString() : "";
            String location = jsonObj.has("location") ? jsonObj.get("location").getAsString() : 
                             currentGame.getCurrentLocation();
            Map<String, Object> metadata = new HashMap<>();
            if (location != null) metadata.put("location", location);
            structuredMessage = new StructuredMessage(MessageType.SITUATION_CONTINUATION, content, characterName, metadata);
        }
        
        // Валидируем тип сообщения
        MessageTypeValidator.ValidationResult validationResult = 
            MessageTypeValidator.validate(structuredMessage.getType(), gameContext);
        
        if (!validationResult.isValid()) {
            System.err.println("⚠️ Валидация ситуации не прошла: " + validationResult.getErrors());
        }
        
        // Обновляем GameContext
        gameContext.updateFromMessage(structuredMessage.getType(), structuredMessage.getContent());
        
        String situation = structuredMessage.getContent();
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
        
        // Синхронизируем GameContext обратно в GameState
        currentGame.setGameContext(gameContext);
        
        if (progressCallback != null) progressCallback.accept("✅ Ситуация создана");
        
        // Сохраняем ситуацию в историю
        currentGame.addGameEvent("situation", situation, characterName);
        
        // Сохраняем ситуацию в БД
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
                    "situation",
                    situation,
                    situation,
                    characterName,
                    currentGame.getCurrentLocation(),
                    null, // npcIds
                    questIds,
                    locationIds
                );
            } catch (Exception e) {
                System.err.println("Ошибка сохранения ситуации: " + e.getMessage());
            }
        }
        
        gameManager.saveGame();
        return situation;
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
        messages.add(Map.of("role", "user", "content", DMPrompts.getWorldBuildingPrompt()));
        
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
        String prompt = DMPrompts.getInitialSceneQuestAndSituationPrompt(world);
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
    
    /**
     * Извлекает строковое значение из JsonElement, обрабатывая как строки, так и массивы
     */
    private String extractStringValue(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            // Если это массив, берем первый элемент или объединяем все элементы
            com.google.gson.JsonArray array = element.getAsJsonArray();
            if (array.size() == 0) {
                return "";
            }
            if (array.size() == 1) {
                return array.get(0).getAsString();
            }
            // Если несколько элементов, объединяем их через пробел
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) sb.append(" ");
                sb.append(array.get(i).getAsString());
            }
            return sb.toString();
        }
        // Если это объект или другой тип, преобразуем в строку
        return element.toString();
    }
    
    private Map<String, Object> extractJsonFromResponse(String response) {
        JsonObject jsonObj = extractJsonObject(response);
        
        Map<String, Object> result = new HashMap<>();
        
        JsonObject questObj = jsonObj.getAsJsonObject("quest");
        Map<String, Object> quest = new HashMap<>();
        quest.put("title", questObj.get("title").getAsString());
        quest.put("goal", questObj.get("goal").getAsString());
        if (questObj.has("description")) {
            quest.put("description", questObj.get("description").getAsString());
        }
        
        List<String> stages = new ArrayList<>();
        questObj.getAsJsonArray("stages").forEach(e -> stages.add(e.getAsString()));
        quest.put("stages", stages);
        
        result.put("quest", quest);
        return result;
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
        
        Map<String, Object> result = new HashMap<>();
        result.put("world_description", extractStringValue(jsonObj.get("world_description")));
        
        if (jsonObj.has("main_location")) {
            JsonObject loc = jsonObj.getAsJsonObject("main_location");
            Map<String, Object> location = new HashMap<>();
            if (loc.has("name")) location.put("name", extractStringValue(loc.get("name")));
            if (loc.has("description")) location.put("description", extractStringValue(loc.get("description")));
            if (loc.has("important_npcs")) {
                List<String> npcs = new ArrayList<>();
                loc.getAsJsonArray("important_npcs").forEach(e -> npcs.add(e.getAsString()));
                location.put("important_npcs", npcs);
            }
            if (loc.has("problems")) {
                List<String> problems = new ArrayList<>();
                loc.getAsJsonArray("problems").forEach(e -> problems.add(e.getAsString()));
                location.put("problems", problems);
            }
            if (loc.has("points_of_interest")) {
                List<String> points = new ArrayList<>();
                loc.getAsJsonArray("points_of_interest").forEach(e -> points.add(e.getAsString()));
                location.put("points_of_interest", points);
            }
            result.put("main_location", location);
        }
        
        if (jsonObj.has("atmosphere")) {
            result.put("atmosphere", extractStringValue(jsonObj.get("atmosphere")));
        }
        if (jsonObj.has("history")) {
            result.put("history", extractStringValue(jsonObj.get("history")));
        }
        
        return result;
    }
    
    /**
     * Проверяет триггеры и генерирует случайное событие если нужно
     */
    private String checkAndGenerateRandomEvent(GameContext gameContext) {
        if (currentGame == null || currentGame.isStoryCompleted()) return null;
        
        try {
            // Проверяем триггеры
            EventTrigger trigger = triggerManager.checkTriggers(currentGame, currentGame.getSessionId());
            
            if (trigger == null) return null;
            
            // Определяем тип события
            EventType eventType = triggerManager.determineEventType(trigger, currentGame);
            
            // Преобразуем EventType в MessageType для валидации
            MessageType messageType = convertEventTypeToMessageType(eventType);
            
            // Валидируем, можно ли генерировать событие этого типа в текущем контексте
            MessageTypeValidator.ValidationResult validationResult = 
                MessageTypeValidator.validate(messageType, gameContext);
            
            if (!validationResult.isValid()) {
                System.out.println("⚠️ Событие типа " + messageType + " не может быть сгенерировано: " + validationResult.getErrors());
                return null;
            }
            
            // Создаем контекст для генерации события
            String currentLocation = currentGame.getCurrentLocation();
            String currentSituation = currentGame.getCurrentSituation();
            Map<String, Object> world = currentGame.getWorld();
            Map<String, Object> mainQuest = currentGame.getMainQuest();
            
            // Получаем последние события для контекста
            List<GameState.GameEvent> recentHistory = currentGame.getGameHistory();
            int historyLimit = Math.min(10, recentHistory.size());
            List<GameState.GameEvent> recentEvents = recentHistory.subList(
                Math.max(0, recentHistory.size() - historyLimit), 
                recentHistory.size()
            );
            
            EventContext context = new EventContext(
                currentGame,
                currentLocation,
                currentSituation,
                world,
                mainQuest,
                recentEvents,
                null, // История будет проанализирована внутри EventGenerator
                null  // Связи будут сгенерированы внутри EventGenerator
            );
            
            // Генерируем событие
            GeneratedEvent event = eventGenerator.generateEvent(trigger, context);
            
            if (event != null) {
                // Сохраняем событие в БД
                if (messageService != null) {
                    try {
                        // Извлекаем связанные сущности из метаданных события (указанные LLM)
                        Map<String, Object> metadata = event.getMetadata();
                        List<Long> npcIds = new ArrayList<>();
                        List<Long> questIds = new ArrayList<>();
                        List<Long> locationIds = new ArrayList<>();
                        
                        if (metadata != null) {
                            // Извлекаем имена NPC из metadata
                            @SuppressWarnings("unchecked")
                            List<String> npcNames = (List<String>) metadata.get("related_npcs");
                            if (npcNames != null && !npcNames.isEmpty()) {
                                npcIds = messageService.findNpcIdsByName(
                                    currentGame.getSessionId(), 
                                    npcNames
                                );
                            }
                            
                            // Извлекаем названия квестов из metadata
                            @SuppressWarnings("unchecked")
                            List<String> questTitles = (List<String>) metadata.get("related_quests");
                            if (questTitles != null && !questTitles.isEmpty() && campaignRepository != null) {
                                // Находим квесты по названиям
                                Campaign campaign = campaignRepository.findBySessionId(currentGame.getSessionId())
                                    .orElse(null);
                                if (campaign != null) {
                                    for (String questTitle : questTitles) {
                                        campaign.getQuests().stream()
                                            .filter(q -> questTitle.equalsIgnoreCase(q.getTitle()))
                                            .findFirst()
                                            .ifPresent(q -> questIds.add(q.getId()));
                                    }
                                }
                            }
                            
                            // Извлекаем названия локаций из metadata
                            @SuppressWarnings("unchecked")
                            List<String> locationNames = (List<String>) metadata.get("related_locations");
                            if (locationNames != null && !locationNames.isEmpty()) {
                                locationIds = messageService.findLocationIdsByName(
                                    currentGame.getSessionId(), 
                                    locationNames
                                );
                            }
                        }
                        
                        // Если локации не указаны LLM, добавляем текущую локацию
                        if (locationIds.isEmpty() && currentGame.getCurrentLocation() != null) {
                            List<Long> currentLocationIds = messageService.findLocationIdsByName(
                                currentGame.getSessionId(), 
                                List.of(currentGame.getCurrentLocation())
                            );
                            locationIds.addAll(currentLocationIds);
                        }
                        
                        messageService.saveDMMessage(
                            currentGame.getSessionId(),
                            event.getType().name().toLowerCase(),
                            event.getDescription(),
                            event.getDescription(),
                            null,
                            currentGame.getCurrentLocation(),
                            npcIds.isEmpty() ? null : npcIds,
                            questIds.isEmpty() ? null : questIds,
                            locationIds.isEmpty() ? null : locationIds
                        );
                    } catch (Exception e) {
                        System.err.println("Ошибка сохранения сгенерированного события: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // Обновляем GameContext на основе типа события
                gameContext.updateFromMessage(messageType, event.getDescription());
                
                // Обновляем флаги и состояния
                updateEventFlags(event);
                
                return event.getDescription();
            }
            
        } catch (Exception e) {
            System.err.println("Ошибка при генерации случайного события: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Преобразует EventType в MessageType для валидации
     */
    private MessageType convertEventTypeToMessageType(EventType eventType) {
        switch (eventType) {
            case NPC_ENCOUNTER:
                return MessageType.NPC_ENCOUNTER;
            case SIDE_QUEST:
                return MessageType.SIDE_QUEST_INTRO;
            case RANDOM_EVENT:
                return MessageType.RANDOM_EVENT;
            case LOCATION_EVENT:
                return MessageType.LOCATION_DESCRIPTION;
            case QUEST_HOOK:
                return MessageType.QUEST_PROGRESSION;
            case REVELATION:
                return MessageType.REVELATION;
            case CONSEQUENCE:
                return MessageType.CONSEQUENCE;
            default:
                return MessageType.RANDOM_EVENT;
        }
    }
    
    /**
     * Обновляет флаги и состояния после генерации события
     */
    private void updateEventFlags(GeneratedEvent event) {
        if (event == null || currentGame == null) {
            return;
        }
        
        // Обновляем время последнего события этого типа
        currentGame.setLastEventTime(event.getType().name(), java.time.LocalDateTime.now());
        
        // Записываем событие локации в триггер-менеджер
        triggerManager.recordLocationEvent(currentGame.getCurrentLocation());
        
        // Обновляем флаги в зависимости от типа события
        switch (event.getType()) {
            case LOCATION_EVENT:
                // Отмечаем локацию как посещенную
                currentGame.addDiscoveredLocation(currentGame.getCurrentLocation());
                break;
                
            case NPC_ENCOUNTER:
                // Можно добавить флаг о встрече с NPC
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) event.getMetadata();
                if (metadata != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> element = (Map<String, Object>) metadata.get("element");
                    if (element != null && element.containsKey("archetype")) {
                        String npcType = (String) element.get("archetype");
                        currentGame.setCampaignFlag("npc_encounter_" + npcType, true);
                    }
                }
                break;
                
            case SIDE_QUEST:
            case QUEST_HOOK:
                // Можно добавить флаг о новом квесте
                currentGame.setCampaignFlag("side_quest_available", true);
                break;
        }
    }
}
