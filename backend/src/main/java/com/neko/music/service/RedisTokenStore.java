package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 用户与管理员会话 Token 仅存 Redis，依赖 TTL 自动过期。
 */
public class RedisTokenStore {
    private static final Logger logger = LoggerFactory.getLogger(RedisTokenStore.class);

    public static final String USER_TOKEN_PREFIX = "user_token:";
    public static final String ADMIN_TOKEN_PREFIX = "admin_token:";

    private final RedisService redisService;

    public RedisTokenStore(RedisService redisService) {
        this.redisService = redisService;
    }

    public static String normalizeBearerToken(String raw) {
        if (raw == null) {
            return "";
        }
        String token = raw.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        return token;
    }

    public void saveUserToken(String token, int userId, int ttlSeconds) {
        if (token == null || token.isEmpty()) {
            return;
        }
        redisService.setWithExpiry(USER_TOKEN_PREFIX + token, String.valueOf(userId), ttlSeconds);
    }

    public Optional<Integer> getUserIdByToken(String token) {
        token = normalizeBearerToken(token);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            String cached = redisService.get(USER_TOKEN_PREFIX + token);
            if (cached != null && !cached.isEmpty()) {
                return Optional.of(Integer.parseInt(cached));
            }
        } catch (NumberFormatException e) {
            logger.warn("Redis 用户 token 值非法，已删除: {}", e.getMessage());
            redisService.del(USER_TOKEN_PREFIX + token);
        } catch (Exception e) {
            logger.error("读取用户 token 失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public boolean deleteUserToken(String token) {
        token = normalizeBearerToken(token);
        if (token.isEmpty()) {
            return false;
        }
        if (!redisService.exists(USER_TOKEN_PREFIX + token)) {
            return false;
        }
        redisService.del(USER_TOKEN_PREFIX + token);
        return true;
    }

    public void saveAdminToken(String token, int adminId, int ttlSeconds) {
        if (token == null || token.isEmpty()) {
            return;
        }
        redisService.setWithExpiry(ADMIN_TOKEN_PREFIX + token, String.valueOf(adminId), ttlSeconds);
    }

    public Optional<Integer> getAdminIdByToken(String token) {
        token = normalizeBearerToken(token);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            String cached = redisService.get(ADMIN_TOKEN_PREFIX + token);
            if (cached != null && !cached.isEmpty()) {
                return Optional.of(Integer.parseInt(cached));
            }
        } catch (NumberFormatException e) {
            logger.warn("Redis 管理员 token 值非法，已删除: {}", e.getMessage());
            redisService.del(ADMIN_TOKEN_PREFIX + token);
        } catch (Exception e) {
            logger.error("读取管理员 token 失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public boolean deleteAdminToken(String token) {
        token = normalizeBearerToken(token);
        if (token.isEmpty()) {
            return false;
        }
        if (!redisService.exists(ADMIN_TOKEN_PREFIX + token)) {
            return false;
        }
        redisService.del(ADMIN_TOKEN_PREFIX + token);
        return true;
    }
}
