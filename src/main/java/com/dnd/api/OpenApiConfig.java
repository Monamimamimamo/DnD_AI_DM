package com.dnd.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация Swagger/OpenAPI
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("AI Dungeon Master API")
                .version("1.0.0")
                .description("""
                    API для AI Dungeon Master - системы для игры в D&D 5e с искусственным интеллектом.
                    
                    ## Возможности:
                    - Управление кампаниями и персонажами
                    - Обработка действий игроков через AI
                    - WebSocket для интерактивной игры
                    - Identity Service для управления пользователями
                    
                    ## Аутентификация:
                    Большинство endpoints требуют JWT токен в заголовке:
                    `Authorization: Bearer <token>`
                    
                    Получить токен можно через `/api/auth/login`
                    """)
                .contact(new Contact()
                    .name("AI Dungeon Master")
                    .email("support@dnd-ai.local"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Локальный сервер"),
                new Server()
                    .url("http://localhost:8080")
                    .description("Docker контейнер")
            ))
            .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement()
                .addList("bearerAuth"))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT токен. Получите через /api/auth/login")));
    }
}

