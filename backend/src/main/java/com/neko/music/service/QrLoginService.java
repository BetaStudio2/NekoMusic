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
 * token 只在 confirmed 时由 PC 取走一次：先原子领取投递权（{@link #claimDelivery}），成功者才删除整个
 * 会话并下发 token，其余并发读者一律收到 {@code expired}。</p>
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
    /** 一次性投递权的键前缀（与会话同 TTL）。 */
    private static final String CLAIM_PREFIX = "qr_login_claimed:";

    /**
     * 原子领取投递权：{@code SET NX}，同一 sessionId 的并发读者里只有第一个返回成功。
     */
    private static final String CLAIM_SCRIPT =
            "if redis.call('SET', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[1])) then return 1 else return 0 end";
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

    /**
     * 领取一次性投递权：同一 sessionId 的并发读者里只有第一个返回 {@code true}。
     *
     * <p>为什么需要它：{@link #load} 与 {@link #remove} 是两次独立的 Redis 调用，两者之间多个并发 SSE
     * 读者都会读到 {@code confirmed} 快照，于是各自把**同一个** token 下发给各自的连接——「token 只能被
     * 取走一次」的承诺在并发下失效（CWE-362）。把「谁有权投递」收敛成一次原子 {@code SET NX} 之后，
     * 无论有多少读者同时在线，token 都只会被投递一次。</p>
     *
     * <p>领取成功后**不要删除这个键**：领到投递权的读者在删除会话、下发 token 期间，其它读者手里可能
     * 还握着删除前读到的 confirmed 快照，键一旦被删它们就能重新领到投递权、把同一个 token 再发一次。
     * 让它与会话一起自然过期即可（sessionId 随机且一次性）。</p>
     */
    public boolean claimDelivery(String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return false;
        }
        try {
            Object result = redisService.eval(CLAIM_SCRIPT,
                    new String[]{CLAIM_PREFIX + sessionId},
                    new String[]{String.valueOf(SESSION_TTL_SECONDS)});
            return result instanceof Number number && number.longValue() == 1L;
        } catch (Exception e) {
            // 领不到投递权就不投递：Redis 真有问题时 load() 同样会失败，会话本就走不到 confirmed
            logger.warn("扫码登录投递权领取失败: {}", e.getMessage());
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
