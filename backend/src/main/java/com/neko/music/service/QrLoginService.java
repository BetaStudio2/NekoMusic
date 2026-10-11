package com.neko.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 扫码登录会话（仅存 Redis，短 TTL 自动过期）。
 *
 * <p>生命周期：{@code pending}（PC 刚生成二维码）→ {@code scanned}（手机已扫码，等待用户确认）
 * → {@code confirmed}（手机已确认，token 待 PC 取走）；手机拒绝或 PC 取消则进入 {@code canceled}。</p>
 *
 * <p>一次性 token 只能由<strong>创建方</strong>凭 {@link #createSession()} 返回的一次性投递密钥
 * 领取，见 {@link #claim}：脚本里「读状态 + 校验密钥 + 删除会话」是一条 Lua 原子操作，密钥不符、
 * 迟到或状态未到 {@code confirmed} 的调用都拿不到 token，会话也不会被误删。</p>
 *
 * <p>token 不写进 SSE 状态帧：CDN 会把同一 URL 的并发长连接合并成一次回源，再把这唯一一路响应
 * 分发给全部订阅者；任何写在帧里的秘密都会被同时投递给多个客户端，等价于广播。故状态帧只播状态，
 * token 单独由持有密钥的创建方领取。</p>
 */
public class QrLoginService {

    /** 二维码文本前缀，内容形如 {@code nekomusic://qrlogin?sid=xxx} */
    public static final String QR_SCHEME_PREFIX = "nekomusic://qrlogin?sid=";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SCANNED = "scanned";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_CANCELED = "canceled";
    /** 会话不存在或已过期（查询接口返回值，不落 Redis） */
    public static final String STATUS_EXPIRED = "expired";

    private static final Logger logger = LoggerFactory.getLogger(QrLoginService.class);

    private static final String KEY_PREFIX = "qr_login:";

    /**
     * 原子领取一次性 token：读会话 → 校验状态与投递密钥 → 删除会话并回传会话原文。
     * 返回值固定为 {@code {结果码, 会话原文}}，结果码见 {@link ClaimOutcome}。
     */
    private static final String CLAIM_SCRIPT =
            "local raw = redis.call('GET', KEYS[1]) "
                    + "if not raw then return {'missing', ''} end "
                    + "local ok, session = pcall(cjson.decode, raw) "
                    + "if not ok then return {'missing', ''} end "
                    + "if session['status'] ~= ARGV[1] then return {'not_confirmed', raw} end "
                    + "if type(session['deliveryKey']) ~= 'string' or session['deliveryKey'] ~= ARGV[2] then "
                    + "return {'rejected', ''} end "
                    + "redis.call('DEL', KEYS[1]) "
                    + "return {'ok', raw}";

    private static final int SESSION_TTL_SECONDS = 180;
    /** 投递密钥长度（字节），URL 安全编码后 43 个字符 */
    private static final int DELIVERY_KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    public QrLoginService(RedisService redisService, ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    /** 会话数据，序列化为 JSON 存 Redis */
    public static class Session {
        public String status = STATUS_PENDING;
        public Integer userId;
        public String token;
        /** 一次性投递密钥，只在创建响应里交给 PC，从不写进 SSE 帧或二维码 */
        public String deliveryKey;
        public long createdAt = System.currentTimeMillis();
    }

    /** 新建会话的结果：会话 ID + 只有创建方拿得到的一次性投递密钥。 */
    public record NewSession(String sessionId, String deliveryKey) {
    }

    /** 一次性 token 领取结果。 */
    public enum ClaimOutcome {
        /** 领取成功，会话已被原子删除 */
        OK,
        /** 会话还在，但手机尚未确认 */
        NOT_CONFIRMED,
        /** 投递密钥不符，即调用方不是创建方 */
        REJECTED,
        /** 会话不存在或已过期 */
        MISSING,
        /** Redis 故障 */
        ERROR
    }

    /** 领取结果；{@link ClaimOutcome#OK} 与 {@link ClaimOutcome#NOT_CONFIRMED} 会带回会话快照。 */
    public record ClaimResult(ClaimOutcome outcome, Session session) {
    }

    public static int sessionTtlSeconds() {
        return SESSION_TTL_SECONDS;
    }

    /** 校验会话 ID 形态，避免被拼成任意 Redis 键 */
    public static boolean isValidSessionId(String sessionId) {
        return sessionId != null && sessionId.matches("[A-Za-z0-9_-]{16,64}");
    }

    public static String qrContentFor(String sessionId) {
        return QR_SCHEME_PREFIX + sessionId;
    }

    /** 新建会话，返回会话 ID 与一次性投递密钥；Redis 不可用时返回 null。 */
    public NewSession createSession() {
        String sessionId = newSessionId();
        String deliveryKey = newDeliveryKey();
        Session session = new Session();
        session.deliveryKey = deliveryKey;
        if (!save(sessionId, session)) {
            return null;
        }
        return new NewSession(sessionId, deliveryKey);
    }

    /** 读取会话；不存在或已过期返回 empty。 */
    public Optional<Session> load(String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return Optional.empty();
        }
        String json;
        try {
            json = redisService.get(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            logger.warn("读取扫码登录会话失败: {}", e.getMessage());
            return Optional.empty();
        }
        Session session = parseSession(json);
        return session == null ? Optional.empty() : Optional.of(session);
    }

    /** 写回会话并续期；失败返回 false。 */
    public boolean save(String sessionId, Session session) {
        if (!isValidSessionId(sessionId) || session == null) {
            return false;
        }
        try {
            redisService.setWithExpiry(KEY_PREFIX + sessionId, objectMapper.writeValueAsString(session),
                    SESSION_TTL_SECONDS);
            return true;
        } catch (Exception e) {
            logger.error("写入扫码登录会话失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 领取一次性登录 token：只有持正确投递密钥、且会话已 {@code confirmed} 的调用会成功，成功即删除会话。
     *
     * <p>「读状态—判断—删除」必须是一条原子操作：若拆成两次 Redis 调用，并发调用方会各自读到同一份
     * confirmed 快照，各自拿到同一个 token（CWE-362）。</p>
     */
    public ClaimResult claim(String sessionId, String deliveryKey) {
        if (!isValidSessionId(sessionId) || deliveryKey == null || deliveryKey.isBlank()) {
            return new ClaimResult(ClaimOutcome.MISSING, null);
        }
        List<Object> raw;
        try {
            raw = redisService.evalMulti(CLAIM_SCRIPT,
                    new String[]{KEY_PREFIX + sessionId},
                    new String[]{STATUS_CONFIRMED, deliveryKey});
        } catch (Exception e) {
            logger.error("扫码登录一次性领取失败: {}", e.getMessage(), e);
            return new ClaimResult(ClaimOutcome.ERROR, null);
        }
        if (raw == null || raw.size() < 2) {
            return new ClaimResult(ClaimOutcome.ERROR, null);
        }
        String code = String.valueOf(raw.get(0));
        String payload = raw.get(1) == null ? "" : String.valueOf(raw.get(1));
        return switch (code) {
            case "ok" -> new ClaimResult(ClaimOutcome.OK, parseSession(payload));
            case "not_confirmed" -> new ClaimResult(ClaimOutcome.NOT_CONFIRMED, parseSession(payload));
            case "rejected" -> new ClaimResult(ClaimOutcome.REJECTED, null);
            default -> new ClaimResult(ClaimOutcome.MISSING, null);
        };
    }

    private Session parseSession(String json) {
        if (json == null || json.isEmpty() || objectMapper == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Session.class);
        } catch (Exception e) {
            logger.warn("解析扫码登录会话失败: {}", e.getMessage());
            return null;
        }
    }

    private static String newSessionId() {
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String newDeliveryKey() {
        byte[] buf = new byte[DELIVERY_KEY_BYTES];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
