package com.dnd.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для автоматической загрузки необходимых моделей Ollama при старте приложения
 */
@Service
public class ModelInitializationService {
    private static final Gson gson = new Gson();
    private final String ollamaBaseUrl;
    private final OkHttpClient httpClient;
    
    // Список необходимых моделей
    private static final List<String> REQUIRED_MODELS = List.of(
        "mistral:7b",  // Для DM и ActionParser
        "bge-m3"       // Для RAG эмбеддингов
    );
    
    public ModelInitializationService(
            @Value("${ollama.base.url:http://localhost:11434}") String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void initializeModels() {
        System.out.println("🔧 [ModelInitializationService] Проверка и загрузка необходимых моделей Ollama...");
        
        try {
            // Ждём немного, чтобы Ollama точно был готов
            Thread.sleep(2000);
            
            // Получаем список установленных моделей
            List<String> installedModels = getInstalledModels();
            System.out.println("📋 [ModelInitializationService] Установленные модели: " + installedModels);
            
            // Проверяем и загружаем недостающие модели
            for (String model : REQUIRED_MODELS) {
                if (isModelInstalled(model, installedModels)) {
                    System.out.println("✅ [ModelInitializationService] Модель " + model + " уже установлена");
                } else {
                    System.out.println("📥 [ModelInitializationService] Загрузка модели " + model + "...");
                    pullModel(model);
                    System.out.println("✅ [ModelInitializationService] Модель " + model + " успешно загружена");
                }
            }
            
            System.out.println("✅ [ModelInitializationService] Все необходимые модели готовы к использованию");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ [ModelInitializationService] Прервано ожидание Ollama");
        } catch (Exception e) {
            System.err.println("❌ [ModelInitializationService] Ошибка при инициализации моделей: " + e.getMessage());
            e.printStackTrace();
            // Не прерываем запуск приложения, возможно модели будут загружены вручную
        }
    }
    
    /**
     * Получает список установленных моделей из Ollama
     */
    private List<String> getInstalledModels() throws IOException {
        Request request = new Request.Builder()
                .url(ollamaBaseUrl + "/api/tags")
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Не удалось получить список моделей: " + response.code());
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            List<String> models = new ArrayList<>();
            if (jsonResponse.has("models")) {
                JsonArray modelsArray = jsonResponse.getAsJsonArray("models");
                for (int i = 0; i < modelsArray.size(); i++) {
                    JsonObject model = modelsArray.get(i).getAsJsonObject();
                    if (model.has("name")) {
                        String name = model.get("name").getAsString();
                        models.add(name);
                    }
                }
            }
            
            return models;
        }
    }
    
    /**
     * Проверяет, установлена ли модель
     */
    private boolean isModelInstalled(String modelName, List<String> installedModels) {
        // Проверяем точное совпадение или совпадение с тегом
        for (String installed : installedModels) {
            if (installed.equals(modelName) || installed.startsWith(modelName + ":")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Загружает модель через Ollama API
     * Ollama API для pull возвращает streaming ответ с прогрессом
     */
    private void pullModel(String modelName) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", modelName);
        
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url(ollamaBaseUrl + "/api/pull")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("Не удалось загрузить модель " + modelName + ": " + response.code() + " - " + errorBody);
            }
            
            // Читаем streaming ответ (Ollama отправляет прогресс построчно в JSON)
            if (response.body() != null) {
                String responseBody = response.body().string();
                // Проверяем последнюю строку ответа (обычно содержит статус "success")
                String[] lines = responseBody.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        JsonObject jsonLine = gson.fromJson(line, JsonObject.class);
                        if (jsonLine.has("status")) {
                            String status = jsonLine.get("status").getAsString();
                            if ("success".equals(status)) {
                                return; // Модель успешно загружена
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки парсинга отдельных строк
                    }
                }
            }
            
            // Если не получили явного подтверждения, проверяем наличие модели в списке
            // Это нужно, так как streaming может не вернуть финальный статус
            Thread.sleep(2000); // Даём время на завершение загрузки
            List<String> models = getInstalledModels();
            if (isModelInstalled(modelName, models)) {
                return; // Модель появилась в списке
            }
            
            throw new IOException("Модель " + modelName + " не была загружена (проверка после запроса)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Прервано ожидание загрузки модели " + modelName, e);
        }
    }
}

