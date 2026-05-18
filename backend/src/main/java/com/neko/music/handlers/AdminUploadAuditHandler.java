package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.model.UserUpload;
import com.neko.music.util.PinyinUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminUploadAuditHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminUploadAuditHandler.class);
    private static final String MUSIC_DIR = "Music";
    private static final String MUSIC_AUDIO_DIR = "Music/music";
    private static final String MUSIC_COVERS_DIR = "Music/covers";
    private static final String MUSIC_LYRICS_DIR = "Music/lyrics";
    private static final String UPLOAD_DIR = "user_upload";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 验证管理员权限
        if (!verifyAdmin(request, response)) {
            return;
        }
        
        // 检查是否有审核查看权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.AUDIT_VIEW)) {
            logger.warn("权限不足，无审核查看权限");
            return;
        }
        
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/pending")) {
            // 获取待审核列表
            handleGetPendingUploads(response);
        } else {
            sendError(response, 404, "请求的资源不存在");
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 验证管理员权限
        if (!verifyAdmin(request, response)) {
            return;
        }
        
        String pathInfo = request.getPathInfo();
        
        if (pathInfo != null && pathInfo.startsWith("/approve/")) {
            // 审核通过
            // 检查是否有审核通过权限
            if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.AUDIT_APPROVE)) {
                logger.warn("权限不足，无审核通过权限");
                return;
            }
            int uploadId = Integer.parseInt(pathInfo.substring(9));
            handleApproveUpload(uploadId, request, response);
        } else if (pathInfo != null && pathInfo.startsWith("/reject/")) {
            // 审核拒绝
            // 检查是否有审核拒绝权限
            if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.AUDIT_REJECT)) {
                logger.warn("权限不足，无审核拒绝权限");
                return;
            }
            int uploadId = Integer.parseInt(pathInfo.substring(8));
            handleRejectUpload(uploadId, request, response);
        } else {
            sendError(response, 404, "请求的资源不存在");
        }
    }
    
    /**
     * 验证管理员权限
     */
    private boolean verifyAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401, "未授权访问");
            return false;
        }
        
        String token = authHeader.substring(7);
        boolean isValid = Main.getAdminAuthService().validateAdminToken(token);
        if (!isValid) {
            sendError(response, 401, "未授权访问");
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取待审核列表
     */
    private void handleGetPendingUploads(HttpServletResponse response) throws IOException {
        try {
            String sql = """
                SELECT id, user_id, title, artist, album, language, tags, duration,
                       music_file_path, cover_file_path, lyrics_file_path, status, created_at
                FROM user_uploads
                WHERE status = 'pending'
                ORDER BY created_at DESC
                """;
            
            List<Map<String, Object>> pendingUploads = new ArrayList<>();
            
            try (Connection conn = Main.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 java.sql.ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    Map<String, Object> upload = new HashMap<>();
                    upload.put("id", rs.getInt("id"));
                    upload.put("userId", rs.getInt("user_id"));
                    upload.put("title", rs.getString("title"));
                    upload.put("artist", rs.getString("artist"));
                    upload.put("album", rs.getString("album"));
                    upload.put("language", rs.getString("language"));
                    upload.put("tags", rs.getString("tags"));
                    upload.put("duration", rs.getInt("duration"));
                    upload.put("musicFilePath", rs.getString("music_file_path"));
                    upload.put("coverFilePath", rs.getString("cover_file_path"));
                    upload.put("lyricsFilePath", rs.getString("lyrics_file_path"));
                    upload.put("status", rs.getString("status"));
                    
                    // 将 Timestamp 转换为字符串
                    java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                    upload.put("createdAt", createdAt != null ? createdAt.toString() : null);
                    
                    pendingUploads.add(upload);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pendingUploads);
            
            sendJsonResponse(response, result);
            logger.info("获取待审核列表成功，共 {} 条记录", pendingUploads.size());
            
        } catch (Exception e) {
            logger.error("获取待审核列表失败: " + e.getMessage(), e);
            sendError(response, 500, "服务器错误: " + e.getMessage());
        }
    }
    
    /**
     * 审核通过 - 将文件从审核目录迁移到正常目录，并插入到music表
     */
    private void handleApproveUpload(int uploadId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Connection conn = null;
        int musicId = 0;
        String newMusicPath = null;
        String newCoverPath = null;
        String newLyricsPath = null;
        
        try {
            // 获取管理员ID
            String authHeader = request.getHeader("Authorization");
            String token = authHeader.substring(7);
            int adminId = getAdminIdByToken(token);
            
            if (adminId <= 0) {
                sendError(response, 401, "未授权访问");
                return;
            }
            
            // 获取上传记录
            com.neko.music.database.UserUploadDatabaseManager uploadManager = 
                new com.neko.music.database.UserUploadDatabaseManager(Main.getDatabaseManager());
            
            UserUpload upload = uploadManager.getUserUploadById(uploadId);
            if (upload == null) {
                sendError(response, 404, "上传记录不存在");
                return;
            }
            
            if (!"pending".equals(upload.getStatus())) {
                sendError(response, 400, "该记录已被审核，无需重复操作");
                return;
            }
            
            // 创建Music目录及三个子目录
            File audioDir = new File(MUSIC_AUDIO_DIR);
            File coversDir = new File(MUSIC_COVERS_DIR);
            File lyricsDir = new File(MUSIC_LYRICS_DIR);
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }
            if (!coversDir.exists()) {
                coversDir.mkdirs();
            }
            if (!lyricsDir.exists()) {
                lyricsDir.mkdirs();
            }

            // 使用事务确保原子性
            conn = Main.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
            
            // 先迁移文件到临时位置，避免并发冲突
            String tempMusicFileName = "temp_" + uploadId + getFileExtension(upload.getMusicFilePath());
            String tempCoverFileName = "temp_" + uploadId + "_cover.jpg";
            String tempLyricsFileName = "temp_" + uploadId + "_lyrics.lrc";
            
            String tempMusicPath = Paths.get(MUSIC_AUDIO_DIR, tempMusicFileName).toString();
            String tempCoverPath = Paths.get(MUSIC_COVERS_DIR, tempCoverFileName).toString();
            String tempLyricsPath = Paths.get(MUSIC_LYRICS_DIR, tempLyricsFileName).toString();
            
            // 验证源文件存在并迁移到临时位置
            if (!Files.exists(Paths.get(upload.getMusicFilePath()))) {
                conn.rollback();
                sendError(response, 500, "音乐文件不存在: " + upload.getMusicFilePath());
                return;
            }
            
            Files.move(Paths.get(upload.getMusicFilePath()), Paths.get(tempMusicPath), StandardCopyOption.REPLACE_EXISTING);
            logger.info("迁移音乐文件到临时位置: {} -> {}", upload.getMusicFilePath(), tempMusicPath);
            
            // 迁移封面文件到临时位置（如果有）
            if (upload.getCoverFilePath() != null && !upload.getCoverFilePath().isEmpty()) {
                if (!Files.exists(Paths.get(upload.getCoverFilePath()))) {
                    conn.rollback();
                    // 回滚音乐文件
                    Files.move(Paths.get(tempMusicPath), Paths.get(upload.getMusicFilePath()), StandardCopyOption.REPLACE_EXISTING);
                    sendError(response, 500, "封面文件不存在: " + upload.getCoverFilePath());
                    return;
                }
                Files.move(Paths.get(upload.getCoverFilePath()), Paths.get(tempCoverPath), StandardCopyOption.REPLACE_EXISTING);
                logger.info("迁移封面文件到临时位置: {} -> {}", upload.getCoverFilePath(), tempCoverPath);
            }
            
            // 迁移歌词文件到临时位置（如果有）
            if (upload.getLyricsFilePath() != null && !upload.getLyricsFilePath().isEmpty()) {
                if (Files.exists(Paths.get(upload.getLyricsFilePath()))) {
                    Files.move(Paths.get(upload.getLyricsFilePath()), Paths.get(tempLyricsPath), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("迁移歌词文件到临时位置: {} -> {}", upload.getLyricsFilePath(), tempLyricsPath);
                }
            }
            
            // 在事务内插入到music表获取音乐ID（与 FileUploadHandler 一致，写入拼音检索列）
            String albumVal = upload.getAlbum() != null && !upload.getAlbum().isEmpty()
                    ? upload.getAlbum()
                    : "未知专辑";
            String title = upload.getTitle();
            String artist = upload.getArtist();

            String insertMusicSql = """
                INSERT INTO music (title, artist, album, duration, file_format, language, tags, upload_user_id,
                    title_pinyin, title_pinyin_initials, title_word_initials,
                    artist_pinyin, artist_pinyin_initials, artist_word_initials, album_pinyin)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement pstmt = conn.prepareStatement(insertMusicSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

                pstmt.setString(1, title);
                pstmt.setString(2, artist);
                pstmt.setString(3, albumVal);
                pstmt.setInt(4, upload.getDuration());
                pstmt.setString(5, getFileExtensionWithoutDot(upload.getMusicFilePath()));
                pstmt.setString(6, upload.getLanguage());
                pstmt.setString(7, upload.getTags());
                pstmt.setInt(8, upload.getUserId());
                pstmt.setString(9, PinyinUtil.getPinyin(title));
                pstmt.setString(10, PinyinUtil.getPinyinInitials(title));
                pstmt.setString(11, PinyinUtil.getWordInitials(title));
                pstmt.setString(12, PinyinUtil.getPinyin(artist));
                pstmt.setString(13, PinyinUtil.getPinyinInitials(artist));
                pstmt.setString(14, PinyinUtil.getWordInitials(artist));
                pstmt.setString(15, PinyinUtil.getPinyin(albumVal));

                int affectedRows = pstmt.executeUpdate();

                if (affectedRows > 0) {
                    try (java.sql.ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            musicId = rs.getInt(1);
                            logger.info("音乐已插入到music表，ID: {}", musicId);
                        }
                    }
                }
            }

            if (musicId == 0) {
                conn.rollback();
                // 回滚文件迁移
                Files.move(Paths.get(tempMusicPath), Paths.get(upload.getMusicFilePath()), StandardCopyOption.REPLACE_EXISTING);
                if (upload.getCoverFilePath() != null && !upload.getCoverFilePath().isEmpty()) {
                    Files.move(Paths.get(tempCoverPath), Paths.get(upload.getCoverFilePath()), StandardCopyOption.REPLACE_EXISTING);
                }
                if (upload.getLyricsFilePath() != null && !upload.getLyricsFilePath().isEmpty()) {
                    Files.move(Paths.get(tempLyricsPath), Paths.get(upload.getLyricsFilePath()), StandardCopyOption.REPLACE_EXISTING);
                }
                sendError(response, 500, "插入音乐记录失败");
                return;
            }

            // 使用音乐ID生成最终的文件名
            String newMusicFileName = musicId + getFileExtension(upload.getMusicFilePath());
            String newCoverFileName = (upload.getCoverFilePath() != null && !upload.getCoverFilePath().isEmpty())
                    ? musicId + getFileExtension(upload.getCoverFilePath())
                    : null;
            String newLyricsFileName = musicId + ".lrc";

            // 将临时文件重命名为最终文件名
            newMusicPath = Paths.get(MUSIC_AUDIO_DIR, newMusicFileName).toString();
            Files.move(Paths.get(tempMusicPath), Paths.get(newMusicPath), StandardCopyOption.REPLACE_EXISTING);
            logger.info("重命名音乐文件: {} -> {}", tempMusicPath, newMusicPath);

            // 重命名封面文件（如果有）
            if (upload.getCoverFilePath() != null && !upload.getCoverFilePath().isEmpty() && newCoverFileName != null) {
                newCoverPath = Paths.get(MUSIC_COVERS_DIR, newCoverFileName).toString();
                Files.move(Paths.get(tempCoverPath), Paths.get(newCoverPath), StandardCopyOption.REPLACE_EXISTING);
                logger.info("重命名封面文件: {} -> {}", tempCoverPath, newCoverPath);
            }

            // 重命名歌词文件（如果有）
            if (upload.getLyricsFilePath() != null && !upload.getLyricsFilePath().isEmpty() && Files.exists(Paths.get(tempLyricsPath))) {
                newLyricsPath = Paths.get(MUSIC_LYRICS_DIR, newLyricsFileName).toString();
                Files.move(Paths.get(tempLyricsPath), Paths.get(newLyricsPath), StandardCopyOption.REPLACE_EXISTING);
                logger.info("重命名歌词文件: {} -> {}", tempLyricsPath, newLyricsPath);
            }

            // 在事务内更新user_uploads表状态为approved
            uploadManager.approveUpload(uploadId, adminId);
            
            // 提交事务
            conn.commit();
            
            // 发送通知
//            try {
//                boolean notificationSent = Main.getNotificationService().sendMusicApprovedNotification(
//                    upload.getTitle(),
//                    upload.getArtist(),
//                    upload.getUserId()
//                );
//
//                if (notificationSent) {
//                    logger.info("审核通过通知已发送");
//                } else {
//                    logger.warn("审核通过通知发送失败");
//                }
//            } catch (Exception e) {
//                logger.error("发送审核通过通知失败: " + e.getMessage(), e);
//                // 通知发送失败不影响审核通过操作
//            }
            
            // 获取用户邮箱并发送通知邮件
            try {
                String userEmail = getUserEmailById(upload.getUserId());
                
                if (userEmail != null && !userEmail.isEmpty()) {
                    boolean emailSent = Main.getEmailService().sendReviewApprovedEmail(
                        userEmail,
                        upload.getTitle(),
                        upload.getArtist()
                    );
                    
                    if (emailSent) {
                        logger.info("审核通过邮件已发送至: {}", userEmail);
                    } else {
                        logger.warn("审核通过邮件发送失败: {}", userEmail);
                    }
                } else {
                    logger.warn("用户 {} 没有邮箱地址，无法发送审核通过邮件", upload.getUserId());
                }
            } catch (Exception e) {
                logger.error("获取用户邮箱或发送邮件失败: " + e.getMessage(), e);
                // 邮件发送失败不影响审核通过操作
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "审核通过，音乐已添加到库中");
            result.put("data", Map.of(
                "uploadId", uploadId,
                "status", "approved"
            ));
            
            sendJsonResponse(response, result);
            logger.info("审核通过成功，上传ID: {}", uploadId);
            
        } catch (Exception e) {
            logger.error("审核通过失败: " + e.getMessage(), e);
            
            // 回滚事务
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.rollback();
                }
            } catch (Exception rollbackEx) {
                logger.error("事务回滚失败: " + rollbackEx.getMessage(), rollbackEx);
            }
            
            sendError(response, 500, "服务器错误: " + e.getMessage());
        } finally {
            // 关闭连接
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (Exception closeEx) {
                logger.error("关闭数据库连接失败: " + closeEx.getMessage(), closeEx);
            }
        }
    }
    
    /**
     * 审核拒绝
     */
    private void handleRejectUpload(int uploadId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // 获取管理员ID
            String authHeader = request.getHeader("Authorization");
            String token = authHeader.substring(7);
            int adminId = getAdminIdByToken(token);
            
            if (adminId <= 0) {
                sendError(response, 401, "未授权访问");
                return;
            }
            
            // 读取拒绝原因
            StringBuilder body = new StringBuilder();
            java.io.BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            
            Map<String, String> requestData = Main.getObjectMapper().readValue(body.toString(), Map.class);
            String reason = requestData.getOrDefault("reason", "管理员拒绝审核");
            
            // 获取上传记录
            com.neko.music.database.UserUploadDatabaseManager uploadManager = 
                new com.neko.music.database.UserUploadDatabaseManager(Main.getDatabaseManager());
            
            UserUpload upload = uploadManager.getUserUploadById(uploadId);
            if (upload == null) {
                sendError(response, 404, "上传记录不存在");
                return;
            }
            
            if (!"pending".equals(upload.getStatus())) {
                sendError(response, 400, "该记录已被审核，无需重复操作");
                return;
            }
            
            // 1. 先获取用户邮箱并发送邮件（在删除记录之前）
            try {
                String userEmail = getUserEmailById(upload.getUserId());
                
                if (userEmail != null && !userEmail.isEmpty()) {
                    boolean emailSent = Main.getEmailService().sendReviewRejectedEmail(
                        userEmail,
                        upload.getTitle(),
                        upload.getArtist(),
                        reason
                    );
                    
                    if (emailSent) {
                        logger.info("审核拒绝邮件已发送至: {}", userEmail);
                    } else {
                        logger.warn("审核拒绝邮件发送失败: {}", userEmail);
                    }
                } else {
                    logger.warn("用户 {} 没有邮箱地址，无法发送审核拒绝邮件", upload.getUserId());
                }
            } catch (Exception e) {
                logger.error("获取用户邮箱或发送邮件失败: " + e.getMessage(), e);
                // 邮件发送失败不影响后续删除操作
            }
            
            // 2. 删除上传的文件
            deleteUploadFiles(upload);
            
            // 3. 删除数据库记录
            uploadManager.deleteUserUpload(uploadId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "审核拒绝，文件已删除");
            result.put("data", Map.of(
                "uploadId", uploadId,
                "status", "rejected",
                "reason", reason
            ));
            
            sendJsonResponse(response, result);
            logger.info("审核拒绝成功，上传ID: {}, 原因: {}", uploadId, reason);
            
        } catch (Exception e) {
            logger.error("审核拒绝失败: " + e.getMessage(), e);
            sendError(response, 500, "服务器错误: " + e.getMessage());
        }
    }
    
    /**
     * 删除上传的文件
     */
    private void deleteUploadFiles(UserUpload upload) {
        try {
            // 删除音乐文件
            if (upload.getMusicFilePath() != null && !upload.getMusicFilePath().isEmpty()) {
                Path musicPath = Paths.get(upload.getMusicFilePath());
                if (Files.exists(musicPath)) {
                    Files.delete(musicPath);
                    logger.info("已删除音乐文件: {}", upload.getMusicFilePath());
                }
            }
            
            // 删除封面文件
            if (upload.getCoverFilePath() != null && !upload.getCoverFilePath().isEmpty()) {
                Path coverPath = Paths.get(upload.getCoverFilePath());
                if (Files.exists(coverPath)) {
                    Files.delete(coverPath);
                    logger.info("已删除封面文件: {}", upload.getCoverFilePath());
                }
            }
            
            // 删除歌词文件
            if (upload.getLyricsFilePath() != null && !upload.getLyricsFilePath().isEmpty()) {
                Path lyricsPath = Paths.get(upload.getLyricsFilePath());
                if (Files.exists(lyricsPath)) {
                    Files.delete(lyricsPath);
                    logger.info("已删除歌词文件: {}", upload.getLyricsFilePath());
                }
            }
        } catch (Exception e) {
            logger.error("删除文件失败: " + e.getMessage(), e);
        }
    }
    
    private String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filePath.substring(lastDotIndex);
    }
    
    private String getFileExtensionWithoutDot(String filePath) {
        String ext = getFileExtension(filePath);
        return ext.isEmpty() ? "" : ext.substring(1);
    }
    
    /**
     * 根据token获取管理员ID
     */
    private int getAdminIdByToken(String token) {
        return Main.getAdminAuthService().getAdminIdByToken(token).orElse(-1);
    }
    
    /**
     * 根据用户ID获取用户邮箱
     */
    private String getUserEmailById(int userId) {
        String sql = "SELECT email FROM users WHERE id = ?";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("email");
                }
            }
        } catch (java.sql.SQLException e) {
            logger.error("获取用户邮箱失败: " + e.getMessage(), e);
        }
        return null;
    }
    
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        response.getWriter().write(Main.getObjectMapper().writeValueAsString(error));
    }
    
    private void sendJsonResponse(HttpServletResponse response, Map<String, Object> data) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Main.getObjectMapper().writeValueAsString(data));
    }
}