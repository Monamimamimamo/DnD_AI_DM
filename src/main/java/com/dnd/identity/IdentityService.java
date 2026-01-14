package com.dnd.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сервис для управления пользователями и аутентификацией
 */
@Service
public class IdentityService {
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Регистрация нового пользователя
     */
    @Transactional
    public User register(String username, String email, String password) {
        // Проверяем, что пользователь с таким username не существует
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
        }
        
        // Проверяем, что пользователь с таким email не существует
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        
        // Валидация
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя пользователя не может быть пустым");
        }
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            throw new IllegalArgumentException("Некорректный email");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Пароль должен содержать минимум 6 символов");
        }
        
        // Хешируем пароль
        String passwordHash = PasswordUtil.hashPassword(password);
        
        // Создаем пользователя
        User user = new User(username, email, passwordHash);
        user = userRepository.save(user);
        
        return user;
    }
    
    /**
     * Аутентификация пользователя
     */
    @Transactional
    public String authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Неверное имя пользователя или пароль"));
        
        if (!PasswordUtil.checkPassword(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Неверное имя пользователя или пароль");
        }
        
        // Обновляем время последнего входа
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        // Генерируем JWT токен
        return JwtUtil.generateToken(user.getId(), user.getUsername());
    }
    
    /**
     * Получить пользователя по ID
     */
    public User getUserById(String userId) {
        return userRepository.findById(userId)
            .orElse(null);
    }
    
    /**
     * Получить пользователя по username
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElse(null);
    }
    
    /**
     * Валидация JWT токена и получение пользователя
     */
    public User validateTokenAndGetUser(String token) {
        if (!JwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("Невалидный токен");
        }
        
        String userId = JwtUtil.extractUserId(token);
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }
    
    /**
     * Добавить персонажа к пользователю
     */
    @Transactional
    public void addCharacterToUser(String userId, String characterId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        
        user.addCharacterId(characterId);
        userRepository.save(user);
    }
    
    /**
     * Добавить кампанию к пользователю
     */
    @Transactional
    public void addCampaignToUser(String userId, String campaignId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        
        user.addCampaignId(campaignId);
        userRepository.save(user);
    }
}

