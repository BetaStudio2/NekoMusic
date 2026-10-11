package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 认证 / 验证码类接口的来源 IP 请求限流（进程内令牌桶，CWE-799）。
 *
 * <p>背景：登录、注册、发码、滑块这些入口除了「失败计数锁定」之外，还需要一道与账号无关的
 * <b>请求速率</b>闸门。否则脚本可以用**不同的账号 / 邮箱**把请求打满：每个账号都没失败几次，
 * 失败锁定不触发，于是「无限速」依旧成立。</p>
 *
 * <p>与既有设施的分工：{@code IPRateLimitFilter} 按来源网段聚合、阈值全局偏宽，只做粗粒度兜底；
 * 这里按「接口类别 + 精确来源 IP」单独封顶，阈值收紧到正常用户无感、自动化脚本必然触发。</p>
 *
 * <p>阈值是类常量，不新增配置项；桶状态只存进程内并定期回收空闲来源（与
 * {@link ReplayIssueLimiter} 同一套思路），不依赖 Redis，因此在存储故障时仍然生效。</p>
 */
public final class AuthAbuseLimiter {

    private static final Logger logger = LoggerFactory.getLogger(AuthAbuseLimiter.class);

    /** 接口类别：不同入口的合理请求速率不同，各自独立计数。 */
    public enum Scope {
        /** 登录：定向爆破由失败锁定兜底，这里限制「高速尝试」本身。 */
        LOGIN(20.0 / 60.0, 12),
        /** 注册：正常用户一次注册几乎不会连发。 */
        REGISTER(10.0 / 60.0, 6),
        /** 发送邮箱验证码（注册 / 重置）：叠加同邮箱冷却与邮箱槽位。 */
        SEND_CODE(6.0 / 60.0, 4),
        /** 滑块挑战 / 校验：正常用户可能重试几次，阈值放宽但不得无限刷。 */
        CAPTCHA(60.0 / 60.0, 20);

        final double permitsPerSecond;
        final int burst;

        Scope(double permitsPerSecond, int burst) {
            this.permitsPerSecond = permitsPerSecond;
            this.burst = burst;
        }
    }

    /** 来源桶空闲多久后回收。 */
    private static final long IP_IDLE_MILLIS = 300_000L;

    /** 最多同时跟踪的来源桶数；超出后拒绝新来源（保护内存优先于可用性）。 */
    private static final int MAX_TRACKED_IPS = 100_000;

    private static final Map<String, Bucket> BUCKETS = new ConcurrentHashMap<>();

    private static volatile LongSupplier clock = System::currentTimeMillis;

    private AuthAbuseLimiter() {
    }

    /**
     * 消耗一个令牌。
     *
     * @return empty 表示放行；非 empty 为建议等待秒数（{@code Retry-After}）
     */
    public static Optional<Long> tryAcquire(Scope scope, String clientIp) {
        long now = clock.getAsLong();
        String key = scope.name() + '|' + normalizeIp(clientIp);

        Bucket bucket = BUCKETS.get(key);
        if (bucket == null) {
            if (BUCKETS.size() >= MAX_TRACKED_IPS) {
                purgeIdle(now);
                if (BUCKETS.size() >= MAX_TRACKED_IPS) {
                    logger.warn("认证限流来源过多，拒绝新来源: tracked={}", BUCKETS.size());
                    return Optional.of(1L);
                }
            }
            bucket = BUCKETS.computeIfAbsent(key, k -> new Bucket(scope.permitsPerSecond, scope.burst));
        }

        double waitSeconds = bucket.tryAcquire(now);
        return waitSeconds > 0 ? Optional.of(Math.max(1, (long) Math.ceil(waitSeconds))) : Optional.empty();
    }

    private static String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank()
                ? "unknown"
                : clientIp.trim().toLowerCase(Locale.ROOT);
    }

    private static void purgeIdle(long now) {
        BUCKETS.entrySet().removeIf(entry -> entry.getValue().idleMillis(now) > IP_IDLE_MILLIS);
    }

    /** 测试用：替换时钟。 */
    public static void useClock(LongSupplier supplier) {
        clock = supplier == null ? System::currentTimeMillis : supplier;
    }

    /** 测试用：清空所有桶。 */
    public static void reset() {
        BUCKETS.clear();
    }

    /** 令牌桶：按毫秒线性补充，最多积攒到容量；额度不足时返回需要等待的秒数。 */
    private static final class Bucket {

        private final double permitsPerSecond;
        private final double capacity;
        private double tokens;
        private long lastRefillMillis;

        Bucket(double permitsPerSecond, double capacity) {
            this.permitsPerSecond = permitsPerSecond;
            this.capacity = capacity;
            this.tokens = capacity;
            // 起始时刻留到首次取令牌时用调用方时钟补齐，避免注入时钟与真实时钟打架。
            this.lastRefillMillis = 0L;
        }

        synchronized double tryAcquire(long nowMillis) {
            refill(nowMillis);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return 0;
            }
            return (1.0 - tokens) / permitsPerSecond;
        }

        synchronized long idleMillis(long nowMillis) {
            return lastRefillMillis == 0L ? 0L : nowMillis - lastRefillMillis;
        }

        private void refill(long nowMillis) {
            if (lastRefillMillis == 0L || nowMillis < lastRefillMillis) {
                lastRefillMillis = nowMillis;
                return;
            }
            long delta = nowMillis - lastRefillMillis;
            if (delta == 0) {
                return;
            }
            tokens = Math.min(capacity, tokens + delta * permitsPerSecond / 1000.0);
            lastRefillMillis = nowMillis;
        }
    }
}
