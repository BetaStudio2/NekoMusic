package com.neko.music.database;

import com.neko.music.util.LyricsPlainTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public class LyricsDatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(LyricsDatabaseManager.class);

    private final DatabaseManager databaseManager;

    public LyricsDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<StoredLyrics> findByMusicId(int musicId) {
        if (musicId <= 0) {
            return Optional.empty();
        }
        String sql = """
                SELECT music_id, content, plain_text, content_hash, format, source, is_placeholder,
                       size_bytes, created_at, updated_at
                FROM music_lyrics
                WHERE music_id = ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, musicId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapStoredLyrics(rs));
            }
        } catch (SQLException e) {
            logger.warn("读取歌词失败 musicId={}: {}", musicId, e.toString());
            return Optional.empty();
        }
    }

    public List<StoredLyrics> findAllForIndex() {
        String sql = """
                SELECT ml.music_id, ml.content, ml.plain_text, ml.content_hash, ml.format, ml.source,
                       ml.is_placeholder, ml.size_bytes, ml.created_at, ml.updated_at
                FROM music_lyrics ml
                JOIN music m ON m.id = ml.music_id
                ORDER BY ml.music_id
                """;
        List<StoredLyrics> rows = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rows.add(mapStoredLyrics(rs));
            }
        } catch (SQLException e) {
            logger.warn("读取歌词索引数据失败: {}", e.toString());
        }
        return rows;
    }

    public List<AdminLyricsMeta> findAllForAdmin() {
        String sql = """
                SELECT ml.music_id, m.title, m.artist, ml.size_bytes, ml.updated_at, ml.is_placeholder
                FROM music_lyrics ml
                LEFT JOIN music m ON m.id = ml.music_id
                ORDER BY ml.music_id
                """;
        List<AdminLyricsMeta> rows = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Timestamp updatedAt = rs.getTimestamp("updated_at");
                rows.add(new AdminLyricsMeta(
                        rs.getInt("music_id"),
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getLong("size_bytes"),
                        updatedAt,
                        rs.getBoolean("is_placeholder"),
                        rs.getString("title") != null
                ));
            }
        } catch (SQLException e) {
            logger.warn("读取管理端歌词列表失败: {}", e.toString());
        }
        return rows;
    }

    public boolean upsert(int musicId, String content, String source) {
        try (Connection conn = databaseManager.getConnection()) {
            upsert(conn, musicId, content, source);
            return true;
        } catch (SQLException e) {
            logger.warn("保存歌词失败 musicId={}: {}", musicId, e.toString());
            return false;
        }
    }

    public void upsert(Connection conn, int musicId, String content, String source) throws SQLException {
        if (musicId <= 0 || content == null) {
            throw new SQLException("无效的歌词参数");
        }
        String normalizedSource = source == null || source.isBlank() ? "manual" : source.trim();
        String plain = LyricsPlainTextExtractor.fromLrc(content);
        boolean placeholder = LyricsPlainTextExtractor.isPlaceholder(plain);
        int sizeBytes = content.getBytes(StandardCharsets.UTF_8).length;
        String hash = sha256Hex(content);
        String sql = """
                INSERT INTO music_lyrics
                    (music_id, content, plain_text, content_hash, format, source, is_placeholder, size_bytes)
                VALUES (?, ?, ?, ?, 'lrc', ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    content = VALUES(content),
                    plain_text = VALUES(plain_text),
                    content_hash = VALUES(content_hash),
                    format = VALUES(format),
                    source = VALUES(source),
                    is_placeholder = VALUES(is_placeholder),
                    size_bytes = VALUES(size_bytes),
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, musicId);
            stmt.setString(2, content);
            stmt.setString(3, plain);
            stmt.setString(4, hash);
            stmt.setString(5, normalizedSource);
            stmt.setBoolean(6, placeholder);
            stmt.setInt(7, sizeBytes);
            stmt.executeUpdate();
        }
    }

    public boolean delete(int musicId) {
        if (musicId <= 0) {
            return false;
        }
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM music_lyrics WHERE music_id = ?")) {
            stmt.setInt(1, musicId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warn("删除数据库歌词失败 musicId={}: {}", musicId, e.toString());
            return false;
        }
    }

    private static StoredLyrics mapStoredLyrics(ResultSet rs) throws SQLException {
        return new StoredLyrics(
                rs.getInt("music_id"),
                rs.getString("content"),
                rs.getString("plain_text"),
                rs.getString("content_hash"),
                rs.getString("format"),
                rs.getString("source"),
                rs.getBoolean("is_placeholder"),
                rs.getInt("size_bytes"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at")
        );
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record StoredLyrics(
            int musicId,
            String content,
            String plainText,
            String contentHash,
            String format,
            String source,
            boolean placeholder,
            int sizeBytes,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
    }

    public record AdminLyricsMeta(
            int musicId,
            String title,
            String artist,
            long sizeBytes,
            Timestamp updatedAt,
            boolean placeholder,
            boolean existsInDb
    ) {
    }
}
