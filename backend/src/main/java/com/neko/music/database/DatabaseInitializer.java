package com.neko.music.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

            // 强制重算所有首字母列（getPinyinInitials和getWordInitials逻辑已变更，需全量更新）
            try {
                String selectAll = "SELECT id, title, artist FROM music";
                String updateInitials = "UPDATE music SET title_pinyin_initials=?, title_word_initials=?, artist_pinyin_initials=?, artist_word_initials=? WHERE id=?";
                try (var selectStmt = conn.prepareStatement(selectAll);
                     var rs = selectStmt.executeQuery();
                     var updateStmt = conn.prepareStatement(updateInitials)) {
                    int count = 0;
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String title = rs.getString("title");
                        String artist = rs.getString("artist");
                        updateStmt.setString(1, com.neko.music.util.PinyinUtil.getPinyinInitials(title));
                        updateStmt.setString(2, com.neko.music.util.PinyinUtil.getWordInitials(title));
                        updateStmt.setString(3, com.neko.music.util.PinyinUtil.getPinyinInitials(artist));
                        updateStmt.setString(4, com.neko.music.util.PinyinUtil.getWordInitials(artist));
                        updateStmt.setInt(5, id);
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
                        logger.info("已重算 {} 条音乐记录的首字母索引（中文/英文分离）", count);
                    }
                }
            } catch (Exception e) {
                logger.warn("重算首字母索引失败: {}", e.getMessage());
            }

            // 回填歌单拼音列
            try {
                String selectNull = "SELECT id, name FROM playlists WHERE name_pinyin IS NULL";
                String updatePinyin = "UPDATE playlists SET name_pinyin=?, name_pinyin_initials=?, name_word_initials=? WHERE id=?";
                try (var selectStmt = conn.prepareStatement(selectNull);
                     var rs = selectStmt.executeQuery();
                     var updateStmt = conn.prepareStatement(updatePinyin)) {
                    int count = 0;
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String name = rs.getString("name");
                        updateStmt.setString(1, com.neko.music.util.PinyinUtil.getPinyin(name));
                        updateStmt.setString(2, com.neko.music.util.PinyinUtil.getPinyinInitials(name));
                        updateStmt.setString(3, com.neko.music.util.PinyinUtil.getWordInitials(name));
                        updateStmt.setInt(4, id);
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
                        logger.info("已回填 {} 条歌单记录的拼音索引", count);
                    }
                }
            } catch (Exception e) {
                logger.warn("回填歌单拼音索引失败: {}", e.getMessage());
            }

            // 强制重算歌单首字母列（逻辑变更）
            try {
                String selectAll = "SELECT id, name FROM playlists";
                String updateInitials = "UPDATE playlists SET name_pinyin_initials=?, name_word_initials=? WHERE id=?";
                try (var selectStmt = conn.prepareStatement(selectAll);
                     var rs = selectStmt.executeQuery();
                     var updateStmt = conn.prepareStatement(updateInitials)) {
                    int count = 0;
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String name = rs.getString("name");
                        updateStmt.setString(1, com.neko.music.util.PinyinUtil.getPinyinInitials(name));
                        updateStmt.setString(2, com.neko.music.util.PinyinUtil.getWordInitials(name));
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
                        logger.info("已重算 {} 条歌单记录的首字母索引（中文/英文分离）", count);
                    }
                }
            } catch (Exception e) {
                logger.warn("重算歌单首字母索引失败: {}", e.getMessage());
            }

            // VIP 价目表（与主库一并备份）
            String createVipPricing = """
                CREATE TABLE IF NOT EXISTS vip_pricing (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    months INT NOT NULL DEFAULT 0,
                    days INT NOT NULL DEFAULT 0,
                    price_yuan DECIMAL(12,2) NOT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    KEY idx_vip_pricing_sort (sort_order, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            try {
                stmt.execute(createVipPricing);
                logger.info("vip_pricing 表已就绪");
            } catch (Exception e) {
                logger.warn("创建 vip_pricing 表失败（可能已存在）: {}", e.getMessage());
            }

            String createVipPayOrders = """
                CREATE TABLE IF NOT EXISTS vip_pay_orders (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    out_trade_no VARCHAR(32) NOT NULL,
                    user_id INT NOT NULL,
                    pricing_id INT NULL,
                    months INT NOT NULL DEFAULT 0,
                    days INT NOT NULL DEFAULT 0,
                    money DECIMAL(12,2) NOT NULL,
                    pay_type VARCHAR(16) NOT NULL,
                    status ENUM('pending','paid','cancelled') NOT NULL DEFAULT 'pending',
                    zpay_trade_no VARCHAR(128) NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    paid_at TIMESTAMP NULL DEFAULT NULL,
                    UNIQUE KEY uk_vip_pay_out_trade_no (out_trade_no),
                    KEY idx_vip_pay_user_created (user_id, created_at),
                    CONSTRAINT fk_vip_pay_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            try {
                stmt.execute(createVipPayOrders);
                logger.info("vip_pay_orders 表已就绪");
            } catch (Exception e) {
                logger.warn("创建 vip_pay_orders 表失败（可能已存在）: {}", e.getMessage());
            }

            migrateAppReleaseTable(conn, stmt);

            logger.info("数据库表初始化完成");
            
        } catch (Exception e) {
            logger.error("初始化数据库表失败: {}", e.getMessage(), e);
        }
    }

    /** 单行版本表：无 id，仅 android_ver / pc_ver */
    private static void migrateAppReleaseTable(Connection conn, Statement stmt) {
        try {
            boolean tableExists = false;
            boolean hasIdColumn = false;
            String listCols = """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'app_release'
                """;
            try (PreparedStatement ps = conn.prepareStatement(listCols);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableExists = true;
                    if ("id".equalsIgnoreCase(rs.getString(1))) {
                        hasIdColumn = true;
                    }
                }
            }

            if (tableExists && hasIdColumn) {
                String androidVer = null;
                String pcVer = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT android_ver, pc_ver FROM app_release ORDER BY id LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        androidVer = rs.getString("android_ver");
                        pcVer = rs.getString("pc_ver");
                    }
                }
                stmt.execute("DROP TABLE app_release");
                logger.info("已删除带 id 的旧 app_release 表，准备重建");
                createAppReleaseTable(stmt);
                if (androidVer != null && !androidVer.isBlank() && pcVer != null && !pcVer.isBlank()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO app_release (android_ver, pc_ver) VALUES (?, ?)")) {
                        ins.setString(1, androidVer.trim());
                        ins.setString(2, pcVer.trim());
                        ins.executeUpdate();
                        logger.info("已迁移 app_release 版本数据");
                    }
                }
                return;
            }

            if (!tableExists) {
                createAppReleaseTable(stmt);
                logger.info("app_release 表已创建");
                return;
            }

            try {
                stmt.execute("""
                    ALTER TABLE app_release
                    ADD COLUMN IF NOT EXISTS pc_ver VARCHAR(64) NOT NULL DEFAULT '' AFTER android_ver
                    """);
            } catch (Exception e) {
                logger.debug("app_release 添加 pc_ver 列: {}", e.getMessage());
            }

            try {
                stmt.execute("""
                    ALTER TABLE app_release
                    ADD COLUMN IF NOT EXISTS pending_android_ver VARCHAR(64) NULL AFTER pc_ver
                    """);
                stmt.execute("""
                    ALTER TABLE app_release
                    ADD COLUMN IF NOT EXISTS pending_pc_ver VARCHAR(64) NULL AFTER pending_android_ver
                    """);
                stmt.execute("""
                    ALTER TABLE app_release
                    ADD COLUMN IF NOT EXISTS pending_effective_at TIMESTAMP NULL AFTER pending_pc_ver
                    """);
            } catch (Exception e) {
                logger.debug("app_release 添加 pending 列: {}", e.getMessage());
            }
            logger.info("app_release 表已就绪");
        } catch (Exception e) {
            logger.warn("app_release 表迁移失败: {}", e.getMessage());
        }
    }

    private static void createAppReleaseTable(Statement stmt) throws Exception {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS app_release (
                android_ver VARCHAR(64) NOT NULL,
                pc_ver VARCHAR(64) NOT NULL,
                pending_android_ver VARCHAR(64) NULL,
                pending_pc_ver VARCHAR(64) NULL,
                pending_effective_at TIMESTAMP NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
    }
}
