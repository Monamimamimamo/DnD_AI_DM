package com.dnd.api;

import com.dnd.identity.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import org.springframework.lang.NonNull;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Interceptor для проверки существования кампании перед установлением WebSocket соединения
 */
@Component
public class CampaignHandshakeInterceptor implements HandshakeInterceptor {
    
    @Autowired
    private CampaignService campaignService;
    
    @Autowired(required = false)
    private IdentityService identityService;
    
    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;
    
    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        
        // Извлекаем campaignId из пути URI
        String path = request.getURI().getPath();
        String campaignId = extractCampaignIdFromPath(path);
        
        if (campaignId == null || campaignId.isEmpty()) {
            System.out.println("❌ WebSocket handshake отклонен: не указан campaign_id в пути: " + path);
            response.setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST);
            return false; // Отклоняем handshake
        }
        
        // Проверяем существование кампании
        boolean exists = campaignService.campaignExists(campaignId);
        if (!exists) {
            System.out.println("❌ WebSocket handshake отклонен: кампания '" + campaignId + "' не найдена");
            response.setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
            return false; // Отклоняем handshake
        }
        
        // Проверяем токен авторизации, если он передан
        String token = extractTokenFromQuery(request.getURI());
        if (token == null || token.isEmpty()) {
            System.err.println("❌ WebSocket handshake отклонен: отсутствует токен авторизации");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false; // Отклоняем handshake
        }

        String userId = null;
        try {
            com.dnd.identity.User user = identityService.validateTokenAndGetUser(token);
            userId = user.getId();
            System.out.println("✅ WebSocket handshake: токен валиден для кампании: " + campaignId + ", userId: " + userId);
        } catch (Exception e) {
            System.err.println("❌ WebSocket handshake отклонен: невалидный токен: " + e.getMessage());
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false; // Отклоняем handshake
        }
        
        // Проверяем статус сессии в памяти
        CampaignSession campaignSession = gameWebSocketHandler.getCampaignSession(campaignId);
        if (campaignSession != null) {
            // Проверяем, подключен ли хост
            if (!campaignSession.isHostConnected()) {
                // Хост не подключен - разрешаем подключение только хосту
                if (!campaignSession.isHostByUserId(userId)) {
                    System.out.println("❌ WebSocket handshake отклонен: хост не подключен, подключение разрешено только хосту");
                    response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                    return false; // Отклоняем handshake
                }
                // Это хост - разрешаем переподключение
                System.out.println("✅ WebSocket handshake: разрешено переподключение хоста " + userId);
            } else if (!campaignSession.canAcceptNewConnections()) {
                // Кампания начата - проверяем, был ли этот пользователь участником
                if (!campaignSession.wasParticipant(userId)) {
                    System.out.println("❌ WebSocket handshake отклонен: кампания '" + campaignId + "' начата, пользователь " + userId + " не был участником");
                    response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                    return false; // Отклоняем handshake
                }
                System.out.println("✅ WebSocket handshake: разрешено переподключение для участника " + userId);
            }
        }
        
        // Сохраняем campaignId и userId в атрибутах для использования в handler
        attributes.put("campaignId", campaignId);
        attributes.put("userId", userId);
        System.out.println("✅ WebSocket handshake разрешен для кампании: " + campaignId);
        return true; // Разрешаем handshake
    }
    
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {
        // После установления соединения ничего не делаем
    }
    
    /**
     * Извлекает campaignId из пути URI
     * Формат: /ws/campaign/{campaignId}
     */
    private String extractCampaignIdFromPath(String path) {
        if (path == null) {
            return null;
        }
        
        String prefix = "/ws/campaign/";
        int index = path.indexOf(prefix);
        if (index == -1) {
            return null;
        }
        
        String campaignId = path.substring(index + prefix.length());
        return campaignId.isEmpty() ? null : campaignId;
    }
    
    /**
     * Извлекает токен из query параметров URI
     */
    private String extractTokenFromQuery(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }

        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                try {
                    return java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return null;
                }
            }
        }

        return null;
    }
}