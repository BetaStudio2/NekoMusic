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
        String sql = "SELECT id, user_id, name, description, cover_path, music_count, created_at, updated_at " +
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
                playlist.setCoverPath(rs.getString("cover_path"));
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
     * 根据ID获取歌单
     */
    public Optional<Playlist> getPlaylistById(int playlistId) {
        logger.info("获取歌单详情: id={}", playlistId);

        String sql = "SELECT id, user_id, name, description, cover_path, music_count, created_at, updated_at " +
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
                playlist.setCoverPath(rs.getString("cover_path"));
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
}
}