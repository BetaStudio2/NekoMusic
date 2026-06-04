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

    private final AdminDatabaseManager adminDatabaseManager;
    private final RedisTokenStore tokenStore;
    private final Argon2 argon2;

    public AdminAuthService(AdminDatabaseManager adminDatabaseManager, RedisTokenStore tokenStore) {
        this.adminDatabaseManager = adminDatabaseManager;
        this.tokenStore = tokenStore;
        this.argon2 = Argon2Factory.create();
    }

    public Optional<Admin> authenticate(String username, String password) {
        Optional<Admin> adminOpt = adminDatabaseManager.findAdminByUsername(username);
        
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            if (argon2.verify(admin.getPasswordHash(), password.toCharArray())) {
                adminDatabaseManager.updateLastLogin(username);
                return Optional.of(admin);
            }
        }
        
        return Optional.empty();
    }

    public boolean createAdmin(String username, String password, String email) {
        if (adminDatabaseManager.adminExists(username)) {
            return false;
        }
        
        String hash = argon2.hash(10, 65536, 1, password.toCharArray());
        Admin admin = new Admin(username, hash);
        admin.setEmail(email);
        
        return adminDatabaseManager.createAdmin(admin);
    }

    public boolean createAdmin(String username, String password) {
        return createAdmin(username, password, null);
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        Optional<Admin> adminOpt = adminDatabaseManager.findAdminByUsername(username);
        
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            if (argon2.verify(admin.getPasswordHash(), oldPassword.toCharArray())) {
                String newHash = argon2.hash(10, 65536, 1, newPassword.toCharArray());
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
        return admin != null && canUploadClientRelease(admin.getRole());
    }

    public Optional<Integer> getAdminIdByToken(String token) {
        return tokenStore.getAdminIdByToken(token);
    }
    
    public String createAdminSession(Admin admin) {
        String sessionToken = generateSessionToken();
        try {
            tokenStore.saveAdminToken(sessionToken, admin.getId(), ADMIN_SESSION_TTL_SECONDS);
            return sessionToken;
        } catch (Exception e) {
            logger.error("创建管理员会话失败", e);
            return null;
        }
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
                    Admin admin = new Admin();
                    admin.setId(rs.getInt("id"));
                    admin.setUsername(rs.getString("username"));
                    admin.setPasswordHash(rs.getString("password_hash"));
                    admin.setEmail(rs.getString("email"));
                    admin.setActive(rs.getBoolean("active"));
                    admin.setRole(rs.getString("role"));
                    admin.setCreatedAt(rs.getLong("created_at"));
                    admin.setLastLoginAt(rs.getLong("last_login_at"));
                    return admin;
                }
            }
        } catch (Exception e) {
            logger.error("根据token获取管理员信息失败", e);
        }

        return null;
    }
}
