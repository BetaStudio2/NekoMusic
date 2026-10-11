package com.neko.music.service;

import com.neko.music.database.AdminDatabaseManager;
import com.neko.music.model.Admin;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class AdminAuthService {
    private static final Logger logger = LoggerFactory.getLogger(AdminAuthService.class);

    /** 管理员会话有效期（秒） */
    private static final int ADMIN_SESSION_TTL_SECONDS = 24 * 60 * 60;

    /** 口令哈希参数：创建、改密与「等时陪跑」三处必须完全一致，否则陪跑就白跑了。 */
    private static final int HASH_ITERATIONS = 10;
    private static final int HASH_MEMORY_KIB = 65536;
    private static final int HASH_PARALLELISM = 1;

    private final AdminDatabaseManager adminDatabaseManager;
    private final RedisTokenStore tokenStore;
    private final Argon2 argon2;
    private final ConstantTimePasswordVerifier passwordVerifier;

    public AdminAuthService(AdminDatabaseManager adminDatabaseManager, RedisTokenStore tokenStore) {
        this.adminDatabaseManager = adminDatabaseManager;
        this.tokenStore = tokenStore;
        this.argon2 = Argon2Factory.create();
        this.passwordVerifier = new ConstantTimePasswordVerifier(
                argon2, HASH_ITERATIONS, HASH_MEMORY_KIB, HASH_PARALLELISM);
    }

    /**
     * 管理员口令认证。
     *
     * <p>关键点：用户名不存在时也走一次与真实校验同参数的 Argon2（{@link ConstantTimePasswordVerifier}），
     * 使「账号存在但口令错」与「账号不存在」两条路径耗时一致——否则该接口是一个可远程稳定观测的
     * 用户名枚举时间侧信道（CWE-204）。陪跑会让不存在的账号也消耗资源，因此限流
     * （{@link AdminLoginThrottleService}）必须同时生效。</p>
     */
    public Optional<Admin> authenticate(String username, String password) {
        Optional<Admin> adminOpt = adminDatabaseManager.findAdminByUsername(username);

        if (passwordVerifier.verify(adminOpt.map(Admin::passwordHash).orElse(null), password.toCharArray())) {
            adminDatabaseManager.updateLastLogin(username);
            return adminOpt;
        }

        return Optional.empty();
    }

    public boolean createAdmin(String username, String password, String email) {
        if (adminDatabaseManager.adminExists(username)) {
            return false;
        }
        
        String hash = argon2.hash(HASH_ITERATIONS, HASH_MEMORY_KIB, HASH_PARALLELISM,
                password.toCharArray());
        Admin admin = new Admin(0, username, hash, email, true, null, System.currentTimeMillis(), 0L);
        
        return adminDatabaseManager.createAdmin(admin);
    }

    public boolean createAdmin(String username, String password) {
        return createAdmin(username, password, null);
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        Optional<Admin> adminOpt = adminDatabaseManager.findAdminByUsername(username);
        
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            if (argon2.verify(admin.passwordHash(), oldPassword.toCharArray())) {
                String newHash = argon2.hash(HASH_ITERATIONS, HASH_MEMORY_KIB, HASH_PARALLELISM,
                        newPassword.toCharArray());
                return adminDatabaseManager.updateAdminPassword(username, newHash);
            }
        }
        
        return false;
    }

    public boolean isPasswordStrong(String password) {
        return password != null && password.length() >= 8;
    }
    
    public boolean adminExists(String username) {
        return adminDatabaseManager.adminExists(username);
    }
    
    public boolean validateAdminToken(String token) {
        return tokenStore.getAdminIdByToken(token).isPresent();
    }

    /** 是否具备上传客户端更新包权限（super_admin / admin；不含 auditor） */
    public static boolean canUploadClientRelease(String role) {
        return "super_admin".equals(role) || "admin".equals(role);
    }

    public boolean canUploadClientReleaseByToken(String token) {
        Admin admin = getAdminByToken(token);
        return admin != null && canUploadClientRelease(admin.role());
    }

    public Optional<Integer> getAdminIdByToken(String token) {
        return tokenStore.getAdminIdByToken(token);
    }
    
    /**
     * 创建管理员会话：签发新令牌前先注销该管理员此前的全部会话（登录即轮换）。
     *
     * <p>否则每次登录都会新增一条彼此独立、各自存活 24h 的令牌，旧令牌在 TTL 内始终可用：
     * 既无法通过「重新登录」挤出被窃会话，也让有效会话随登录次数无上限累积。</p>
     */
    public String createAdminSession(Admin admin) {
        String sessionToken = generateSessionToken();
        try {
            tokenStore.revokeAllAdminTokens(admin.id());
            tokenStore.saveAdminToken(sessionToken, admin.id(), ADMIN_SESSION_TTL_SECONDS);
            return sessionToken;
        } catch (Exception e) {
            logger.error("创建管理员会话失败", e);
            return null;
        }
    }

    /** 注销某管理员的全部会话（改密、删除管理员后调用）。 */
    public int revokeAllAdminTokens(int adminId) {
        return tokenStore.revokeAllAdminTokens(adminId);
    }
    
    private String generateSessionToken() {
        return java.util.UUID.randomUUID().toString();
    }
    
    public boolean logout(String token) {
        return tokenStore.deleteAdminToken(token);
    }
    
    public Admin getAdminByToken(String token) {
        Optional<Integer> adminIdOpt = tokenStore.getAdminIdByToken(token);
        if (adminIdOpt.isEmpty()) {
            return null;
        }

        String sql = "SELECT * FROM admins WHERE id = ? AND active = TRUE";

        try (java.sql.Connection conn = adminDatabaseManager.getDatabaseManager().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, adminIdOpt.get());
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Admin admin = new Admin(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("email"),
                            rs.getBoolean("active"),
                            rs.getString("role"),
                            rs.getLong("created_at"),
                            rs.getLong("last_login_at"));
                    return admin;
                }
            }
        } catch (Exception e) {
            logger.error("根据token获取管理员信息失败", e);
        }

        return null;
    }
}
