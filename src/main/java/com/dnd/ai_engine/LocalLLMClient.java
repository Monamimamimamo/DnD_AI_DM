package com.dnd.ai_engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import okhttp3.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Клиент для работы с локальными языковыми моделями через Ollama
 */
public class LocalLLMClient {
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    private static final Gson gson = new GsonBuilder().setLenient().create();
    private final OkHttpClient httpClient;
    private final LocalLLMConfig config;
    private final String ollamaBaseUrl;

    public LocalLLMClient(LocalLLMConfig config) {
        this(config, getOllamaBaseUrlFromEnv());
    }
    
    public LocalLLMClient(LocalLLMConfig config, String ollamaBaseUrl) {
        this.config = config;
        this.ollamaBaseUrl = ollamaBaseUrl != null ? ollamaBaseUrl : DEFAULT_OLLAMA_BASE_URL;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(600, TimeUnit.SECONDS) 
            .build();
        initializeModel();
    }
    
    private static String getOllamaBaseUrlFromEnv() {
        String url = System.getenv("OLLAMA_BASE_URL");
        if (url == null || url.isEmpty()) {
            url = System.getProperty("ollama.base.url");
        }
        return url;
    }

    private void initializeModel() {
            try {
                // Проверяем доступные модели
                Request request = new Request.Builder()
                    .url(ollamaBaseUrl + "/api/tags")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject obj = parseJsonLenient(json);
                    // Проверяем наличие модели
                    System.out.println("✅ Ollama модель " + config.getModelName() + " готова к использованию");
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Ошибка инициализации Ollama: " + e.getMessage());
            throw new RuntimeException("Ollama недоступен. Убедитесь, что Ollama запущен.", e);
        }
    }

    public String generateResponse(List<Map<String, String>> messages, String systemPrompt) {
        try {
            // Формируем промпт
            StringBuilder promptBuilder = new StringBuilder();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                promptBuilder.append("System: ").append(systemPrompt).append("\n\n");
            }
            
            for (Map<String, String> message : messages) {
                String role = message.getOrDefault("role", "user");
                String content = message.getOrDefault("content", "");
                promptBuilder.append(role.substring(0, 1).toUpperCase())
                           .append(role.substring(1))
                           .append(": ")
                           .append(content)
                           .append("\n\n");
            }
            promptBuilder.append("Assistant:");
            
            // Отправляем запрос в Ollama
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", config.getModelName());
            requestBody.addProperty("prompt", promptBuilder.toString());
            requestBody.addProperty("stream", false); // Отключаем streaming для получения полного ответа
            
            JsonObject options = new JsonObject();
            options.addProperty("temperature", config.getTemperature());
            options.addProperty("num_predict", config.getMaxTokens());
            requestBody.add("options", options);
            
            RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
            );
            
                Request request = new Request.Builder()
                    .url(ollamaBaseUrl + "/api/generate")
                    .post(body)
                    .build();
            
            long requestStartTime = System.currentTimeMillis();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    
                    // Логируем полный ответ от Ollama для отладки
                    if (json.length() < 100) {
                        System.out.println("⚠️ Короткий ответ от Ollama: " + json);
                    }
                    
                    long requestTime = System.currentTimeMillis() - requestStartTime;
                    JsonObject obj = parseJsonLenient(json);
                    if (obj.has("response")) {
                        String llmResponse = obj.get("response").getAsString().trim();
                        if (llmResponse.isEmpty()) {
                            throw new RuntimeException("LLM вернул пустой ответ. Полный ответ от Ollama: " + json);
                        }
                        if (llmResponse.length() < 10) {
                            System.err.println("⚠️ Подозрительно короткий ответ от LLM: '" + llmResponse + "'. Полный ответ от Ollama: " + json);
                        }
                        System.out.println("📊 Запрос к Ollama (" + config.getModelName() + ") выполнен за " + (requestTime / 1000.0) + " сек, токенов: ~" + llmResponse.length() / 4);
                        return llmResponse;
                    } else {
                        throw new RuntimeException("Ответ от Ollama не содержит поле 'response'. Полный ответ: " + json);
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "нет тела ответа";
                    throw new RuntimeException("Ошибка HTTP запроса к Ollama: " + response.code() + " " + response.message() + ". Тело: " + errorBody);
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("⏱️ Таймаут при генерации ответа: " + e.getMessage());
            throw new RuntimeException("Таймаут при генерации ответа от Ollama. Попробуйте увеличить таймауты или использовать более быструю модель.", e);
        } catch (IOException e) {
            System.err.println("❌ Ошибка при генерации ответа: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Ошибка при генерации ответа от Ollama: " + e.getMessage(), e);
        }
    }

    private JsonObject parseJsonLenient(String json) {
        try {
            // Пытаемся парсить как обычно
            return gson.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            // Если не получилось, пробуем с lenient режимом через JsonReader
            try {
                JsonReader reader = new JsonReader(new StringReader(json));
                reader.setLenient(true);
                return gson.fromJson(reader, JsonObject.class);
            } catch (Exception e2) {
                // Если и это не помогло, пытаемся извлечь JSON из текста
                int startIdx = json.indexOf('{');
                int endIdx = json.lastIndexOf('}');
                if (startIdx >= 0 && endIdx > startIdx) {
                    String extracted = json.substring(startIdx, endIdx + 1);
                    JsonReader reader = new JsonReader(new StringReader(extracted));
                    reader.setLenient(true);
                    return gson.fromJson(reader, JsonObject.class);
                }
                throw new RuntimeException("Не удалось распарсить JSON: " + e2.getMessage(), e2);
            }
        }
    }

    public static class LocalLLMConfig {
        private String modelName = "mistral:7b";
        private double temperature = 0.7;
        private int maxTokens = 1000;

        public LocalLLMConfig() {
        }

        public LocalLLMConfig(String modelName, double temperature, int maxTokens) {
            this.modelName = modelName;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
        }

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }
    
    public LocalLLMConfig getConfig() {
        return config;
    }
}

