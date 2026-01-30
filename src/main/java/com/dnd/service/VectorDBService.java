package com.dnd.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для работы с векторной БД (PostgreSQL + pgvector)
 * Обеспечивает сохранение и поиск эмбеддингов событий
 */
@Service
public class VectorDBService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Сохраняет эмбеддинг события в БД
     * 
     * @param eventId ID события
     * @param campaignId ID кампании
     * @param embedding Вектор эмбеддинга
     * @param description Описание события
     * @param questContext Контекст квеста
     * @param locationContext Контекст локации
     * @param npcContext Контекст NPC
     * @param eventType Тип события
     * @return ID сохраненного эмбеддинга
     */
    @Transactional
    public Long saveEmbedding(Long eventId, Long campaignId, float[] embedding,
                             String description, String questContext, 
                             String locationContext, String npcContext,
                             String eventType) {
        // Проверяем, существует ли уже эмбеддинг для этого события
        String checkSql = "SELECT id FROM event_embeddings WHERE event_id = ?";
        List<Long> existing = jdbcTemplate.query(checkSql, 
                (rs, rowNum) -> rs.getLong("id"), eventId);
        
        if (!existing.isEmpty()) {
            // Обновляем существующий
            String updateSql = """
                UPDATE event_embeddings 
                SET embedding = ?::vector, 
                    description = ?, 
                    quest_context = ?, 
                    location_context = ?, 
                    npc_context = ?, 
                    event_type = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE event_id = ?
                """;
            
            jdbcTemplate.update(updateSql,
                    arrayToString(embedding),
                    description,
                    questContext,
                    locationContext,
                    npcContext,
                    eventType,
                    eventId);
            
            return existing.get(0);
        } else {
            // Создаем новый
            String insertSql = """
                INSERT INTO event_embeddings 
                (event_id, campaign_id, embedding, description, quest_context, 
                 location_context, npc_context, event_type)
                VALUES (?, ?, ?::vector, ?, ?, ?, ?, ?)
                RETURNING id
                """;
            
            Long id = jdbcTemplate.queryForObject(insertSql, Long.class,
                    eventId,
                    campaignId,
                    arrayToString(embedding),
                    description,
                    questContext,
                    locationContext,
                    npcContext,
                    eventType);
            
            return id;
        }
    }
    
    /**
     * Ищет похожие события по векторному поиску
     * 
     * @param queryEmbedding Вектор запроса
     * @param campaignId ID кампании для фильтрации
     * @param topK Количество результатов (null или <= 0 означает без ограничения)
     * @param minSimilarity Минимальная похожесть (0.0 - 1.0)
     * @return Список найденных событий с оценкой похожести
     */
    public List<SimilarEvent> searchSimilar(float[] queryEmbedding, Long campaignId, 
                                          Integer topK, double minSimilarity) {
        String embeddingStr = arrayToString(queryEmbedding);
        
        // Если topK не указан или <= 0, получаем все релевантные события без ограничения
        if (topK == null || topK <= 0) {
            String sql = """
                SELECT 
                    e.event_id,
                    e.description,
                    e.quest_context,
                    e.location_context,
                    e.npc_context,
                    e.event_type,
                    1 - (e.embedding <=> ?::vector) as similarity
                FROM event_embeddings e
                WHERE e.campaign_id = ?
                AND 1 - (e.embedding <=> ?::vector) >= ?
                ORDER BY e.embedding <=> ?::vector
                """;
            
            return jdbcTemplate.query(sql, new SimilarEventRowMapper(),
                    embeddingStr, campaignId, embeddingStr, minSimilarity, embeddingStr);
        } else {
            String sql = """
                SELECT 
                    e.event_id,
                    e.description,
                    e.quest_context,
                    e.location_context,
                    e.npc_context,
                    e.event_type,
                    1 - (e.embedding <=> ?::vector) as similarity
                FROM event_embeddings e
                WHERE e.campaign_id = ?
                AND 1 - (e.embedding <=> ?::vector) >= ?
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """;
            
            return jdbcTemplate.query(sql, new SimilarEventRowMapper(),
                    embeddingStr, campaignId, embeddingStr, minSimilarity, embeddingStr, topK);
        }
    }
    
    /**
     * Ищет похожие события с дополнительными фильтрами
     * 
     * @param queryEmbedding Вектор запроса
     * @param campaignId ID кампании
     * @param eventType Фильтр по типу события (null = все типы)
     * @param location Фильтр по локации (null = все локации)
     * @param topK Количество результатов (null или <= 0 означает без ограничения)
     * @param minSimilarity Минимальная похожесть
     * @return Список найденных событий
     */
    public List<SimilarEvent> searchSimilarWithFilters(float[] queryEmbedding, Long campaignId,
                                                      String eventType, String location,
                                                      Integer topK, double minSimilarity) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                e.event_id,
                e.description,
                e.quest_context,
                e.location_context,
                e.npc_context,
                e.event_type,
                1 - (e.embedding <=> ?::vector) as similarity
            FROM event_embeddings e
            WHERE e.campaign_id = ?
            AND 1 - (e.embedding <=> ?::vector) >= ?
            """);
        
        List<Object> params = new ArrayList<>();
        String embeddingStr = arrayToString(queryEmbedding);
        params.add(embeddingStr);
        params.add(campaignId);
        params.add(embeddingStr);
        params.add(minSimilarity);
        
        if (eventType != null && !eventType.trim().isEmpty()) {
            sql.append(" AND e.event_type = ?");
            params.add(eventType);
        }
        
        if (location != null && !location.trim().isEmpty()) {
            sql.append(" AND e.location_context LIKE ?");
            params.add("%" + location + "%");
        }
        
        sql.append(" ORDER BY e.embedding <=> ?::vector");
        params.add(embeddingStr);
        
        // Добавляем LIMIT только если topK указан и > 0
        if (topK != null && topK > 0) {
            sql.append(" LIMIT ?");
            params.add(topK);
        }
        
        return jdbcTemplate.query(sql.toString(), new SimilarEventRowMapper(), params.toArray());
    }
    
    /**
     * Удаляет эмбеддинг события
     * 
     * @param eventId ID события
     */
    @Transactional
    public void deleteEmbedding(Long eventId) {
        String sql = "DELETE FROM event_embeddings WHERE event_id = ?";
        jdbcTemplate.update(sql, eventId);
    }
    
    /**
     * Удаляет все эмбеддинги кампании
     * 
     * @param campaignId ID кампании
     */
    @Transactional
    public void deleteCampaignEmbeddings(Long campaignId) {
        String sql = "DELETE FROM event_embeddings WHERE campaign_id = ?";
        jdbcTemplate.update(sql, campaignId);
    }
    
    /**
     * Преобразует массив float в строку для PostgreSQL vector типа
     */
    private String arrayToString(float[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Массив не может быть пустым");
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Класс для хранения результата поиска
     */
    public static class SimilarEvent {
        private Long eventId;
        private String description;
        private String questContext;
        private String locationContext;
        private String npcContext;
        private String eventType;
        private double similarity;
        
        public SimilarEvent(Long eventId, String description, String questContext,
                           String locationContext, String npcContext, String eventType,
                           double similarity) {
            this.eventId = eventId;
            this.description = description;
            this.questContext = questContext;
            this.locationContext = locationContext;
            this.npcContext = npcContext;
            this.eventType = eventType;
            this.similarity = similarity;
        }
        
        // Getters
        public Long getEventId() { return eventId; }
        public String getDescription() { return description; }
        public String getQuestContext() { return questContext; }
        public String getLocationContext() { return locationContext; }
        public String getNpcContext() { return npcContext; }
        public String getEventType() { return eventType; }
        public double getSimilarity() { return similarity; }
    }
    
    /**
     * RowMapper для SimilarEvent
     */
    private static class SimilarEventRowMapper implements RowMapper<SimilarEvent> {
        @Override
        public SimilarEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SimilarEvent(
                    rs.getLong("event_id"),
                    rs.getString("description"),
                    rs.getString("quest_context"),
                    rs.getString("location_context"),
                    rs.getString("npc_context"),
                    rs.getString("event_type"),
                    rs.getDouble("similarity")
            );
        }
    }
}
