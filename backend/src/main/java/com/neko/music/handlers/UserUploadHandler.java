package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import com.neko.music.util.AudioFileValidator;
import com.neko.music.util.LrcValidator;
import com.neko.music.util.SensitiveWordUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class UserUploadHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserUploadHandler.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String UPLOAD_DIR = "user_upload";
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 启用 multipart 支持
        request.setAttribute("org.eclipse.jetty.multipartConfig", new jakarta.servlet.MultipartConfigElement(System.getProperty("java.io.tmpdir")));
        
        // 验证用户登录
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401, "未授权访问");
            return;
        }
        
        String token = authHeader.substring(7);
        java.util.Optional<Integer> userIdOpt = Main.getUserAuthService().validateToken(token);
        if (userIdOpt.isEmpty()) {
            sendError(response, 401, "未授权访问");
            return;
        }
        
        int userId = userIdOpt.get();
        
        try {
            // 获取表单数据
            String title = getPartString(request, "title");
            String artist = getPartString(request, "artist");
            String language = getPartString(request, "language");
            String tags = getPartString(request, "tags");
            String album = getPartString(request, "album");
            String durationStr = getPartString(request, "duration");
            String uploadUserIdStr = getPartString(request, "uploadUserId");
            
            // 验证必填字段
            if (title == null || title.isEmpty() || artist == null || artist.isEmpty() || language == null || language.isEmpty()) {
                sendError(response, 400, "标题、歌手和语言为必填项");
                return;
            }

            // 验证违禁词
            if (SensitiveWordUtil.contains(title)) {
                sendError(response, 400, "标题包含违禁词");
                return;
            }
            else logger.info("标题不包含违禁词");
            if (SensitiveWordUtil.contains(artist)) {
                sendError(response, 400, "歌手名包含违禁词");
                return;
            }
            else logger.info("歌手名不包含违禁词");
            if (album != null && !album.isEmpty() && SensitiveWordUtil.contains(album)) {
                sendError(response, 400, "专辑名包含违禁词");
                return;
            }
            // 验证语言字段白名单
            if (!java.util.Set.of("中文", "粤语", "上海语", "英文", "日语", "韩语", "法语", "德语", "俄语", "纯音乐").contains(language)) {
                sendError(response, 400, "语言字段不合法，仅支持：中文、粤语、上海语、英文、日语、韩语、法语、德语、俄语、纯音乐");
                return;
            }
            if (tags != null && !tags.isEmpty() && SensitiveWordUtil.contains(tags)) {
                sendError(response, 400, "标签包含违禁词");
                return;
            }
            else logger.info("所有内容未包含违禁词");
            
            // 查重检查：检查是否已存在相同的音乐
            String duplicateType = isDuplicateMusic(title, artist, album);
            if (duplicateType != null) {
                if ("pending".equals(duplicateType)) {
                    sendError(response, 409, "已有用户上传。请勿重复提交");
                } else {
                    sendError(response, 409, "已有重复音乐，请检查后重新上传");
                }
                return;
            }
            
            int duration = 0;
            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    duration = Integer.parseInt(durationStr);
                } catch (NumberFormatException e) {
                    // 忽略解析错误，使用默认值0
                }
            }
            
            // 获取文件
            Part musicFilePart = request.getPart("musicFile");
            Part coverFilePart = request.getPart("coverFile");
            Part lyricsFilePart = request.getPart("lyricsFile");
            
            if (musicFilePart == null || musicFilePart.getSize() == 0) {
                sendError(response, 400, "请上传音乐文件");
                return;
            }
            
            // 验证音乐文件格式 - 完全基于文件内容检测格式
            String musicFileExtension = getFileExtension(musicFilePart.getSubmittedFileName()).toLowerCase();
            String fileFormat;
            
            // 使用魔数验证音频文件的真实格式，不信任文件扩展名
            try (InputStream musicInputStream = musicFilePart.getInputStream()) {
                AudioFileValidator.FormatDetectionResult detectionResult = AudioFileValidator.detectAndValidate(
                        musicInputStream, musicFileExtension);
                
                if (!detectionResult.isValid()) {
                    sendError(response, 400, "音频文件格式错误: " + detectionResult.getErrorMessage());
                    return;
                }
                
                // 根据检测到的实际格式设置文件格式
                switch (detectionResult.getFormat()) {
                    case MP3:
                        fileFormat = "mp3";
                        break;
                    case FLAC:
                        fileFormat = "flac";
                        break;
                    case WAV:
                        fileFormat = "wav";
                        break;
                    default:
                        sendError(response, 400, "不支持的音频格式");
                        return;
                }
                
                logger.info("检测到文件格式: {} (实际格式: {})", fileFormat, detectionResult.getFormatDescription());
            } catch (Exception e) {
                logger.error("验证音频文件格式时出错", e);
                sendError(response, 400, "验证音频文件格式时出错: " + e.getMessage());
                return;
            }
            
            // 创建上传目录
            String uploadBaseDir = UPLOAD_DIR;
            File uploadDir = new File(uploadBaseDir);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            
            // 生成唯一文件名
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // 保存音乐文件（使用检测到的实际格式）
            String musicFileName = "music_" + timestamp + "." + fileFormat;
            String musicFilePath = Paths.get(uploadBaseDir, musicFileName).toString();
            saveFile(musicFilePart, musicFilePath);
            
            // 保存封面文件（可选）
            String coverFilePath = null;
            if (coverFilePart != null && coverFilePart.getSize() > 0) {
                String coverFileName = "cover_" + timestamp + getFileExtension(coverFilePart.getSubmittedFileName());
                coverFilePath = Paths.get(uploadBaseDir, coverFileName).toString();
                saveFile(coverFilePart, coverFilePath);
            }
            
            // 保存歌词文件（可选）
            String lyricsFilePath = null;
            if (lyricsFilePart != null && lyricsFilePart.getSize() > 0) {
                // 检查歌词文件类型
                String lyricsFileName = "lyrics_" + timestamp + getFileExtension(lyricsFilePart.getSubmittedFileName());
                if (!lyricsFileName.toLowerCase().endsWith(".lrc")) {
                    sendError(response, 400, "只支持LRC格式的歌词文件");
                    // 删除已上传的音乐和封面文件
                    deleteFileIfExists(musicFilePath);
                    deleteFileIfExists(coverFilePath);
                    return;
                }

                // 校验歌词文件格式
                try (InputStream lyricsInputStream = lyricsFilePart.getInputStream()) {
                    LrcValidator.ValidationResult validationResult = LrcValidator.validate(
                            lyricsInputStream, lyricsFilePart.getSize());
                    if (!validationResult.isValid()) {
                        sendError(response, 400, "歌词文件格式错误: " + validationResult.getErrorMessage());
                        // 删除已上传的音乐和封面文件
                        deleteFileIfExists(musicFilePath);
                        deleteFileIfExists(coverFilePath);
                        return;
                    }
                } catch (Exception e) {
                    logger.error("校验歌词文件时出错", e);
                    sendError(response, 400, "校验歌词文件时出错: " + e.getMessage());
                    // 删除已上传的音乐和封面文件
                    deleteFileIfExists(musicFilePath);
                    deleteFileIfExists(coverFilePath);
                    return;
                }

                lyricsFilePath = Paths.get(uploadBaseDir, lyricsFileName).toString();
                saveFile(lyricsFilePart, lyricsFilePath);
            } else {
                // 如果没有上传歌词文件，使用资源目录中的 no_lrc.lrc
                String lyricsFileName = "lyrics_" + timestamp + ".lrc";
                lyricsFilePath = Paths.get(uploadBaseDir, lyricsFileName).toString();
                copyResourceFile("no_lrc.lrc", lyricsFilePath);
            }
            
            // 创建上传记录
            com.neko.music.model.UserUpload upload = new com.neko.music.model.UserUpload();
            upload.setUserId(userId);
            upload.setTitle(title);
            upload.setArtist(artist);
            upload.setLanguage(language);
            upload.setTags(tags != null ? tags : "");
            upload.setAlbum(album != null ? album : "");
            upload.setDuration(duration);
            upload.setMusicFilePath(musicFilePath);
            upload.setCoverFilePath(coverFilePath);
            upload.setLyricsFilePath(lyricsFilePath);
            upload.setStatus("pending");
            upload.setCreatedAt(java.time.LocalDateTime.now());
            
            // 保存到数据库
            com.neko.music.database.UserUploadDatabaseManager uploadDatabaseManager = 
                new com.neko.music.database.UserUploadDatabaseManager(Main.getDatabaseManager());
            int uploadId = uploadDatabaseManager.createUserUpload(upload);
            
            if (uploadId > 0) {
                // 发送通知
                try {
                    boolean notificationSent = Main.getNotificationService().sendMusicUploadNotification(
                        title,
                        artist,
                        userId
                    );
                    
                    if (notificationSent) {
                        logger.info("上传通知已发送");
                    } else {
                        logger.warn("上传通知发送失败");
                    }
                } catch (Exception e) {
                    logger.error("发送上传通知失败: " + e.getMessage(), e);
                    // 通知发送失败不影响上传操作
                }
                
                // 返回成功响应
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "上传成功，等待审核");
                result.put("data", Map.of(
                    "id", uploadId,
                    "status", "pending",
                    "createdAt", upload.getCreatedAt().toString()
                ));
                
                sendJsonResponse(response, result);
                logger.info("用户ID {} 上传音乐成功，记录ID: {}", userId, uploadId);
            } else {
                // 保存失败，删除已上传的文件
                deleteFileIfExists(musicFilePath);
                deleteFileIfExists(coverFilePath);
                deleteFileIfExists(lyricsFilePath);
                
                sendError(response, 500, "保存上传记录失败");
            }
            
        } catch (Exception e) {
            logger.error("处理用户上传失败: " + e.getMessage(), e);
            sendError(response, 500, "服务器错误: " + e.getMessage());
        }
    }
    
    private String getPartString(HttpServletRequest request, String partName) throws ServletException, IOException {
        Part part = request.getPart(partName);
        if (part == null || part.getSize() == 0) {
            return null;
        }
        return new String(part.getInputStream().readAllBytes(), "UTF-8").trim();
    }
    
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex);
    }
    
    private void saveFile(Part part, String filePath) throws IOException {
        Path targetPath = Paths.get(filePath);
        Files.copy(part.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
    
    private void copyResourceFile(String resourceName, String targetPath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream != null) {
                Files.copy(inputStream, Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);
            } else {
                logger.warn("资源文件不存在: {}", resourceName);
            }
        }
    }
    
    private void deleteFileIfExists(String filePath) {
        if (filePath != null) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException e) {
                logger.warn("删除文件失败: " + filePath);
            }
        }
    }
    
    // 查重检查：检查是否已存在相同的音乐（包括已审核通过的和等待审核的）
    // 返回值：null 表示不重复，"music" 表示 music 表重复，"pending" 表示等待审核列表重复
    private String isDuplicateMusic(String title, String artist, String album) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 1. 检查 user_uploads 表（等待审核的音乐）- 优先检查，防止重复提交
            String uploadSql = "SELECT artist, album FROM user_uploads WHERE title = ? AND status = 'pending'";
            try (PreparedStatement stmt = conn.prepareStatement(uploadSql)) {
                stmt.setString(1, title);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String existingArtist = rs.getString("artist");
                        String existingAlbum = rs.getString("album");
                        
                        // 如果标题相同，检查艺术家或专辑是否相同
                        if (artist.equals(existingArtist) || 
                            (album != null && !album.isEmpty() && album.equals(existingAlbum))) {
                            return "pending"; // 发现重复（等待审核中）
                        }
                    }
                }
            }
            
            // 2. 检查 music 表（已审核通过的音乐）
            String musicSql = "SELECT artist, album FROM music WHERE title = ?";
            try (PreparedStatement stmt = conn.prepareStatement(musicSql)) {
                stmt.setString(1, title);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String existingArtist = rs.getString("artist");
                        String existingAlbum = rs.getString("album");
                        
                        // 如果标题相同，检查艺术家或专辑是否相同
                        if (artist.equals(existingArtist) || 
                            (album != null && !album.isEmpty() && album.equals(existingAlbum))) {
                            return "music"; // 发现重复（已审核通过）
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("查重检查失败: " + e.getMessage(), e);
        }
        return null;
    }
    
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
    
    private void sendJsonResponse(HttpServletResponse response, Map<String, Object> data) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(data));
    }
}