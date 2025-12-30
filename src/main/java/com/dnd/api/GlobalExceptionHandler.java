package com.dnd.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений для API
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        System.err.println("❌ Глобальная ошибка: " + e.getMessage());
        e.printStackTrace();
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        String errorMessage = e.getMessage();
        if (errorMessage == null) {
            errorMessage = "Неизвестная ошибка: " + e.getClass().getSimpleName();
        }
        error.put("error", errorMessage);
        error.put("type", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header("Content-Type", "application/json;charset=UTF-8")
            .body(error);
    }
}

