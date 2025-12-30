package com.dnd.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Утилита для работы с JWT токенами
 */
public class JwtUtil {
    private static final String SECRET_KEY = System.getenv("JWT_SECRET") != null ? 
        System.getenv("JWT_SECRET") : "dnd-ai-dungeon-master-secret-key-change-in-production-very-long-secret-key";
    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000; // 7 дней
    
    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Генерирует JWT токен для пользователя
     */
    public static String generateToken(String userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        
        return Jwts.builder()
            .claims(claims)
            .subject(userId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(getSigningKey())
            .compact();
    }
    
    /**
     * Извлекает Claims из токена
     */
    public static Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    /**
     * Извлекает userId из токена
     */
    public static String extractUserId(String token) {
        Claims claims = extractClaims(token);
        return claims.get("userId", String.class);
    }
    
    /**
     * Извлекает username из токена
     */
    public static String extractUsername(String token) {
        Claims claims = extractClaims(token);
        return claims.get("username", String.class);
    }
    
    /**
     * Проверяет валидность токена
     */
    public static boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

