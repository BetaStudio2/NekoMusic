package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 管理端登录失败限流与锁定。
 *
 * <p>背景：{@code POST /api/admin/login} 原本没有任何失败限速与锁定，可以无成本地慢速爆破；而
 * 反自动化门禁可被脚本化通过、来源 IP 也可能被伪造头影响，因此不能只靠「IP 维度」兜底
 * （CWE-307）。</p>
 *
 * <p>本服务对**每一次失败尝试**按「提交的用户名」与「来源 IP」两个维度计数，任一维度达到阈值即锁定
 * 一段时间。计数只看请求本身、与账号是否存在无关：不存在的用户名同样会被累计与锁定，因此「是否被
 * 限流」不会反过来变成账号存在性信号。</p>
 *
 * <p>存储不可用时放行（只记 WARN）：限流不可用不应把管理后台登录整条链路打死。</p>
 */
public class AdminLoginThrottleService {

    private static final Logger logger = LoggerFactory.getLogger(AdminLoginThrottleService.class);

    /** 失败计数窗口（秒）。 */
    static final int FAILURE_WINDOW_SECONDS = 900;

    /** 同一用户名在窗口内允许的失败次数；达到即锁定。 */
    public static final int MAX_FAILURES_PER_USERNAME = 10;

    /** 同一来源 IP 在窗口内允许的失败次数；达到即锁定（比用户名阈值宽，兼顾 NAT 后的正常误输）。 */
    public static final int MAX_FAILURES_PER_SOURCE = 20;

    /** 计数与锁定键的保留时长（秒）。 */
    static final int LOCK_SECONDS = 900;

    private static final String USER_FAIL_PREFIX = "admin_login_fail:user:";
    private static final String USER_LOCK_PREFIX = "admin_login_lock:user:";
    private static final String SOURCE_FAIL_PREFIX = "admin_login_fail:ip:";
    private static final String SOURCE_LOCK_PREFIX = "admin_login_lock:ip:";

    private final Store store;

    public AdminLoginThrottleService(Store store) {
        this.store = store;
    }

    /**
     * 计数与锁定的存储抽象：生产实现是 Redis（{@link RedisStore}），测试注入内存实现，
     * 从而让单元测试完全不依赖真实 Redis。
     */
    public interface Store {

        /** 读剩余 TTL（秒）；无键 / 已过期 / 存储不可用均返回 {@code 0}。 */
        long ttlSeconds(String key);

        /** 窗口内自增计数：首次写入时设置过期，返回自增后的值；存储不可用返回 {@code -1}。 */
        long incrementInWindow(String key, int windowSeconds);

        /** 写入带过期的标记。 */
        void put(String key, int ttlSeconds);

        /** 删除键。 */
        void delete(String key);
    }

    /** 该用户名 / 来源是否处于锁定中；非 empty 为建议等待秒数。 */
    public Optional<Long> lockRemaining(String username, String sourceIp) {
        long byUsername = safeTtl(USER_LOCK_PREFIX + key(username));
        long bySource = safeTtl(SOURCE_LOCK_PREFIX + key(sourceIp));
        long remaining = Math.max(byUsername, bySource);
        return remaining > 0 ? Optional.of(remaining) : Optional.empty();
    }

    /**
     * 记录一次失败尝试：用户名与来源 IP 各记一笔，任一维度达到阈值即锁定该维度。
     *
     * <p>用户名维度只按「提交的字符串」计数，不与数据库中是否存在对应账号关联，因此不构成枚举面。</p>
     */
    public void recordFailure(String username, String sourceIp) {
        String userFailKey = USER_FAIL_PREFIX + key(username);
        if (store.incrementInWindow(userFailKey, FAILURE_WINDOW_SECONDS) >= MAX_FAILURES_PER_USERNAME) {
            store.put(USER_LOCK_PREFIX + key(username), LOCK_SECONDS);
            store.delete(userFailKey);
            logger.warn("管理员登录失败次数过多，锁定该用户名: {}", key(username));
        }

        String sourceFailKey = SOURCE_FAIL_PREFIX + key(sourceIp);
        if (store.incrementInWindow(sourceFailKey, FAILURE_WINDOW_SECONDS) >= MAX_FAILURES_PER_SOURCE) {
            store.put(SOURCE_LOCK_PREFIX + key(sourceIp), LOCK_SECONDS);
            store.delete(sourceFailKey);
            logger.warn("管理员登录失败次数过多，锁定该来源: {}", key(sourceIp));
        }
    }

    /**
     * 登录成功：只清掉该用户名的失败计数。刻意不动来源 IP 维度——否则攻击者只要掌握一个可用账号，
     * 就能靠成功登录把爆破来源的计数洗白。
     */
    public void clearFailures(String username) {
        store.delete(USER_FAIL_PREFIX + key(username));
    }

    private long safeTtl(String key) {
        if (key.isEmpty()) {
            return 0;
        }
        return store.ttlSeconds(key);
    }

    /** 键片段：去掉空白；用户名大小写敏感，不做归一化，避免把不同账号并进同一个桶。 */
    private static String key(String value) {
        return value == null ? "" : value.trim();
    }

    /** 生产实现：计数走单条 Lua（自增 + 首次设过期），锁定用带过期的标记键。 */
    public static final class RedisStore implements Store {

        private static final String INCREMENT_SCRIPT =
                "local count = redis.call('INCR', KEYS[1]) "
                        + "if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end "
                        + "return count";

        private final RedisService redisService;

        public RedisStore(RedisService redisService) {
            this.redisService = redisService;
        }

        @Override
        public long ttlSeconds(String key) {
            if (redisService == null) {
                return 0;
            }
            long ttl = redisService.ttl(key);
            return ttl > 0 ? ttl : 0;
        }

        @Override
        public long incrementInWindow(String key, int windowSeconds) {
            if (redisService == null) {
                return -1;
            }
            Object result = redisService.eval(INCREMENT_SCRIPT,
                    new String[]{key}, new String[]{String.valueOf(windowSeconds)});
            if (result instanceof Number number) {
                return number.longValue();
            }
            logger.warn("管理端登录限流计数不可用（Redis 无响应），本次放行: key={}", key);
            return -1;
        }

        @Override
        public void put(String key, int ttlSeconds) {
            if (redisService != null) {
                redisService.setWithExpiry(key, "1", ttlSeconds);
            }
        }

        @Override
        public void delete(String key) {
            if (redisService != null) {
                redisService.del(key);
            }
        }
    }
}
