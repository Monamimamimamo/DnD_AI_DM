package com.dnd.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor для проверки JWT токенов в запросах
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Autowired
    private IdentityService identityService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Пропускаем публичные endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || path.equals("/api/health")) {
            return true;
        }
        
        // Проверяем токен в заголовке Authorization
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Требуется токен авторизации\"}");
            return false;
        }
        
        try {
            String token = authHeader.substring(7);
            User user = identityService.validateTokenAndGetUser(token);
            
            // Сохраняем пользователя в атрибутах запроса для использования в контроллерах
            request.setAttribute("currentUser", user);
            request.setAttribute("userId", user.getId());
            request.setAttribute("username", user.getUsername());
            
            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            return false;
        }
    }
}

