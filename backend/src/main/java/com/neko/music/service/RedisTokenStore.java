package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/**
 * 用户与管理员会话 Token 仅存 Redis，依赖 TTL 自动过期。
 */
public class RedisTokenStore {
    private static final Logger logger = LoggerFactory.getLogger(RedisTokenStore.class);

    public static final String USER_TOKEN_PREFIX = "user_token:";
    /** 某用户当前全部有效 token（SET），与单 token 键同 TTL 续期 */
    public static final String USER_TOKEN_INDEX_PREFIX = "user_token_index:";
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
        String indexKey = userTokenIndexKey(userId);
        redisService.sadd(indexKey, token);
        redisService.expire(indexKey, ttlSeconds);
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
            deleteUserToken(token);
        } catch (Exception e) {
            logger.error("读取用户 token 失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public void extendUserTokenIfExpiring(String token, int userId, long renewWindowSeconds, long extensionSeconds) {
        String normalized = normalizeBearerToken(token);
        if (normalized.isEmpty() || userId <= 0 || renewWindowSeconds <= 0 || extensionSeconds <= 0) {
            return;
        }

        String tokenKey = USER_TOKEN_PREFIX + normalized;
        try {
            long ttl = redisService.ttl(tokenKey);
            if (ttl <= 0 || ttl > renewWindowSeconds) {
                return;
            }

            long renewedTtl = ttl + extensionSeconds;
            redisService.expire(tokenKey, renewedTtl);
            String indexKey = userTokenIndexKey(userId);
            redisService.sadd(indexKey, normalized);
            long indexTtl = redisService.ttl(indexKey);
            if (indexTtl < 0 || indexTtl < renewedTtl) {
                redisService.expire(indexKey, renewedTtl);
            }
            logger.info("用户 token 临近过期，已延期 userId={} ttl={} renewedTtl={}", userId, ttl, renewedTtl);
        } catch (Exception e) {
            logger.error("延期用户 token TTL 失败 userId={}: {}", userId, e.getMessage(), e);
        }
    }

    public boolean deleteUserToken(String token) {
        String normalized = normalizeBearerToken(token);
        if (normalized.isEmpty()) {
            return false;
        }
        Optional<Integer> userId = getUserIdByToken(normalized);
        if (!redisService.exists(USER_TOKEN_PREFIX + normalized)) {
            return false;
        }
        redisService.del(USER_TOKEN_PREFIX + normalized);
        userId.ifPresent(id -> redisService.srem(userTokenIndexKey(id), normalized));
        return true;
    }

    /**
     * 注销某用户的全部会话 token（改密、重置密码后调用）。
     *
     * @return 删除的 token 数量
     */
    public int revokeAllUserTokens(int userId) {
        if (userId <= 0) {
            return 0;
        }
        String indexKey = userTokenIndexKey(userId);
        Set<String> tokens = redisService.smembers(indexKey);
        int count = 0;
        for (String t : tokens) {
            if (t == null || t.isEmpty()) {
                continue;
            }
            redisService.del(USER_TOKEN_PREFIX + t);
            count++;
        }
        redisService.del(indexKey);
        if (count > 0) {
            logger.info("已注销用户全部会话 token userId={} count={}", userId, count);
        }
        return count;
    }

    private static String userTokenIndexKey(int userId) {
        return USER_TOKEN_INDEX_PREFIX + userId;
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
