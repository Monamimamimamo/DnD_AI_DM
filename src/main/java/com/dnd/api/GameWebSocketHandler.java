package com.dnd.api;

import com.dnd.game_state.Character;
import com.dnd.game_state.GameState;
import com.dnd.identity.IdentityService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket обработчик для интерактивной игры с AI Dungeon Master
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    
    private static final Gson gson = new GsonBuilder().setLenient().create();
    
    // Храним кампании и их сессии: campaignId -> CampaignSession
    private final Map<String, CampaignSession> campaigns = new ConcurrentHashMap<>();
    
    @Autowired
    private CampaignService campaignService;
    
    @Autowired
    private IdentityService identityService;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Извлекаем campaignId из URI (он уже проверен в HandshakeInterceptor)
        String uri = session.getUri().toString();
        String campaignId = extractCampaignIdFromUri(uri);
        
        // campaignId уже проверен в HandshakeInterceptor, но на всякий случай проверяем еще раз
        if (campaignId == null || campaignId.isEmpty()) {
            sendError(session, "Не указан campaign_id в URL");
            session.close();
            return;
        }
        
        System.out.println("✅ WebSocket соединение установлено: " + session.getId() + " для кампании: " + campaignId);
        
        // Извлекаем userId из атрибутов (сохранен в HandshakeInterceptor)
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            // Пытаемся извлечь из токена
            String token = extractTokenFromUri(uri);
            if (token != null) {
                try {
                    userId = identityService.validateTokenAndGetUser(token).getId();
                } catch (Exception e) {
                    sendError(session, "Не удалось определить пользователя");
                    session.close();
                    return;
                }
            }
        }
        
        try {
            CampaignSession campaignSession = campaigns.get(campaignId);
            
            if (campaignSession == null) {
                // Кампания существует в БД, но сессий нет
                // Загружаем информацию о кампании
                Map<String, Object> gameStatus = campaignService.getGameStatus(campaignId);
                
                // Проверяем статус кампании (если есть локация или ситуация - значит начата)
                String currentLocation = (String) gameStatus.get("current_location");
                String currentSituation = (String) gameStatus.get("current_situation");
                boolean isStarted = (currentLocation != null && !currentLocation.isEmpty() && !currentLocation.equals("Неизвестная локация"))
                                 || (currentSituation != null && !currentSituation.isEmpty());
                
                // Если кампания начата, первый участник из списка становится хостом
                // Иначе первый подключившийся становится хостом
                String hostUserId = userId; // По умолчанию этот пользователь - хост
                
                // Создаем сессию
                campaignSession = new CampaignSession(campaignId, session.getId(), hostUserId);
                if (isStarted) {
                    campaignSession.setStatus(CampaignSession.CampaignStatus.STARTED);
                }
                campaigns.put(campaignId, campaignSession);
                campaignSession.addSession(session, CampaignSession.PlayerRole.HOST, userId);
                
                // Отправляем информацию о загрузке
                Map<String, Object> welcomeMessage = new HashMap<>();
                welcomeMessage.put("type", "campaign_loaded");
                welcomeMessage.put("campaign_id", campaignId);
                welcomeMessage.put("role", "host");
                welcomeMessage.put("status", campaignSession.getStatus().toString().toLowerCase());
                welcomeMessage.put("current_location", gameStatus.get("current_location"));
                welcomeMessage.put("main_quest", gameStatus.get("quest"));
                welcomeMessage.put("world", gameStatus.get("world")); // Добавляем информацию о мире
                
                // Преобразуем объекты Character в Map, если требуется
                List<Map<String, Object>> characters = new ArrayList<>();
                for (Object obj : (List<?>) gameStatus.get("characters")) {
                    if (obj instanceof Character) {
                        characters.add(characterToMap((Character) obj));
                    } else if (obj instanceof Map) {
                        characters.add((Map<String, Object>) obj);
                    } else {
                        throw new IllegalArgumentException("Неизвестный тип объекта в списке персонажей: " + obj.getClass().getName());
                    }
                }

                if (characters != null && !characters.isEmpty()) {
                    welcomeMessage.put("characters", characters);
                }
                
                sendMessage(session, welcomeMessage);
                
                if (isStarted) {
                    // Если кампания уже начата, отправляем текущую ситуацию
                    if (characters != null && !characters.isEmpty()) {
                        String charName = (String) characters.get(0).get("name");
                        generateAndBroadcastSituation(campaignId, charName);
                    }
                } else {
                    welcomeMessage.put("message", "Кампания загружена. Дождитесь подключения игроков и нажмите 'Начать кампанию'");
                }
                
                return;
            } else {
                // Кампания уже существует
                // Все проверки уже выполнены в CampaignHandshakeInterceptor
                // Определяем роль пользователя
                CampaignSession.PlayerRole role = campaignSession.isHostByUserId(userId) 
                    ? CampaignSession.PlayerRole.HOST 
                    : CampaignSession.PlayerRole.PLAYER;
                campaignSession.addSession(session, role, userId);
                
                // Восстанавливаем персонажа игрока, если он был
                String characterName = campaignSession.getCharacterByUserId(userId);
                if (characterName != null) {
                    campaignSession.setCharacter(session.getId(), characterName);
                }
                
                // Загружаем информацию о кампании для отправки ключевых сообщений
                Map<String, Object> gameStatus = campaignService.getGameStatus(campaignId);
                String currentLocation = (String) gameStatus.get("current_location");
                Object mainQuest = gameStatus.get("quest");
                
                // Определяем роль пользователя
                CampaignSession.PlayerRole userRole = campaignSession.getRole(session.getId());
                String roleString = userRole == CampaignSession.PlayerRole.HOST ? "host" : "player";
                
                // Уведомляем всех о новом подключении
                Map<String, Object> playerJoined = new HashMap<>();
                playerJoined.put("type", "player_joined");
                playerJoined.put("session_id", session.getId());
                playerJoined.put("message", "Новый игрок подключился к кампании");
                broadcastToCampaign(campaignId, playerJoined, session); // Отправляем всем кроме нового игрока
                
                // Отправляем приветствие новому игроку с полной информацией
                Map<String, Object> welcomeMessage = new HashMap<>();
                welcomeMessage.put("type", "campaign_joined");
                welcomeMessage.put("campaign_id", campaignId);
                welcomeMessage.put("role", roleString);
                welcomeMessage.put("status", campaignSession.getStatus().toString().toLowerCase());
                
                if (userRole == CampaignSession.PlayerRole.HOST) {
                    welcomeMessage.put("message", "Вы подключились к кампании как хост.");
                } else {
                    welcomeMessage.put("message", "Вы подключились к кампании.");
                }
                
                // Добавляем ключевую информацию о кампании
                if (currentLocation != null && !currentLocation.isEmpty()) {
                    welcomeMessage.put("current_location", currentLocation);
                }
                if (mainQuest != null) {
                    welcomeMessage.put("main_quest", mainQuest);
                }
                // Добавляем информацию о мире
                welcomeMessage.put("world", gameStatus.get("world"));
                
                // Восстанавливаем персонажа, если он был
                if (characterName != null) {
                    welcomeMessage.put("character_name", characterName);
                    // Находим информацию о персонаже
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> characters = (List<Map<String, Object>>) gameStatus.get("characters");
                    if (characters != null) {
                        for (Map<String, Object> charData : characters) {
                            if (characterName.equals(charData.get("name"))) {
                                welcomeMessage.put("character", charData);
                                break;
                            }
                        }
                    }
                }
                
                sendMessage(session, welcomeMessage);
                
                // Если кампания начата, отправляем текущую ситуацию
                if (campaignSession.getStatus() == CampaignSession.CampaignStatus.STARTED) {
                    if (characterName != null) {
                        generateAndBroadcastSituation(campaignId, characterName);
                    } else if (gameStatus.get("characters") != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> characters = (List<Map<String, Object>>) gameStatus.get("characters");
                        if (characters != null && !characters.isEmpty()) {
                            String firstCharName = (String) characters.get(0).get("name");
                            generateAndBroadcastSituation(campaignId, firstCharName);
                        }
                    }
                }
                
                // Отправляем список подключенных игроков
                Map<String, Object> playersList = new HashMap<>();
                playersList.put("type", "players_list");
                playersList.put("players", campaignSession.getConnectedPlayers());
                sendMessage(session, playersList);
                
                return;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Ошибка при работе с кампанией: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> error = new HashMap<>();
            error.put("type", "error");
            error.put("message", "Не удалось загрузить/создать кампанию: " + e.getMessage());
            sendMessage(session, error);
        }
    }
    
    /**
     * Извлекает campaignId из URI
     * Формат URI: ws://host:port/ws/campaign/{campaignId}
     */
    private String extractCampaignIdFromUri(String uri) {
        if (uri == null) {
            return null;
        }
        
        // Ищем паттерн /ws/campaign/ в URI
        int index = uri.indexOf("/ws/campaign/");
        if (index == -1) {
            return null;
        }
        
        // Извлекаем campaignId после /ws/campaign/
        String campaignId = uri.substring(index + "/ws/campaign/".length());
        
        // Убираем query параметры, если есть
        int queryIndex = campaignId.indexOf("?");
        if (queryIndex != -1) {
            campaignId = campaignId.substring(0, queryIndex);
        }
        
        return campaignId.isEmpty() ? null : campaignId;
    }
    
    /**
     * Извлекает токен из query параметров URI
     * Формат URI: ws://host:port/ws/campaign/{campaignId}?token=...
     */
    private String extractTokenFromUri(String uriString) {
        if (uriString == null) {
            return null;
        }
        
        try {
            URI uri = new URI(uriString);
            String query = uri.getQuery();
            if (query == null || query.isEmpty()) {
                return null;
            }
            
            // Парсим query параметры
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                    return java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка извлечения токена из URI: " + e.getMessage());
        }
        
        return null;
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("📨 Получено сообщение от " + session.getId() + ": " + payload);
        
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "unknown";
            
            CampaignSession campaignSession = getCampaignSession(session);
            if (campaignSession == null) {
                sendError(session, "Кампания не найдена. Переподключитесь.");
                return;
            }
            
            String campaignId = campaignSession.getCampaignId();
            
            switch (type) {
                case "start_campaign":
                    handleStartCampaign(session, campaignSession);
                    break;
                case "character_info":
                    handleCharacterInfo(session, json, campaignSession);
                    break;
                case "action":
                    handlePlayerAction(session, json, campaignSession);
                    break;
                default:
                    sendError(session, "Неизвестный тип сообщения: " + type);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Ошибка обработки сообщения: " + e.getMessage());
            e.printStackTrace();
            sendError(session, "Ошибка обработки сообщения: " + e.getMessage());
        }
    }
    
    private CampaignSession getCampaignSession(WebSocketSession session) {
        for (CampaignSession campaignSession : campaigns.values()) {
            for (WebSocketSession s : campaignSession.getSessions()) {
                if (s.getId().equals(session.getId())) {
                    return campaignSession;
                }
            }
        }
        return null;
    }
    
    /**
     * Получить сессию кампании (для использования в interceptor'е)
     */
    public CampaignSession getCampaignSession(String campaignId) {
        return campaigns.get(campaignId);
    }
    
    private void handleStartCampaign(WebSocketSession session, CampaignSession campaignSession) throws Exception {
        // Только хост может начать кампанию (проверяем по userId)
        String userId = campaignSession.getUserId(session.getId());
        if (userId == null || !campaignSession.isHostByUserId(userId)) {
            sendError(session, "Только хост может начать кампанию");
            return;
        }
        
        // Проверяем, что кампания еще не начата
        if (campaignSession.getStatus() == CampaignSession.CampaignStatus.STARTED) {
            sendError(session, "Кампания уже начата");
            return;
        }
        
        String campaignId = campaignSession.getCampaignId();
        
        // Проверяем, что все подключенные игроки имеют персонажей
        if (!campaignSession.allPlayersHaveCharacters()) {
            List<String> playersWithoutChars = campaignSession.getPlayersWithoutCharacters();
            sendError(session, "Не все игроки добавили персонажей. Дождитесь, пока все игроки добавят персонажей перед началом кампании.");
            return;
        }
        
        // Получаем всех персонажей из кампании
        Map<String, Object> gameStatus = campaignService.getGameStatus(campaignId);
        @SuppressWarnings("unchecked")
        List<Character> characters = (List<Character>) gameStatus.get("characters");
        
        if (characters == null || characters.isEmpty()) {
            sendError(session, "Нет персонажей в кампании. Добавьте хотя бы одного персонажа перед началом.");
            return;
        }
        
        // СРАЗУ меняем статус на STARTED, чтобы блокировать новые подключения
        campaignSession.setStatus(CampaignSession.CampaignStatus.STARTED);
        // Генерируем начальную сцену и квест
        List<String> progressMessages = new ArrayList<>();
        Map<String, Object> campaign = campaignService.startCampaign(
            campaignId,
            msg -> progressMessages.add(msg)
        );
        
        // Начальная ситуация уже включена в результат startCampaign
        String initialSituation = (String) campaign.get("initial_situation");
        
        // Обновляем статус игры для получения информации о мире (после генерации)
        gameStatus = campaignService.getGameStatus(campaignId);
        
        // Отправляем информацию о мире отдельным сообщением для вывода в чат
        @SuppressWarnings("unchecked")
        Map<String, Object> world = (Map<String, Object>) gameStatus.get("world");
        if (world != null) {
            String worldDescription = (String) world.get("world_description");
            if (worldDescription != null && !worldDescription.isEmpty()) {
                Map<String, Object> worldMessage = new HashMap<>();
                worldMessage.put("type", "world_info");
                worldMessage.put("message", "🌍 **Мир кампании:**\n\n" + worldDescription);
                broadcastToCampaign(campaignId, worldMessage, null);
            }
        }
        
        // Отправляем информацию о квесте отдельным сообщением для вывода в чат
        @SuppressWarnings("unchecked")
        Map<String, Object> mainQuest = (Map<String, Object>) campaign.get("main_quest");
        if (mainQuest != null) {
            String questTitle = (String) mainQuest.get("title");
            String questGoal = (String) mainQuest.get("goal");
            String questDescription = (String) mainQuest.get("description");
            
            if (questTitle != null || questGoal != null) {
                StringBuilder questText = new StringBuilder("📜 **Основной квест:**\n\n");
                if (questTitle != null && !questTitle.isEmpty()) {
                    questText.append("**").append(questTitle).append("**\n\n");
                }
                if (questGoal != null && !questGoal.isEmpty()) {
                    questText.append("**Цель:** ").append(questGoal).append("\n\n");
                }
                if (questDescription != null && !questDescription.isEmpty()) {
                    questText.append(questDescription);
                }
                
                Map<String, Object> questMessage = new HashMap<>();
                questMessage.put("type", "quest_info");
                questMessage.put("message", questText.toString());
                broadcastToCampaign(campaignId, questMessage, null);
            }
        }
        
        // Отправляем всем игрокам уведомление о начале
        Map<String, Object> campaignStarted = new HashMap<>();
        campaignStarted.put("type", "campaign_started");
        campaignStarted.put("message", "Кампания началась!");
        campaignStarted.put("main_quest", campaign.get("main_quest"));
        campaignStarted.put("initial_situation", initialSituation);
        campaignStarted.put("current_location", gameStatus.get("current_location"));
        campaignStarted.put("world", gameStatus.get("world")); // Добавляем информацию о мире
        campaignStarted.put("progress", progressMessages);
        
        broadcastToCampaign(campaignId, campaignStarted, null); // Отправляем всем
    }
    
    private void handleCharacterInfo(WebSocketSession session, JsonObject json, CampaignSession campaignSession) throws Exception {
        // Проверяем, что кампания еще не начата
        if (campaignSession.getStatus() == CampaignSession.CampaignStatus.STARTED) {
            sendError(session, "Кампания уже начата. Нельзя добавлять новых персонажей.");
            return;
        }
        
        // Парсим информацию о персонаже
        if (!json.has("name")) {
            sendError(session, "Требуется поле 'name' для персонажа");
            return;
        }
        
        String characterName = json.get("name").getAsString();
        String campaignId = campaignSession.getCampaignId();
        
        // Проверяем уникальность имени персонажа
        try {
            campaignService.ensureCampaignLoaded(campaignId);
            GameState gameState = campaignService.getGameState(campaignId);
            if (gameState != null) {
                List<Character> existingCharacters = gameState.getCharacters();
                if (existingCharacters != null) {
                    for (Character existingChar : existingCharacters) {
                        if (existingChar.getName() != null && existingChar.getName().equalsIgnoreCase(characterName)) {
                            sendError(session, "Персонаж с именем '" + characterName + "' уже существует в этой кампании. Выберите другое имя.");
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Если не удалось проверить, продолжаем (не критично)
            System.err.println("Не удалось проверить уникальность имени персонажа: " + e.getMessage());
        }
        
        // Создаем персонажа
        Character character = parseCharacterFromJson(json);
        campaignService.addCharacter(campaignId, character);
        
        campaignSession.setCharacter(session.getId(), characterName);
        
        // Отправляем подтверждение отправителю
        Map<String, Object> response = new HashMap<>();
        response.put("type", "character_added");
        response.put("character", characterToMap(character));
        response.put("message", "Персонаж успешно добавлен в кампанию!");
        sendMessage(session, response);
        
        // Уведомляем всех о новом персонаже
        Map<String, Object> playerCharacterAdded = new HashMap<>();
        playerCharacterAdded.put("type", "player_character_added");
        playerCharacterAdded.put("character_name", characterName);
        playerCharacterAdded.put("message", "Игрок " + characterName + " присоединился к кампании");
        broadcastToCampaign(campaignId, playerCharacterAdded, session);
        
        // Обновляем список игроков для всех
        Map<String, Object> playersList = new HashMap<>();
        playersList.put("type", "players_list");
        playersList.put("players", campaignSession.getConnectedPlayers());
        broadcastToCampaign(campaignId, playersList, null);
    }
    
    private void handlePlayerAction(WebSocketSession session, JsonObject json, CampaignSession campaignSession) throws Exception {
        // Проверяем, что кампания начата
        if (campaignSession.getStatus() != CampaignSession.CampaignStatus.STARTED) {
            sendError(session, "Кампания еще не начата. Дождитесь начала от хоста.");
            return;
        }
        
        if (!json.has("action")) {
            sendError(session, "Требуется поле 'action'");
            return;
        }
        
        String action = json.get("action").getAsString();
        String campaignId = campaignSession.getCampaignId();
        String characterName = campaignSession.getCharacter(session.getId());
        
        if (characterName == null) {
            sendError(session, "Персонаж не установлен. Отправьте информацию о персонаже.");
            return;
        }
        
        // Обрабатываем действие
        Map<String, Object> result = campaignService.processAction(campaignId, action, characterName);
        
        // Отправляем ответ DM всем игрокам
        Map<String, Object> response = new HashMap<>();
        response.put("type", "dm_response");
        response.put("character_name", characterName);
        response.put("action", action);
        response.put("dm_response", result.get("dm_response"));
        response.put("current_location", result.get("current_location"));
        response.put("game_mode", result.get("game_mode"));
        response.put("success", result.get("success"));
        response.put("quest_advanced", result.get("quest_advanced"));
        response.put("story_completed", result.get("story_completed"));
        
        if (result.containsKey("rule_result")) {
            response.put("rule_result", result.get("rule_result"));
        }
        
        // Отправляем всем игрокам
        broadcastToCampaign(campaignId, response, null);
        
        // Если требуется новое действие, генерируем новую ситуацию для всех
        if (result.getOrDefault("requires_new_action", false).equals(true)) {
            generateAndBroadcastSituation(campaignId, characterName);
        }
    }
    
    private void generateAndBroadcastSituation(String campaignId, String characterName) {
        try {
            List<String> progressMessages = new ArrayList<>();
            String situation = campaignService.generateSituation(
                campaignId, 
                characterName, 
                msg -> progressMessages.add(msg)
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("type", "situation");
            response.put("situation", situation);
            response.put("current_location", campaignService.getGameStatus(campaignId).get("current_location"));
            response.put("progress", progressMessages);
            
            // Отправляем всем игрокам
            broadcastToCampaign(campaignId, response, null);
            
        } catch (Exception e) {
            System.err.println("❌ Ошибка генерации ситуации: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> error = new HashMap<>();
            error.put("type", "error");
            error.put("message", "Не удалось сгенерировать ситуацию: " + e.getMessage());
            broadcastToCampaign(campaignId, error, null);
        }
    }
    
    /**
     * Отправляет сообщение всем подключенным к кампании игрокам
     * @param campaignId ID кампании
     * @param message Сообщение для отправки
     * @param excludeSession Сессия, которой НЕ отправлять сообщение (null = отправлять всем)
     */
    private void broadcastToCampaign(String campaignId, Map<String, Object> message, WebSocketSession excludeSession) {
        CampaignSession campaignSession = campaigns.get(campaignId);
        if (campaignSession == null) {
            return;
        }
        
        List<WebSocketSession> sessions = campaignSession.getSessions();
        String json = gson.toJson(message);
        
        for (WebSocketSession session : sessions) {
            if (excludeSession != null && session.getId().equals(excludeSession.getId())) {
                continue; // Пропускаем исключенную сессию
            }
            
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                System.err.println("❌ Ошибка отправки сообщения сессии " + session.getId() + ": " + e.getMessage());
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("🔌 WebSocket соединение закрыто: " + session.getId());
        
        CampaignSession campaignSession = getCampaignSession(session);
        if (campaignSession != null) {
            String campaignId = campaignSession.getCampaignId();
            String characterName = campaignSession.getCharacter(session.getId());
            
            // Удаляем сессию
            campaignSession.removeSession(session);
            
            // Уведомляем всех о отключении игрока
            if (characterName != null) {
                Map<String, Object> playerLeft = new HashMap<>();
                playerLeft.put("type", "player_left");
                playerLeft.put("character_name", characterName);
                playerLeft.put("message", "Игрок " + characterName + " покинул кампанию");
                broadcastToCampaign(campaignId, playerLeft, null);
            }
            
            // Если это был хост и он отключился, НЕ удаляем сессию полностью
            // Сохраняем информацию о хосте и участниках для переподключения
            if (campaignSession.isHost(session.getId())) {
                Map<String, Object> hostLeft = new HashMap<>();
                hostLeft.put("type", "host_left");
                hostLeft.put("message", "Хост покинул кампанию.");
                broadcastToCampaign(campaignId, hostLeft, null);
                
                // Закрываем все сессии
                for (WebSocketSession s : campaignSession.getSessions()) {
                    try {
                        if (s.isOpen()) {
                            s.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                
                // НЕ удаляем сессию - сохраняем информацию о хосте и участниках
                // Сессия останется в памяти для проверки при переподключении
            }
        }
    }
    
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String json = gson.toJson(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            System.err.println("❌ Ошибка отправки сообщения: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendError(WebSocketSession session, String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("type", "error");
        error.put("message", errorMessage);
        sendMessage(session, error);
    }
    
    private Character parseCharacterFromJson(JsonObject json) {
        String name = json.get("name").getAsString();
        String className = json.has("class") ? json.get("class").getAsString() : "FIGHTER";
        String raceName = json.has("race") ? json.get("race").getAsString() : "HUMAN";
        int level = json.has("level") ? json.get("level").getAsInt() : 1;
        
        com.dnd.game_state.CharacterClass charClass = 
            com.dnd.game_state.CharacterClass.valueOf(className.toUpperCase());
        com.dnd.game_state.CharacterRace race = 
            com.dnd.game_state.CharacterRace.valueOf(raceName.toUpperCase());
        
        com.dnd.game_state.AbilityScores abilityScores;
        if (json.has("ability_scores")) {
            JsonObject scores = json.getAsJsonObject("ability_scores");
            abilityScores = new com.dnd.game_state.AbilityScores(
                scores.has("strength") ? scores.get("strength").getAsInt() : 10,
                scores.has("dexterity") ? scores.get("dexterity").getAsInt() : 10,
                scores.has("constitution") ? scores.get("constitution").getAsInt() : 10,
                scores.has("intelligence") ? scores.get("intelligence").getAsInt() : 10,
                scores.has("wisdom") ? scores.get("wisdom").getAsInt() : 10,
                scores.has("charisma") ? scores.get("charisma").getAsInt() : 10
            );
        } else {
            abilityScores = new com.dnd.game_state.AbilityScores(10, 10, 10, 10, 10, 10);
        }
        
        String background = json.has("background") ? json.get("background").getAsString() : "";
        String alignment = json.has("alignment") ? json.get("alignment").getAsString() : "neutral";
        
        return new Character(name, charClass, race, level, abilityScores, background, alignment);
    }
    
    private Map<String, Object> characterToMap(Character character) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", character.getName());
        map.put("class", character.getCharacterClass());
        map.put("race", character.getRace());
        map.put("level", character.getLevel());
        // Добавьте другие поля, если необходимо
        return map;
    }
}


