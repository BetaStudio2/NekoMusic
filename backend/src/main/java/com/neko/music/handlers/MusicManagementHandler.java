package com.neko.music.handlers;

import com.neko.music.util.HandlerResponses;
import com.neko.music.util.MusicPinyinColumns;

import com.neko.music.model.SuccessResponse;
import com.neko.music.model.ErrorResponse;
import com.neko.music.Main;
import com.neko.music.service.MusicIngestSupport;
import com.neko.music.util.MusicAssetLocator;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MusicManagementHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicManagementHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
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
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
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
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
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
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
            return;
        }
        
        // 检查是否有音乐删除权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.MUSIC_DELETE)) {
            logger.warn("权限不足，无音乐删除权限");
            return;
        }
        
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("音乐ID不能为空"));
            return;
        }
        
        // 解析路径，只处理 /delete/{id} 格式
        String idStr = "";
        if (pathInfo.startsWith("/delete/")) {
            idStr = pathInfo.substring("/delete/".length());
        } else {
            // 如果路径不是以 /delete/ 开头，返回错误
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的DELETE请求路径: " + pathInfo + "，应为 /api/music/delete/{id}"));
            return;
        }
        
        int id;

        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的音乐ID: " + idStr));
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
            HandlerResponses.writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, new ErrorResponse("删除音乐失败: " + e.getMessage()));
            return;
        }

        if (rowsDeleted == 0) {
            HandlerResponses.writeJson(response, HttpStatus.NOT_FOUND_404, new ErrorResponse("音乐不存在或删除失败"));
            return;
        }

        MusicAssetLocator.deleteAudioVariants(id);
        MusicAssetLocator.deleteCoverVariants(id);

        Main.getLyricsDatabaseManager().delete(id);
        if (Main.getLyricsSearchIndex() != null) {
            Main.getLyricsSearchIndex().rebuildOne(id);
        }
        MusicIngestSupport.invalidateRecognitionIndex();

        HandlerResponses.writeJson(response, HttpStatus.OK_200, new SuccessResponse(true, "删除音乐成功"));
    }
    
    // 检查管理员权限

    private void getAllMusic(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<Music> musicList = new ArrayList<>();
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, duration, language, tags, upload_user_id, created_at, updated_at FROM music ORDER BY created_at DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    musicList.add(mapMusic(rs));
                }
            }
        } catch (Exception e) {
            logger.error("获取音乐列表时出错", e);
            HandlerResponses.writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, new ErrorResponse("获取音乐列表失败: " + e.getMessage()));
            return;
        }
        
        HandlerResponses.writeJson(response, HttpStatus.OK_200, new MusicListResponse(true, "获取音乐列表成功", musicList));
    }

    private void getMusicById(String pathInfo, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String idStr = pathInfo.replace("/", "");
        int id;
        
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的音乐ID"));
            return;
        }
        
        Music music = null;
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, duration, language, tags, upload_user_id, created_at, updated_at FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    music = mapMusic(rs);
                }
            }
        } catch (Exception e) {
            logger.error("获取音乐详情时出错", e);
            HandlerResponses.writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, new ErrorResponse("获取音乐详情失败: " + e.getMessage()));
            return;
        }
        
        if (music == null) {
            HandlerResponses.writeJson(response, HttpStatus.NOT_FOUND_404, new ErrorResponse("音乐不存在"));
            return;
        }
        
        HandlerResponses.writeJson(response, HttpStatus.OK_200, new MusicResponse(true, "获取音乐详情成功", music));
    }

    private void addMusic(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 读取请求体
        String requestBody = new String(request.getInputStream().readAllBytes(), "UTF-8");

        try {
            // 解析JSON请求体
            AddMusicRequest addRequest = Main.getObjectMapper().readValue(requestBody, AddMusicRequest.class);
            
            if (addRequest.title() == null || addRequest.title().trim().isEmpty() ||
                addRequest.artist() == null || addRequest.artist().trim().isEmpty() ||
                addRequest.language() == null || addRequest.language().trim().isEmpty()) {
                HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("音乐标题、艺术家和语言不能为空"));
                return;
            }
            
            int id;
            try (Connection conn = Main.getDatabaseManager().getConnection()) {
                String sql = "INSERT INTO music (title, artist, album, duration, language, tags, upload_user_id, title_pinyin, title_pinyin_initials, title_word_initials, artist_pinyin, artist_pinyin_initials, artist_word_initials, album_pinyin) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, addRequest.title());
                    stmt.setString(2, addRequest.artist());
                    stmt.setString(3, addRequest.album() != null ? addRequest.album() : "未知专辑");
                    stmt.setInt(4, addRequest.duration() != null ? addRequest.duration() : 0);
                    stmt.setString(5, addRequest.language() != null ? addRequest.language() : "未知语言");
                    stmt.setString(6, addRequest.tags() != null ? addRequest.tags() : "");
                    // 使用NULL而不是0以避免外键约束问题
                    stmt.setObject(7, null);
                    // 预计算拼音列
                    MusicPinyinColumns.bind(stmt, 8, addRequest.title(), addRequest.artist(), addRequest.album());
                    
                    int affectedRows = stmt.executeUpdate();
                    
                    if (affectedRows == 0) {
                        HandlerResponses.writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, new ErrorResponse("添加音乐失败"));
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
                        newMusic = mapMusic(rs);
                    }
                }
            }

            MusicIngestSupport.invalidateRecognitionIndex();
            
            HandlerResponses.writeJson(response, HttpStatus.OK_200, new MusicResponse(true, "添加音乐成功", newMusic));
            
        } catch (Exception e) {
            // JSON解析错误或其他异常
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("请求格式错误: " + e.getMessage()));
        }
    }

    private void editMusic(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 读取请求体
        String requestBody = new String(request.getInputStream().readAllBytes(), "UTF-8");

        try {
            // 解析JSON请求体
            EditMusicRequest editRequest = Main.getObjectMapper().readValue(requestBody, EditMusicRequest.class);
            
            // 验证必填字段
            if (editRequest.id() == null || editRequest.title() == null || editRequest.title().trim().isEmpty() ||
                editRequest.artist() == null || editRequest.artist().trim().isEmpty() ||
                editRequest.language() == null || editRequest.language().trim().isEmpty()) {
                HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("音乐ID、标题、艺术家和语言不能为空"));
                return;
            }
            
            // 验证歌词必填
            if (editRequest.lyrics() == null || editRequest.lyrics().trim().isEmpty()) {
                HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("歌词内容不能为空"));
                return;
            }
            
            int rowsUpdated;
            try (Connection conn = Main.getDatabaseManager().getConnection()) {
                String sql = "UPDATE music SET title = ?, artist = ?, album = ?, duration = ?, language = ?, tags = ?, upload_user_id = ?, title_pinyin = ?, title_pinyin_initials = ?, title_word_initials = ?, artist_pinyin = ?, artist_pinyin_initials = ?, artist_word_initials = ?, album_pinyin = ?, updated_at = NOW() WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, editRequest.title());
                    stmt.setString(2, editRequest.artist());
                    stmt.setString(3, editRequest.album() != null ? editRequest.album() : "未知专辑");
                    stmt.setInt(4, editRequest.duration() != null ? editRequest.duration() : 0);
                    stmt.setString(5, editRequest.language() != null ? editRequest.language() : "未知语言");
                    stmt.setString(6, editRequest.tags() != null ? editRequest.tags() : "");
                    // 使用NULL而不是0以避免外键约束问题
                    stmt.setObject(7, null);
                    // 预计算拼音列
                    MusicPinyinColumns.bind(stmt, 8, editRequest.title(), editRequest.artist(), editRequest.album());
                    stmt.setInt(15, editRequest.id());
                    
                    rowsUpdated = stmt.executeUpdate();
                }
                
                // 保存歌词到数据库
                if (rowsUpdated > 0) {
                    saveLyricsToDatabase(editRequest.id(), editRequest.lyrics());
                }
            }
            
            if (rowsUpdated == 0) {
                HandlerResponses.writeJson(response, HttpStatus.NOT_FOUND_404, new ErrorResponse("音乐不存在或更新失败"));
                return;
            }
            
            // 获取更新后的音乐信息
            Music updatedMusic = null;
            try (Connection conn = Main.getDatabaseManager().getConnection()) {
                String sql = "SELECT id, title, artist, album, duration, language, upload_user_id, created_at, updated_at FROM music WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, editRequest.id());
                    
                    ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next()) {
                        updatedMusic = new Music(
                                rs.getInt("id"),
                                rs.getString("title"),
                                rs.getString("artist"),
                                rs.getString("album"),
                                rs.getInt("duration"),
                                MusicAssetLocator.fileApiUrl(rs.getInt("id")),
                                MusicAssetLocator.coverApiUrl(rs.getInt("id")),
                                rs.getString("language"),
                                null,
                                rs.getInt("upload_user_id"),
                                rs.getTimestamp("created_at").toString(),
                                rs.getTimestamp("updated_at").toString(),
                                MusicAssetLocator.coverApiUrl(rs.getInt("id")));
                    }
                }
            }
            
            MusicIngestSupport.invalidateRecognitionIndex();

            HandlerResponses.writeJson(response, HttpStatus.OK_200, new MusicResponse(true, "编辑音乐成功", updatedMusic));
            
        } catch (Exception e) {
            // JSON解析错误或其他异常
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("请求格式错误: " + e.getMessage()));
        }
    }

    /** 从 music 查询结果行映射为 Music（列与顺序一致，提取自 3 处相同内联代码）。 */
    private static Music mapMusic(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        return new Music(
                id,
                rs.getString("title"),
                rs.getString("artist"),
                rs.getString("album"),
                rs.getInt("duration"),
                MusicAssetLocator.fileApiUrl(id),
                MusicAssetLocator.coverApiUrl(id),
                rs.getString("language"),
                rs.getString("tags"),
                rs.getInt("upload_user_id"),
                rs.getTimestamp("created_at").toString(),
                rs.getTimestamp("updated_at").toString(),
                MusicAssetLocator.coverApiUrl(id));
    }

    // 内部类用于表示音乐对象
    // 音乐对象：只读数据载体。coverUrl 由构造时按 id 计算。
    public record Music(
            int id, String title, String artist, String album, int duration,
            String filePath, String coverFilePath, String language, String tags,
            int uploadUserId, String createdAt, String updatedAt, String coverUrl) {
    }
    
    // 内部类用于表示添加音乐请求
    // 添加音乐请求（Jackson 反序列化，只读）
    private record AddMusicRequest(
            String title, String artist, String album, Integer duration,
            String filePath, String coverFilePath, String language, String tags,
            Integer uploadUserId) {
    }
    
    // 内部类用于表示编辑音乐请求
    // 编辑音乐请求（Jackson 反序列化，只读）
    private record EditMusicRequest(
            Integer id, String title, String artist, String album, Integer duration,
            String filePath, String coverFilePath, String language, String tags,
            Integer uploadUserId, String lyrics) {
    }
    
    // 内部类用于表示音乐列表响应
    private record MusicListResponse(boolean success, String message, List<Music> data) {
    }
    
    // 内部类用于表示单个音乐响应
    private record MusicResponse(boolean success, String message, Music data) {
    }
    
    // 保存歌词到数据库
    private void saveLyricsToDatabase(Integer musicId, String lyricsContent) {
        try {
            MusicIngestSupport.saveLyricsAndRebuild(musicId, lyricsContent, "admin", logger);
        } catch (Exception e) {
            logger.error("保存数据库歌词失败: {}", e.getMessage(), e);
        }
    }
    
    // 内部类用于表示成功响应
    
    // 内部类用于表示错误响应
}
