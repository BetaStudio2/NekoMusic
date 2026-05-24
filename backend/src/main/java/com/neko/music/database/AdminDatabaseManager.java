package com.neko.music.database;

import com.neko.music.model.Admin;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdminDatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(AdminDatabaseManager.class);
    private final DatabaseManager databaseManager;
    private final Argon2 argon2;

    public AdminDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.argon2 = Argon2Factory.create();
        initializeTable();
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private void initializeTable() {
        // 创建管理员表
        String adminSql = """
            CREATE TABLE IF NOT EXISTS admins (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(255) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                email VARCHAR(255),
                active BOOLEAN DEFAULT TRUE,
                role ENUM('super_admin', 'admin', 'auditor') DEFAULT 'admin',
                created_at BIGINT NOT NULL,
                last_login_at BIGINT
            )
        """;
        
        // 创建音乐表（用于统计）
        String musicSql = """
            CREATE TABLE IF NOT EXISTS music (
                id INT AUTO_INCREMENT PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                artist VARCHAR(255),
                album VARCHAR(255),
                duration INT,
                upload_user_id INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
        """;
        
        // 创建用户表（用于统计）
        String userSql = """
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(255) UNIQUE NOT NULL,
                email VARCHAR(255),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;
        

        
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(adminSql);
            stmt.execute(musicSql);
            stmt.execute(userSql);
            
            // 检查并更新music表结构（添加missing列）
            updateMusicTableStructure(conn);
            
            // 检查并更新admins表结构，添加role字段
            updateAdminsTableStructure(conn);
            
            logger.info("管理员相关表初始化完成");
        } catch (SQLException e) {
            logger.error("表初始化失败", e);
        }
    }

    public boolean createAdmin(Admin admin) {
            // 检查是否是第一个管理员，如果是则设置为super_admin
            boolean isFirstAdmin = getAllAdmins().isEmpty();
            if (isFirstAdmin && admin.getRole() == null) {
                admin.setRole("super_admin");
            }
            
            String sql = """
                INSERT INTO admins (username, password_hash, email, active, created_at, role)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, admin.getUsername());
                stmt.setString(2, admin.getPasswordHash());
                stmt.setString(3, admin.getEmail());
                stmt.setBoolean(4, admin.isActive());
                stmt.setLong(5, admin.getCreatedAt());
                stmt.setString(6, admin.getRole() != null ? admin.getRole() : "admin");
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                logger.error("创建管理员失败", e);
                return false;
            }
        }
    public Optional<Admin> findAdminByUsername(String username) {
        String sql = "SELECT * FROM admins WHERE username = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
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
                    return Optional.of(admin);
                }
            }
        } catch (SQLException e) {
            logger.error("查找管理员失败", e);
        }
        return Optional.empty();
    }

    public boolean updateLastLogin(String username) {
        String sql = "UPDATE admins SET last_login_at = ? WHERE username = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, username);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.error("更新登录时间失败", e);
            return false;
        }
    }

    public boolean updateAdminPassword(String username, String newPasswordHash) {
        String sql = "UPDATE admins SET password_hash = ? WHERE username = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, newPasswordHash);
            stmt.setString(2, username);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.error("更新密码失败", e);
            return false;
        }
    }

    public boolean deleteAdmin(String username) {
        String sql = "DELETE FROM admins WHERE username = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.error("删除管理员失败", e);
            return false;
        }
    }

    /** 所有已启用且配置了邮箱的管理员地址（用于系统通知群发）。 */
    public List<String> getActiveAdminEmails() {
        List<String> emails = new ArrayList<>();
        String sql = "SELECT email FROM admins WHERE active = 1 AND email IS NOT NULL AND TRIM(email) <> ''";
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String email = rs.getString("email");
                if (email != null && !email.isBlank()) {
                    emails.add(email.trim());
                }
            }
        } catch (SQLException e) {
            logger.error("获取管理员邮箱列表失败", e);
        }
        return emails;
    }

    public List<Admin> getAllAdmins() {
        List<Admin> admins = new ArrayList<>();
        String sql = "SELECT * FROM admins ORDER BY created_at DESC";
        
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Admin admin = new Admin();
                admin.setId(rs.getInt("id"));
                admin.setUsername(rs.getString("username"));
                admin.setPasswordHash(rs.getString("password_hash"));
                admin.setEmail(rs.getString("email"));
                admin.setActive(rs.getBoolean("active"));
                admin.setRole(rs.getString("role"));
                admin.setCreatedAt(rs.getLong("created_at"));
                admin.setLastLoginAt(rs.getLong("last_login_at"));
                admins.add(admin);
            }
        } catch (SQLException e) {
            logger.error("获取所有管理员失败", e);
        }
        return admins;
    }
    
    public boolean adminExists(String username) {
        String sql = "SELECT COUNT(*) FROM admins WHERE username = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("检查管理员是否存在失败", e);
        }
        return false;
    }
    
    /**
     * 检查并更新music表结构，确保包含所有必需的列
     * @param conn 数据库连接
     */
    private void updateMusicTableStructure(Connection conn) {
        try {
            // 检查upload_user_id列是否存在
            boolean hasUploadUserId = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "music", "upload_user_id")) {
                hasUploadUserId = rs.next();
            }
            
            // 检查updated_at列是否存在
            boolean hasUpdatedAt = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "music", "updated_at")) {
                hasUpdatedAt = rs.next();
            }
            
            // 检查language列是否存在
            boolean hasLanguage = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "music", "language")) {
                hasLanguage = rs.next();
            }
            
            // 检查tags列是否存在
            boolean hasTags = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "music", "tags")) {
                hasTags = rs.next();
            }
            
            // 添加缺少的列
            try (Statement stmt = conn.createStatement()) {
                if (!hasUploadUserId) {
                    stmt.execute("ALTER TABLE music ADD COLUMN upload_user_id INT DEFAULT 0");
                    logger.info("已添加upload_user_id列到music表");
                }
                
                if (!hasUpdatedAt) {
                    stmt.execute("ALTER TABLE music ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                    logger.info("已添加updated_at列到music表");
                }
                
                if (!hasLanguage) {
                    stmt.execute("ALTER TABLE music ADD COLUMN language VARCHAR(50) NOT NULL DEFAULT '未知语言'");
                    logger.info("已添加language列到music表");
                }
                
                if (!hasTags) {
                    stmt.execute("ALTER TABLE music ADD COLUMN tags VARCHAR(500)");
                    logger.info("已添加tags列到music表");
                }
            }
        } catch (SQLException e) {
            logger.error("更新music表结构失败", e);
        }
    }
    
    /**
     * 检查并更新admins表结构，确保包含role字段
     * @param conn 数据库连接
     */
    private void updateAdminsTableStructure(Connection conn) {
        try {
            // 检查role列是否存在
            boolean hasRole = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "admins", "role")) {
                hasRole = rs.next();
            }
            
            // 添加缺少的列
            try (Statement stmt = conn.createStatement()) {
                if (!hasRole) {
                    stmt.execute("ALTER TABLE admins ADD COLUMN role ENUM('super_admin', 'admin', 'auditor') DEFAULT 'admin'");
                    logger.info("已添加role列到admins表");
                }
                
                // 检查是否有超级管理员，如果没有，将第一个管理员设置为super_admin
                ResultSet superAdminCheck = stmt.executeQuery("SELECT COUNT(*) FROM admins WHERE role = 'super_admin'");
                if (superAdminCheck.next() && superAdminCheck.getInt(1) == 0) {
                    int updated = stmt.executeUpdate("UPDATE admins SET role = 'super_admin' WHERE id = (SELECT MIN(id) FROM admins)");
                    if (updated > 0) {
                        logger.info("已将第一个管理员设置为super_admin");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("更新admins表结构失败", e);
        }
    }
}