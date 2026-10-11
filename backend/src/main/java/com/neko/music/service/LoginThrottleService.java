package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

/**
 * 登录失败限流与锁定的通用实现：按「提交的账号」与「来源 IP」两个维度对**每一次登录尝试**计数，
 * 任一维度超过阈值即拒绝该次尝试并给出等待秒数（CWE-307）。
 *
 * <p>本类只实现通用机制，键前缀与阈值由子类（{@link AdminLoginThrottleService}、
 * {@link UserLoginThrottleService}）给定：管理端与用户端因此共用同一套经过验证的原子计数逻辑，
 * 各自的阈值仍可独立调整，也不必复制两份存储脚本。</p>
 *
 * <p>计数只看请求本身、与账号是否存在无关：不存在的账号同样会被累计与锁定，因此「是否被限流」
 * 不会反过来变成账号存在性信号。</p>
 *
 * <p><b>为什么是「先自增再判定」</b>：若先读计数判断是否超限、等口令校验失败后再自增
 * （load-then-increment），并发突发时所有请求都会读到「未超限」而全部放行，阈值形同虚设
 * （TOCTOU 竞态，CWE-362）。这里把「检查」与「计数」合并为一次原子自增（Redis {@code INCR}），
 * 每个请求拿到互不相同的计数，超过阈值的那些请求在口令校验之前就被拒掉——即便同一瞬间涌入任意多
 * 请求，实际进入口令校验的也不会超过阈值。</p>
 *
 * <p><b>为什么计数键要大小写归一</b>：账号查找用的是数据库的大小写不敏感比较（MySQL 默认
 * {@code *_ci} 排序规则），{@code Admin} 与 {@code admin} 是同一个账号；计数键若原样区分大小写，
 * 攻击者就能用 {@code 2^len} 个大小写变体各开一个空桶、成倍放大尝试配额（CWE-178）。键与认证判定
 * 口径必须一致。</p>
 *
 * <p>存储不可用时放行（只记 WARN）：限流不可用不应把登录整条链路打死。</p>
 */
public class LoginThrottleService {

    private static final Logger logger = LoggerFactory.getLogger(LoginThrottleService.class);

    private final Store store;
    private final String keyPrefix;
    private final int maxFailuresPerAccount;
    private final int maxFailuresPerSource;
    private final int windowSeconds;
    private final int fallbackLockSeconds;

    protected LoginThrottleService(Store store, String keyPrefix,
                                   int maxFailuresPerAccount, int maxFailuresPerSource,
                                   int windowSeconds, int fallbackLockSeconds) {
        this.store = store;
        this.keyPrefix = keyPrefix;
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.maxFailuresPerSource = maxFailuresPerSource;
        this.windowSeconds = windowSeconds;
        this.fallbackLockSeconds = fallbackLockSeconds;
    }

    /**
     * 记为一次登录尝试（**必须在口令校验之前调用**），原子自增两个维度的计数。
     *
     * @return 非空表示本次尝试应立即拒绝，值为建议等待秒数；空表示放行，可继续校验口令。
     */
    public Optional<Long> beginAttempt(String account, String sourceIp) {
        String accountFailureKey = accountKey(account);
        long byAccount = store.incrementInWindow(accountFailureKey, windowSeconds);
        if (byAccount > maxFailuresPerAccount) {
            return Optional.of(cooldown(accountFailureKey, "账号", byAccount));
        }

        String sourceFailureKey = sourceKey(sourceIp);
        long bySource = store.incrementInWindow(sourceFailureKey, windowSeconds);
        if (bySource > maxFailuresPerSource) {
            return Optional.of(cooldown(sourceFailureKey, "来源", bySource));
        }

        return Optional.empty();
    }

    /**
     * 登录成功：清掉该账号的失败计数，并退回本次成功尝试占用的来源计数格。
     *
     * <p>来源维度只退回一格而不是清零：成功登录本来就不该被算作失败，但也别让正常用户反复登录
     * 之外的任何东西被洗白——清零会让「有可用账号的人」把爆破来源的计数整段抹掉。</p>
     */
    public void recordSuccess(String account, String sourceIp) {
        store.delete(accountKey(account));
        store.release(sourceKey(sourceIp));
    }

    /** 取冷却剩余秒数（计数键 TTL），取不到时兜底 {@link #fallbackLockSeconds}。 */
    private long cooldown(String key, String dimension, long count) {
        long ttl = store.ttlSeconds(key);
        long remaining = ttl > 0 ? ttl : fallbackLockSeconds;
        logger.warn("登录失败次数过多，拒绝该{}的尝试: 窗口内已 {} 次", dimension, count);
        return remaining;
    }

    private String accountKey(String account) {
        return keyPrefix + "user:" + accountKeyPart(account);
    }

    private String sourceKey(String sourceIp) {
        return keyPrefix + "ip:" + sourceKeyPart(sourceIp);
    }

    /** 账号键片段：去空白 + 转小写，与数据库的大小写不敏感账号比较口径一致。 */
    private static String accountKeyPart(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }

    /** 来源 IP 键片段：去空白 + 转小写（IPv6 十六进制大小写无关）。 */
    private static String sourceKeyPart(String sourceIp) {
        return sourceIp == null ? "" : sourceIp.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 计数与锁定的存储抽象：生产实现是 Redis（{@link RedisStore}），测试注入内存实现，
     * 从而让单元测试完全不依赖真实 Redis。
     */
    public interface Store {

        /** 读剩余 TTL（秒）；无键 / 已过期 / 存储不可用均返回 {@code 0}。 */
        long ttlSeconds(String key);

        /** 窗口内原子自增：首次写入时设置过期，返回自增后的值；存储不可用返回 {@code -1}。 */
        long incrementInWindow(String key, int windowSeconds);

        /** 原子退回一格（成功尝试不该占用失败配额）；计数降到 0 即删除；存储不可用无副作用。 */
        void release(String key);

        /** 删除键。 */
        void delete(String key);
    }

    /** 生产实现：计数走单条 Lua（自增 + 首次设过期），成功时同样走 Lua 退回（降到 0 即删）。 */
    public static final class RedisStore implements Store {

        private static final String INCREMENT_SCRIPT =
                "local count = redis.call('INCR', KEYS[1]) "
                        + "if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end "
                        + "return count";

        private static final String RELEASE_SCRIPT =
                "if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end "
                        + "local value = redis.call('DECR', KEYS[1]) "
                        + "if value <= 0 then redis.call('DEL', KEYS[1]) return 0 end "
                        + "return value";

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
            logger.warn("登录限流计数不可用（Redis 无响应），本次放行: key={}", key);
            return -1;
        }

        @Override
        public void release(String key) {
            if (redisService != null) {
                redisService.eval(RELEASE_SCRIPT, new String[]{key}, new String[0]);
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
