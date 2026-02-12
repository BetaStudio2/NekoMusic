package com.neko.music.handlers;

import com.neko.music.Main;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UserUploadPreviewHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserUploadPreviewHandler.class);
    private static final String UPLOAD_DIR = "user_upload";
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 验证管理员权限
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(401, "未授权访问");
            return;
        }
        
        String token = authHeader.substring(7);
        boolean isValid = Main.getAdminAuthService().validateAdminToken(token);
        if (!isValid) {
            response.sendError(401, "未授权访问");
            return;
        }
        
        // 获取文件路径参数
        String filePath = request.getParameter("path");
        if (filePath == null || filePath.isEmpty()) {
            response.sendError(400, "缺少文件路径参数");
            return;
        }
        
        // 安全检查：确保文件路径在审核目录内
        Path requestedPath = Paths.get(filePath).normalize();
        Path uploadDirPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
        
        if (!requestedPath.startsWith(uploadDirPath)) {
            response.sendError(403, "访问被拒绝");
            return;
        }
        
        // 检查文件是否存在
        if (!Files.exists(requestedPath) || !Files.isRegularFile(requestedPath)) {
            response.sendError(404, "文件不存在");
            return;
        }
        
        // 获取文件名并设置Content-Type
        String fileName = requestedPath.getFileName().toString();
        String contentType = getContentType(fileName);
        
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
        response.setContentLengthLong(Files.size(requestedPath));
        
        // 输出文件内容
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(requestedPath, out);
            out.flush();
        }
        
        logger.info("管理员预览文件: {}", filePath);
    }
    
    private String getContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "lrc" -> "text/plain";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
}