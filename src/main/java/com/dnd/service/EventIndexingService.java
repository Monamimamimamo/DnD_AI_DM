package com.dnd.service;

import com.dnd.entity.GameEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Сервис для автоматической индексации событий в векторную БД
 * Вызывается при создании новых событий для создания эмбеддингов
 */
@Service
public class EventIndexingService {
    
    @Autowired(required = false)
    private EmbeddingService embeddingService;
    
    @Autowired(required = false)
    private VectorDBService vectorDBService;
    
    @Value("${rag.enabled:true}")
    private boolean ragEnabled;
    
    /**
     * Индексирует событие в векторную БД (асинхронно)
     * 
     * @param event Событие для индексации
     */
    @Async
    public void indexEvent(GameEvent event) {
        if (!ragEnabled || embeddingService == null || vectorDBService == null) {
            return; // RAG отключен или сервисы недоступны
        }
        
        if (!embeddingService.isAvailable()) {
            System.err.println("⚠️ Ollama недоступен, пропускаем индексацию события " + event.getId());
            return;
        }
        
        try {
            // Получаем контекст для улучшения качества эмбеддинга
            String questContext = extractQuestContext(event);
            String locationContext = event.getLocationName();
            String npcContext = extractNPCContext(event);
            
            // Формируем расширенный текст
            String enhancedText = embeddingService.buildEnhancedText(
                event.getDescription(),
                questContext,
                locationContext,
                npcContext
            );
            
            // Получаем эмбеддинг
            float[] embedding = embeddingService.embed(enhancedText);
            
            // Сохраняем в векторную БД
            Long campaignId = event.getCampaign() != null ? event.getCampaign().getId() : null;
            if (campaignId == null) {
                System.err.println("⚠️ Campaign ID не найден для события " + event.getId());
                return;
            }
            
            vectorDBService.saveEmbedding(
                event.getId(),
                campaignId,
                embedding,
                event.getDescription(),
                questContext,
                locationContext,
                npcContext,
                event.getEventType()
            );
            
            System.out.println("✅ Событие " + event.getId() + " проиндексировано в векторную БД");
            
        } catch (Exception e) {
            System.err.println("⚠️ Ошибка индексации события " + event.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Переиндексирует событие (обновляет эмбеддинг)
     */
    @Async
    public void reindexEvent(GameEvent event) {
        indexEvent(event); // Используем ту же логику
    }
    
    /**
     * Удаляет событие из векторной БД
     */
    public void removeEvent(Long eventId) {
        if (!ragEnabled || vectorDBService == null) {
            return;
        }
        
        try {
            vectorDBService.deleteEmbedding(eventId);
            System.out.println("✅ Событие " + eventId + " удалено из векторной БД");
        } catch (Exception e) {
            System.err.println("⚠️ Ошибка удаления события " + eventId + ": " + e.getMessage());
        }
    }
    
    /**
     * Извлекает контекст квеста из события
     */
    private String extractQuestContext(GameEvent event) {
        if (event.getQuests() == null || event.getQuests().isEmpty()) {
            return null;
        }
        
        StringBuilder context = new StringBuilder();
        for (var quest : event.getQuests()) {
            if (context.length() > 0) context.append(", ");
            context.append(quest.getTitle());
            if (quest.getGoal() != null && !quest.getGoal().isEmpty()) {
                context.append(": ").append(quest.getGoal());
            }
        }
        
        return context.toString();
    }
    
    /**
     * Извлекает контекст NPC из события
     */
    private String extractNPCContext(GameEvent event) {
        if (event.getNpcs() == null || event.getNpcs().isEmpty()) {
            return null;
        }
        
        StringBuilder context = new StringBuilder();
        for (var npc : event.getNpcs()) {
            if (context.length() > 0) context.append(", ");
            context.append(npc.getName());
        }
        
        return context.toString();
    }
}
