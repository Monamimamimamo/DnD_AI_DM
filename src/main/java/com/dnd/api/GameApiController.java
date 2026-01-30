package com.dnd.api;

import com.dnd.ai_engine.DungeonMasterAI;
import com.dnd.game_state.Character;
import com.dnd.game_state.CharacterClass;
import com.dnd.game_state.CharacterRace;
import com.dnd.game_state.AbilityScores;
import com.dnd.game_state.GameState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;
import java.util.function.Consumer;

/**
 * REST контроллер для AI Dungeon Master API
 * Все операции привязаны к campaign_id
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@Tag(name = "Game API", description = "API для управления кампаниями, персонажами и игровыми действиями")
@SecurityRequirement(name = "bearerAuth")
public class GameApiController {
    
    private static final Gson gson = new GsonBuilder().setLenient().create();
    
    @Autowired
    private DungeonMasterAI dm;
    
    @Autowired
    private CampaignService campaignService;
    
    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;
    
    /**
     * GET /api/health - Проверка здоровья сервера
     */
    @Operation(summary = "Проверка здоровья сервера", description = "Возвращает статус работы сервера")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Сервер работает")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "ok");
        health.put("service", "AI Dungeon Master");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }
    
    /**
     * POST /api/campaigns - Создать новую кампанию
     * Возвращает campaign_id
     */
    @Operation(summary = "Создать новую кампанию", description = "Создает новую D&D кампанию и возвращает ID и WebSocket URL")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Кампания успешно создана"),
        @ApiResponse(responseCode = "500", description = "Ошибка создания кампании")
    })
    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String sessionId = null;
            
            if (body != null && body.containsKey("campaign_id")) {
                sessionId = (String) body.get("campaign_id");
            }

            // Создаем кампанию без генерации сцены/квеста (только структура)
            Map<String, Object> campaign = campaignService.createCampaign(sessionId);
            String campaignId = (String) campaign.get("session_id");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("campaign_id", campaignId);
            response.put("campaign", campaign);
            response.put("websocket_url", "ws://localhost:8080/ws/campaign/" + campaignId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Ошибка создания кампании: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            // Убеждаемся, что сообщение об ошибке в UTF-8
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = "Неизвестная ошибка при создании кампании";
            }
            error.put("error", errorMessage);
            error.put("exception", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(error);
        }
    }
    
    /**
     * POST /api/campaigns/{campaignId}/characters - Добавить персонажа в кампанию
     */
    @PostMapping("/campaigns/{campaignId}/characters")
    public ResponseEntity<Map<String, Object>> addCharacter(
            @PathVariable String campaignId,
            @RequestBody Map<String, Object> body) {
        try {
            campaignService.ensureCampaignLoaded(campaignId);
            
            if (body == null || !body.containsKey("name")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Требуется поле 'name'");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            Character character = parseCharacterFromJson(body);
            dm.addCharacter(character);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("character", characterToMap(character));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * GET /api/campaigns/{campaignId}/characters/{name} - Получить информацию о персонаже
     */
    @GetMapping("/campaigns/{campaignId}/characters/{name}")
    public ResponseEntity<Map<String, Object>> getCharacter(
            @PathVariable String campaignId,
            @PathVariable String name) {
        try {
            campaignService.ensureCampaignLoaded(campaignId);
            
            var gameStatus = dm.getGameStatus();
            @SuppressWarnings("unchecked")
            var characters = (List<Map<String, Object>>) gameStatus.get("characters");
            
            if (characters != null) {
                for (Map<String, Object> charData : characters) {
                    if (name.equalsIgnoreCase((String) charData.get("name"))) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("character", charData);
                        return ResponseEntity.ok(response);
                    }
                }
            }

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Персонаж не найден: " + name);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * POST /api/campaigns/{campaignId}/actions - Ответное действие на ситуацию
     */
    @PostMapping("/campaigns/{campaignId}/actions")
    public ResponseEntity<Map<String, Object>> processAction(
            @PathVariable String campaignId,
            @RequestBody Map<String, Object> body) {
        try {
            campaignService.ensureCampaignLoaded(campaignId);
            
            if (body == null || !body.containsKey("action") || !body.containsKey("character_name")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Требуются поля 'action' и 'character_name'");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            String action = (String) body.get("action");
            String characterName = (String) body.get("character_name");

            Map<String, Object> result = dm.processAction(action, characterName);
            
            if (result.containsKey("error")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", result.get("error"));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.putAll(result);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * GET /api/campaigns/{campaignId}/status - Получить статус игры
     */
    @GetMapping("/campaigns/{campaignId}/status")
    public ResponseEntity<Map<String, Object>> getGameStatus(@PathVariable String campaignId) {
        try {
            campaignService.ensureCampaignLoaded(campaignId);
            
            Map<String, Object> status = dm.getGameStatus();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", status);
            
            // Добавляем информацию о хосте
            CampaignSession campaignSession = gameWebSocketHandler.getCampaignSession(campaignId);
            if (campaignSession != null) {
                response.put("isStarted", campaignSession.getStatus() == CampaignSession.CampaignStatus.STARTED);
                response.put("isHostConnected", campaignSession.isHostConnected());
            } else {
                response.put("isStarted", false);
                response.put("isHostConnected", false);
            }
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * GET /api/campaigns/{campaignId} - Получить информацию о кампании
     */
    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<Map<String, Object>> getCampaign(@PathVariable String campaignId) {
        try {
            campaignService.ensureCampaignLoaded(campaignId);
            
            Map<String, Object> status = dm.getGameStatus();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("campaign", status);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * GET /api/campaigns/{campaignId}/full - Получить полную информацию о кампании
     * Возвращает все поля: квесты, NPC, локации, события, флаги и т.д.
     */
    @Operation(summary = "Получить полную информацию о кампании", 
               description = "Возвращает всю информацию о кампании: квесты, NPC, локации, события, флаги, персонажи и все остальные поля")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Информация о кампании успешно получена"),
        @ApiResponse(responseCode = "404", description = "Кампания не найдена"),
        @ApiResponse(responseCode = "500", description = "Ошибка получения информации")
    })
    @GetMapping("/campaigns/{campaignId}/full")
    public ResponseEntity<Map<String, Object>> getFullCampaignInfo(@PathVariable String campaignId) {
        try {
            Map<String, Object> fullInfo = campaignService.getFullCampaignInfo(campaignId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.putAll(fullInfo);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения полной информации о кампании: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    private Character parseCharacterFromJson(Map<String, Object> json) {
        String name = (String) json.get("name");
        String className = (String) json.getOrDefault("class", "FIGHTER");
        String raceName = (String) json.getOrDefault("race", "HUMAN");
        int level = json.containsKey("level") ? ((Double) json.get("level")).intValue() : 1;

        CharacterClass charClass = CharacterClass.valueOf(className.toUpperCase());
        CharacterRace race = CharacterRace.valueOf(raceName.toUpperCase());

        AbilityScores abilityScores;
        if (json.containsKey("ability_scores")) {
            Map<String, Object> scores = (Map<String, Object>) json.get("ability_scores");
            abilityScores = new AbilityScores(
                ((Double) scores.getOrDefault("strength", 10)).intValue(),
                ((Double) scores.getOrDefault("dexterity", 10)).intValue(),
                ((Double) scores.getOrDefault("constitution", 10)).intValue(),
                ((Double) scores.getOrDefault("intelligence", 10)).intValue(),
                ((Double) scores.getOrDefault("wisdom", 10)).intValue(),
                ((Double) scores.getOrDefault("charisma", 10)).intValue()
            );
        } else {
            abilityScores = new AbilityScores(10, 10, 10, 10, 10, 10);
        }

        String background = (String) json.getOrDefault("background", "");
        String alignment = (String) json.getOrDefault("alignment", "neutral");

        return new Character(name, charClass, race, level, abilityScores, background, alignment);
    }

    private Map<String, Object> characterToMap(Character character) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", character.getName());
        map.put("class", character.getCharacterClass().toString());
        map.put("race", character.getRace().toString());
        map.put("level", character.getLevel());
        map.put("hit_points", character.getHitPoints());
        map.put("max_hit_points", character.getMaxHitPoints());
        return map;
    }
}

