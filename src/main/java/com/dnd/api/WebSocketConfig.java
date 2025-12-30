package com.dnd.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Конфигурация WebSocket для интерактивной игры
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final CampaignHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler, CampaignHandshakeInterceptor handshakeInterceptor) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Поддержка подключения к конкретной кампании: /ws/campaign/{campaignId}
        // HandshakeInterceptor проверяет существование кампании ДО установления соединения
        registry.addHandler(gameWebSocketHandler, "/ws/campaign/{campaignId}")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*"); // В продакшене указать конкретные домены
    }
}

