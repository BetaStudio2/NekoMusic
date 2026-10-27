package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import com.neko.music.database.DatabaseManager;
import com.neko.music.model.User;
import com.neko.music.util.DbTimeUtil;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.sql.*;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserAuthService {
    private static final Logger logger = LoggerFactory.getLogger(UserAuthService.class);
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final EmailService emailService;
    private final RedisService redisService;
    private final RedisTokenStore tokenStore;
    private final VerificationCodeRateLimitService verificationCodeRateLimitService;
    private final Argon2 argon2 = Argon2Factory.create();
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * 验证码异步发信线程：密码重置必须让「已注册 / 未注册」的响应耗时不可区分，
     * 因此发信放到后台线程，调用方立即返回统一响应。单线程足够——真正的串行瓶颈在 SMTP。
     */
    private static final ExecutorService ASYNC_CODE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "verification-code-async");
        thread.setDaemon(true);
        return thread;
    });
    
    private static final String VERIFICATION_CODE_PREFIX = "verification_code:";
    private static final String VERIFICATION_CODE_ATTEMPT_PREFIX = "verification_code_attempts:";
    private static final String VERIFICATION_PURPOSE_REGISTER = "register";
    private static final String VERIFICATION_PURPOSE_PASSWORD_RESET = "password_reset";
    private static final int VERIFICATION_CODE_EXPIRY = 300; // 5分钟过期
    private static final int VERIFICATION_CODE_MAX_ATTEMPTS = 5;
    private static final int TOKEN_EXPIRY_DAYS = 365; // Token 有效期 1 年
    private static final int TOKEN_EXPIRY_SECONDS = TOKEN_EXPIRY_DAYS * 86400;
    private static final int TOKEN_RENEW_WINDOW_DAYS = 31;
    private static final int TOKEN_RENEW_EXTENSION_SECONDS = TOKEN_RENEW_WINDOW_DAYS * 86400;

    public UserAuthService(DatabaseManager databaseManager, ConfigManager configManager,
                           EmailService emailService, RedisService redisService,
                           RedisTokenStore tokenStore,
                           VerificationCodeRateLimitService verificationCodeRateLimitService) {
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.emailService = emailService;
        this.redisService = redisService;
        this.tokenStore = tokenStore;
        this.verificationCodeRateLimitService = verificationCodeRateLimitService;
    }

    /**
     * 用户注册（仅邮箱必须唯一，昵称可重复）
     */
    public boolean registerUser(String nickname, String password, String email) {
        logger.info("开始注册用户: {}", nickname);

        if (emailExists(email)) {
            logger.warn("邮箱已存在: {}", email);
            return false;
        }

        String hashedPassword = hashPassword(password);

        String sql = "INSERT INTO users (nickname, password, email, created_at) VALUES (?, ?, ?, "
                + DbTimeUtil.SQL_NOW_SHANGHAI + ")";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, nickname);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, email);

            int affectedRows = stmt.executeUpdate();
            String storedCreatedAt = null;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int userId = keys.getInt(1);
                    try (PreparedStatement read = conn.prepareStatement(
                            "SELECT created_at FROM users WHERE id = ?")) {
                        read.setInt(1, userId);
                        try (ResultSet rs = read.executeQuery()) {
                            if (rs.next()) {
                                storedCreatedAt = DbTimeUtil.formatStoredWallClock(rs.getString("created_at"));
                            }
                        }
                    }
                }
            }
            logger.info("用户注册结果: {}, 昵称: {}, created_at(+08)={}",
                    affectedRows > 0, nickname, storedCreatedAt != null ? storedCreatedAt : DbTimeUtil.nowShanghaiWallClock());
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.error("用户注册失败: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 用户登录（仅支持邮箱登录）
     */
    public Optional<User> authenticate(String email, String password) {
        logger.info("用户登录尝试: {}", email);

        String sql = "SELECT id, nickname, password, email, created_at, vip_expires_at FROM users WHERE email = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedPasswordHash = rs.getString("password");

                if (argon2.verify(storedPasswordHash, password.toCharArray())) {
                    User user = mapUser(rs);

                    logger.info("用户登录成功: {}", email);
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            logger.error("用户认证失败: {}", e.getMessage(), e);
        }

        logger.warn("用户登录失败: {}", email);
        return Optional.empty();
    }

    /**
     * 按用户 ID 查询用户信息（不含密码字段），用于扫码登录等需要回传用户资料的场景。
     */
    public Optional<User> findUserById(int userId) {
        String sql = "SELECT id, nickname, email, created_at, vip_expires_at FROM users WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = mapUser(rs);
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            logger.error("按ID查询用户失败: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * 将 users 查询结果行映射为 {@link User}（读取 id/nickname/email/created_at/vip_expires_at）。
     */
    private static User mapUser(ResultSet rs) throws SQLException {
        java.sql.Timestamp vipTs = rs.getTimestamp("vip_expires_at");
        boolean hasVip = !rs.wasNull();
        return new User(
                rs.getInt("id"),
                rs.getString("nickname"),
                null,
                rs.getString("email"),
                false,
                null,
                DbTimeUtil.formatStoredWallClock(rs.getString("created_at")),
                hasVip ? vipTs : null);
    }

    /**
     * 发送注册验证码；同邮箱有发信冷却。
     */
    public SendVerificationCodeResult sendVerificationCode(String email, String nickname) {
        return sendVerificationCode(email, nickname, VERIFICATION_PURPOSE_REGISTER);
    }

    /**
     * 发送密码重置验证码；与注册验证码隔离，避免跨流程复用。
     */
    public SendVerificationCodeResult sendResetPasswordCode(String email, String nickname) {
        return sendVerificationCode(email, nickname, VERIFICATION_PURPOSE_PASSWORD_RESET);
    }

    private SendVerificationCodeResult sendVerificationCode(String email, String nickname, String purpose) {
        var cooldown = verificationCodeRateLimitService.tryAcquireSendSlot(email);
        if (cooldown.isPresent()) {
            return SendVerificationCodeResult.cooldown(cooldown.get());
        }
        return deliverVerificationCode(email, nickname, purpose);
    }

    /**
     * 重置密码验证码：统一限流通过后**异步**发信，调用方立即返回。
     *
     * <p>为什么异步：密码重置接口必须对「邮箱是否注册」给出完全一致的响应。同步发信时，已注册
     * 邮箱要多走一次 SMTP（可达数百毫秒到数秒），未注册邮箱只查一次库，响应时间差本身就是
     * 账号存在性信号。放到后台线程后两条分支的耗时都只剩「一次查询」，失败只记日志
     * （对外文案本就统一）。</p>
     */
    public void sendResetPasswordCodeAsync(String email, String nickname) {
        ASYNC_CODE_EXECUTOR.execute(() -> {
            try {
                deliverVerificationCode(email, nickname, VERIFICATION_PURPOSE_PASSWORD_RESET);
            } catch (RuntimeException e) {
                logger.error("异步发送重置密码验证码失败: {}", email, e);
            }
        });
    }

    /**
     * 重置密码发码的统一限流：来源 IP 与邮箱各占一个槽位，两者都必须在「邮箱是否注册」之前完成，
     * 否则 {@code 429} 会变成账号存在性信号。
     *
     * @return empty 表示放行；非 empty 为需等待的秒数
     */
    public Optional<Long> acquireResetCodeSlot(String email, String clientIp) {
        Optional<Long> byIp = verificationCodeRateLimitService.tryAcquireIpSlot(clientIp);
        if (byIp.isPresent()) {
            return byIp;
        }
        return verificationCodeRateLimitService.tryAcquireSendSlot(email);
    }

    /**
     * 注册发码的来源 IP 限流：只占 IP 维度一个槽位。
     *
     * <p>邮箱级冷却（{@link #sendVerificationCode}）只拦「同邮箱重复发码」，挡不住换收件人批量发码；
     * 因此发码入口还必须按来源 IP 封顶。邮箱槽位仍由发信逻辑内部占用，此处不重复占用，否则会把
     * 同一次发信的邮箱冷却误判成超限。</p>
     *
     * @return empty 表示放行；非 empty 为需等待的秒数
     */
    public Optional<Long> acquireVerificationIpSlot(String clientIp) {
        return verificationCodeRateLimitService.tryAcquireIpSlot(clientIp);
    }

    /** 生成验证码 → 发信 → 落 Redis；冷却槽位由调用方（同步 / 异步两条路径）负责。 */
    private SendVerificationCodeResult deliverVerificationCode(String email, String nickname, String purpose) {
        logger.info("发送验证码至: {}", email);

        String verificationCode = emailService.generateVerificationCode();

        boolean emailSent = emailService.sendVerificationCode(email, nickname, verificationCode);

        if (emailSent) {
            String key = verificationCodeKey(purpose, email);
            redisService.setWithExpiry(key, verificationCode, VERIFICATION_CODE_EXPIRY);
            redisService.del(verificationCodeAttemptKey(purpose, email));
            logger.info("验证码已生成并存储到Redis: {}, purpose={}", email, purpose);
            return SendVerificationCodeResult.ok();
        }

        verificationCodeRateLimitService.releaseSendSlot(email);
        logger.error("发送验证码邮件失败: {}", email);
        return SendVerificationCodeResult.sendFailed();
    }
    
    /**
     * 检查邮箱是否在白名单中
     */
    public boolean isWhitelistedEmail(String email) {
        String emailWhitelist = configManager.getEmailWhitelist();
        
        if (emailWhitelist == null || emailWhitelist.trim().isEmpty()) {
            return true;
        }
        
        String emailDomain = email.substring(email.lastIndexOf("@") + 1).toLowerCase().trim();
        String[] whitelist = emailWhitelist.split(",");
        
        for (String whitelistedDomain : whitelist) {
            if (emailDomain.equals(whitelistedDomain.trim())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 验证注册验证码，并对同一邮箱短时间内的错误尝试次数封顶。
     *
     * <p>注册验证码是 6 位纯数字、有效期 5 分钟，若不做尝试次数限制，配合「无限速发码」可被穷举
     * （10^6 量级）。这里复用与密码重置一致的 {@link #VERIFICATION_CODE_MAX_ATTEMPTS} 策略：
     * 同一邮箱累计错误到上限即删除验证码，须重新发码才能继续。</p>
     */
    public boolean verifyCode(String email, String code) {
        return verifyCode(email, code, VERIFICATION_PURPOSE_REGISTER, true);
    }

    /**
     * 验证密码重置验证码，并限制同一邮箱短时间内的错误尝试次数。
     */
    public boolean verifyResetPasswordCode(String email, String code) {
        return verifyCode(email, code, VERIFICATION_PURPOSE_PASSWORD_RESET, true);
    }

    private boolean verifyCode(String email, String code, String purpose, boolean limitAttempts) {
        if (email == null || code == null) {
            return false;
        }

        if (limitAttempts && isVerificationAttemptBlocked(purpose, email)) {
            logger.warn("验证码错误次数过多，暂时拒绝验证: {}, purpose={}", email, purpose);
            return false;
        }

        String key = verificationCodeKey(purpose, email);
        String storedCode = redisService.get(key);
        
        if (storedCode == null) {
            if (limitAttempts) {
                recordFailedVerificationAttempt(purpose, email);
            }
            logger.warn("未找到邮箱对应的验证码: {}, purpose={}", email, purpose);
            return false;
        }
        
        if (storedCode.equals(code)) {
            redisService.del(key);
            redisService.del(verificationCodeAttemptKey(purpose, email));
            logger.info("验证码验证成功: {}, purpose={}", email, purpose);
            return true;
        } else {
            if (limitAttempts) {
                long attempts = recordFailedVerificationAttempt(purpose, email);
                if (attempts >= VERIFICATION_CODE_MAX_ATTEMPTS) {
                    redisService.del(key);
                    logger.warn("验证码错误次数达到上限，已删除验证码: {}, purpose={}", email, purpose);
                }
            }
            logger.warn("验证码不匹配: {}, purpose={}", email, purpose);
            return false;
        }
    }

    private String verificationCodeKey(String purpose, String email) {
        return VERIFICATION_CODE_PREFIX + purpose + ":" + normalizeEmail(email);
    }

    private String verificationCodeAttemptKey(String purpose, String email) {
        return VERIFICATION_CODE_ATTEMPT_PREFIX + purpose + ":" + normalizeEmail(email);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean isVerificationAttemptBlocked(String purpose, String email) {
        String attempts = redisService.get(verificationCodeAttemptKey(purpose, email));
        if (attempts == null || attempts.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(attempts) >= VERIFICATION_CODE_MAX_ATTEMPTS;
        } catch (NumberFormatException e) {
            redisService.del(verificationCodeAttemptKey(purpose, email));
            return false;
        }
    }

    private long recordFailedVerificationAttempt(String purpose, String email) {
        String key = verificationCodeAttemptKey(purpose, email);
        Object result = redisService.eval(
                "local count = redis.call('INCR', KEYS[1]) " +
                        "if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end " +
                        "return count",
                new String[]{key},
                new String[]{String.valueOf(VERIFICATION_CODE_EXPIRY)}
        );
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        return 0;
    }

    private boolean emailExists(String email) {
        return emailExistsWithLog(email, "检查邮箱存在性失败:");
    }

    /**
     * 根据邮箱检查用户是否存在（公开方法，用于密码重置）
     */
    public boolean userExistsByEmail(String email) {
        return emailExistsWithLog(email, "检查用户邮箱失败:");
    }

    /**
     * 查询邮箱是否存在；失败日志前缀由调用方传入以保持各自原有文案。
     */
    private boolean emailExistsWithLog(String email, String errorLogPrefix) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error(errorLogPrefix + " {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 重置用户密码，成功后注销该用户全部会话 token。
     */
    public boolean resetPassword(String email, String newPassword) {
        logger.info("重置用户密码: {}", email);

        Optional<Integer> userIdOpt = findUserIdByEmail(email);

        String sql = "UPDATE users SET password = ? WHERE email = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String hashedPassword = hashPassword(newPassword);
            stmt.setString(1, hashedPassword);
            stmt.setString(2, email);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                userIdOpt.ifPresent(this::revokeAllTokensForUser);
                logger.info("密码重置成功: {}", email);
                return true;
            }
            logger.warn("密码重置未影响任何行: {}", email);
        } catch (SQLException e) {
            logger.error("重置密码失败: {}", e.getMessage(), e);
        }

        return false;
    }

    public Optional<Integer> findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id FROM users WHERE email = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("id"));
                }
            }
        } catch (SQLException e) {
            logger.error("按邮箱查询用户 ID 失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 注销某用户全部会话（改密、重置密码后须重新登录）。
     */
    public void revokeAllTokensForUser(int userId) {
        tokenStore.revokeAllUserTokens(userId);
    }

    private String hashPassword(String password) {
        return argon2.hash(10, 65536, 1, password.toCharArray());
    }
    
    public String generateToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : tokenBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * 为用户创建会话 token（仅存 Redis，TTL {@link #TOKEN_EXPIRY_DAYS} 天）。
     */
    public String createTokenForUser(int userId) {
        String token = generateToken();
        try {
            tokenStore.saveUserToken(token, userId, TOKEN_EXPIRY_SECONDS);
            logger.info("Token创建成功，用户ID: {}", userId);
            return token;
        } catch (Exception e) {
            logger.error("创建token失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public Optional<String> findEmailByUserId(int userId) {
        String sql = "SELECT email FROM users WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String email = rs.getString("email");
                    if (email != null && !email.isBlank()) {
                        return Optional.of(email.trim());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("查询用户邮箱失败 userId={}: {}", userId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<java.sql.Timestamp> findVipExpiresAtByUserId(int userId) {
        String sql = "SELECT vip_expires_at FROM users WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp vip = rs.getTimestamp("vip_expires_at");
                    return Optional.ofNullable(rs.wasNull() ? null : vip);
                }
            }
        } catch (SQLException e) {
            logger.error("查询 VIP 到期时间失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 验证 token 并返回用户 ID（仅查 Redis）。若 token 剩余有效期不足 31 天，则按活跃会话延期 31 天。
     */
    public Optional<Integer> validateToken(String token) {
        // 校验与「临近过期时续期」合并为一次 Redis 往返（见 RedisTokenStore#validateAndRenew）：
        // 认证路径在每个已登录请求上都会走这里，多一次 TTL 查询就是每个请求多一次往返。
        return tokenStore.validateAndRenew(token, TOKEN_RENEW_EXTENSION_SECONDS, TOKEN_RENEW_EXTENSION_SECONDS);
    }
    
    /**
     * 只读解析 token 对应的用户 ID（仅查 Redis，**不续期**）。
     *
     * <p>给审计 / 日志这类旁路使用：它们需要知道「是谁」，但不该因为记了一条日志就把会话
     * 悄悄续上——那会让「开不开日志」影响会话时长。认证路径请继续用 {@link #validateToken}。</p>
     */
    public Optional<Integer> findUserIdByToken(String token) {
        return tokenStore.getUserIdByToken(token);
    }

    /**
     * 注销 token（从 Redis 删除）。
     */
    public boolean revokeToken(String token) {
        boolean revoked = tokenStore.deleteUserToken(token);
        if (revoked) {
            logger.info("Token已注销");
        }
        return revoked;
    }

}
