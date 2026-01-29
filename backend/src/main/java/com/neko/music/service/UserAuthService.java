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
    private final Argon2 argon2 = Argon2Factory.create();
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private static final String VERIFICATION_CODE_PREFIX = "verification_code:";
    private static final int VERIFICATION_CODE_EXPIRY = 300; // 5分钟过期
    private static final int TOKEN_EXPIRY_DAYS = 30; // Token有效期30天

    public UserAuthService(DatabaseManager databaseManager, ConfigManager configManager, EmailService emailService, RedisService redisService) {
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.emailService = emailService;
        this.redisService = redisService;
    }

    /**
     * 用户注册（仅邮箱必须唯一，用户名可重复）
     */
    public boolean registerUser(String username, String password, String email) {
        logger.info("开始注册用户: {}", username);

        // 检查邮箱是否已存在（用户名允许重复）
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

        String sql = "SELECT id, username, password, email, created_at FROM users WHERE email = ?";

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
        
        // 检查邮箱是否在黑名单中
        if (isBlacklistedEmail(email)) {
            logger.warn("邮箱在黑名单中: {}", email);
            return false;
        }
        
        // 生成验证码
        String verificationCode = emailService.generateVerificationCode();
        
        // 尝试发送邮件
        boolean emailSent = emailService.sendVerificationCode(email, username, verificationCode);
        
        if (emailSent) {
            // 将验证码存储到Redis中，设置5分钟过期
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
     * 检查邮箱是否在黑名单中
     */
    private boolean isBlacklistedEmail(String email) {
        String emailDomain = email.substring(email.lastIndexOf("@") + 1).toLowerCase().trim();
        String[] blacklist = configManager.getEmailBlacklist().split(",");
        
        for (String blacklistedDomain : blacklist) {
            if (emailDomain.equals(blacklistedDomain.trim())) {
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
        
        // 验证码匹配
        if (storedCode.equals(code)) {
            // 验证成功后删除验证码
            redisService.del(key);
            logger.info("验证码验证成功: {}", email);
            return true;
        } else {
            logger.warn("验证码不匹配: {}", email);
            return false;
        }
    }

    /**
     * 检查邮箱是否已存在
     */
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
     * 密码哈希
     */
    private String hashPassword(String password) {
        return argon2.hash(10, 65536, 1, password.toCharArray());
    }
    
    /**
     * 生成随机token
     */
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
     * 为用户创建并保存token
     */
    public String createTokenForUser(int userId) {
        String token = generateToken();
        String sql = "INSERT INTO user_tokens (user_id, token, created_at, expires_at) VALUES (?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? DAY))";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, token);
            stmt.setInt(3, TOKEN_EXPIRY_DAYS);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Token创建成功，用户ID: {}", userId);
                return token;
            }
        } catch (SQLException e) {
            logger.error("创建token失败: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 验证token并返回用户ID
     */
    public Optional<Integer> validateToken(String token) {
        String sql = "SELECT user_id FROM user_tokens WHERE token = ? AND expires_at > NOW()";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                int userId = rs.getInt("user_id");
                logger.info("Token验证成功，用户ID: {}", userId);
                return Optional.of(userId);
            }
        } catch (SQLException e) {
            logger.error("验证token失败: {}", e.getMessage(), e);
        }
        
        return Optional.empty();
    }
    
    /**
     * 注销token
     */
    public boolean revokeToken(String token) {
        String sql = "DELETE FROM user_tokens WHERE token = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.info("Token已注销");
                return true;
            }
        } catch (SQLException e) {
            logger.error("注销token失败: {}", e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * 清理过期token
     */
    public void cleanupExpiredTokens() {
        String sql = "DELETE FROM user_tokens WHERE expires_at < NOW()";
        
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            int deletedCount = stmt.executeUpdate(sql);
            if (deletedCount > 0) {
                logger.info("清理了 {} 个过期token", deletedCount);
            }
        } catch (SQLException e) {
            logger.error("清理过期token失败: {}", e.getMessage(), e);
        }
    }
}