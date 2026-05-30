package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.util.MusicAssetLocator;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MusicManagementHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicManagementHandler.class);
    private static final String LYRICS_DIR = "Music/lyrics";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否有音乐查看权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.MUSIC_VIEW)) {
            logger.warn("权限不足，无音乐查看权限");
            return;
        }
        
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || "/list".equals(pathInfo)) {
            // 获取所有音乐列表
            getAllMusic(request, response);
        } else {
            // 获取特定ID的音乐
            getMusicById(pathInfo, request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否有音乐添加权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.MUSIC_ADD)) {
            logger.warn("权限不足，无音乐添加权限");
            return;
        }
        
        // 对于POST请求，总是执行添加操作
        addMusic(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否有音乐编辑权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.MUSIC_EDIT)) {
            logger.warn("权限不足，无音乐编辑权限");
            return;
        }
        
        // 对于PUT请求，总是执行编辑操作，不严格区分路径
        // 因为PUT方法的语义就是更新资源
        editMusic(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否有音乐删除权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.MUSIC_DELETE)) {
            logger.warn("权限不足，无音乐删除权限");
            return;
        }
        
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("音乐ID不能为空");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 解析路径，只处理 /delete/{id} 格式
        String idStr = "";
        if (pathInfo.startsWith("/delete/")) {
            idStr = pathInfo.substring("/delete/".length());
        } else {
            // 如果路径不是以 /delete/ 开头，返回错误
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的DELETE请求路径: " + pathInfo + "，应为 /api/music/delete/{id}");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        int id;

        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的音乐ID: " + idStr);
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        // 删除数据库记录
        int rowsDeleted;
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "DELETE FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);

                rowsDeleted = stmt.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("删除音乐时出错", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("删除音乐失败: " + e.getMessage());
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        if (rowsDeleted == 0) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("音乐不存在或删除失败");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        MusicAssetLocator.deleteAudioVariants(id);
        MusicAssetLocator.deleteCoverVariants(id);

        // 删除歌词文件（根据音乐ID查找）
        String lyricsFilePath = "Music/lyrics/" + id + ".lrc";
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(lyricsFilePath));
            logger.info("已删除歌词文件: {}", lyricsFilePath);
        } catch (Exception e) {
            logger.warn("删除歌词文件失败（可能残留孤儿文件） musicId={} path={}: {}", id, lyricsFilePath, e.toString());
        }

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        SuccessResponse successResponse = new SuccessResponse(true, "删除音乐成功");
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(successResponse));
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

    private void getAllMusic(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<Music> musicList = new ArrayList<>();
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, duration, language, tags, upload_user_id, created_at, updated_at FROM music ORDER BY created_at DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Music music = new Music();
                    music.setId(rs.getInt("id"));
                    music.setTitle(rs.getString("title"));
                    music.setArtist(rs.getString("artist"));
                    music.setAlbum(rs.getString("album"));
                    music.setDuration(rs.getInt("duration"));
                    music.setFilePath(MusicAssetLocator.fileApiUrl(music.getId()));
                    music.setCoverFilePath(MusicAssetLocator.coverApiUrl(music.getId()));
                    music.setLanguage(rs.getString("language"));
                    music.setTags(rs.getString("tags"));
                    music.setUploadUserId(rs.getInt("upload_user_id"));
                    music.setCreatedAt(rs.getTimestamp("created_at").toString());
                    music.setUpdatedAt(rs.getTimestamp("updated_at").toString());
                    
                    musicList.add(music);
                }
            }
        } catch (Exception e) {
            logger.error("获取音乐列表时出错", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("获取音乐列表失败: " + e.getMessage());
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        MusicListResponse musicListResponse = new MusicListResponse(true, "获取音乐列表成功", musicList);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(musicListResponse));
    }

    private void getMusicById(String pathInfo, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String idStr = pathInfo.replace("/", "");
        int id;
        
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的音乐ID");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        Music music = null;
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, duration, language, tags, upload_user_id, created_at, updated_at FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    music = new Music();
                    music.setId(rs.getInt("id"));
                    music.setTitle(rs.getString("title"));
                    music.setArtist(rs.getString("artist"));
                    music.setAlbum(rs.getString("album"));
                    music.setDuration(rs.getInt("duration"));
                    music.setFilePath(MusicAssetLocator.fileApiUrl(music.getId()));
                    music.setCoverFilePath(MusicAssetLocator.coverApiUrl(music.getId()));
                    music.setLanguage(rs.getString("language"));
                    music.setTags(rs.getString("tags"));
                    music.setUploadUserId(rs.getInt("upload_user_id"));
                    music.setCreatedAt(rs.getTimestamp("created_at").toString());
                    music.setUpdatedAt(rs.getTimestamp("updated_at").toString());
                }
            }
        } catch (Exception e) {
            logger.error("获取音乐详情时出错", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("获取音乐详情失败: " + e.getMessage());
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        if (music == null) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("音乐不存在");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        MusicResponse musicResponse = new MusicResponse(true, "获取音乐详情成功", music);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(musicResponse));
    }

    private void addMusic(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 读取请求体
        String requestBody = new String(request.getInputStream().readAllBytes(), "UTF-8");

        try {
            // 解析JSON请求体
            AddMusicRequest addRequest = Main.getObjectMapper().readValue(requestBody, AddMusicRequest.class);
            
            if (addRequest.getTitle() == null || addRequest.getTitle().trim().isEmpty() ||
                addRequest.getArtist() == null || addRequest.getArtist().trim().isEmpty() ||
                addRequest.getLanguage() == null || addRequest.getLanguage().trim().isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("音乐标题、艺术家和语言不能为空");
                response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
                return;
            }
            
            int id;
            try (Connection conn = Main.getDatabaseManager().getConnection()) {
                String sql = "INSERT INTO music (title, artist, album, duration, language, tags, upload_user_id, title_pinyin, title_pinyin_initials, title_word_initials, artist_pinyin, artist_pinyin_initials, artist_word_initials, album_pinyin) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, addRequest.getTitle());
                    stmt.setString(2, addRequest.getArtist());
                    stmt.setString(3, addRequest.getAlbum() != null ? addRequest.getAlbum() : "未知专辑");
                    stmt.setInt(4, addRequest.getDuration() != null ? addRequest.getDuration() : 0);
                    stmt.setString(5, addRequest.getLanguage() != null ? addRequest.getLanguage() : "未知语言");
                    stmt.setString(6, addRequest.getTags() != null ? addRequest.getTags() : "");
                    // 使用NULL而不是0以避免外键约束问题
                    stmt.setObject(7, null);
                    // 预计算拼音列
                    stmt.setString(8, com.neko.music.util.PinyinUtil.getPinyin(addRequest.getTitle()));
                    stmt.setString(9, com.neko.music.util.PinyinUtil.getPinyinInitials(addRequest.getTitle()));
                    stmt.setString(10, com.neko.music.util.PinyinUtil.getWordInitials(addRequest.getTitle()));
                    stmt.setString(11, com.neko.music.util.PinyinUtil.getPinyin(addRequest.getArtist()));
                    stmt.setString(12, com.neko.music.util.PinyinUtil.getPinyinInitials(addRequest.getArtist()));
                    stmt.setString(13, com.neko.music.util.PinyinUtil.getWordInitials(addRequest.getArtist()));
                    stmt.setString(14, addRequest.getAlbum() != null ? com.neko.music.util.PinyinUtil.getPinyin(addRequest.getAlbum()) : "");
                    
                    int affectedRows = stmt.executeUpdate();
                    
                    if (affectedRows == 0) {
                        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                        response.setContentType("application/json;charset=utf-8");
                        ErrorResponse errorResponse = new ErrorResponse("添加音乐失败");
                        response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
                        return;
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
            
            // 获取新添加的音乐信息
            Music newMusic = null;
            try (Connection conn = Main.getDatabaseManager().getConnection()) {
                String sql = "SELECT id, title, artist, album, duration, language, tags, upload_user_id, created_at, updated_at FROM music WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    
                    ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next()) {
                        newMusic = new Music();
                        newMusic.setId(rs.getInt("id"));
                        newMusic.setTitle(rs.getString("title"));
                        newMusic.setArtist(rs.getString("artist"));
                        newMusic.setAlbum(rs.getString("album"));
                        newMusic.setDuration(rs.getInt("duration"));
                        newMusic.setFilePath(MusicAssetLocator.fileApiUrl(newMusic.getId()));
                        newMusic.setCoverFilePath(MusicAssetLocator.coverApiUrl(newMusic.getId()));
                        newMusic.setLanguage(rs.getString("language"));
                        newMusic.setTags(rs.getString("tags"));
                        newMusic.setUploadUserId(rs.getInt("upload_user_id"));
                        newMusic.setCreatedAt(rs.getTimestamp("created_at").toString());
                        newMusic.setUpdatedAt(rs.getTimestamp("updated_at").toString());
                    }
                }
            }
            
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            MusicResponse musicResponse = new MusicResponse(true, "添加音乐成功", newMusic);
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(musicResponse));
            
        } catch (Exception e) {
            // JSON解析错误或其他异常
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("请求格式错误: " + e.getMessage());
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
        }
    }

    private void editMusic(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 读取请求体
        String requestBody = new String(request.getInputStream().readAllBytes(), "UTF-8");

        try {
            // 解析JSON请求体
            EditMusicRequest editRequest = Main.getObjectMapper().readValue(requestBody, EditMusicRequest.class);
            
            // 验证必填字段
            if (editRequest.getId() == null || editRequest.getTitle() == null || editRequest.getTitle().trim().isEmpty() ||
                editRequest.getArtist() == null || editRequest.getArtist().trim().isEmpty() ||
                editRequest.getLanguage() == null || editRequest.getLanguage().trim().isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("音乐ID、标题、艺术家和语言不能为空");
                response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
                return;
            }
            
            // 验证歌词必填
            if (editRequest.getLyrics() == null || editRequest.getLyrics().trim().isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("歌词内容不能为空");
                response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
                return;
            }
            
            int rowsUpdated;
            try (Connection conn = Main.getDatabaseManager().getConnection()) {
                String sql = "UPDATE music SET title = ?, artist = ?, album = ?, duration = ?, language = ?, tags = ?, upload_user_id = ?, title_pinyin = ?, title_pinyin_initials = ?, title_word_initials = ?, artist_pinyin = ?, artist_pinyin_initials = ?, artist_word_initials = ?, album_pinyin = ?, updated_at = NOW() WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, editRequest.getTitle());
                    stmt.setString(2, editRequest.getArtist());
                    stmt.setString(3, editRequest.getAlbum() != null ? editRequest.getAlbum() : "未知专辑");
                    stmt.setInt(4, editRequest.getDuration() != null ? editRequest.getDuration() : 0);
                    stmt.setString(5, editRequest.getLanguage() != null ? editRequest.getLanguage() : "未知语言");
                    stmt.setString(6, editRequest.getTags() != null ? editRequest.getTags() : "");
                    // 使用NULL而不是0以避免外键约束问题
                    stmt.setObject(7, null);
                    // 预计算拼音列
                    stmt.setString(8, com.neko.music.util.PinyinUtil.getPinyin(editRequest.getTitle()));
                    stmt.setString(9, com.neko.music.util.PinyinUtil.getPinyinInitials(editRequest.getTitle()));
                    stmt.setString(10, com.neko.music.util.PinyinUtil.getWordInitials(editRequest.getTitle()));
                    stmt.setString(11, com.neko.music.util.PinyinUtil.getPinyin(editRequest.getArtist()));
                    stmt.setString(12, com.neko.music.util.PinyinUtil.getPinyinInitials(editRequest.getArtist()));
                    stmt.setString(13, com.neko.music.util.PinyinUtil.getWordInitials(editRequest.getArtist()));
                    stmt.setString(14, editRequest.getAlbum() != null ? com.neko.music.util.PinyinUtil.getPinyin(editRequest.getAlbum()) : "");
                    stmt.setInt(15, editRequest.getId());
                    
                    rowsUpdated = stmt.executeUpdate();
                }
                
                // 保存歌词文件到 \Music\lyrics 目录
                if (rowsUpdated > 0) {
                    saveLyricsFile(editRequest.getId(), editRequest.getLyrics());
                }
            }
            
            if (rowsUpdated == 0) {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("音乐不存在或更新失败");
                response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
                return;
            }
            
            // 获取更新后的音乐信息
            Music updatedMusic = null;
            try (Connection conn = Main.getDatabaseManager().getConnection()) {
                String sql = "SELECT id, title, artist, album, duration, language, upload_user_id, created_at, updated_at FROM music WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, editRequest.getId());
                    
                    ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next()) {
                        updatedMusic = new Music();
                        updatedMusic.setId(rs.getInt("id"));
                        updatedMusic.setTitle(rs.getString("title"));
                        updatedMusic.setArtist(rs.getString("artist"));
                        updatedMusic.setAlbum(rs.getString("album"));
                        updatedMusic.setDuration(rs.getInt("duration"));
                        updatedMusic.setFilePath(MusicAssetLocator.fileApiUrl(updatedMusic.getId()));
                        updatedMusic.setCoverFilePath(MusicAssetLocator.coverApiUrl(updatedMusic.getId()));
                        updatedMusic.setLanguage(rs.getString("language"));
                        updatedMusic.setUploadUserId(rs.getInt("upload_user_id"));
                        updatedMusic.setCreatedAt(rs.getTimestamp("created_at").toString());
                        updatedMusic.setUpdatedAt(rs.getTimestamp("updated_at").toString());
                    }
                }
            }
            
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            MusicResponse musicResponse = new MusicResponse(true, "编辑音乐成功", updatedMusic);
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(musicResponse));
            
        } catch (Exception e) {
            // JSON解析错误或其他异常
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("请求格式错误: " + e.getMessage());
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
        }
    }

    // 内部类用于表示音乐对象
    public static class Music {
        private int id;
        private String title;
        private String artist;
        private String album;
        private int duration; // 时长，单位秒
        private String filePath;
        private String coverFilePath; // 封面路径
        private String language; // 语言
        private String tags; // 标签
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
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getCoverFilePath() { return coverFilePath; }
        public void setCoverFilePath(String coverFilePath) { this.coverFilePath = coverFilePath; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public int getUploadUserId() { return uploadUserId; }
        public void setUploadUserId(int uploadUserId) { this.uploadUserId = uploadUserId; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
        
        public String getCoverUrl() {
            if (id <= 0) {
                return "/api/defaultIcon";
            }
            return MusicAssetLocator.coverApiUrl(id);
        }
    }
    
    // 内部类用于表示添加音乐请求
    private static class AddMusicRequest {
        private String title;
        private String artist;
        private String album;
        private Integer duration;
        private String filePath;
        private String coverFilePath;
        private String language;
        private String tags;
        private Integer uploadUserId;
        
        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }
        public String getAlbum() { return album; }
        public void setAlbum(String album) { this.album = album; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getCoverFilePath() { return coverFilePath; }
        public void setCoverFilePath(String coverFilePath) { this.coverFilePath = coverFilePath; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public Integer getUploadUserId() { return uploadUserId; }
        public void setUploadUserId(Integer uploadUserId) { this.uploadUserId = uploadUserId; }
    }
    
    // 内部类用于表示编辑音乐请求
    private static class EditMusicRequest {
        private Integer id;
        private String title;
        private String artist;
        private String album;
        private Integer duration;
        private String filePath;
        private String coverFilePath;
        private String language;
        private String tags;
        private Integer uploadUserId;
        private String lyrics; // 歌词内容
        
        // Getters and Setters
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }
        public String getAlbum() { return album; }
        public void setAlbum(String album) { this.album = album; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getCoverFilePath() { return coverFilePath; }
        public void setCoverFilePath(String coverFilePath) { this.coverFilePath = coverFilePath; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public Integer getUploadUserId() { return uploadUserId; }
        public void setUploadUserId(Integer uploadUserId) { this.uploadUserId = uploadUserId; }
        public String getLyrics() { return lyrics; }
        public void setLyrics(String lyrics) { this.lyrics = lyrics; }
    }
    
    // 内部类用于表示音乐列表响应
    private static class MusicListResponse {
        private boolean success;
        private String message;
        private List<Music> data;
        
        public MusicListResponse(boolean success, String message, List<Music> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<Music> getData() { return data; }
        public void setData(List<Music> data) { this.data = data; }
    }
    
    // 内部类用于表示单个音乐响应
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
    
    // 保存歌词文件到 \Music\lyrics 目录
    private void saveLyricsFile(Integer musicId, String lyricsContent) {
        try {
            // 构建歌词文件路径
            String lyricsFilePath = LYRICS_DIR + File.separator + musicId.toString() + ".lrc";
            java.io.File lyricsFile = new java.io.File(lyricsFilePath);
            
            // 创建歌词目录（如果不存在）
            java.io.File lyricsDir = lyricsFile.getParentFile();
            if (!lyricsDir.exists()) {
                lyricsDir.mkdirs();
            }
            
            // 写入歌词内容
            java.nio.file.Files.write(lyricsFile.toPath(), lyricsContent.getBytes("UTF-8"));
            
            logger.info("歌词文件已保存: {}", lyricsFile.getAbsolutePath());
            if (Main.getLyricsSearchIndex() != null) {
                Main.getLyricsSearchIndex().rebuildOne(musicId);
            }
        } catch (Exception e) {
            logger.error("保存歌词文件失败: {}", e.getMessage(), e);
        }
    }
    
    // 内部类用于表示成功响应
    private static class SuccessResponse {
        private boolean success;
        private String message;
        
        public SuccessResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
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
}