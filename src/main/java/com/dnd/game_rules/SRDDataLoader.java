package com.dnd.game_rules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;
import java.util.*;

/**
 * Загрузчик данных SRD из 5e-srd-api
 */
public class SRDDataLoader {
    private static final String DEFAULT_SRD_API_BASE = "http://localhost:3000/api";
    private static final Gson gson = new GsonBuilder().setLenient().create();
    private final OkHttpClient httpClient;
    private final String apiUrl;
    private final String version;

    public SRDDataLoader() {
        this("2014");
    }

    public SRDDataLoader(String version) {
        this(version, getSrdApiBaseFromEnv());
    }
    
    public SRDDataLoader(String version, String srdApiBase) {
        this.version = version;
        String baseUrl = srdApiBase != null ? srdApiBase : DEFAULT_SRD_API_BASE;
        this.apiUrl = baseUrl + "/" + version;
        this.httpClient = new OkHttpClient();
    }
    
    private static String getSrdApiBaseFromEnv() {
        String url = System.getenv("SRD_API_URL");
        if (url == null || url.isEmpty()) {
            url = System.getProperty("srd.api.url");
        }
        // Если указан полный URL, используем его; если только хост, добавляем /api
        if (url != null && !url.isEmpty() && !url.endsWith("/api")) {
            if (!url.endsWith("/")) {
                url = url + "/api";
            } else {
                url = url + "api";
            }
        }
        return url;
    }

    public Map<String, String> getAvailableEndpoints() {
        try {
            Request request = new Request.Builder()
                .url(apiUrl)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject obj = gson.fromJson(json, JsonObject.class);
                    
                    Map<String, String> endpoints = new HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        endpoints.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    return endpoints;
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️ Ошибка запроса к API: " + e.getMessage());
        }
        return new HashMap<>();
    }

    public List<Map<String, Object>> loadEndpointData(String endpoint) {
        try {
            Request request = new Request.Builder()
                .url(apiUrl + "/" + endpoint)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonElement element = gson.fromJson(json, JsonElement.class);
                    
                    if (element.isJsonObject() && element.getAsJsonObject().has("results")) {
                        // Коллекция с results
                        JsonObject obj = element.getAsJsonObject();
                        return parseResultsArray(obj.getAsJsonArray("results"));
                    } else if (element.isJsonArray()) {
                        // Прямой массив
                        return parseResultsArray(element.getAsJsonArray());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️ Ошибка загрузки данных (" + endpoint + "): " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public Map<String, List<Map<String, Object>>> loadMultipleEndpoints(List<String> endpoints) {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (String endpoint : endpoints) {
            List<Map<String, Object>> data = loadEndpointData(endpoint);
            
            // Для эндпоинта "skills" загружаем полную информацию о каждом навыке
            if ("skills".equals(endpoint)) {
                data = loadFullSkillData(data);
            }
            
            result.put(endpoint, data);
        }
        return result;
    }
    
    /**
     * Загружает полную информацию о навыках по их URL
     */
    private List<Map<String, Object>> loadFullSkillData(List<Map<String, Object>> skillsList) {
        List<Map<String, Object>> fullSkills = new ArrayList<>();
        
        for (Map<String, Object> skillRef : skillsList) {
            String url = (String) skillRef.get("url");
            if (url != null && !url.isEmpty()) {
                // Загружаем полную информацию о навыке
                Map<String, Object> fullSkill = loadSingleItem(url);
                if (fullSkill != null && !fullSkill.isEmpty()) {
                    fullSkills.add(fullSkill);
                } else {
                    // Если не удалось загрузить, используем краткую информацию
                    System.out.println("⚠️ [SRDDataLoader] Не удалось загрузить полную информацию для навыка: " + 
                                     skillRef.get("name") + " (URL: " + url + ")");
                    fullSkills.add(skillRef);
                }
            } else {
                System.out.println("⚠️ [SRDDataLoader] Навык " + skillRef.get("name") + " не имеет URL");
                fullSkills.add(skillRef);
            }
        }
        return fullSkills;
    }
    
    /**
     * Загружает один элемент по его URL
     */
    private Map<String, Object> loadSingleItem(String url) {
        try {
            // URL может быть относительным (начинаться с /api) или абсолютным
            String fullUrl;
            if (url.startsWith("http")) {
                fullUrl = url;
            } else if (url.startsWith("/api")) {
                // Относительный URL вида /api/2014/skills/acrobatics
                // apiUrl имеет вид: http://localhost:3000/api/2014
                // Нужно получить базовый URL: http://localhost:3000
                // Находим позицию "/api" и берем всё до неё
                int apiIndex = apiUrl.indexOf("/api");
                if (apiIndex > 0) {
                    String baseUrl = apiUrl.substring(0, apiIndex);
                    fullUrl = baseUrl + url;
                } else {
                    // Если не нашли /api в apiUrl (не должно происходить), используем apiUrl как есть
                    fullUrl = apiUrl + url.substring(url.indexOf("/", url.indexOf("/api") + 4));
                }
            } else {
                fullUrl = apiUrl + "/" + url;
            }
            
            Request request = new Request.Builder()
                .url(fullUrl)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonElement element = gson.fromJson(json, JsonElement.class);
                    
                    if (element.isJsonObject()) {
                        Map<String, Object> result = parseJsonObject(element.getAsJsonObject());
                        return result;
                    } else {
                        System.err.println("⚠️ [SRDDataLoader] Ответ не является JSON объектом для URL: " + fullUrl);
                    }
                } else {
                    System.err.println("⚠️ [SRDDataLoader] HTTP ошибка " + response.code() + " для URL: " + fullUrl);
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️ [SRDDataLoader] Ошибка загрузки (" + url + "): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("⚠️ [SRDDataLoader] Неожиданная ошибка при загрузке (" + url + "): " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private List<Map<String, Object>> parseResultsArray(com.google.gson.JsonArray array) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                results.add(parseJsonObject(element.getAsJsonObject()));
            }
        }
        return results;
    }

    private Map<String, Object> parseJsonObject(JsonObject obj) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            map.put(entry.getKey(), parseJsonElement(entry.getValue()));
        }
        return map;
    }

    private Object parseJsonElement(JsonElement element) {
        if (element.isJsonPrimitive()) {
            var prim = element.getAsJsonPrimitive();
            if (prim.isString()) return prim.getAsString();
            if (prim.isNumber()) return prim.getAsNumber();
            if (prim.isBoolean()) return prim.getAsBoolean();
        } else if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement e : element.getAsJsonArray()) {
                list.add(parseJsonElement(e));
            }
            return list;
        } else if (element.isJsonObject()) {
            return parseJsonObject(element.getAsJsonObject());
        }
        return null;
    }

    public Map<String, Integer> getDifficultyTable() {
        // Упрощенная версия - можно улучшить парсинг из rule-sections
        return Map.of(
            "very_easy", 5,
            "easy", 10,
            "medium", 15,
            "hard", 20,
            "very_hard", 25,
            "nearly_impossible", 30
        );
    }
}

