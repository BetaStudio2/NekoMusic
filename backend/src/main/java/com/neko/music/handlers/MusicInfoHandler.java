package com.neko.music.handlers;

import com.neko.music.model.ErrorResponse;
import com.neko.music.Main;
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

public class MusicInfoHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicInfoHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("音乐ID不能为空");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 解析音乐ID (路径格式: /{id})
        String idStr = pathInfo.replace("/", "");
        int musicId;
        
        try {
            musicId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的音乐ID");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 获取音乐信息（无需管理员权限）
        Music music = getMusicById(musicId);
        
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
    
    /**
     * 根据ID获取音乐信息（无需管理员权限）
     */
    private Music getMusicById(int musicId) {
        Music music = null;
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, duration, language, tags, upload_user_id, created_at, updated_at FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, musicId);
                
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    int id = rs.getInt("id");
                    music = new Music(
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
                            id <= 0 ? "/api/defaultIcon" : MusicAssetLocator.coverApiUrl(id));
                }
            }
        } catch (Exception e) {
            logger.error("获取音乐详情时出错", e);
        }
        
        return music;
    }

    // 音乐对象：只读数据载体。coverUrl 由构造时按 id 计算（等价于原派生 getter）。
    public record Music(
            int id,
            String title,
            String artist,
            String album,
            int duration,
            String filePath,
            String coverFilePath,
            String language,
            String tags,
            int uploadUserId,
            String createdAt,
            String updatedAt,
            String coverUrl) {
    }
    
    // 内部类用于表示单个音乐响应
    private record MusicResponse(boolean success, String message, Music data) {
    }
    
    // 内部类用于表示错误响应
}