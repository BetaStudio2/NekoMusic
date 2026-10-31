package com.neko.music.service;

import com.neko.music.util.RequestScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/**
 * 管理员会话存储。
 *
 * <p>用户 token 由 NekoAuth 签发与保存，NekoMusic 不再落任何 user_token 键；
 * 管理员仍使用本地独立账号体系，因此保留 Redis 会话及按管理员索引的集合。</p>
 */
public class RedisTokenStore {
    private static final Logger logger = LoggerFactory.getLogger(RedisTokenStore.class);

    public static final String ADMIN_TOKEN_PREFIX = "admin_token:";
    public static final String ADMIN_TOKEN_INDEX_PREFIX = "admin_token_index:";
    private static final String ADMIN_TOKEN_SCOPE_PREFIX = "adminTokenId:";

    private final RedisService redisService;

    public RedisTokenStore(RedisService redisService) {
        this.redisService = redisService;
    }

    public static String normalizeBearerToken(String raw) {
        if (raw == null) {
            return "";
        }
        String token = raw.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            token = token.substring("Bearer ".length()).trim();
        }
        return token;
    }

    public void saveAdminToken(String token, int adminId, int ttlSeconds) {
        if (token == null || token.isEmpty()) {
            return;
        }
        redisService.setWithExpiry(ADMIN_TOKEN_PREFIX + token, String.valueOf(adminId), ttlSeconds);
        String indexKey = adminTokenIndexKey(adminId);
        redisService.sadd(indexKey, token);
        redisService.expire(indexKey, ttlSeconds);
    }

    public Optional<Integer> getAdminIdByToken(String token) {
        String normalized = normalizeBearerToken(token);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        Integer adminId = RequestScope.computeIfAbsent(ADMIN_TOKEN_SCOPE_PREFIX + normalized,
                () -> lookupAdminId(normalized));
        return Optional.ofNullable(adminId);
    }

    private Integer lookupAdminId(String token) {
        try {
            String cached = redisService.get(ADMIN_TOKEN_PREFIX + token);
            if (cached != null && !cached.isEmpty()) {
                return Integer.parseInt(cached);
            }
        } catch (NumberFormatException e) {
            logger.warn("Redis 管理员 token 值非法，已删除: {}", e.getMessage());
            redisService.del(ADMIN_TOKEN_PREFIX + token);
        } catch (Exception e) {
            logger.error("读取管理员 token 失败: {}", e.getMessage(), e);
        }
        return null;
    }

    public boolean deleteAdminToken(String token) {
        String normalized = normalizeBearerToken(token);
        if (normalized.isEmpty()) {
            return false;
        }
        if (!redisService.exists(ADMIN_TOKEN_PREFIX + normalized)) {
            return false;
        }
        Optional<Integer> adminId = getAdminIdByToken(normalized);
        redisService.del(ADMIN_TOKEN_PREFIX + normalized);
        adminId.ifPresent(id -> redisService.srem(adminTokenIndexKey(id), normalized));
        return true;
    }

    public int revokeAllAdminTokens(int adminId) {
        if (adminId <= 0) {
            return 0;
        }
        String indexKey = adminTokenIndexKey(adminId);
        Set<String> tokens = redisService.smembers(indexKey);
        int count = 0;
        for (String token : tokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            redisService.del(ADMIN_TOKEN_PREFIX + token);
            count++;
        }
        redisService.del(indexKey);
        if (count > 0) {
            logger.info("已注销管理员全部会话 token adminId={} count={}", adminId, count);
        }
        return count;
    }

    private static String adminTokenIndexKey(int adminId) {
        return ADMIN_TOKEN_INDEX_PREFIX + adminId;
    }
}
