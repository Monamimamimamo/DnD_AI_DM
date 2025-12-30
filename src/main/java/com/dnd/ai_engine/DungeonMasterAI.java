package com.dnd.ai_engine;

import com.dnd.game_state.Character;
import com.dnd.game_state.GameManager;
import com.dnd.game_state.GameState;
import com.dnd.prompts.DMPrompts;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * AI Dungeon Master - основная система с мультиагентной архитектурой
 */
public class DungeonMasterAI {
    private static final Gson gson = new GsonBuilder().setLenient().create();
    private final GameManager gameManager;
    private GameState currentGame;
    private final LocalLLMClient llmClient;
    private final GameOrchestrator orchestrator;

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
        System.out.println("✅ Инициализирован локальный клиент с моделью " + localModel);
        
        // Инициализируем Orchestrator
        this.orchestrator = new GameOrchestrator(llmClient);
        System.out.println("✅ Инициализирован Game Orchestrator");
    }
    
    public GameState getCurrentGame() {
        return currentGame;
    }
    
    public void setCurrentGame(GameState game) {
        this.currentGame = game;
        if (game != null) {
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
            progressCallback.accept("⏳ Генерация начальной сцены и квеста...");
        }
        
        // Затем генерируем начальную сцену и квест с учетом мира
        Map<String, Object> sceneAndQuest = generateInitialSceneAndQuest(world);
        
        String initialScene = (String) sceneAndQuest.get("initial_scene");
        if (initialScene == null || initialScene.isEmpty()) {
            throw new RuntimeException("Не удалось сгенерировать начальную сцену");
        }
        
        Map<String, Object> mainQuest = (Map<String, Object>) sceneAndQuest.get("quest");
        if (mainQuest == null) {
            throw new RuntimeException("Не удалось сгенерировать основной квест");
        }
        
        currentGame.setCurrentScene(initialScene);
        
        // Извлекаем локацию
        String initialLocation = extractLocationFromSituation(initialScene);
        if (initialLocation == null || initialLocation.isEmpty() || initialLocation.equals("Неизвестная локация")) {
            System.err.println("⚠️ Не удалось извлечь локацию из начальной сцены. Сцена: " + initialScene);
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
        
        if (progressCallback != null) {
            progressCallback.accept("✅ Начальная сцена и квест созданы");
        }
        
        gameManager.saveGame();
        
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", currentGame.getSessionId());
        result.put("initial_scene", initialScene);
        result.put("main_quest", mainQuest);
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
        orchestratorContext.put("current_scene", currentGame.getCurrentScene());
        orchestratorContext.put("current_situation", lastSituation);
        orchestratorContext.put("environment", new ArrayList<>());
        orchestratorContext.put("game_mode", currentGame.getGameMode());
        
        try {
            Map<String, Object> result = orchestrator.processPlayerAction(
                action, character, orchestratorContext
            );
            
            String dmResponse = (String) result.get("dm_narrative");
            
            // Извлекаем локацию из ответа
            String newLocation = extractLocationFromSituation(dmResponse);
            if (newLocation != null && !newLocation.equals(currentGame.getCurrentLocation())) {
                currentGame.setCurrentLocation(newLocation);
                gameManager.updateGameState(Map.of("current_location", newLocation));
            }
            
            // Проверяем прогресс квеста
            boolean questAdvanced = false;
            if (result.getOrDefault("success", false).equals(true) && !currentGame.isStoryCompleted()) {
                questAdvanced = checkAndAdvanceQuest();
            }
            
            currentGame.addGameEvent("player_action", action, characterName);
            currentGame.addGameEvent("dm_response", dmResponse, characterName);
            
            // Финальная сцена
            if (currentGame.isStoryCompleted() && questAdvanced) {
                String finalScene = generateFinalScene();
                dmResponse = dmResponse + "\n\n" + finalScene;
            }
            
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
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", DMPrompts.getSituationPrompt(
            currentGame.getCurrentScene(),
            characterName,
            currentGame.getCurrentLocation(),
            questInfo
        )));
        
        String situation = llmClient.generateResponse(messages, systemPrompt);
        long generationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Генерация ситуации завершена за " + (generationTime / 1000.0) + " секунд");
        
        if (situation == null || situation.trim().isEmpty()) {
            throw new RuntimeException("LLM вернул пустой ответ при генерации ситуации");
        }
        
        if (progressCallback != null) {
            progressCallback.accept("✅ Ситуация создана");
        }
        
        // Извлекаем и обновляем локацию из описания ситуации
        String extractedLocation = extractLocationFromSituation(situation);
        if (extractedLocation != null && !extractedLocation.isEmpty() && !extractedLocation.equals("Неизвестная локация")) {
            currentGame.setCurrentLocation(extractedLocation);
            gameManager.updateGameState(Map.of("current_location", extractedLocation));
        }
        
        // Сохраняем ситуацию в историю
        currentGame.addGameEvent("situation", situation, characterName);
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
        result.put("current_scene", currentGame.getCurrentScene());
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
    
    private Map<String, Object> generateInitialSceneAndQuest(Map<String, Object> world) {
        long startTime = System.currentTimeMillis();
        System.out.println("⏳ Начало генерации начальной сцены и квеста...");
        
        int maxTokens = llmClient.getConfig().getMaxTokens();
        String systemPrompt = DMPrompts.getSystemPrompt(maxTokens);
        
        List<Map<String, String>> messages = new ArrayList<>();
        String prompt = DMPrompts.getInitialSceneAndQuestPromptWithWorld(world);
        messages.add(Map.of("role", "user", "content", prompt));
        
        String response = llmClient.generateResponse(messages, systemPrompt);
        long generationTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Генерация начальной сцены и квеста завершена за " + (generationTime / 1000.0) + " секунд");
        
        Map<String, Object> parsedData = extractJsonFromResponse(response);
        
        if (!parsedData.containsKey("initial_scene") || !parsedData.containsKey("quest")) {
            throw new RuntimeException("Не удалось распарсить ответ LLM для генерации начальной сцены и квеста. Ответ: " + response);
        }
        
        return parsedData;
    }

    private String extractLocationFromSituation(String situation) {
        if (situation == null || situation.isEmpty()) {
            return currentGame != null ? currentGame.getCurrentLocation() : "Неизвестная локация";
        }
        
        Pattern pattern = Pattern.compile("Вы (?:находитесь|стоите|в|на|у) (?:в|на|у) ([^.]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(situation);
        if (matcher.find()) {
            String location = matcher.group(1).trim();
            if (location.length() > 5 && location.length() < 100) {
                return location;
            }
        }
        
        return currentGame != null ? currentGame.getCurrentLocation() : "Неизвестная локация";
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
                currentGame.addGameEvent("quest_progress", 
                    "Прогресс квеста: " + oldStage + " → " + newStage, "");
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
        
        try {
            return gson.fromJson(jsonStr, JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка парсинга JSON: " + e.getMessage() + ". JSON: " + jsonStr, e);
        }
    }
    
    private Map<String, Object> extractJsonFromResponse(String response) {
        JsonObject jsonObj = extractJsonObject(response);
        
        Map<String, Object> result = new HashMap<>();
        result.put("initial_scene", jsonObj.get("initial_scene").getAsString());
        
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
    
    private Map<String, Object> extractWorldFromResponse(String response) {
        JsonObject jsonObj = extractJsonObject(response);
        
        Map<String, Object> result = new HashMap<>();
        result.put("world_description", jsonObj.get("world_description").getAsString());
        
        if (jsonObj.has("main_location")) {
            JsonObject loc = jsonObj.getAsJsonObject("main_location");
            Map<String, Object> location = new HashMap<>();
            if (loc.has("name")) location.put("name", loc.get("name").getAsString());
            if (loc.has("description")) location.put("description", loc.get("description").getAsString());
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
        
        if (jsonObj.has("atmosphere")) result.put("atmosphere", jsonObj.get("atmosphere").getAsString());
        if (jsonObj.has("magic_system")) result.put("magic_system", jsonObj.get("magic_system").getAsString());
        if (jsonObj.has("history")) result.put("history", jsonObj.get("history").getAsString());
        
        return result;
    }
}
