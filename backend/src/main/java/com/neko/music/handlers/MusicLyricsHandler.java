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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

public class MusicLyricsHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicLyricsHandler.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private static final String LYRICS_DIR = "Music/lyrics";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("音乐ID不能为空");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
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
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 获取歌词信息
        String lyrics = getLyricsById(musicId);
        
        if (lyrics == null) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("歌词文件不存在");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 增加播放次数（带防刷逻辑）
        incrementPlayCount(musicId, request);
        
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        LyricsResponse lyricsResponse = new LyricsResponse(true, "获取歌词成功", lyrics);
        response.getWriter().println(objectMapper.writeValueAsString(lyricsResponse));
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查是否为管理员
        if (!isAdminRequest(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("需要管理员权限");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("音乐ID不能为空");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
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
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 读取请求体中的歌词
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            requestBody.append(line);
        }
        
        LyricsRequest lyricsRequest;
        try {
            lyricsRequest = objectMapper.readValue(requestBody.toString(), LyricsRequest.class);
        } catch (Exception e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的请求格式");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        if (lyricsRequest.getLyrics() == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("歌词内容不能为空");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 更新歌词文件
        boolean success = updateLyricsFile(musicId, lyricsRequest.getLyrics());
        
        if (!success) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("更新歌词失败");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        SuccessResponse successResponse = new SuccessResponse(true, "更新歌词成功");
        response.getWriter().println(objectMapper.writeValueAsString(successResponse));
    }
    
    /**
     * 根据音乐ID获取歌词
     */
    private String getLyricsById(int musicId) {
        try {
            // 构建歌词文件路径
            String lyricsFilePath = LYRICS_DIR + File.separator + musicId + ".lrc";
            File lyricsFile = new File(lyricsFilePath);
            
            if (!lyricsFile.exists()) {
                logger.debug("歌词文件不存在: {}", lyricsFile.getAbsolutePath());
                return null; // 歌词文件不存在
            }
            
            // 读取歌词文件内容
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(lyricsFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            // 移除最后的换行符
            if (content.length() > 0) {
                content.deleteCharAt(content.length() - 1);
            }

            // 将所有 [mm:ss:xx] 格式转换为 [mm:ss.xx] 标准格式
            String lyricsContent = content.toString();
            lyricsContent = lyricsContent.replaceAll("\\[(\\d{2}):(\\d{2}):(\\d{2,3})\\]", "[$1:$2.$3]");

            logger.info("成功读取歌词文件: {}", lyricsFile.getAbsolutePath());
            return lyricsContent;
        } catch (Exception e) {
            logger.error("读取歌词文件时出错: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 更新歌词文件
     */
    private boolean updateLyricsFile(int musicId, String lyrics) {
        try {
            // 构建歌词文件路径
            String lyricsFilePath = LYRICS_DIR + File.separator + musicId + ".lrc";
            File lyricsFile = new File(lyricsFilePath);
            
            // 写入歌词内容
            try (FileWriter writer = new FileWriter(lyricsFile)) {
                writer.write(lyrics);
            }
            
            logger.info("成功更新歌词文件: {}", lyricsFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            logger.error("更新歌词文件时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 增加音乐的播放次数（带防刷逻辑）
     */
    private void incrementPlayCount(int musicId, HttpServletRequest request) {
        try {
            // 获取客户端IP地址
            String ipAddress = getClientIpAddress(request);
            
            if (ipAddress == null || ipAddress.isEmpty()) {
                logger.warn("无法获取客户端IP地址，跳过播放次数统计");
                return;
            }
            
            // 生成Redis键名：play_log:musicId:ipAddress
            String redisKey = "play_log:" + musicId + ":" + ipAddress;
            
            // 检查Redis中是否已存在该键（一分钟内已播放过）
            boolean exists = Main.getRedisService().exists(redisKey);
            
            if (exists) {
                logger.debug("同一IP {} 在一分钟内已播放过音乐ID {}，跳过播放次数统计", ipAddress, musicId);
                return; // 一分钟内已播放过，不计入播放次数
            }
            
            // 增加播放次数
            try (java.sql.Connection conn = Main.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE music SET play_count = play_count + 1 WHERE id = ?")) {
                stmt.setInt(1, musicId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    logger.info("已增加音乐ID {} 的播放次数 (IP: {})", musicId, ipAddress);
                } else {
                    logger.warn("未找到音乐ID {}，无法更新播放次数", musicId);
                }
            }
            
            // 在Redis中记录播放日志，设置60秒过期时间
            Main.getRedisService().setWithExpiry(redisKey, "1", 60);
            
        } catch (Exception e) {
            logger.error("增加播放次数时出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 处理多个IP的情况（X-Forwarded-For可能包含多个IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
    
    /**
     * 检查请求是否来自管理员
     */
    private boolean isAdminRequest(HttpServletRequest request) {
        String adminToken = request.getHeader("Admin-Token");
        if (adminToken == null || adminToken.isEmpty()) {
            return false;
        }
        
        // 验证管理员令牌
        return Main.getAdminAuthService().validateAdminToken(adminToken);
    }

    // 内部类用于表示歌词请求
    private static class LyricsRequest {
        private String lyrics;
        
        public String getLyrics() { return lyrics; }
        public void setLyrics(String lyrics) { this.lyrics = lyrics; }
    }
    
    // 内部类用于表示歌词响应
    private static class LyricsResponse {
        private boolean success;
        private String message;
        private String data;
        
        public LyricsResponse(boolean success, String message, String data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
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