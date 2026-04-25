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
            
            // 创建用户上传审核表
            String createUserUploadsTable = """
                CREATE TABLE IF NOT EXISTS user_uploads (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    artist VARCHAR(255) NOT NULL,
                    album VARCHAR(255),
                    language VARCHAR(50) NOT NULL,
                    tags VARCHAR(255),
                    duration INT DEFAULT 0,
                    music_file_path VARCHAR(512) NOT NULL,
                    cover_file_path VARCHAR(512),
                    lyrics_file_path VARCHAR(512),
                    status ENUM('pending', 'approved', 'rejected') DEFAULT 'pending',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    INDEX idx_user_id (user_id),
                    INDEX idx_status (status),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            try {
                stmt.execute(createUserUploadsTable);
                logger.info("user_uploads 表创建成功");
            } catch (Exception e) {
                logger.warn("创建 user_uploads 表失败（可能是表已存在）: {}", e.getMessage());
            }
            
            // 回填拼音索引列：为 title_pinyin 等字段为 NULL 的记录计算拼音
            try {
                String selectNull = "SELECT id, title, artist, album FROM music WHERE title_pinyin IS NULL";
                String updatePinyin = "UPDATE music SET title_pinyin=?, title_pinyin_initials=?, artist_pinyin=?, artist_pinyin_initials=?, album_pinyin=?, title_word_initials=?, artist_word_initials=? WHERE id=?";
                try (var selectStmt = conn.prepareStatement(selectNull);
                     var rs = selectStmt.executeQuery();
                     var updateStmt = conn.prepareStatement(updatePinyin)) {
                    int count = 0;
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String title = rs.getString("title");
                        String artist = rs.getString("artist");
                        String album = rs.getString("album");
                        updateStmt.setString(1, com.neko.music.util.PinyinUtil.getPinyin(title));
                        updateStmt.setString(2, com.neko.music.util.PinyinUtil.getPinyinInitials(title));
                        updateStmt.setString(3, com.neko.music.util.PinyinUtil.getPinyin(artist));
                        updateStmt.setString(4, com.neko.music.util.PinyinUtil.getPinyinInitials(artist));
                        updateStmt.setString(5, album != null ? com.neko.music.util.PinyinUtil.getPinyin(album) : "");
                        updateStmt.setString(6, com.neko.music.util.PinyinUtil.getWordInitials(title));
                        updateStmt.setString(7, com.neko.music.util.PinyinUtil.getWordInitials(artist));
                        updateStmt.setInt(8, id);
                        updateStmt.addBatch();
                        count++;
                        if (count % 100 == 0) {
                            updateStmt.executeBatch();
                        }
                    }
                    if (count % 100 != 0) {
                        updateStmt.executeBatch();
                    }
                    if (count > 0) {
                        logger.info("已回填 {} 条音乐记录的拼音索引", count);
                    }
                }
            } catch (Exception e) {
                logger.warn("回填拼音索引失败: {}", e.getMessage());
            }

            // 回填词首字母列：为 title_word_initials 为 NULL 的记录计算词首字母
            try {
                String selectNull = "SELECT id, title, artist FROM music WHERE title_word_initials IS NULL";
                String updateWordInitials = "UPDATE music SET title_word_initials=?, artist_word_initials=? WHERE id=?";
                try (var selectStmt = conn.prepareStatement(selectNull);
                     var rs = selectStmt.executeQuery();
                     var updateStmt = conn.prepareStatement(updateWordInitials)) {
                    int count = 0;
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String title = rs.getString("title");
                        String artist = rs.getString("artist");
                        updateStmt.setString(1, com.neko.music.util.PinyinUtil.getWordInitials(title));
                        updateStmt.setString(2, com.neko.music.util.PinyinUtil.getWordInitials(artist));
                        updateStmt.setInt(3, id);
                        updateStmt.addBatch();
                        count++;
                        if (count % 100 == 0) {
                            updateStmt.executeBatch();
                        }
                    }
                    if (count % 100 != 0) {
                        updateStmt.executeBatch();
                    }
                    if (count > 0) {
                        logger.info("已回填 {} 条音乐记录的词首字母索引", count);
                    }
                }
            } catch (Exception e) {
                logger.warn("回填词首字母索引失败: {}", e.getMessage());
            }
            
            logger.info("数据库表初始化完成");
            
        } catch (Exception e) {
            logger.error("初始化数据库表失败: {}", e.getMessage(), e);
        }
    }
}
