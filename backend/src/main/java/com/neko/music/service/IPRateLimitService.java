package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * IP 频率限制服务
 * 使用 Redis Lua 脚本实现原子计数+检查+封锁，一次往返完成。
 * 计数与封锁键按 IPv4 的 /24 网段聚合（a.b.c.* → a.b.c.0），兼顾 NAT 用户与误伤范围。
 */
public class IPRateLimitService {
    private static final Logger logger = LoggerFactory.getLogger(IPRateLimitService.class);

    private final ConfigManager configManager;
    private final RedisService redisService;

    private static final String IP_REQUEST_COUNT_PREFIX = "ip_req_count:";
    private static final String IP_BLOCKED_PREFIX = "ip_blocked:";

    /**
     * 一次往返完成的全部门禁判定（IP 段封锁 → 来源封禁 → 计数 → 超限则封锁）。
     *
     * <p>为什么要合并：这些判定都在请求关键路径上，分开调用就是每个请求多几次 Redis 往返
     * （本机 Redis 一次约 40µs，跨机则按网络 RTT 计）。合并后动态接口每次请求只需要 1 次。
     * 判定顺序与原来一致：已封锁的段不再计数，被封禁的来源也不消耗配额。</p>
     *
     * <p>KEYS[1]=请求计数 key，KEYS[2]=IP 段封锁 key，KEYS[3]=来源封禁 key；
     * ARGV[1]=窗口秒数，ARGV[2]=窗口内最大请求数，ARGV[3]=封锁时长秒数。
     * 返回 {@code {判定码, Retry-After 秒数}}。</p>
     */
    private static final String GUARD_SCRIPT =
        "local blocked = redis.call('GET', KEYS[2]) " +
        "if blocked then return {1, redis.call('TTL', KEYS[2])} end " +
        "local banned = redis.call('GET', KEYS[3]) " +
        "if banned then return {3, redis.call('TTL', KEYS[3])} end " +
        "local count = redis.call('INCR', KEYS[1]) " +
        "if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end " +
        "if tonumber(count) > tonumber(ARGV[2]) then " +
        "  redis.call('SETEX', KEYS[2], tonumber(ARGV[3]), '1') " +
        "  redis.call('DEL', KEYS[1]) " +
        "  return {2, tonumber(ARGV[3])} " +
        "end " +
        "return {0, 0}";

    /**
     * IP 限流关闭时只做来源封禁判定（同样一次往返）：认证失败封禁与 IP 频率限制是两套独立策略，
     * 前者不能因为后者被关掉就一起失效。
     */
    private static final String SOURCE_BAN_ONLY_SCRIPT =
        "local banned = redis.call('GET', KEYS[1]) " +
        "if banned then return {3, redis.call('TTL', KEYS[1])} end " +
        "return {0, 0}";

    public IPRateLimitService(ConfigManager configManager, RedisService redisService) {
        this.configManager = configManager;
        this.redisService = redisService;
    }

    /**
     * 一次往返的门禁判定结果。
     *
     * @param code               判定码，见 {@link #ALLOWED} 等常量
     * @param retryAfterSeconds  建议的 {@code Retry-After}（秒）；放行时为 0
     */
    public record Decision(int code, long retryAfterSeconds) {

        /** 放行。 */
        public static final int ALLOWED = 0;
        /** 该 IP 段已被限流封锁。 */
        public static final int IP_BLOCKED = 1;
        /** 本次请求超过窗口上限，已被封锁。 */
        public static final int RATE_EXCEEDED = 2;
        /** 该来源因反复认证失败被临时封禁。 */
        public static final int SOURCE_BANNED = 3;
        /** 存储不可用：按可用性优先放行（与各限流的失败策略一致）。 */
        public static final int UNAVAILABLE = -1;

        public boolean allowed() {
            return code == ALLOWED || code == UNAVAILABLE;
        }
    }

    /** 将 IPv4 规范为 /24 代表地址，用作 Redis 键；非点分四段则原样返回。 */
    private String convertToIPSegment(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".0";
            }
        } catch (Exception e) {
            logger.warn("转换 IP 段失败: {}", ip, e);
        }
        return ip;
    }

    /**
     * 请求门禁：IP 频率限制 + 认证失败来源封禁，合并为一次 Redis 往返。
     *
     * <p>IP 限流关闭时退化为「只查来源封禁」，仍是一次往返，因此 {@code SourceBanFilter} 可以直接
     * 复用这里的结果，不必再单独查一次 Redis。</p>
     */
    public Decision evaluate(String ip) {
        if (redisService == null) {
            return new Decision(Decision.UNAVAILABLE, 0);
        }
        String ipSegment = convertToIPSegment(ip);
        try {
            List<Object> raw;
            if (!configManager.isRateLimitEnabled()) {
                raw = redisService.evalMulti(SOURCE_BAN_ONLY_SCRIPT,
                        new String[] { SourceBanService.bannedKey(ip) }, new String[0]);
            } else {
                raw = redisService.evalMulti(GUARD_SCRIPT,
                        new String[] {
                                IP_REQUEST_COUNT_PREFIX + ipSegment,
                                IP_BLOCKED_PREFIX + ipSegment,
                                SourceBanService.bannedKey(ip) },
                        new String[] {
                                String.valueOf(configManager.getRateLimitTimeWindow()),
                                String.valueOf(configManager.getRateLimitMaxRequests()),
                                String.valueOf(configManager.getRateLimitBlockDuration()) });
            }
            if (raw == null || raw.size() < 2) {
                logger.error("限流判定失败（Redis 无响应），放行请求");
                return new Decision(Decision.UNAVAILABLE, 0);
            }
            int code = (int) toLong(raw.get(0));
            long retryAfter = toLong(raw.get(1));
            if (code == Decision.IP_BLOCKED) {
                logger.debug("IP 段已被封锁: {}", ipSegment);
            } else if (code == Decision.RATE_EXCEEDED) {
                logger.warn("IP 段超过频率限制，已被封锁: {} (原始 IP: {})", ipSegment, ip);
            }
            return new Decision(code, Math.max(0, retryAfter));
        } catch (Exception e) {
            logger.error("Rate limit检查失败，放行请求: {}", e.getMessage());
            return new Decision(Decision.UNAVAILABLE, 0);
        }
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 封锁 IP 段
     */

    /**
     * 解除 IP 封锁
     */
    public void unblockIP(String ip) {
        String ipSegment = convertToIPSegment(ip);
        redisService.del(IP_BLOCKED_PREFIX + ipSegment);
        logger.info("IP 段封锁已解除: {} (原始 IP: {})", ipSegment, ip);
    }

    /**
     * 获取 IP 的请求计数
     */
    public int getRequestCount(String ip) {
        String ipSegment = convertToIPSegment(ip);
        String countStr = redisService.get(IP_REQUEST_COUNT_PREFIX + ipSegment);
        if (countStr != null && !countStr.isEmpty()) {
            try {
                return Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 获取当前 IP 段封锁剩余时间（秒），供 429 等响应展示。
     * Redis TTL 正常时返回精确剩余；TTL 为 -1 但键仍存在时退回配置的封锁总时长。
     */
    public long getBlockTimeRemaining(String ip) {
        if (!configManager.isRateLimitEnabled()) {
            return 0;
        }
        String key = IP_BLOCKED_PREFIX + convertToIPSegment(ip);
        long ttl = redisService.ttl(key);
        if (ttl > 0) {
            return ttl;
        }
        if (ttl == -1 && redisService.exists(key)) {
            return configManager.getRateLimitBlockDuration();
        }
        return 0;
    }
}
