package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public class FileUploadHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(FileUploadHandler.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    
    // 定义上传目录（相对于JAR运行目录）
    private static final String MUSIC_DIR = "Music/music";
    private static final String COVER_DIR = "Music/covers";
    private static final String LYRICS_DIR = "Music/lyrics";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        try {
            // 设置请求为multipart类型，用于文件上传
            request.setCharacterEncoding("UTF-8");
            
            // 获取所有上传的文件部分
            Collection<Part> parts = request.getParts();
            
            String title = null;
            String artist = null;
            String album = null;
            String language = null;
            String tags = null;
            Integer duration = 0;
            Integer uploadUserId = null;
            Part musicFilePart = null;
            Part coverFilePart = null;
            Part lyricsFilePart = null;
            
            // 解析表单字段和文件
            for (Part part : parts) {
                String fieldName = part.getName();
                
                if ("title".equals(fieldName)) {
                    title = getPartValue(request, part);
                } else if ("artist".equals(fieldName)) {
                    artist = getPartValue(request, part);
                } else if ("album".equals(fieldName)) {
                    album = getPartValue(request, part);
                } else if ("language".equals(fieldName)) {
                    language = getPartValue(request, part);
                } else if ("tags".equals(fieldName)) {
                    tags = getPartValue(request, part);
                } else if ("duration".equals(fieldName)) {
                    String durationStr = getPartValue(request, part);
                    if (durationStr != null && !durationStr.trim().isEmpty()) {
                        try {
                            duration = Integer.parseInt(durationStr);
                        } catch (NumberFormatException e) {
                            logger.error("解析音乐时长失败: " + durationStr, e);
                        }
                    }
                } else if ("uploadUserId".equals(fieldName)) {
                    String userIdStr = getPartValue(request, part);
                    if (userIdStr != null && !userIdStr.trim().isEmpty()) {
                        try {
                            uploadUserId = Integer.parseInt(userIdStr);
                        } catch (NumberFormatException e) {
                            logger.error("解析上传用户ID失败: " + userIdStr, e);
                        }
                    }
                } else if ("musicFile".equals(fieldName) && part.getSize() > 0) {
                    musicFilePart = part;
                } else if ("coverFile".equals(fieldName) && part.getSize() > 0) {
                    coverFilePart = part;
                } else if ("lyricsFile".equals(fieldName) && part.getSize() > 0) {
                    lyricsFilePart = part;
                }
            }
            
            // 验证必要字段
            if (title == null || title.trim().isEmpty() || artist == null || artist.trim().isEmpty() || 
                language == null || language.trim().isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("音乐标题、艺术家和语言不能为空");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 查重检查：检查是否已存在相同的音乐
            if (isDuplicateMusic(title, artist, album)) {
                response.setStatus(HttpStatus.OK_200);
                response.setContentType("application/json;charset=utf-8");
                MusicResponse errorResponse = new MusicResponse(false, "已有重复音乐", null);
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 验证歌词文件必填
            if (lyricsFilePart == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("歌词文件不能为空");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 检查歌词文件类型
            String lyricsFileName = getFileName(lyricsFilePart);
            if (!lyricsFileName.toLowerCase().endsWith(".lrc")) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("只支持LRC格式的歌词文件");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            if (musicFilePart == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("音乐文件不能为空");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 检查文件类型
            String musicContentType = musicFilePart.getContentType();
            String musicFileName = getFileName(musicFilePart);
            
            if (!"audio/mpeg".equals(musicContentType) && !musicFileName.toLowerCase().endsWith(".mp3")) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("只支持MP3格式的音乐文件");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 创建上传目录
            Path musicPath = Paths.get(MUSIC_DIR);
            Path coverPath = Paths.get(COVER_DIR);
            Files.createDirectories(musicPath);
            Files.createDirectories(coverPath);
            
            // 检查封面文件类型（如果是上传了的话）
            String coverFileName = null;
            if (coverFilePart != null) {
                String coverContentType = coverFilePart.getContentType();
                String fileName = getFileName(coverFilePart);
                
                if (!coverContentType.startsWith("image/")) {
                    response.setStatus(HttpStatus.BAD_REQUEST_400);
                    response.setContentType("application/json;charset=utf-8");
                    ErrorResponse errorResponse = new ErrorResponse("只支持图片格式的封面文件");
                    response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                    return;
                }
                
                coverFileName = fileName;
            }
            
            // 读取音频时长（如果前端没有提供）
            if (duration == 0) {
                duration = readAudioDuration(musicFilePart);
            }
            
            // 生成音乐ID并保存音乐信息到数据库
            int musicId = insertMusicToDatabase(title, artist, album, language, tags, duration, uploadUserId);
            
            // 构建文件路径
            String musicFilePath = MUSIC_DIR + File.separator + musicId + ".mp3";
            String coverFilePath = null;
            
            if (coverFilePart != null) {
                // 获取文件扩展名
                String extension = getFileExtension(coverFileName);
                coverFilePath = COVER_DIR + File.separator + musicId + "." + extension;
            }
            
            // 保存音乐文件
            Path musicFile = Paths.get(musicFilePath);
            try (InputStream inputStream = musicFilePart.getInputStream()) {
                Files.copy(inputStream, musicFile);
                logger.info("音乐文件已保存到: " + musicFilePath);
            }
            
            // 保存封面文件（如果存在）
            if (coverFilePart != null) {
                Path coverFile = Paths.get(coverFilePath);
                try (InputStream inputStream = coverFilePart.getInputStream()) {
                    Files.copy(inputStream, coverFile);
                    logger.info("封面文件已保存到: " + coverFilePath);
                }
            }
            
            // 保存歌词文件
            saveLyricsFile(musicId, lyricsFilePart);
            
            // 更新数据库中的文件路径
            updateFilePathsInDatabase(musicId, musicFilePath, coverFilePath);
            
            // 获取完整的音乐信息
            Music music = getMusicById(musicId);
            
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            MusicResponse musicResponse = new MusicResponse(true, "上传音乐成功", music);
            response.getWriter().println(objectMapper.writeValueAsString(musicResponse));
            
        } catch (Exception e) {
            logger.error("上传音乐时出错", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("上传音乐失败: " + e.getMessage());
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        try {
            // 设置请求为multipart类型，用于文件上传
            request.setCharacterEncoding("UTF-8");
            
            // 获取所有上传的文件部分
            Collection<Part> parts = request.getParts();
            
            Integer id = null;
            String title = null;
            String artist = null;
            String album = null;
            String language = null;
            String tags = null;
            Integer duration = 0;
            Integer uploadUserId = null;
            Part musicFilePart = null;
            Part coverFilePart = null;
            Part lyricsFilePart = null;
            
            // 解析表单字段和文件
            for (Part part : parts) {
                String fieldName = part.getName();
                
                if ("id".equals(fieldName)) {
                    String idStr = getPartValue(request, part);
                    if (idStr != null && !idStr.trim().isEmpty()) {
                        try {
                            id = Integer.parseInt(idStr);
                        } catch (NumberFormatException e) {
                            logger.error("解析音乐ID失败: " + idStr, e);
                        }
                    }
                } else if ("title".equals(fieldName)) {
                    title = getPartValue(request, part);
                } else if ("artist".equals(fieldName)) {
                    artist = getPartValue(request, part);
                } else if ("album".equals(fieldName)) {
                    album = getPartValue(request, part);
                } else if ("language".equals(fieldName)) {
                    language = getPartValue(request, part);
                } else if ("tags".equals(fieldName)) {
                    tags = getPartValue(request, part);
                } else if ("duration".equals(fieldName)) {
                    String durationStr = getPartValue(request, part);
                    if (durationStr != null && !durationStr.trim().isEmpty()) {
                        try {
                            duration = Integer.parseInt(durationStr);
                        } catch (NumberFormatException e) {
                            logger.error("解析音乐时长失败: " + durationStr, e);
                        }
                    }
                } else if ("uploadUserId".equals(fieldName)) {
                    String userIdStr = getPartValue(request, part);
                    if (userIdStr != null && !userIdStr.trim().isEmpty()) {
                        try {
                            uploadUserId = Integer.parseInt(userIdStr);
                        } catch (NumberFormatException e) {
                            logger.error("解析上传用户ID失败: " + userIdStr, e);
                        }
                    }
                } else if ("musicFile".equals(fieldName) && part.getSize() > 0) {
                    musicFilePart = part;
                } else if ("coverFile".equals(fieldName) && part.getSize() > 0) {
                    coverFilePart = part;
                } else if ("lyricsFile".equals(fieldName) && part.getSize() > 0) {
                    lyricsFilePart = part;
                }
            }
            
            // 验证必要字段
            if (id == null || title == null || title.trim().isEmpty() || artist == null || artist.trim().isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("音乐ID、标题和艺术家不能为空");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 验证歌词文件必填
            if (lyricsFilePart == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("歌词文件不能为空");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 检查歌词文件类型
            String lyricsFileName = getFileName(lyricsFilePart);
            if (!lyricsFileName.toLowerCase().endsWith(".lrc")) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("只支持LRC格式的歌词文件");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 获取当前音乐信息
            Music currentMusic = getMusicById(id);
            if (currentMusic == null) {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("音乐不存在");
                response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // 创建上传目录
            Path musicPath = Paths.get(MUSIC_DIR);
            Path coverPath = Paths.get(COVER_DIR);
            Files.createDirectories(musicPath);
            Files.createDirectories(coverPath);
            
            String musicFilePath = currentMusic.getFilePath();
            String coverFilePath = currentMusic.getCoverFilePath(); // 假设数据库中有封面路径字段
            
            // 检查是否上传了新的音乐文件
            if (musicFilePart != null) {
                // 检查文件类型
                String musicContentType = musicFilePart.getContentType();
                String musicFileName = getFileName(musicFilePart);
                
                if (!"audio/mpeg".equals(musicContentType) && !musicFileName.toLowerCase().endsWith(".mp3")) {
                    response.setStatus(HttpStatus.BAD_REQUEST_400);
                    response.setContentType("application/json;charset=utf-8");
                    ErrorResponse errorResponse = new ErrorResponse("只支持MP3格式的音乐文件");
                    response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                    return;
                }
                
                // 读取音频时长（如果前端没有提供）
                if (duration == 0) {
                    duration = readAudioDuration(musicFilePart);
                }
                
                // 构建新文件路径
                musicFilePath = MUSIC_DIR + File.separator + id + ".mp3";
                
                // 保存音乐文件
                Path musicFile = Paths.get(musicFilePath);
                try (InputStream inputStream = musicFilePart.getInputStream()) {
                    Files.copy(inputStream, musicFile);
                    logger.info("音乐文件已保存到: " + musicFilePath);
                }
            } else {
                // 如果没有上传新音乐文件，但前端提供了时长，更新时长
                if (duration != 0) {
                    currentMusic.setDuration(duration);
                }
            }
            
            // 检查是否上传了新的封面文件
            if (coverFilePart != null) {
                String coverContentType = coverFilePart.getContentType();
                String fileName = getFileName(coverFilePart);
                
                if (!coverContentType.startsWith("image/")) {
                    response.setStatus(HttpStatus.BAD_REQUEST_400);
                    response.setContentType("application/json;charset=utf-8");
                    ErrorResponse errorResponse = new ErrorResponse("只支持图片格式的封面文件");
                    response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
                    return;
                }
                
                // 获取文件扩展名
                String extension = getFileExtension(fileName);
                coverFilePath = COVER_DIR + File.separator + id + "." + extension;
                
                // 保存封面文件
                Path coverFile = Paths.get(coverFilePath);
                try (InputStream inputStream = coverFilePart.getInputStream()) {
                    Files.copy(inputStream, coverFile);
                    logger.info("封面文件已保存到: " + coverFilePath);
                }
            }
            
            // 保存歌词文件
            saveLyricsFile(id, lyricsFilePart);
            
            // 更新数据库中的音乐信息
            updateMusicInDatabase(id, title, artist, album, language, tags, duration, musicFilePath, coverFilePath, uploadUserId);
            
            // 获取更新后的音乐信息
            Music updatedMusic = getMusicById(id);
            
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            MusicResponse musicResponse = new MusicResponse(true, "更新音乐成功", updatedMusic);
            response.getWriter().println(objectMapper.writeValueAsString(musicResponse));
            
        } catch (Exception e) {
            logger.error("更新音乐时出错", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("更新音乐失败: " + e.getMessage());
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
        }
    }
    
    // 检查管理员权限
    private boolean isAdminAuthorized(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String token = authHeader.substring(7); // 移除 "Bearer " 前缀
        // 验证管理员令牌
        return Main.getAdminAuthService().validateAdminToken(token);
    }
    
    private String getPartValue(HttpServletRequest request, Part part) throws IOException {
        StringBuilder value = new StringBuilder();
        try (InputStream inputStream = part.getInputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                value.append(new String(buffer, 0, bytesRead, request.getCharacterEncoding()));
            }
        }
        return value.toString();
    }
    
    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        String[] tokens = contentDisposition.split(";");
        for (String token : tokens) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf("=") + 2, token.length() - 1);
            }
        }
        return "";
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "jpg"; // 默认扩展名
    }
    
    // 读取音频时长
    private int readAudioDuration(Part musicFilePart) {
        try (InputStream inputStream = musicFilePart.getInputStream()) {
            // 创建临时文件来读取音频信息
            File tempFile = File.createTempFile("temp_audio", ".mp3");
            Files.copy(inputStream, tempFile.toPath());
            
            // 使用JAudiotagger库读取音频时长
            AudioFile audioFile = AudioFileIO.read(tempFile);
            int duration = audioFile.getAudioHeader().getTrackLength();
            
            // 删除临时文件
            tempFile.delete();
            
            return duration;
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
            logger.error("读取音频时长失败", e);
            return 0; // 返回默认时长
        }
    }
    
    // 将音乐信息插入数据库
    private int insertMusicToDatabase(String title, String artist, String album, String language, String tags, int duration, Integer uploadUserId) throws SQLException {
        int id;
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 验证提供的uploadUserId是否存在于users表中
            Integer validUploadUserId = null;
            if (uploadUserId != null) {
                if (isUserExists(conn, uploadUserId)) {
                    validUploadUserId = uploadUserId;
                } else {
                    // 如果用户不存在，记录警告并使用null
                    logger.warn("提供的upload_user_id {} 不存在于users表中，将使用NULL", uploadUserId);
                }
            }
            
            String sql = "INSERT INTO music (title, artist, album, language, tags, duration, file_path, cover_path, upload_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, title);
                stmt.setString(2, artist);
                stmt.setString(3, album != null ? album : "未知专辑");
                stmt.setString(4, language != null ? language : "未知语言");
                stmt.setString(5, tags != null ? tags : "");
                stmt.setInt(6, duration);
                stmt.setString(7, ""); // 文件路径将在后续更新
                stmt.setString(8, ""); // 封面路径将在后续更新
                stmt.setObject(9, validUploadUserId); // 使用验证后的用户ID或null
                
                int affectedRows = stmt.executeUpdate();
                
                if (affectedRows == 0) {
                    throw new SQLException("添加音乐失败");
                }
                
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        id = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("获取新音乐ID失败");
                    }
                }
            }
        }
        return id;
    }
    
    // 验证用户是否存在
    private boolean isUserExists(Connection conn, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    // 查重检查：检查是否已存在相同的音乐
    private boolean isDuplicateMusic(String title, String artist, String album) throws SQLException {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 检查标题是否相同
            String sql = "SELECT artist, album FROM music WHERE title = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, title);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String existingArtist = rs.getString("artist");
                        String existingAlbum = rs.getString("album");
                        
                        // 如果标题相同，检查艺术家或专辑是否相同
                        if (artist.equals(existingArtist) || 
                            (album != null && album.equals(existingAlbum))) {
                            return true; // 发现重复
                        }
                    }
                }
            }
        }
        return false;
    }
    
    // 更新数据库中的文件路径
    private void updateFilePathsInDatabase(int id, String musicFilePath, String coverFilePath) throws SQLException {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "UPDATE music SET file_path = ?, cover_path = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, musicFilePath);
                stmt.setString(2, coverFilePath);
                stmt.setInt(3, id);
                
                stmt.executeUpdate();
            }
        }
    }
    
    // 更新音乐信息到数据库
    private void updateMusicInDatabase(int id, String title, String artist, String album, String language, String tags, int duration, 
                                      String musicFilePath, String coverFilePath, Integer uploadUserId) throws SQLException {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 验证提供的uploadUserId是否存在于users表中
            Integer validUploadUserId = null;
            if (uploadUserId != null) {
                if (isUserExists(conn, uploadUserId)) {
                    validUploadUserId = uploadUserId;
                } else {
                    // 如果用户不存在，记录警告并使用null
                    logger.warn("提供的upload_user_id {} 不存在于users表中，将使用NULL", uploadUserId);
                }
            }
            
            String sql = "UPDATE music SET title = ?, artist = ?, album = ?, language = ?, tags = ?, duration = ?, file_path = ?, cover_path = ?, upload_user_id = ?, updated_at = NOW() WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, title);
                stmt.setString(2, artist);
                stmt.setString(3, album != null ? album : "未知专辑");
                stmt.setString(4, language != null ? language : "未知语言");
                stmt.setString(5, tags != null ? tags : "");
                stmt.setInt(6, duration);
                stmt.setString(7, musicFilePath);
                stmt.setString(8, coverFilePath);
                stmt.setObject(9, validUploadUserId); // 使用验证后的用户ID或null
                stmt.setInt(10, id);
                
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated == 0) {
                    throw new SQLException("更新音乐失败");
                }
            }
        }
    }
    
    // 根据ID获取音乐信息
    private Music getMusicById(int id) throws SQLException {
        Music music = null;
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, language, tags, duration, file_path, cover_path, upload_user_id, created_at, updated_at FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    music = new Music();
                    music.setId(rs.getInt("id"));
                    music.setTitle(rs.getString("title"));
                    music.setArtist(rs.getString("artist"));
                    music.setAlbum(rs.getString("album"));
                    music.setLanguage(rs.getString("language"));
                    music.setTags(rs.getString("tags"));
                    music.setDuration(rs.getInt("duration"));
                    music.setFilePath(rs.getString("file_path"));
                    String coverPath = rs.getString("cover_path");
                    music.setCoverFilePath(coverPath != null && !coverPath.trim().isEmpty() ? coverPath : null);
                    music.setUploadUserId(rs.getInt("upload_user_id"));
                    music.setCreatedAt(rs.getTimestamp("created_at").toString());
                    music.setUpdatedAt(rs.getTimestamp("updated_at").toString());
                }
            }
        }
        
        return music;
    }
    
    // 内部类用于表示音乐对象
    public static class Music {
        private int id;
        private String title;
        private String artist;
        private String album;
        private String language; // 语言
        private String tags; // 标签
        private int duration; // 时长，单位秒
        private String filePath;
        private String coverFilePath; // 封面路径
        private int uploadUserId;
        private String createdAt;
        private String updatedAt;
        
        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }
        public String getAlbum() { return album; }
        public void setAlbum(String album) { this.album = album; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getCoverFilePath() { return coverFilePath; }
        public void setCoverFilePath(String coverFilePath) { this.coverFilePath = coverFilePath; }
        public int getUploadUserId() { return uploadUserId; }
        public void setUploadUserId(int uploadUserId) { this.uploadUserId = uploadUserId; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
    
    // 内部类用于表示音乐响应
    private static class MusicResponse {
        private boolean success;
        private String message;
        private Music data;
        
        public MusicResponse(boolean success, String message, Music data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Music getData() { return data; }
        public void setData(Music data) { this.data = data; }
    }
    
    // 内部类用于表示错误响应
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
    
    // 保存歌词文件
    private void saveLyricsFile(int musicId, Part lyricsFilePart) {
        try {
            // 创建歌词目录（如果不存在）
            Path lyricsPath = Paths.get(LYRICS_DIR);
            Files.createDirectories(lyricsPath);
            
            // 构建歌词文件路径
            String lyricsFilePath = LYRICS_DIR + File.separator + musicId + ".lrc";
            Path lyricsFile = Paths.get(lyricsFilePath);
            
            // 保存歌词文件
            try (InputStream inputStream = lyricsFilePart.getInputStream()) {
                Files.copy(inputStream, lyricsFile);
            }
            
            logger.info("歌词文件已保存到: " + lyricsFile.toAbsolutePath());
        } catch (Exception e) {
            logger.error("保存歌词文件失败", e);
        }
    }
}