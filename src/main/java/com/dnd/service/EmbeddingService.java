package com.dnd.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для работы с эмбеддингами через Ollama API
 * Использует модель BGE-M3 для векторизации текста
 */
@Service
public class EmbeddingService {
    private static final Gson gson = new Gson();
    private final OkHttpClient httpClient;
    private final String ollamaBaseUrl;
    private final String embeddingModel;
    
    // BGE-M3 возвращает векторы размерностью 1024
    public static final int VECTOR_SIZE = 1024;
    
    public EmbeddingService(
            @Value("${ollama.base.url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${ollama.embedding.model:bge-m3}") String embeddingModel) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.embeddingModel = embeddingModel;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Получает эмбеддинг для одного текста
     * 
     * @param text Текст для векторизации
     * @return Массив float размерностью 1024
     * @throws IOException При ошибке запроса к Ollama
     */
    public float[] embed(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Текст не может быть пустым");
        }
        
        // Формируем запрос к Ollama
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", embeddingModel);
        requestBody.addProperty("prompt", text);
        
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.get("application/json; charset=utf-8")
        );
        
        Request request = new Request.Builder()
                .url(ollamaBaseUrl + "/api/embeddings")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("Ошибка запроса к Ollama: " + response.code() + " - " + errorBody);
            }
            
            if (response.body() == null) {
                throw new IOException("Пустой ответ от Ollama");
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (!jsonResponse.has("embedding")) {
                throw new IOException("Ответ от Ollama не содержит embedding: " + responseBody);
            }
            
            JsonArray embeddingArray = jsonResponse.getAsJsonArray("embedding");
            float[] embedding = new float[embeddingArray.size()];
            
            for (int i = 0; i < embeddingArray.size(); i++) {
                embedding[i] = embeddingArray.get(i).getAsFloat();
            }
            
            // Проверяем размерность
            if (embedding.length != VECTOR_SIZE) {
                System.err.println("⚠️ Предупреждение: размер вектора (" + embedding.length + 
                        ") не соответствует ожидаемому (" + VECTOR_SIZE + ")");
            }
            
            return embedding;
        }
    }
    
    /**
     * Получает эмбеддинги для нескольких текстов (батч)
     * 
     * @param texts Список текстов для векторизации
     * @return Список массивов float
     * @throws IOException При ошибке запроса к Ollama
     */
    public List<float[]> embedBatch(List<String> texts) throws IOException {
        List<float[]> embeddings = new ArrayList<>();
        
        for (String text : texts) {
            embeddings.add(embed(text));
        }
        
        return embeddings;
    }
    
    /**
     * Создает расширенный текст с контекстом для улучшения качества эмбеддинга
     * 
     * @param description Основное описание события
     * @param questContext Контекст квеста
     * @param locationContext Контекст локации
     * @param npcContext Контекст NPC
     * @return Расширенный текст для векторизации
     */
    public String buildEnhancedText(String description, String questContext, 
                                   String locationContext, String npcContext) {
        StringBuilder enhanced = new StringBuilder();
        enhanced.append("D&D 5e событие: ").append(description);
        
        if (questContext != null && !questContext.trim().isEmpty()) {
            enhanced.append(". Квест: ").append(questContext);
        }
        
        if (locationContext != null && !locationContext.trim().isEmpty()) {
            enhanced.append(". Локация: ").append(locationContext);
        }
        
        if (npcContext != null && !npcContext.trim().isEmpty()) {
            enhanced.append(". NPC: ").append(npcContext);
        }
        
        return enhanced.toString();
    }
    
    /**
     * Проверяет доступность Ollama и модели
     * 
     * @return true если Ollama доступен и модель загружена
     */
    public boolean isAvailable() {
        try {
            Request request = new Request.Builder()
                    .url(ollamaBaseUrl + "/api/tags")
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return false;
                }
                
                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                if (jsonResponse.has("models")) {
                    JsonArray models = jsonResponse.getAsJsonArray("models");
                    for (int i = 0; i < models.size(); i++) {
                        JsonObject model = models.get(i).getAsJsonObject();
                        if (model.has("name") && model.get("name").getAsString().contains(embeddingModel)) {
                            return true;
                        }
                    }
                }
                
                return false;
            }
        } catch (Exception e) {
            System.err.println("Ошибка проверки доступности Ollama: " + e.getMessage());
            return false;
        }
    }
}
