package com.neko.music.database;

import com.neko.music.model.UserUpload;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserUploadDatabaseManager {
    private DatabaseManager databaseManager;
    
    public UserUploadDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    /**
     * 创建用户上传记录
     */
    public int createUserUpload(UserUpload upload) {
        String sql = """
            INSERT INTO user_uploads (user_id, title, artist, album, language, tags, duration, music_file_path, cover_file_path, lyrics_file_path, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, upload.getUserId());
            pstmt.setString(2, upload.getTitle());
            pstmt.setString(3, upload.getArtist());
            pstmt.setString(4, upload.getAlbum());
            pstmt.setString(5, upload.getLanguage());
            pstmt.setString(6, upload.getTags());
            pstmt.setInt(7, upload.getDuration());
            pstmt.setString(8, upload.getMusicFilePath());
            pstmt.setString(9, upload.getCoverFilePath());
            pstmt.setString(10, upload.getLyricsFilePath());
            pstmt.setString(11, upload.getStatus());
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return -1;
            
        } catch (SQLException e) {
            System.err.println("创建用户上传记录失败: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * 根据ID获取用户上传记录
     */
    public UserUpload getUserUploadById(int id) {
        String sql = "SELECT * FROM user_uploads WHERE id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapRowToUserUpload(rs);
            }
            return null;
            
        } catch (SQLException e) {
            System.err.println("获取用户上传记录失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取用户的所有上传记录
     */
    public List<UserUpload> getUserUploadsByUserId(int userId) {
        String sql = "SELECT * FROM user_uploads WHERE user_id = ? ORDER BY created_at DESC";
        List<UserUpload> uploads = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                uploads.add(mapRowToUserUpload(rs));
            }
            
        } catch (SQLException e) {
            System.err.println("获取用户上传记录失败: " + e.getMessage());
        }
        
        return uploads;
    }
    
    /**
     * 根据状态获取上传记录
     */
    public List<UserUpload> getUserUploadsByStatus(String status) {
        String sql = "SELECT * FROM user_uploads WHERE status = ? ORDER BY created_at DESC";
        List<UserUpload> uploads = new ArrayList<>();
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, status);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                uploads.add(mapRowToUserUpload(rs));
            }
            
        } catch (SQLException e) {
            System.err.println("获取上传记录失败: " + e.getMessage());
        }
        
        return uploads;
    }
    
    /**
     * 获取所有待审核的上传记录
     */
    public List<UserUpload> getPendingUploads() {
        return getUserUploadsByStatus("pending");
    }
    
    /**
     * 审核通过上传
     */
    public boolean approveUpload(int uploadId, int adminId) {
        String sql = """
            UPDATE user_uploads 
            SET status = 'approved', reviewed_at = ?, reviewed_by_admin_id = ?
            WHERE id = ?
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setInt(2, adminId);
            pstmt.setInt(3, uploadId);
            
            return pstmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            System.err.println("审核通过失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 审核拒绝上传
     */
    public boolean rejectUpload(int uploadId, int adminId, String reason) {
        String sql = """
            UPDATE user_uploads 
            SET status = 'rejected', reject_reason = ?, reviewed_at = ?, reviewed_by_admin_id = ?
            WHERE id = ?
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, reason);
            pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setInt(3, adminId);
            pstmt.setInt(4, uploadId);
            
            return pstmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            System.err.println("审核拒绝失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 删除用户上传记录
     */
    public boolean deleteUserUpload(int uploadId) {
        String sql = "DELETE FROM user_uploads WHERE id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // 先获取上传记录，删除相关文件
            UserUpload upload = getUserUploadById(uploadId);
            if (upload != null) {
                deleteUploadFiles(upload);
            }
            
            pstmt.setInt(1, uploadId);
            return pstmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            System.err.println("删除用户上传记录失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 删除上传的文件
     */
    private void deleteUploadFiles(UserUpload upload) {
        try {
            if (upload.getMusicFilePath() != null) {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(upload.getMusicFilePath()));
            }
            if (upload.getCoverFilePath() != null) {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(upload.getCoverFilePath()));
            }
            if (upload.getLyricsFilePath() != null) {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(upload.getLyricsFilePath()));
            }
        } catch (Exception e) {
            System.err.println("删除文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 将ResultSet映射为UserUpload对象
     */
    private UserUpload mapRowToUserUpload(ResultSet rs) throws SQLException {
        UserUpload upload = new UserUpload();
        upload.setId(rs.getInt("id"));
        upload.setUserId(rs.getInt("user_id"));
        upload.setTitle(rs.getString("title"));
        upload.setArtist(rs.getString("artist"));
        upload.setAlbum(rs.getString("album"));
        upload.setLanguage(rs.getString("language"));
        upload.setTags(rs.getString("tags"));
        upload.setDuration(rs.getInt("duration"));
        upload.setMusicFilePath(rs.getString("music_file_path"));
        upload.setCoverFilePath(rs.getString("cover_file_path"));
        upload.setLyricsFilePath(rs.getString("lyrics_file_path"));
        upload.setStatus(rs.getString("status"));
        upload.setRejectReason(rs.getString("reject_reason"));
        upload.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        if (reviewedAt != null) {
            upload.setReviewedAt(reviewedAt.toLocalDateTime());
        }
        
        upload.setReviewedByAdminId(rs.getInt("reviewed_by_admin_id"));
        
        return upload;
    }
}