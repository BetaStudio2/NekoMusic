package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 非 VIP 用户每日视频生成次数限制（Redis 计数，按东八区自然日）。
 */
public class VideoRenderQuotaService {
    private static final Logger logger = LoggerFactory.getLogger(VideoRenderQuotaService.class);
    private static final ZoneId CN = ZoneId.of("Asia/Shanghai");

    private static final String RESERVE_SCRIPT = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[2])
            if current >= limit then
              return -1
            end
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return limit - n
            """;

    private final ConfigManager configManager;
    private final RedisService redisService;

    public VideoRenderQuotaService(ConfigManager configManager, RedisService redisService) {
        this.configManager = configManager;
        this.redisService = redisService;
    }

    public int getTodayUsage(int userId) {
        String raw = redisService.get(dailyKey(userId));
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 预占一次额度。成功返回剩余次数；额度用尽返回 -1。
     */
    public int reserveDailySlot(int userId) {
        int limit = configManager.getVideoRenderNonVipDailyLimit();
        int ttl = secondsUntilMidnightCn();
        Object result = redisService.eval(
                RESERVE_SCRIPT,
                new String[]{dailyKey(userId)},
                new String[]{String.valueOf(ttl), String.valueOf(limit)});
        if (result == null) {
            logger.warn("Redis 配额脚本失败，拒绝非 VIP 视频任务 userId={}", userId);
            return -1;
        }
        return ((Number) result).intValue();
    }

    public int remainingToday(int userId) {
        int limit = configManager.getVideoRenderNonVipDailyLimit();
        return Math.max(0, limit - getTodayUsage(userId));
    }

    private static String dailyKey(int userId) {
        String day = LocalDate.now(CN).toString().replace("-", "");
        return "video_render:daily:" + userId + ":" + day;
    }

    static int secondsUntilMidnightCn() {
        ZonedDateTime now = ZonedDateTime.now(CN);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(CN);
        return (int) Math.max(60, Duration.between(now, midnight).getSeconds());
    }
}
