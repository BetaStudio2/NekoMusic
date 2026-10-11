package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 验证码发信限流：同邮箱在冷却期内不可重复请求发码，并对来源 IP 做固定窗口计数。
 *
 * <p>邮箱级冷却只拦「同邮箱重复发码」，挡不住换邮箱批量探测；因此对密码重置这类接口，
 * 调用方必须先过来源 IP 限流，且**必须在「邮箱是否注册」之前**完成——否则 {@code 429}
 * 本身就成了账号存在性的旁路信号。</p>
 */
public class VerificationCodeRateLimitService {
    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeRateLimitService.class);

    private static final String EMAIL_COOLDOWN_PREFIX = "verify_code:cooldown:email:";
    private static final String SENDER_IP_COUNT_PREFIX = "verify_code:rate:ip:";

    /** 来源 IP 的发码窗口（秒）与窗口内允许次数：与邮箱是否注册无关。 */
    private static final int IP_WINDOW_SECONDS = 600;
    private static final int IP_MAX_REQUESTS = 10;

    /**
     * 固定窗口自增：{@code 0} 表示放行，负值表示需等待的秒数。
     * 一次往返完成「自增 + 首次设过期 + 判断上限」。
     */
    private static final String IP_WINDOW_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end "
                    + "if tonumber(count) > tonumber(ARGV[2]) then return -redis.call('TTL', KEYS[1]) end "
                    + "return 0";

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

    /**
     * 尝试占用来源 IP 的发码槽位（与邮箱无关）。
     *
     * <p>Redis 故障时放行（可用性优先），只记 WARN：限流不可用不应把找回密码整条链路打死。</p>
     *
     * @return empty 表示可继续；非 empty 为需等待的秒数
     */
    public Optional<Long> tryAcquireIpSlot(String clientIp) {
        String ip = clientIp == null ? "" : clientIp.trim();
        if (ip.isEmpty()) {
            return Optional.empty();
        }

        Object result = redisService.eval(IP_WINDOW_SCRIPT,
                new String[]{SENDER_IP_COUNT_PREFIX + ip},
                new String[]{String.valueOf(IP_WINDOW_SECONDS), String.valueOf(IP_MAX_REQUESTS)});

        if (!(result instanceof Number number)) {
            logger.warn("发码 IP 限流不可用（Redis 无响应），本次放行: ip={}", ip);
            return Optional.empty();
        }

        long code = number.longValue();
        if (code >= 0) {
            return Optional.empty();
        }

        long retryAfter = Math.max(1, -code);
        logger.warn("发码请求超过来源 IP 上限: ip={} retryAfter={}s", ip, retryAfter);
        return Optional.of(retryAfter);
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
