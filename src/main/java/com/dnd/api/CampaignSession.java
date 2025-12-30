package com.dnd.api;

import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление сессиями для кампании
 */
public class CampaignSession {
    private final String campaignId;
    private final String hostSessionId; // ID сессии создателя (хоста)
    private final String hostUserId; // ID пользователя-хоста (привязка к пользователю, а не к сессии)
    private CampaignStatus status = CampaignStatus.WAITING; // waiting, started
    private final List<WebSocketSession> sessions = new ArrayList<>();
    private final ConcurrentHashMap<String, String> sessionToCharacter = new ConcurrentHashMap<>(); // sessionId -> characterName
    private final ConcurrentHashMap<String, PlayerRole> sessionToRole = new ConcurrentHashMap<>(); // sessionId -> role
    private final ConcurrentHashMap<String, String> sessionToUserId = new ConcurrentHashMap<>(); // sessionId -> userId
    private final ConcurrentHashMap<String, String> userIdToSession = new ConcurrentHashMap<>(); // userId -> sessionId
    private final ConcurrentHashMap<String, Boolean> participants = new ConcurrentHashMap<>(); // userId -> true (постоянный список участников)
    private final ConcurrentHashMap<String, String> userIdToCharacter = new ConcurrentHashMap<>(); // userId -> characterName (постоянное хранилище)
    
    public enum CampaignStatus {
        WAITING,  // Ожидание игроков, можно подключаться
        STARTED   // Кампания начата, новые подключения запрещены
    }
    
    public enum PlayerRole {
        HOST,     // Создатель кампании
        PLAYER    // Обычный игрок
    }
    
    public CampaignSession(String campaignId, String hostSessionId, String hostUserId) {
        this.campaignId = campaignId;
        this.hostSessionId = hostSessionId;
        this.hostUserId = hostUserId;
    }
    
    public String getCampaignId() {
        return campaignId;
    }
    
    public String getHostSessionId() {
        return hostSessionId;
    }
    
    public String getHostUserId() {
        return hostUserId;
    }
    
    public boolean isHost(String sessionId) {
        return hostSessionId.equals(sessionId);
    }
    
    public boolean isHostByUserId(String userId) {
        return hostUserId != null && hostUserId.equals(userId);
    }
    
    public boolean hasUser(String userId) {
        return userIdToSession.containsKey(userId) || participants.containsKey(userId);
    }

    public boolean wasParticipant(String userId) {
        return participants.containsKey(userId);
    }
    
    /**
     * Проверить, подключен ли хост
     */
    public boolean isHostConnected() {
        if (hostUserId == null) {
            return false;
        }
        // Проверяем, есть ли активная сессия для хоста
        String hostSessionId = userIdToSession.get(hostUserId);
        if (hostSessionId == null) {
            return false;
        }
        // Проверяем, что сессия существует и открыта
        for (WebSocketSession session : getSessions()) {
            if (session.getId().equals(hostSessionId) && session.isOpen()) {
                return true;
            }
        }
        return false;
    }
    
    public CampaignStatus getStatus() {
        return status;
    }
    
    public void setStatus(CampaignStatus status) {
        this.status = status;
    }
    
    public boolean canAcceptNewConnections() {
        return status == CampaignStatus.WAITING;
    }
    
    public void addSession(WebSocketSession session, PlayerRole role, String userId) {
        synchronized (sessions) {
            if (!sessions.contains(session)) {
                sessions.add(session);
                sessionToRole.put(session.getId(), role);
                if (userId != null) {
                    sessionToUserId.put(session.getId(), userId);
                    userIdToSession.put(userId, session.getId());
                    participants.put(userId, true);
                }
            }
        }
    }
    
    public void removeSession(WebSocketSession session) {
        synchronized (sessions) {
            sessions.remove(session);
            String sessionId = session.getId();
            sessionToCharacter.remove(sessionId);
            sessionToRole.remove(sessionId);
            String userId = sessionToUserId.remove(sessionId);
            if (userId != null) {
                userIdToSession.remove(userId);
            }
        }
    }
    
    public String getUserId(String sessionId) {
        return sessionToUserId.get(sessionId);
    }
    
    public List<WebSocketSession> getSessions() {
        synchronized (sessions) {
            return new ArrayList<>(sessions);
        }
    }
    
    public void setCharacter(String sessionId, String characterName) {
        sessionToCharacter.put(sessionId, characterName);
        // Сохраняем также по userId для восстановления при переподключении
        String userId = sessionToUserId.get(sessionId);
        if (userId != null) {
            userIdToCharacter.put(userId, characterName);
        }
    }
    
    public String getCharacter(String sessionId) {
        return sessionToCharacter.get(sessionId);
    }
    
    public String getCharacterByUserId(String userId) {
        return userIdToCharacter.get(userId);
    }
    
    public PlayerRole getRole(String sessionId) {
        return sessionToRole.getOrDefault(sessionId, PlayerRole.PLAYER);
    }
    
    public List<String> getConnectedPlayers() {
        List<String> players = new ArrayList<>();
        for (WebSocketSession session : getSessions()) {
            String charName = sessionToCharacter.get(session.getId());
            if (charName != null) {
                players.add(charName);
            }
        }
        return players;
    }
    
    /**
     * Получить список всех подключенных сессий (включая тех, у кого нет персонажей)
     */
    public List<WebSocketSession> getAllSessions() {
        return getSessions();
    }
    
    /**
     * Проверить, есть ли у всех подключенных игроков персонажи
     */
    public boolean allPlayersHaveCharacters() {
        List<WebSocketSession> allSessions = getAllSessions();
        if (allSessions.isEmpty()) {
            return false; // Нет подключенных игроков
        }
        
        for (WebSocketSession session : allSessions) {
            String charName = sessionToCharacter.get(session.getId());
            if (charName == null || charName.isEmpty()) {
                return false; // Найден игрок без персонажа
            }
        }
        return true; // У всех есть персонажи
    }
    
    /**
     * Получить список игроков без персонажей
     */
    public List<String> getPlayersWithoutCharacters() {
        List<String> playersWithoutChars = new ArrayList<>();
        for (WebSocketSession session : getAllSessions()) {
            String userId = sessionToUserId.get(session.getId());
            String charName = sessionToCharacter.get(session.getId());
            if (charName == null || charName.isEmpty()) {
                if (userId != null) {
                    playersWithoutChars.add(userId);
                } else {
                    playersWithoutChars.add(session.getId());
                }
            }
        }
        return playersWithoutChars;
    }
}

