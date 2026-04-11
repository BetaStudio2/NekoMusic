package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IP频率限制服务
 * 用于管理和检查 IP 段请求频率，防止暴力攻击
 * 按 /16 网段进行限制
 */
public class IPRateLimitService {
    private static final Logger logger = LoggerFactory.getLogger(IPRateLimitService.class);

    private final ConfigManager configManager;
    private final RedisService redisService;

    // Redis key 前缀
    private static final String IP_REQUEST_COUNT_PREFIX = "ip_req_count:";
    private static final String IP_BLOCKED_PREFIX = "ip_blocked:";

    public IPRateLimitService(ConfigManager configManager, RedisService redisService) {
        this.configManager = configManager;
        this.redisService = redisService;
    }

    /**
     * 将 IP 转换为 /16 网段
     * 例如：192.168.1.100 -> 192.168.0.0
     */
    private String convertToIPSegment(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                // 取前两个八位组，后两个设为 0
                return parts[0] + "." + parts[1] + ".0.0";
            }
        } catch (Exception e) {
            logger.warn("转换 IP 段失败: {}", ip, e);
        }
        // 如果转换失败，返回原始 IP
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
        String key = IP_BLOCKED_PREFIX + ipSegment;
        String blocked = redisService.get(key);
        return blocked != null && !blocked.isEmpty();
    }

    /**
     * 记录 IP 请求并检查是否超过频率限制
     * @return true 表示允许请求，false 表示超过限制（IP 段将被封锁）
     */
    public boolean recordRequest(String ip) {
        if (!configManager.isRateLimitEnabled()) {
            return true;
        }

        String ipSegment = convertToIPSegment(ip);

        // 检查是否已经被封锁
        if (isIPBlocked(ip)) {
            logger.debug("IP 段已被封锁: {} (原始 IP: {})", ipSegment, ip);
            return false;
        }

        // 记录请求
        String countKey = IP_REQUEST_COUNT_PREFIX + ipSegment;
        String countStr = redisService.get(countKey);

        int count = 0;
        if (countStr != null && !countStr.isEmpty()) {
            try {
                count = Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                logger.warn("解析 IP 段请求计数失败: {}", countStr);
                count = 0;
            }
        }

        count++;

        // 检查是否超过限制
        int maxRequests = configManager.getRateLimitMaxRequests();
        if (count > maxRequests) {
            // 封锁 IP 段
            blockIP(ip);
            logger.warn("IP 段超过频率限制，已被封锁: {} (原始 IP: {}, 请求次数: {})", ipSegment, ip, count);
            return false;
        }

        // 更新计数，设置过期时间为时间窗口
        redisService.setWithExpiry(countKey, String.valueOf(count), configManager.getRateLimitTimeWindow());

        return true;
    }

    /**
     * 封锁 IP 段
     */
    private void blockIP(String ip) {
        String ipSegment = convertToIPSegment(ip);
        String key = IP_BLOCKED_PREFIX + ipSegment;
        redisService.setWithExpiry(key, "1", configManager.getRateLimitBlockDuration());

        // 清除请求计数
        String countKey = IP_REQUEST_COUNT_PREFIX + ipSegment;
        redisService.del(countKey);
    }

    /**
     * 解除 IP 封锁（管理员功能）
     */
    public void unblockIP(String ip) {
        String ipSegment = convertToIPSegment(ip);
        String key = IP_BLOCKED_PREFIX + ipSegment;
        redisService.del(key);
        logger.info("IP 段封锁已解除: {} (原始 IP: {})", ipSegment, ip);
    }

    /**
     * 获取 IP 的请求计数
     */
    public int getRequestCount(String ip) {
        String ipSegment = convertToIPSegment(ip);
        String countKey = IP_REQUEST_COUNT_PREFIX + ipSegment;
        String countStr = redisService.get(countKey);
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
     * 获取 IP 剩余封锁时间（秒）
     */
    public long getBlockTimeRemaining(String ip) {
        String ipSegment = convertToIPSegment(ip);
        String key = IP_BLOCKED_PREFIX + ipSegment;
        String blocked = redisService.get(key);
        if (blocked != null && !blocked.isEmpty()) {
            // Redis 的 TTL 命令返回剩余生存时间（秒）
            // 这里简化处理，实际可能需要调用 Redis 的 TTL 命令
            // 由于 RedisService 可能没有 TTL 方法，我们返回一个估算值
            return configManager.getRateLimitBlockDuration();
        }
        return 0;
    }
}