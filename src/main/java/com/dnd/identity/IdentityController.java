package com.dnd.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * REST контроллер для Identity Service
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Tag(name = "Identity Service", description = "API для регистрации, аутентификации и управления пользователями")
public class IdentityController {
    
    @Autowired
    private IdentityService identityService;
    
    /**
     * POST /api/auth/register - Регистрация нового пользователя
     */
    @Operation(summary = "Регистрация пользователя", description = "Создает нового пользователя в системе")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Пользователь успешно зарегистрирован"),
        @ApiResponse(responseCode = "400", description = "Неверные данные или пользователь уже существует")
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String email = body.get("email");
            String password = body.get("password");
            
            if (username == null || email == null || password == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Требуются поля: username, email, password");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            User user = identityService.register(username, email, password);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user_id", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("message", "Пользователь успешно зарегистрирован");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Ошибка регистрации: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * POST /api/auth/login - Аутентификация пользователя
     */
    @Operation(summary = "Аутентификация", description = "Аутентифицирует пользователя и возвращает JWT токен")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Успешная аутентификация"),
        @ApiResponse(responseCode = "401", description = "Неверное имя пользователя или пароль")
    })
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            
            if (username == null || password == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Требуются поля: username, password");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            String token = identityService.authenticate(username, password);
            User user = identityService.getUserByUsername(username);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", token);
            response.put("user_id", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("message", "Успешная аутентификация");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Ошибка аутентификации: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * GET /api/auth/me - Получить информацию о текущем пользователе
     */
    @Operation(summary = "Информация о текущем пользователе", description = "Возвращает информацию о аутентифицированном пользователе")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Информация о пользователе"),
        @ApiResponse(responseCode = "401", description = "Требуется токен авторизации")
    })
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Требуется токен авторизации");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
            
            String token = authHeader.substring(7);
            User user = identityService.validateTokenAndGetUser(token);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user_id", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("created_at", user.getCreatedAt());
            response.put("last_login_at", user.getLastLoginAt());
            response.put("character_ids", user.getCharacterIds());
            response.put("campaign_ids", user.getCampaignIds());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Ошибка получения информации о пользователе: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * POST /api/auth/validate - Валидация токена
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> body) {
        try {
            String token = body.get("token");
            
            if (token == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Требуется поле: token");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            User user = identityService.validateTokenAndGetUser(token);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("valid", true);
            response.put("user_id", user.getId());
            response.put("username", user.getUsername());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("valid", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}

