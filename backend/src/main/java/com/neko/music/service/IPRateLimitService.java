package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // Lua脚本：原子地检查封锁状态、递增计数、检查限制、必要时封锁
    // KEYS[1]=请求计数key, KEYS[2]=封锁key
    // ARGV[1]=时间窗口秒数, ARGV[2]=最大请求数, ARGV[3]=封锁时长秒数
    // 返回: 0=允许, 1=已被封锁, 2=超过限制已封锁
    private static final String RATE_LIMIT_SCRIPT =
        "local blocked = redis.call('GET', KEYS[2]) " +
        "if blocked then return 1 end " +
        "local count = redis.call('INCR', KEYS[1]) " +
        "if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end " +
        "if tonumber(count) > tonumber(ARGV[2]) then " +
        "  redis.call('SETEX', KEYS[2], tonumber(ARGV[3]), '1') " +
        "  redis.call('DEL', KEYS[1]) " +
        "  return 2 " +
        "end " +
        "return 0";

    public IPRateLimitService(ConfigManager configManager, RedisService redisService) {
        this.configManager = configManager;
        this.redisService = redisService;
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
     * 检查 IP 是否被封锁
     */
    public boolean isIPBlocked(String ip) {
        if (!configManager.isRateLimitEnabled()) {
            return false;
        }
        String ipSegment = convertToIPSegment(ip);
        return redisService.exists(IP_BLOCKED_PREFIX + ipSegment);
    }

    /**
     * 记录 IP 请求并检查是否超过频率限制（原子操作）
     * @return true 表示允许请求，false 表示超过限制或被封锁
     */
    public boolean recordRequest(String ip) {
        if (!configManager.isRateLimitEnabled()) {
            return true;
        }

        String ipSegment = convertToIPSegment(ip);
        String countKey = IP_REQUEST_COUNT_PREFIX + ipSegment;
        String blockedKey = IP_BLOCKED_PREFIX + ipSegment;

        try {
            Object result = redisService.eval(
                RATE_LIMIT_SCRIPT,
                new String[]{countKey, blockedKey},
                new String[]{
                    String.valueOf(configManager.getRateLimitTimeWindow()),
                    String.valueOf(configManager.getRateLimitMaxRequests()),
                    String.valueOf(configManager.getRateLimitBlockDuration())
                }
            );

            if (result instanceof Number) {
                long code = ((Number) result).longValue();
                if (code == 0) {
                    return true; // 允许
                } else if (code == 1) {
                    logger.debug("IP 段已被封锁: {}", ipSegment);
                    return false;
                } else {
                    logger.warn("IP 段超过频率限制，已被封锁: {} (原始 IP: {})", ipSegment, ip);
                    return false;
                }
            }
            return true; // Lua执行异常时放行
        } catch (Exception e) {
            logger.error("Rate limit检查失败，放行请求: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 封锁 IP 段
     */
    private void blockIP(String ip) {
        String ipSegment = convertToIPSegment(ip);
        String key = IP_BLOCKED_PREFIX + ipSegment;
        redisService.setWithExpiry(key, "1", configManager.getRateLimitBlockDuration());
        String countKey = IP_REQUEST_COUNT_PREFIX + ipSegment;
        redisService.del(countKey);
    }

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
