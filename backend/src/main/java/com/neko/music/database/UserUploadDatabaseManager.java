package com.neko.music.database;

import com.neko.music.model.UserUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserUploadDatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(UserUploadDatabaseManager.class);
    private static final String APPROVE_UPLOAD_SQL = """
        UPDATE user_uploads
        SET status = 'approved'
        WHERE id = ?
        """;

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
            logger.error("创建用户上传记录失败", e);
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
            logger.error("获取用户上传记录失败", e);
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
            logger.error("获取用户上传记录失败", e);
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
            logger.error("获取上传记录失败", e);
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
     *
     * <p>使用独立连接（自动提交）。需要与其它写操作同事务时，改用
     * {@link #approveUpload(Connection, int)}。
     */
    public boolean approveUpload(int uploadId, int adminId) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(APPROVE_UPLOAD_SQL)) {
            return executeApprove(pstmt, uploadId);
        } catch (SQLException e) {
            logger.error("审核通过失败", e);
            return false;
        }
    }

    /**
     * 审核通过上传，复用调用方连接，使状态更新与同一事务内的其它写操作一起提交/回滚。
     */
    public boolean approveUpload(Connection conn, int uploadId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(APPROVE_UPLOAD_SQL)) {
            return executeApprove(pstmt, uploadId);
        }
    }

    private static boolean executeApprove(PreparedStatement pstmt, int uploadId) throws SQLException {
        pstmt.setInt(1, uploadId);
        return pstmt.executeUpdate() > 0;
    }
    
    /**
     * 删除用户上传记录（只删数据库行，不动文件）。
     *
     * <p>文件删除由调用方在事务提交成功后自行处理：反过来「先删文件再删记录」一旦
     * 删记录失败，就会留下一条永远无法再处理的 pending 记录。
     */
    public boolean deleteUserUpload(int uploadId) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM user_uploads WHERE id = ?")) {
            pstmt.setInt(1, uploadId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除用户上传记录失败", e);
            return false;
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
        upload.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        
        return upload;
    }
}
