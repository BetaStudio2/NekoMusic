package com.neko.music.database;

import com.neko.music.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    email VARCHAR(100),
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