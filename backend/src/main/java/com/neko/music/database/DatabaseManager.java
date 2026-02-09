package com.neko.music.database;

import com.neko.music.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private HikariDataSource dataSource;
    private ConfigManager configManager;

    public DatabaseManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void init() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + configManager.getMysqlHost() + ":" + 
                         configManager.getMysqlPort() + "/" + configManager.getMysqlDatabase() + 
                         "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        config.setUsername(configManager.getMysqlUsername());
        config.setPassword(configManager.getMysqlPassword());
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        this.dataSource = new HikariDataSource(config);
        
        // 初始化数据库表
        initializeTables();
    }

    private void initializeTables() {
        try (Connection conn = dataSource.getConnection()) {
            // 创建用户表
            String createUserTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(50) NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            try (PreparedStatement stmt = conn.prepareStatement(createUserTable)) {
                stmt.execute();
            }
            
            // 创建音乐表
            String createMusicTable = """
                CREATE TABLE IF NOT EXISTS music (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    artist VARCHAR(255) NOT NULL,
                    album VARCHAR(255),
                    duration INT, -- 时长，单位秒
                    file_path VARCHAR(500),
                    cover_path VARCHAR(500),
                    file_format VARCHAR(10) DEFAULT 'mp3', -- 音频文件格式：mp3/flac/wav
                    language VARCHAR(50) NOT NULL DEFAULT '未知语言',
                    tags VARCHAR(500),
                    upload_user_id INT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FOREIGN KEY (upload_user_id) REFERENCES users(id)
                )
                """;
            try (PreparedStatement stmt = conn.prepareStatement(createMusicTable)) {
                stmt.execute();
            }

            // 为已存在的 music 表添加 file_format 字段（如果不存在）
            try {
                String alterTable = """
                    ALTER TABLE music
                    ADD COLUMN IF NOT EXISTS file_format VARCHAR(10) DEFAULT 'mp3'
                    AFTER cover_path
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(alterTable)) {
                    stmt.execute();
                }
                logger.info("已为 music 表添加 file_format 字段");
            } catch (SQLException e) {
                // 字段可能已存在，忽略错误
                logger.debug("file_format 字段可能已存在，跳过添加");
            }

            // 为已存在的 music 表添加 play_count 字段（如果不存在）
            try {
                String alterPlayCountTable = """
                    ALTER TABLE music
                    ADD COLUMN IF NOT EXISTS play_count INT DEFAULT 0
                    AFTER file_format
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(alterPlayCountTable)) {
                    stmt.execute();
                }
                logger.info("已为 music 表添加 play_count 字段");
            } catch (SQLException e) {
                // 字段可能已存在，忽略错误
                logger.debug("play_count 字段可能已存在，跳过添加");
            }

            // 为已存在的 users 表迁移约束（移除username唯一约束，添加email唯一约束）
            try {
                // 检查是否有username的唯一索引
                String checkIndex = """
                    SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                    AND table_name = 'users'
                    AND index_name != 'PRIMARY'
                    AND column_name = 'username'
                    AND non_unique = 0
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(checkIndex);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        // 删除username的唯一索引
                        String dropIndex = "ALTER TABLE users DROP INDEX username";
                        try (PreparedStatement dropStmt = conn.prepareStatement(dropIndex)) {
                            dropStmt.execute();
                            logger.info("已删除 users 表的 username 唯一索引");
                        }
                    }
                }
            } catch (SQLException e) {
                logger.debug("删除 username 唯一索引失败（可能不存在）: {}", e.getMessage());
            }

            try {
                // 检查email字段是否有唯一约束
                String checkEmailUnique = """
                    SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                    AND table_name = 'users'
                    AND index_name != 'PRIMARY'
                    AND column_name = 'email'
                    AND non_unique = 0
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(checkEmailUnique);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        // 添加email的唯一索引
                        String addIndex = "ALTER TABLE users ADD UNIQUE INDEX idx_email (email)";
                        try (PreparedStatement addStmt = conn.prepareStatement(addIndex)) {
                            addStmt.execute();
                            logger.info("已为 users 表的 email 字段添加唯一索引");
                        }
                    }
                }
            } catch (SQLException e) {
                logger.debug("添加 email 唯一索引失败（可能已存在）: {}", e.getMessage());
            }

            try {
                // 检查email字段是否允许NULL
                String checkNull = """
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                    AND table_name = 'users'
                    AND column_name = 'email'
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(checkNull);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && "YES".equals(rs.getString("is_nullable"))) {
                        // 修改email字段为NOT NULL
                        String alterEmail = "ALTER TABLE users MODIFY email VARCHAR(100) UNIQUE NOT NULL";
                        try (PreparedStatement alterStmt = conn.prepareStatement(alterEmail)) {
                            alterStmt.execute();
                            logger.info("已修改 users 表的 email 字段为 NOT NULL");
                        }
                    }
                }
            } catch (SQLException e) {
                logger.debug("修改 email 字段为 NOT NULL 失败（可能已修改）: {}", e.getMessage());
            }

            // 为已存在的 users 表添加 avatar 字段（如果不存在）
            try {
                String alterAvatarTable = """
                    ALTER TABLE users
                    ADD COLUMN IF NOT EXISTS avatar VARCHAR(500) DEFAULT NULL
                    AFTER email
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(alterAvatarTable)) {
                    stmt.execute();
                    logger.info("已为 users 表添加 avatar 字段");
                }
            } catch (SQLException e) {
                logger.debug("avatar 字段可能已存在，跳过添加: {}", e.getMessage());
            }

            // 创建歌单表
            String createPlaylistTable = """
                CREATE TABLE IF NOT EXISTS playlists (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    description VARCHAR(500),
                    music_count INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    INDEX idx_user_id (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            try (PreparedStatement stmt = conn.prepareStatement(createPlaylistTable)) {
                stmt.execute();
                logger.info("playlists 表创建完成");
            } catch (SQLException e) {
                logger.debug("playlists 表可能已存在: {}", e.getMessage());
            }

            // 创建歌单音乐关联表
            String createPlaylistMusicTable = """
                CREATE TABLE IF NOT EXISTS playlist_music (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    playlist_id INT NOT NULL,
                    music_id INT NOT NULL,
                    position INT NOT NULL,
                    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_playlist_music_position (playlist_id, position),
                    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                    FOREIGN KEY (music_id) REFERENCES music(id) ON DELETE CASCADE,
                    INDEX idx_playlist_id (playlist_id),
                    INDEX idx_music_id (music_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            try (PreparedStatement stmt = conn.prepareStatement(createPlaylistMusicTable)) {
                stmt.execute();
                logger.info("playlist_music 表创建完成");
            } catch (SQLException e) {
                logger.debug("playlist_music 表可能已存在: {}", e.getMessage());
            }

            // 删除 playlists 表的 cover_path 字段（如果存在）
            try {
                String checkColumn = """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                    AND table_name = 'playlists'
                    AND column_name = 'cover_path'
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(checkColumn);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        // 删除cover_path字段
                        String dropColumn = "ALTER TABLE playlists DROP COLUMN cover_path";
                        try (PreparedStatement dropStmt = conn.prepareStatement(dropColumn)) {
                            dropStmt.execute();
                            logger.info("已删除 playlists 表的 cover_path 字段");
                        }
                    }
                }
            } catch (SQLException e) {
                logger.debug("删除 cover_path 字段失败（可能不存在）: {}", e.getMessage());
            }
            try (PreparedStatement stmt = conn.prepareStatement(createPlaylistMusicTable)) {
                stmt.execute();
                logger.info("playlist_music 表创建完成");
            } catch (SQLException e) {
                logger.debug("playlist_music 表可能已存在: {}", e.getMessage());
            }

            logger.info("数据库表初始化完成");
        } catch (SQLException e) {
            logger.error("数据库表初始化失败", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}