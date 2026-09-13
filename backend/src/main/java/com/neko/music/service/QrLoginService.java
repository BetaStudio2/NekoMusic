package com.neko.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 扫码登录会话（仅存 Redis，短 TTL 自动过期）。
 *
 * <p>生命周期：{@code pending}（PC 刚生成二维码）→ {@code scanned}（手机已扫码，等待用户确认）
 * → {@code confirmed}（手机已确认，token 待 PC 取走）；手机拒绝或 PC 取消则进入 {@code canceled}。
 * token 只在 confirmed 时由 PC 取走一次，取走即删除整个会话。</p>
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
    private static final int SESSION_TTL_SECONDS = 180;
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
        public long createdAt = System.currentTimeMillis();
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

    /** 新建会话，返回会话 ID；Redis 不可用时返回 null。 */
    public String createSession() {
        String sessionId = newSessionId();
        Session session = new Session();
        if (!save(sessionId, session)) {
            return null;
        }
        return sessionId;
    }

    /** 读取会话；不存在或已过期返回 empty。 */
    public Optional<Session> load(String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return Optional.empty();
        }
        try {
            String json = redisService.get(KEY_PREFIX + sessionId);
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, Session.class));
        } catch (Exception e) {
            logger.warn("读取扫码登录会话失败: {}", e.getMessage());
            return Optional.empty();
        }
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

    /** 删除会话（PC 取走 token 后调用）。 */
    public void remove(String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return;
        }
        try {
            redisService.del(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            logger.warn("删除扫码登录会话失败: {}", e.getMessage());
        }
    }

    private static String newSessionId() {
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
