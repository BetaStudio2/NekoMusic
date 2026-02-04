package com.neko.music.service;

import com.neko.music.database.DatabaseManager;
import com.neko.music.model.Playlist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlaylistService {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistService.class);
    private final DatabaseManager databaseManager;

    public PlaylistService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * 创建歌单
     */
    public Optional<Playlist> createPlaylist(int userId, String name, String description) {
        logger.info("创建歌单: userId={}, name={}", userId, name);

        String sql = "INSERT INTO playlists (user_id, name, description, music_count) VALUES (?, ?, ?, 0)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, userId);
            stmt.setString(2, name);
            stmt.setString(3, description);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int playlistId = generatedKeys.getInt(1);
                        Optional<Playlist> playlistOpt = getPlaylistById(playlistId);
                        if (playlistOpt.isPresent()) {
                            logger.info("歌单创建成功: id={}", playlistId);
                            return playlistOpt;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("创建歌单失败: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * 获取用户的所有歌单
     */
    public List<Playlist> getUserPlaylists(int userId) {
        logger.info("获取用户歌单: userId={}", userId);

        List<Playlist> playlists = new ArrayList<>();
        String sql = "SELECT id, user_id, name, description, music_count, created_at, updated_at " +
                     "FROM playlists WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Playlist playlist = new Playlist();
                playlist.setId(rs.getInt("id"));
                playlist.setUserId(rs.getInt("user_id"));
                playlist.setName(rs.getString("name"));
                playlist.setDescription(rs.getString("description"));
                playlist.setMusicCount(rs.getInt("music_count"));
                playlist.setCreatedAt(rs.getString("created_at"));
                playlist.setUpdatedAt(rs.getString("updated_at"));
                playlists.add(playlist);
            }

            logger.info("获取到 {} 个歌单", playlists.size());
        } catch (SQLException e) {
            logger.error("获取用户歌单失败: {}", e.getMessage(), e);
        }

        return playlists;
    }

    /**
     * 获取所有歌单
     */
    public List<Playlist> getAllPlaylists() {
        logger.info("获取所有歌单");

        List<Playlist> playlists = new ArrayList<>();
        String sql = "SELECT id, user_id, name, description, music_count, created_at, updated_at " +
                     "FROM playlists ORDER BY created_at DESC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Playlist playlist = new Playlist();
                playlist.setId(rs.getInt("id"));
                playlist.setUserId(rs.getInt("user_id"));
                playlist.setName(rs.getString("name"));
                playlist.setDescription(rs.getString("description"));
                playlist.setMusicCount(rs.getInt("music_count"));
                playlist.setCreatedAt(rs.getString("created_at"));
                playlist.setUpdatedAt(rs.getString("updated_at"));
                playlists.add(playlist);
            }

            logger.info("获取到 {} 个歌单", playlists.size());
        } catch (SQLException e) {
            logger.error("获取所有歌单失败: {}", e.getMessage(), e);
        }

        return playlists;
    }

    /**
     * 根据ID获取歌单
     */
    public Optional<Playlist> getPlaylistById(int playlistId) {
        logger.info("获取歌单详情: id={}", playlistId);

        String sql = "SELECT id, user_id, name, description, music_count, created_at, updated_at " +
                     "FROM playlists WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Playlist playlist = new Playlist();
                playlist.setId(rs.getInt("id"));
                playlist.setUserId(rs.getInt("user_id"));
                playlist.setName(rs.getString("name"));
                playlist.setDescription(rs.getString("description"));
                playlist.setMusicCount(rs.getInt("music_count"));
                playlist.setCreatedAt(rs.getString("created_at"));
                playlist.setUpdatedAt(rs.getString("updated_at"));

                logger.info("歌单详情获取成功: id={}", playlistId);
                return Optional.of(playlist);
            }
        } catch (SQLException e) {
            logger.error("获取歌单详情失败: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * 更新歌单信息
     */
    public boolean updatePlaylist(int playlistId, String name, String description) {
        logger.info("更新歌单: id={}, name={}", playlistId, name);

        String sql = "UPDATE playlists SET name = ?, description = ? WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setInt(3, playlistId);

            int affectedRows = stmt.executeUpdate();
            boolean success = affectedRows > 0;

            if (success) {
                logger.info("歌单更新成功: id={}", playlistId);
            } else {
                logger.warn("歌单更新失败: id={}", playlistId);
            }

            return success;
        } catch (SQLException e) {
            logger.error("更新歌单失败: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 添加音乐到歌单
     */
    public boolean addMusicToPlaylist(int playlistId, int musicId) {
        logger.info("添加音乐到歌单: playlistId={}, musicId={}", playlistId, musicId);

        // 检查音乐是否已经在歌单中
        String checkSql = "SELECT COUNT(*) FROM playlist_music WHERE playlist_id = ? AND music_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setInt(1, playlistId);
            checkStmt.setInt(2, musicId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                logger.warn("音乐已存在于歌单中: playlistId={}, musicId={}", playlistId, musicId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("检查音乐是否存在失败: {}", e.getMessage(), e);
            return false;
        }

        // 从最大的position开始倒序更新，避免重复键冲突
        String getMaxPositionSql = "SELECT MAX(position) as max_position FROM playlist_music WHERE playlist_id = ?";
        String insertSql = "INSERT INTO playlist_music (playlist_id, music_id, position) VALUES (?, ?, 1)";

        try (Connection conn = databaseManager.getConnection()) {
            // 开启事务
            conn.setAutoCommit(false);

            try {
                // 获取当前最大的 position
                int maxPosition = 0;
                try (PreparedStatement stmt = conn.prepareStatement(getMaxPositionSql)) {
                    stmt.setInt(1, playlistId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        maxPosition = rs.getInt("max_position");
                    }
                }

                // 从最大的 position 开始倒序更新，每个 position + 1
                // 这样可以避免重复键冲突
                for (int i = maxPosition; i >= 1; i--) {
                    String updateSql = "UPDATE playlist_music SET position = position + 1 WHERE playlist_id = ? AND position = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                        stmt.setInt(1, playlistId);
                        stmt.setInt(2, i);
                        stmt.executeUpdate();
                    }
                }

                // 插入新音乐到 position = 1
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setInt(1, playlistId);
                    stmt.setInt(2, musicId);
                    
                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows == 0) {
                        logger.warn("音乐添加到歌单失败: playlistId={}, musicId={}", playlistId, musicId);
                        conn.rollback();
                        return false;
                    }
                }

                // 提交事务
                conn.commit();
                logger.info("音乐添加到歌单成功: playlistId={}, musicId={}, position=1", playlistId, musicId);
                
                // 更新歌单的音乐数量
                updateMusicCount(playlistId);
                return true;

            } catch (SQLException e) {
                // 发生异常，回滚事务
                conn.rollback();
                logger.error("添加音乐到歌单失败: {}", e.getMessage(), e);
                return false;
            }
        } catch (SQLException e) {
            logger.error("获取数据库连接失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除歌单
     */
    public boolean deletePlaylist(int playlistId) {
        logger.info("删除歌单: id={}", playlistId);

        String sql = "DELETE FROM playlists WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);

            int affectedRows = stmt.executeUpdate();
            boolean success = affectedRows > 0;

            if (success) {
                logger.info("歌单删除成功: id={}", playlistId);
            } else {
                logger.warn("歌单删除失败: id={}", playlistId);
            }

            return success;
        } catch (SQLException e) {
            logger.error("删除歌单失败: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 从歌单中移除音乐
     */
    public boolean removeMusicFromPlaylist(int playlistId, int musicId) {
        logger.info("从歌单中移除音乐: playlistId={}, musicId={}", playlistId, musicId);

        // 先获取要删除的音乐的position
        int removedPosition = -1;
        String getPositionSql = "SELECT position FROM playlist_music WHERE playlist_id = ? AND music_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(getPositionSql)) {

            stmt.setInt(1, playlistId);
            stmt.setInt(2, musicId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                removedPosition = rs.getInt("position");
            } else {
                logger.warn("音乐不存在于歌单中: playlistId={}, musicId={}", playlistId, musicId);
                return false;
            }
        } catch (SQLException e) {
            logger.error("获取音乐position失败: {}", e.getMessage(), e);
            return false;
        }

        // 删除音乐
        String deleteSql = "DELETE FROM playlist_music WHERE playlist_id = ? AND music_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteSql)) {

            stmt.setInt(1, playlistId);
            stmt.setInt(2, musicId);

            int affectedRows = stmt.executeUpdate();
            boolean success = affectedRows > 0;

            if (success) {
                logger.info("音乐从歌单中移除成功: playlistId={}, musicId={}", playlistId, musicId);

                // 重新排序position：将所有position > removedPosition的记录减1
                String updateSql = "UPDATE playlist_music SET position = position - 1 WHERE playlist_id = ? AND position > ?";
                try (Connection updateConn = databaseManager.getConnection();
                     PreparedStatement updateStmt = updateConn.prepareStatement(updateSql)) {

                    updateStmt.setInt(1, playlistId);
                    updateStmt.setInt(2, removedPosition);
                    updateStmt.executeUpdate();
                    logger.info("已重新排序歌单中剩余音乐的position: playlistId={}", playlistId);
                } catch (SQLException e) {
                    logger.error("重新排序position失败: {}", e.getMessage(), e);
                }

                // 更新歌单的音乐数量
                updateMusicCount(playlistId);
            } else {
                logger.warn("音乐从歌单中移除失败: playlistId={}, musicId={}", playlistId, musicId);
            }

            return success;
        } catch (SQLException e) {
            logger.error("从歌单中移除音乐失败: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 检查用户是否是歌单的所有者
     */
    public boolean isPlaylistOwner(int playlistId, int userId) {
        logger.info("检查歌单所有权: playlistId={}, userId={}", playlistId, userId);

        String sql = "SELECT COUNT(*) FROM playlists WHERE id = ? AND user_id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("检查歌单所有权失败: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 更新歌单的音乐数量
     */
    public boolean updateMusicCount(int playlistId) {
        logger.info("更新歌单音乐数量: id={}", playlistId);

        String sql = "UPDATE playlists SET music_count = (SELECT COUNT(*) FROM playlist_music WHERE playlist_id = ?) WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);
            stmt.setInt(2, playlistId);

            int affectedRows = stmt.executeUpdate();
            boolean success = affectedRows > 0;

            if (success) {
                logger.info("歌单音乐数量更新成功: id={}", playlistId);
            }

            return success;
        } catch (SQLException e) {
            logger.error("更新歌单音乐数量失败: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * 获取歌单中的音乐列表
     */
    public List<com.google.gson.JsonObject> getPlaylistMusic(int playlistId) {
        logger.info("获取歌单音乐列表: playlistId={}", playlistId);

        List<com.google.gson.JsonObject> musicList = new ArrayList<>();
        String sql = "SELECT m.id, m.title, m.artist, m.album, m.duration, m.cover_path, m.file_path, m.file_format, m.language, pm.position, pm.added_at " +
                     "FROM playlist_music pm " +
                     "JOIN music m ON pm.music_id = m.id " +
                     "WHERE pm.playlist_id = ? " +
                     "ORDER BY pm.position ASC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                com.google.gson.JsonObject music = new com.google.gson.JsonObject();
                music.addProperty("id", rs.getInt("id"));
                music.addProperty("title", rs.getString("title"));
                music.addProperty("artist", rs.getString("artist"));
                music.addProperty("album", rs.getString("album"));
                music.addProperty("duration", rs.getInt("duration"));
                music.addProperty("coverPath", rs.getString("cover_path"));
                music.addProperty("filePath", rs.getString("file_path"));
                music.addProperty("fileFormat", rs.getString("file_format"));
                music.addProperty("language", rs.getString("language"));
                music.addProperty("position", rs.getInt("position"));
                music.addProperty("addedAt", rs.getString("added_at"));
                musicList.add(music);
            }

            logger.info("获取到 {} 首音乐: playlistId={}", musicList.size(), playlistId);
        } catch (SQLException e) {
            logger.error("获取歌单音乐列表失败: {}", e.getMessage(), e);
        }

        return musicList;
    }

    /**
     * 搜索歌单
     */
    public List<com.google.gson.JsonObject> searchPlaylists(String query) {
        logger.info("搜索歌单: query={}", query);

        List<com.google.gson.JsonObject> results = new ArrayList<>();
        int limit = 50;
        
        try {
            // 判断查询是否是拼音
            boolean isPinyin = com.neko.music.util.PinyinUtil.isLikelyPinyin(query);
            
            if (isPinyin) {
                // 如果是拼音，查询所有歌单（在应用层面进行拼音匹配）
                String sql = "SELECT p.*, " +
                    "(SELECT m.id FROM playlist_music pm JOIN music m ON pm.music_id = m.id " +
                    " WHERE pm.playlist_id = p.id ORDER BY pm.position ASC LIMIT 1) as first_music_id, " +
                    "(SELECT m.cover_path FROM playlist_music pm JOIN music m ON pm.music_id = m.id " +
                    " WHERE pm.playlist_id = p.id ORDER BY pm.position ASC LIMIT 1) as first_music_cover " +
                    "FROM playlists p " +
                    "ORDER BY p.created_at DESC " +
                    "LIMIT 500";
                
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    ResultSet rs = stmt.executeQuery();
                    List<com.google.gson.JsonObject> allPlaylists = new ArrayList<>();
                    
                    while (rs.next()) {
                        com.google.gson.JsonObject playlist = new com.google.gson.JsonObject();
                        playlist.addProperty("id", rs.getInt("id"));
                        playlist.addProperty("userId", rs.getInt("user_id"));
                        playlist.addProperty("name", rs.getString("name"));
                        
                        // 处理可能为 null 的 description
                        String desc = rs.getString("description");
                        playlist.addProperty("description", desc != null ? desc : "");
                        
                        playlist.addProperty("musicCount", rs.getInt("music_count"));
                        playlist.addProperty("createdAt", rs.getString("created_at"));
                        playlist.addProperty("updatedAt", rs.getString("updated_at"));
                        
                        // 第一首音乐的封面 URL
                        int firstMusicId = rs.getInt("first_music_id");
                        if (firstMusicId > 0) {
                            playlist.addProperty("firstMusicId", firstMusicId);
                            playlist.addProperty("firstMusicCover", rs.getString("first_music_cover"));
                        } else {
                            playlist.addProperty("firstMusicCover", "/api/user/avatar/default");
                        }
                        
                        allPlaylists.add(playlist);
                    }
                    
                    // 在内存中进行混合匹配（拼音+中文）
                    for (com.google.gson.JsonObject playlist : allPlaylists) {
                        if (matchPlaylistMixedInput(playlist, query)) {
                            results.add(playlist);
                            if (results.size() >= limit) {
                                break;
                            }
                        }
                    }
                }
            } else {
                // 如果不是拼音，使用正常的繁简体搜索
                List<String> variants = com.neko.music.util.ChineseConverter.getFullSearchVariants(query);
                
                // 构建 SQL 查询，支持繁简体搜索
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("SELECT p.*, ");
                sqlBuilder.append("(SELECT m.id FROM playlist_music pm JOIN music m ON pm.music_id = m.id ");
                sqlBuilder.append(" WHERE pm.playlist_id = p.id ORDER BY pm.position ASC LIMIT 1) as first_music_id, ");
                sqlBuilder.append("(SELECT m.cover_path FROM playlist_music pm JOIN music m ON pm.music_id = m.id ");
                sqlBuilder.append(" WHERE pm.playlist_id = p.id ORDER BY pm.position ASC LIMIT 1) as first_music_cover ");
                sqlBuilder.append("FROM playlists p ");
                sqlBuilder.append("WHERE (");
                
                List<String> conditions = new ArrayList<>();
                for (int i = 0; i < variants.size(); i++) {
                    conditions.add("(p.name LIKE ? OR p.description LIKE ?)");
                }
                sqlBuilder.append(String.join(" OR ", conditions));
                sqlBuilder.append(") ");
                sqlBuilder.append("ORDER BY p.created_at DESC ");
                sqlBuilder.append("LIMIT ?");
                
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                    
                    // 设置参数
                    int paramIndex = 1;
                    for (String variant : variants) {
                        stmt.setString(paramIndex++, "%" + variant + "%");
                        stmt.setString(paramIndex++, "%" + variant + "%");
                    }
                    stmt.setInt(paramIndex, limit);
                    
                    ResultSet rs = stmt.executeQuery();
                    
                    while (rs.next()) {
                        com.google.gson.JsonObject playlist = new com.google.gson.JsonObject();
                        playlist.addProperty("id", rs.getInt("id"));
                        playlist.addProperty("userId", rs.getInt("user_id"));
                        playlist.addProperty("name", rs.getString("name"));
                        playlist.addProperty("description", rs.getString("description"));
                        playlist.addProperty("musicCount", rs.getInt("music_count"));
                        playlist.addProperty("createdAt", rs.getString("created_at"));
                        playlist.addProperty("updatedAt", rs.getString("updated_at"));
                        
                        // 第一首音乐的封面 URL
                        int firstMusicId = rs.getInt("first_music_id");
                        if (firstMusicId > 0) {
                            playlist.addProperty("firstMusicId", firstMusicId);
                            playlist.addProperty("firstMusicCover", rs.getString("first_music_cover"));
                        } else {
                            playlist.addProperty("firstMusicCover", "/api/user/avatar/default");
                        }
                        
                        results.add(playlist);
                    }
                }
            }
            
            logger.info("搜索到 {} 个歌单: query={}", results.size(), query);
        } catch (SQLException e) {
            logger.error("搜索歌单失败: {}", e.getMessage(), e);
        }

        return results;
    }
    
    /**
     * 检查歌单是否匹配混合输入（拼音+中文）
     */
    private boolean matchPlaylistMixedInput(com.google.gson.JsonObject playlist, String query) {
        // 检查歌单名称
        if (playlist.has("name") && !playlist.get("name").isJsonNull()) {
            String name = playlist.get("name").getAsString();
            if (matchFieldMixedInput(name, query)) {
                return true;
            }
        }
        // 检查描述
        if (playlist.has("description") && !playlist.get("description").isJsonNull()) {
            String description = playlist.get("description").getAsString();
            if (matchFieldMixedInput(description, query)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查单个字段是否匹配混合输入
     * 支持以下匹配方式：
     * 1. 纯拼音：hddjp 匹配 豪大大鸡排
     * 2. 完整拼音：haodadajipai 匹配 豪大大鸡排
     * 3. 混合输入：hao大大鸡排 匹配 豪大大鸡排
     * 4. 中文匹配：豪大大鸡排 匹配 豪大大鸡排
     */
    private boolean matchFieldMixedInput(String field, String query) {
        if (field == null || field.isEmpty()) {
            return false;
        }
        
        String queryLower = query.toLowerCase();
        
        // 首先检查是否是混合输入（同时包含拼音和中文）
        String pinyinPart = extractPinyinPart(query);
        String chinesePart = extractChinesePart(query);
        boolean isMixedInput = !pinyinPart.isEmpty() && !chinesePart.isEmpty();
        
        if (isMixedInput) {
            // 处理混合输入（如 hao大大鸡排）
            String fieldPinyin = com.neko.music.util.PinyinUtil.getPinyin(field);
            String fieldInitials = com.neko.music.util.PinyinUtil.getPinyinInitials(field);
            
            String pinyinPartLower = pinyinPart.toLowerCase();
            
            // 检查拼音部分是否匹配（宽松匹配：可以是前缀、包含等）
            boolean pinyinMatch = fieldPinyin.contains(pinyinPartLower) || 
                                  fieldInitials.contains(pinyinPartLower) ||
                                  fieldPinyin.startsWith(pinyinPartLower) ||
                                  fieldInitials.startsWith(pinyinPartLower);
            
            // 检查中文部分是否匹配（宽松匹配：可以是子串）
            boolean chineseMatch = field.contains(chinesePart);
            
            // 严格匹配：拼音和中文都要匹配
            if (pinyinMatch && chineseMatch) {
                return true;
            }
            
            // 宽松匹配：只要拼音匹配或中文匹配即可
            // 例如：hao大大鸡排 可能只想匹配拼音 hao 开头的，或者包含 大大鸡排 的
            if (pinyinMatch || chineseMatch) {
                return true;
            }
        }
        
        // 1. 直接匹配（中文或英文）
        if (field.toLowerCase().contains(queryLower)) {
            return true;
        }
        
        // 2. 获取字段的拼音变体
        java.util.Set<String> variants = com.neko.music.util.PinyinUtil.getPinyinVariants(field);
        
        // 3. 检查查询字符串是否匹配任何拼音变体
        for (String variant : variants) {
            if (variant.contains(queryLower)) {
                return true;
            }
        }
        
        // 4. 检查拼音首字母匹配（如 hddjp）
        String fieldInitials = com.neko.music.util.PinyinUtil.getPinyinInitials(field);
        if (fieldInitials.contains(queryLower)) {
            return true;
        }
        
        // 5. 检查完整拼音匹配
        String fieldPinyin = com.neko.music.util.PinyinUtil.getPinyin(field);
        if (fieldPinyin.contains(queryLower)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 从混合字符串中提取拼音部分
     */
    private String extractPinyinPart(String str) {
        StringBuilder pinyinPart = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isLetter(c)) {
                pinyinPart.append(c);
            }
        }
        return pinyinPart.toString();
    }
    
    /**
     * 从混合字符串中提取中文部分
     */
    private String extractChinesePart(String str) {
        StringBuilder chinesePart = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (isChinese(c)) {
                chinesePart.append(c);
            }
        }
        return chinesePart.toString();
    }
    
    /**
     * 判断字符是否是中文字符
     */
    private boolean isChinese(char c) {
        return (c >= 0x4E00 && c <= 0x9FA5) || 
               (c >= 0x3400 && c <= 0x4DBF);
    }
}