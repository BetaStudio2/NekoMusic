package com.neko.music.service;

import com.neko.music.util.RequestScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用户与管理员会话 Token 仅存 Redis，依赖 TTL 自动过期，并各带一份「按账号索引的 SET」
 * （{@code user_token_index:<id>} / {@code admin_token_index:<id>}）用于主动吊销：
 * 登出、改密、删号、重新登录轮换都需要能一次性删掉某账号的全部会话，而不只是等 TTL。
 */
public class RedisTokenStore {
    private static final Logger logger = LoggerFactory.getLogger(RedisTokenStore.class);

    public static final String USER_TOKEN_PREFIX = "user_token:";
    /** 某用户当前全部有效 token（SET），与单 token 键同 TTL 续期 */
    public static final String USER_TOKEN_INDEX_PREFIX = "user_token_index:";
    public static final String ADMIN_TOKEN_PREFIX = "admin_token:";
    /** 某管理员当前全部有效 token（SET），与单 token 键同 TTL */
    public static final String ADMIN_TOKEN_INDEX_PREFIX = "admin_token_index:";

    /** {@link com.neko.music.util.RequestScope} 键前缀：一次请求内同一个令牌只解析一次。 */
    private static final String USER_TOKEN_SCOPE_PREFIX = "userTokenUserId:";
    private static final String ADMIN_TOKEN_SCOPE_PREFIX = "adminTokenId:";
    /** 本次请求是否已经对某个令牌做过「临近过期续期」判定（只读解析不算）。 */
    private static final String RENEW_SCOPE_PREFIX = "userTokenRenewed:";

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
        String normalized = normalizeBearerToken(token);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        // 同一个请求里访问日志与处理器都要解析同一个令牌：按令牌缓存一次，避免重复一次 Redis 往返。
        // 返回 null 表示「令牌无效」，同样会被记住（否定结果也值得缓存）。
        Integer userId = RequestScope.computeIfAbsent(USER_TOKEN_SCOPE_PREFIX + normalized,
                () -> lookupUserId(normalized));
        return Optional.ofNullable(userId);
    }

    private Integer lookupUserId(String token) {
        try {
            String cached = redisService.get(USER_TOKEN_PREFIX + token);
            if (cached != null && !cached.isEmpty()) {
                return Integer.parseInt(cached);
            }
        } catch (NumberFormatException e) {
            logger.warn("Redis 用户 token 值非法，已删除: {}", e.getMessage());
            deleteUserToken(token);
        } catch (Exception e) {
            logger.error("读取用户 token 失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 一次往返完成「校验令牌 + 临近过期时续期」。
     *
     * <p>原来认证路径是 {@code GET} 再 {@code TTL}（续期时还要 {@code EXPIRE} + {@code SADD} +
     * {@code TTL} + {@code EXPIRE}），每个已登录请求固定多一次 Redis 往返。这里合并成一条 Lua：
     * 取值与 TTL 在同一原子上下文里读，需要续期时顺带续期 token 与「按账号索引」的 SET。</p>
     *
     * <p>语义与分步实现一致：TTL 为 -1（无过期）或大于续期窗口时不动；续期时把 token 的 TTL
     * 延长 {@code extensionSeconds}，索引 SET 的 TTL 跟随（不短于新的 token TTL）。</p>
     *
     * @return 令牌有效时返回用户 ID；无效 / 存储故障返回空
     */
    public Optional<Integer> validateAndRenew(String token, long renewWindowSeconds, long extensionSeconds) {
        String normalized = normalizeBearerToken(token);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String scopeKey = USER_TOKEN_SCOPE_PREFIX + normalized;
        Integer cached = RequestScope.get(scopeKey);
        // 只有「本次请求已经做过续期判定」才能直接返回缓存：只读解析（访问日志用的
        // findUserIdByToken）也会缓存 ID，但它刻意不续期，不能因此跳过这里的续期。
        if (cached != null && Boolean.TRUE.equals(RequestScope.get(RENEW_SCOPE_PREFIX + normalized))) {
            return Optional.of(cached);
        }
        if (renewWindowSeconds <= 0 || extensionSeconds <= 0) {
            Optional<Integer> userId = getUserIdByToken(normalized);
            userId.ifPresent(id -> RequestScope.put(scopeKey, id));
            RequestScope.put(RENEW_SCOPE_PREFIX + normalized, Boolean.TRUE);
            return userId;
        }
        List<Object> raw;
        try {
            raw = redisService.evalMulti(VALIDATE_RENEW_SCRIPT,
                    new String[] { USER_TOKEN_PREFIX + normalized },
                    new String[] {
                            String.valueOf(renewWindowSeconds),
                            String.valueOf(extensionSeconds),
                            USER_TOKEN_INDEX_PREFIX,
                            normalized });
        } catch (Exception e) {
            logger.error("校验用户 token 失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
        if (raw == null || raw.size() < 2) {
            logger.error("校验用户 token 失败（Redis 无响应）");
            return Optional.empty();
        }
        long userId = raw.get(0) instanceof Number number ? number.longValue() : 0L;
        if (userId <= 0) {
            return Optional.empty();
        }
        if (raw.get(1) instanceof Number renewed && renewed.longValue() == 1L) {
            logger.info("用户 token 临近过期，已延期 userId={}", userId);
        }
        RequestScope.put(scopeKey, (int) userId);
        RequestScope.put(RENEW_SCOPE_PREFIX + normalized, Boolean.TRUE);
        return Optional.of((int) userId);
    }

    /**
     * 「校验 + 可选续期」：KEYS[1]=token 键；ARGV[1]=续期窗口秒数，ARGV[2]=延长秒数，
     * ARGV[3]=索引键前缀，ARGV[4]=令牌原文。返回 {@code {userId, 是否续期}}。
     */
    private static final String VALIDATE_RENEW_SCRIPT =
            "local v = redis.call('GET', KEYS[1]) "
                    + "if not v then return {0, 0} end "
                    + "local uid = tonumber(v) "
                    + "if not uid then return {0, 0} end "
                    + "local ttl = redis.call('TTL', KEYS[1]) "
                    + "if ttl <= 0 or ttl > tonumber(ARGV[1]) then return {uid, 0} end "
                    + "local renewed = ttl + tonumber(ARGV[2]) "
                    + "redis.call('EXPIRE', KEYS[1], renewed) "
                    + "local index = ARGV[3] .. v "
                    + "redis.call('SADD', index, ARGV[4]) "
                    + "local indexTtl = redis.call('TTL', index) "
                    + "if indexTtl < 0 or indexTtl < renewed then redis.call('EXPIRE', index, renewed) end "
                    + "return {uid, 1}";

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
        String indexKey = adminTokenIndexKey(adminId);
        redisService.sadd(indexKey, token);
        redisService.expire(indexKey, ttlSeconds);
    }

    public Optional<Integer> getAdminIdByToken(String token) {
        String normalized = normalizeBearerToken(token);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        // 同用户令牌：一次请求内只解析一次（访问日志 + 各管理端处理器共用）
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
        token = normalizeBearerToken(token);
        if (token.isEmpty()) {
            return false;
        }
        if (!redisService.exists(ADMIN_TOKEN_PREFIX + token)) {
            return false;
        }
        Optional<Integer> adminId = getAdminIdByToken(token);
        redisService.del(ADMIN_TOKEN_PREFIX + token);
        if (adminId.isPresent()) {
            redisService.srem(adminTokenIndexKey(adminId.get()), token);
        }
        return true;
    }

    /**
     * 注销某管理员的全部会话 token（重新登录轮换、改密、删除管理员后调用）。
     *
     * @return 删除的 token 数量
     */
    public int revokeAllAdminTokens(int adminId) {
        if (adminId <= 0) {
            return 0;
        }
        String indexKey = adminTokenIndexKey(adminId);
        Set<String> tokens = redisService.smembers(indexKey);
        int count = 0;
        for (String t : tokens) {
            if (t == null || t.isEmpty()) {
                continue;
            }
            redisService.del(ADMIN_TOKEN_PREFIX + t);
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
