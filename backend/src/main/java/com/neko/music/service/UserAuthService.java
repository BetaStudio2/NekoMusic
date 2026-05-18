package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import com.neko.music.database.DatabaseManager;
import com.neko.music.model.User;
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
    private final Argon2 argon2 = Argon2Factory.create();
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private static final String VERIFICATION_CODE_PREFIX = "verification_code:";
    private static final int VERIFICATION_CODE_EXPIRY = 300; // 5分钟过期
    private static final int TOKEN_EXPIRY_DAYS = 365; // Token 有效期 1 年
    private static final int TOKEN_EXPIRY_SECONDS = TOKEN_EXPIRY_DAYS * 86400;

    public UserAuthService(DatabaseManager databaseManager, ConfigManager configManager,
                           EmailService emailService, RedisService redisService,
                           RedisTokenStore tokenStore) {
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.emailService = emailService;
        this.redisService = redisService;
        this.tokenStore = tokenStore;
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

        String sql = "INSERT INTO users (username, password, email, created_at) VALUES (?, ?, ?, NOW())";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, email);

            int affectedRows = stmt.executeUpdate();
            logger.info("用户注册结果: {}, 用户名: {}", affectedRows > 0, username);
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
                    user.setCreatedAt(rs.getString("created_at"));
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
     * 发送验证码
     */
    public boolean sendVerificationCode(String email, String username) {
        logger.info("发送验证码至: {}", email);

        String verificationCode = emailService.generateVerificationCode();
        
        boolean emailSent = emailService.sendVerificationCode(email, username, verificationCode);
        
        if (emailSent) {
            String key = VERIFICATION_CODE_PREFIX + email;
            redisService.setWithExpiry(key, verificationCode, VERIFICATION_CODE_EXPIRY);
            logger.info("验证码已生成并存储到Redis: {}", email);
            return true;
        } else {
            logger.error("发送验证码邮件失败: {}", email);
            return false;
        }
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
     * 验证验证码
     */
    public boolean verifyCode(String email, String code) {
        String key = VERIFICATION_CODE_PREFIX + email;
        String storedCode = redisService.get(key);
        
        if (storedCode == null) {
            logger.warn("未找到邮箱对应的验证码: {}", email);
            return false;
        }
        
        if (storedCode.equals(code)) {
            redisService.del(key);
            logger.info("验证码验证成功: {}", email);
            return true;
        } else {
            logger.warn("验证码不匹配: {}", email);
            return false;
        }
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
     * 验证 token 并返回用户 ID（仅查 Redis）。
     */
    public Optional<Integer> validateToken(String token) {
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
