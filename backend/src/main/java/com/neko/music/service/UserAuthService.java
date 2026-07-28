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
     * 用户注册（仅邮箱必须唯一，用户名可重复）
     */
    public boolean registerUser(String username, String password, String email) {
        logger.info("开始注册用户: {}", username);

        if (emailExists(email)) {
            logger.warn("邮箱已存在: {}", email);
            return false;
        }

        String hashedPassword = hashPassword(password);

        String sql = "INSERT INTO users (username, password, email, created_at) VALUES (?, ?, ?, "
                + DbTimeUtil.SQL_NOW_SHANGHAI + ")";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, username);
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
            logger.info("用户注册结果: {}, 用户名: {}, created_at(+08)={}",
                    affectedRows > 0, username, storedCreatedAt != null ? storedCreatedAt : DbTimeUtil.nowShanghaiWallClock());
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

        String sql = "SELECT id, username, password, email, created_at, vip_expires_at FROM users WHERE email = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedPasswordHash = rs.getString("password");

                if (argon2.verify(storedPasswordHash, password.toCharArray())) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setCreatedAt(DbTimeUtil.formatStoredWallClock(rs.getString("created_at")));
                    java.sql.Timestamp vipTs = rs.getTimestamp("vip_expires_at");
                    user.setVipExpiresAt(rs.wasNull() ? null : vipTs);

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
     * 发送注册验证码；同邮箱有发信冷却。
     */
    public SendVerificationCodeResult sendVerificationCode(String email, String username) {
        return sendVerificationCode(email, username, VERIFICATION_PURPOSE_REGISTER);
    }

    /**
     * 发送密码重置验证码；与注册验证码隔离，避免跨流程复用。
     */
    public SendVerificationCodeResult sendResetPasswordCode(String email, String username) {
        return sendVerificationCode(email, username, VERIFICATION_PURPOSE_PASSWORD_RESET);
    }

    private SendVerificationCodeResult sendVerificationCode(String email, String username, String purpose) {
        logger.info("发送验证码至: {}", email);

        var cooldown = verificationCodeRateLimitService.tryAcquireSendSlot(email);
        if (cooldown.isPresent()) {
            return SendVerificationCodeResult.cooldown(cooldown.get());
        }

        String verificationCode = emailService.generateVerificationCode();

        boolean emailSent = emailService.sendVerificationCode(email, username, verificationCode);

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
     * 验证注册验证码。
     */
    public boolean verifyCode(String email, String code) {
        return verifyCode(email, code, VERIFICATION_PURPOSE_REGISTER, false);
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
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("检查邮箱存在性失败: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 根据邮箱检查用户是否存在（公开方法，用于密码重置）
     */
    public boolean userExistsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("检查用户邮箱失败: {}", e.getMessage(), e);
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
        Optional<Integer> userId = tokenStore.getUserIdByToken(token);
        userId.ifPresent(id -> tokenStore.extendUserTokenIfExpiring(
                token,
                id,
                TOKEN_RENEW_EXTENSION_SECONDS,
                TOKEN_RENEW_EXTENSION_SECONDS));
        return userId;
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

    /**
     * 修改密码成功后调用：更新数据库密码并注销全部会话。
     */
    public boolean changePassword(int userId, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hashPassword(newPassword));
            stmt.setInt(2, userId);
            if (stmt.executeUpdate() > 0) {
                revokeAllTokensForUser(userId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("修改密码失败 userId={}: {}", userId, e.getMessage(), e);
        }
        return false;
    }
}
