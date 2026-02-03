package com.neko.music.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Statement;

public class DatabaseInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    public static void initializeTables(DatabaseManager databaseManager) {
        logger.info("开始初始化数据库表...");
        
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // 创建用户收藏表
            String createFavoritesTable = """
                CREATE TABLE IF NOT EXISTS user_favorites (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    music_id INT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_user_music (user_id, music_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (music_id) REFERENCES music(id) ON DELETE CASCADE,
                    INDEX idx_user_id (user_id),
                    INDEX idx_music_id (music_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            try {
                stmt.execute(createFavoritesTable);
                logger.info("user_favorites 表创建成功");
            } catch (Exception e) {
                logger.warn("创建 user_favorites 表失败（可能是表已存在）: {}", e.getMessage());
            }
            
            // 创建用户token表
            String createTokensTable = """
                CREATE TABLE IF NOT EXISTS user_tokens (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    token VARCHAR(64) NOT NULL UNIQUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    INDEX idx_user_id (user_id),
                    INDEX idx_token (token),
                    INDEX idx_expires_at (expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            try {
                stmt.execute(createTokensTable);
                logger.info("user_tokens 表创建成功");
            } catch (Exception e) {
                logger.warn("创建 user_tokens 表失败（可能是表已存在）: {}", e.getMessage());
            }
            
            // 创建用户收藏歌单表
            String createFavoritePlaylistsTable = """
                CREATE TABLE IF NOT EXISTS user_favorite_playlists (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    playlist_id INT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_user_playlist (user_id, playlist_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                    INDEX idx_user_id (user_id),
                    INDEX idx_playlist_id (playlist_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            try {
                stmt.execute(createFavoritePlaylistsTable);
                logger.info("user_favorite_playlists 表创建成功");
            } catch (Exception e) {
                logger.warn("创建 user_favorite_playlists 表失败（可能是表已存在）: {}", e.getMessage());
            }
            
            logger.info("数据库表初始化完成");
            
        } catch (Exception e) {
            logger.error("初始化数据库表失败: {}", e.getMessage(), e);
        }
    }
}
