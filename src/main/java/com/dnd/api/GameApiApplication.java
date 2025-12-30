package com.dnd.api;

import com.dnd.ai_engine.DungeonMasterAI;
import com.dnd.game_state.GameManager;
import com.dnd.identity.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot Application для AI Dungeon Master API
 */
@SpringBootApplication
@Configuration
@ComponentScan(basePackages = "com.dnd")
public class GameApiApplication implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    public static void main(String[] args) {
        System.out.println("=== AI Dungeon Master API Server ===");
        System.out.println("Инициализация AI Dungeon Master с локальной моделью...");

        SpringApplication.run(GameApiApplication.class, args);
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Регистрируем interceptor для всех запросов, кроме публичных
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/auth/**", "/api/health");
    }
    
    @Bean
    public GameManager gameManager() {
        return new GameManager();
    }
    
    @Bean
    public DungeonMasterAI dungeonMasterAI(GameManager gameManager) {
        try {
            DungeonMasterAI dm = new DungeonMasterAI(gameManager, "mistral:7b");
            System.out.println("✅ AI Dungeon Master инициализирован");
            return dm;
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Не удалось инициализировать AI Dungeon Master", e);
        }
    }
}

