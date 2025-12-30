package com.dnd.identity;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Утилита для работы с паролями (хеширование и проверка)
 */
public class PasswordUtil {
    private static final int ROUNDS = 10;
    
    /**
     * Хеширует пароль с использованием BCrypt
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(ROUNDS));
    }
    
    /**
     * Проверяет пароль против хеша
     */
    public static boolean checkPassword(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (Exception e) {
            return false;
        }
    }
}

