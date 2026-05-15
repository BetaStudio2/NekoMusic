package com.neko.music.database;

import com.neko.music.model.VideoRenderJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;

public class VideoRenderDatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(VideoRenderDatabaseManager.class);

    private final DatabaseManager databaseManager;

    public VideoRenderDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insertPending(VideoRenderJob job) throws SQLException {
        String sql = """
                INSERT INTO video_render_jobs
                (id, download_token, user_id, music_id, start_sec, duration_sec, watermarked, status)
                VALUES (?,?,?,?,?,?,?, 'pending')
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, job.getId());
            ps.setString(2, job.getDownloadToken());
            ps.setInt(3, job.getUserId());
            ps.setInt(4, job.getMusicId());
            ps.setDouble(5, job.getStartSec());
            ps.setDouble(6, job.getDurationSec());
            ps.setBoolean(7, job.isWatermarked());
            ps.executeUpdate();
        }
    }

    public void ensureDownloadToken(String jobId, String downloadToken) throws SQLException {
        String sql = """
                UPDATE video_render_jobs
                SET download_token = ?
                WHERE id = ? AND (download_token IS NULL OR download_token = '')
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, downloadToken);
            ps.setString(2, jobId);
            ps.executeUpdate();
        }
    }

    public Optional<VideoRenderJob> findByDownloadToken(String downloadToken) {
        if (downloadToken == null || downloadToken.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM video_render_jobs WHERE download_token = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, downloadToken.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("按 download_token 查询视频任务失败", e);
        }
        return Optional.empty();
    }

    public void markProcessing(String jobId) throws SQLException {
        updateStatus(jobId, "processing", null, null);
    }

    public void markDone(String jobId, String outputRelPath) throws SQLException {
        String sql = """
                UPDATE video_render_jobs
                SET status = 'done', output_rel_path = ?, finished_at = CURRENT_TIMESTAMP, error_message = NULL
                WHERE id = ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, outputRelPath);
            ps.setString(2, jobId);
            ps.executeUpdate();
        }
    }

    public void markFailed(String jobId, String errorMessage) {
        try {
            updateStatus(jobId, "failed", null, errorMessage);
        } catch (SQLException e) {
            logger.error("标记视频任务失败状态时出错 jobId={}", jobId, e);
        }
    }

    private void updateStatus(String jobId, String status, String outputRelPath, String errorMessage) throws SQLException {
        String sql = """
                UPDATE video_render_jobs
                SET status = ?, output_rel_path = ?, error_message = ?,
                    finished_at = CASE WHEN ? IN ('done','failed') THEN CURRENT_TIMESTAMP ELSE finished_at END
                WHERE id = ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            if (outputRelPath != null) {
                ps.setString(2, outputRelPath);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            if (errorMessage != null) {
                ps.setString(3, errorMessage);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.setString(4, status);
            ps.setString(5, jobId);
            ps.executeUpdate();
        }
    }

    public Optional<VideoRenderJob> findById(String jobId) {
        return findByIdAndUserId(jobId, null);
    }

    public Optional<VideoRenderJob> findByIdAndUserId(String jobId, Integer userId) {
        String sql = userId == null
                ? "SELECT * FROM video_render_jobs WHERE id = ?"
                : "SELECT * FROM video_render_jobs WHERE id = ? AND user_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            if (userId != null) {
                ps.setInt(2, userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("查询视频任务失败 jobId={}", jobId, e);
        }
        return Optional.empty();
    }

    private static VideoRenderJob mapRow(ResultSet rs) throws SQLException {
        VideoRenderJob job = new VideoRenderJob();
        job.setId(rs.getString("id"));
        job.setDownloadToken(rs.getString("download_token"));
        job.setUserId(rs.getInt("user_id"));
        job.setMusicId(rs.getInt("music_id"));
        job.setStartSec(rs.getDouble("start_sec"));
        job.setDurationSec(rs.getDouble("duration_sec"));
        job.setWatermarked(rs.getBoolean("watermarked"));
        job.setStatus(rs.getString("status"));
        job.setErrorMessage(rs.getString("error_message"));
        job.setOutputRelPath(rs.getString("output_rel_path"));
        job.setCreatedAt(rs.getTimestamp("created_at"));
        job.setFinishedAt(rs.getTimestamp("finished_at"));
        return job;
    }
}
