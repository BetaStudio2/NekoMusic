package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 验证码发信限流：同邮箱在冷却期内不可重复请求发码。
 */
public class VerificationCodeRateLimitService {
    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeRateLimitService.class);

    private static final String EMAIL_COOLDOWN_PREFIX = "verify_code:cooldown:email:";

    private final ConfigManager configManager;
    private final RedisService redisService;

    public VerificationCodeRateLimitService(ConfigManager configManager, RedisService redisService) {
        this.configManager = configManager;
        this.redisService = redisService;
    }

    /**
     * 尝试占用发码冷却槽位（SET NX）。
     * @return empty 表示可继续发信；非 empty 为需等待的秒数
     */
    public Optional<Long> tryAcquireSendSlot(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        int cooldownSec = configManager.getVerificationCodeEmailCooldownSeconds();
        String key = EMAIL_COOLDOWN_PREFIX + normalized;

        if (redisService.setIfAbsentWithExpiry(key, "1", cooldownSec)) {
            return Optional.empty();
        }

        long retryAfter = redisService.ttl(key);
        if (retryAfter <= 0) {
            retryAfter = cooldownSec;
        }
        logger.warn("验证码发信冷却中: email={}", normalized);
        return Optional.of(Math.max(1, retryAfter));
    }

    /** 发信失败时释放冷却，允许用户立即重试 */
    public void releaseSendSlot(String email) {
        String normalized = normalizeEmail(email);
        if (!normalized.isEmpty()) {
            redisService.del(EMAIL_COOLDOWN_PREFIX + normalized);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }
}
