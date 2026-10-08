package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * nonce 签发限额（进程内令牌桶）：给「领取 nonce」这条路封顶，让 Redis 的写放大有明确上限。
 *
 * <p>两道闸门叠加：</p>
 * <ul>
 *   <li><b>单来源</b>：每个来源（IP 指纹）每秒可发起的签发请求数有限，突发额度有限。</li>
 *   <li><b>全站</b>：每秒可签发的 nonce **个数**（读 + 写合计）有限。这条是硬顶——无论攻击者
 *       伪造多少来源、带什么请求头，Redis 每秒承受的写入都不会超过它。</li>
 * </ul>
 *
 * <p>阈值全部是类常量，不新增配置项。桶状态只存进程内并定期回收空闲来源，避免被海量伪造 IP
 * 撑爆内存；回收后仍然超限就直接拒绝新来源（保护内存优先于可用性）。</p>
 */
public final class ReplayIssueLimiter {

    private static final Logger logger = LoggerFactory.getLogger(ReplayIssueLimiter.class);

    /** 单个来源每秒可发起的签发请求数。 */
    static final int PER_IP_REQUESTS_PER_SECOND = 8;

    /** 单个来源的突发额度。 */
    public static final int PER_IP_REQUEST_BURST = 16;

    /** 全站每秒可签发的 nonce 个数（读 + 写合计）。 */
    static final int GLOBAL_NONCES_PER_SECOND = 12_000;

    /** 全站突发额度。 */
    static final int GLOBAL_NONCE_BURST = 24_000;

    /** 来源桶空闲多久后回收。 */
    private static final long IP_IDLE_MILLIS = 120_000L;

    /** 最多同时跟踪的来源数。 */
    private static final int MAX_TRACKED_IPS = 50_000;

    private static final Map<String, Bucket> PER_IP = new ConcurrentHashMap<>();

    private static final Bucket GLOBAL_NONCES =
            new Bucket(GLOBAL_NONCES_PER_SECOND, GLOBAL_NONCE_BURST);

    private static volatile LongSupplier clock = System::currentTimeMillis;

    private ReplayIssueLimiter() {
    }

    /** 单来源请求闸门：每次调用消耗一个令牌。 */
    public static boolean tryAcquireRequest(String clientIp) {
        long now = clock.getAsLong();
        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        Bucket bucket = PER_IP.get(key);
        if (bucket == null) {
            if (PER_IP.size() >= MAX_TRACKED_IPS) {
                purgeIdle(now);
                if (PER_IP.size() >= MAX_TRACKED_IPS) {
                    logger.warn("防重放签发来源过多，拒绝新来源: tracked={}", PER_IP.size());
                    return false;
                }
            }
            bucket = PER_IP.computeIfAbsent(key,
                    k -> new Bucket(PER_IP_REQUESTS_PER_SECOND, PER_IP_REQUEST_BURST));
        }
        return bucket.tryAcquire(1, now);
    }

    /** 全站 nonce 额度闸门：一次申请 {@code count} 个 nonce。 */
    public static boolean tryAcquireNonces(int count) {
        if (count <= 0) {
            return true;
        }
        return GLOBAL_NONCES.tryAcquire(count, clock.getAsLong());
    }

    private static void purgeIdle(long now) {
        PER_IP.entrySet().removeIf(entry -> entry.getValue().idleMillis(now) > IP_IDLE_MILLIS);
    }

    /** 测试用：替换时钟。 */
    public static void useClock(LongSupplier supplier) {
        clock = supplier == null ? System::currentTimeMillis : supplier;
    }

    /** 测试用：清空所有桶。 */
    public static void reset() {
        PER_IP.clear();
        GLOBAL_NONCES.reset();
    }

    /** 令牌桶：按毫秒线性补充，最多积攒到容量。 */
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

        synchronized boolean tryAcquire(double permits, long nowMillis) {
            refill(nowMillis);
            if (tokens + 1e-9 < permits) {
                return false;
            }
            tokens -= permits;
            return true;
        }

        synchronized long idleMillis(long nowMillis) {
            return lastRefillMillis == 0L ? 0L : nowMillis - lastRefillMillis;
        }

        synchronized void reset() {
            tokens = capacity;
            lastRefillMillis = 0L;
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
