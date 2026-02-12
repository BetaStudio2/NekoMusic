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
        // 设置CORS响应头
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        
        // 处理OPTIONS预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        // 验证管理员权限
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("未授权访问预览接口，缺少Authorization头");
            response.sendError(401, "未授权访问");
            return;
        }
        
        String token = authHeader.substring(7);
        boolean isValid = Main.getAdminAuthService().validateAdminToken(token);
        if (!isValid) {
            logger.warn("未授权访问预览接口，无效的token");
            response.sendError(401, "未授权访问");
            return;
        }
        
        // 获取文件路径参数
        String filePath = request.getParameter("path");
        if (filePath == null || filePath.isEmpty()) {
            logger.warn("预览请求缺少文件路径参数");
            response.sendError(400, "缺少文件路径参数");
            return;
        }
        
        // 安全检查：确保文件路径在审核目录内
        Path requestedPath;
        Path uploadDirPath;
        try {
            // 统一使用正斜杠处理路径
            String normalizedFilePath = filePath.replace('\\', '/');
            
            // 确保请求的路径是相对路径
            if (Paths.get(normalizedFilePath).isAbsolute()) {
                // 如果是绝对路径，尝试转换为相对路径
                Path currentDir = Paths.get("").toAbsolutePath();
                Path absolutePath = Paths.get(normalizedFilePath).toAbsolutePath();
                requestedPath = currentDir.relativize(absolutePath).normalize();
                logger.info("检测到绝对路径，已转换为相对路径: {}", requestedPath);
            } else {
                requestedPath = Paths.get(normalizedFilePath).normalize();
            }
            
            uploadDirPath = Paths.get(UPLOAD_DIR).normalize();
            
            logger.info("请求路径: {}", requestedPath);
            logger.info("上传目录: {}", uploadDirPath);
            logger.info("请求路径是否以上传目录开头: {}", requestedPath.startsWith(uploadDirPath));
            
        } catch (Exception e) {
            logger.error("文件路径解析失败: {}", filePath, e);
            response.sendError(400, "无效的文件路径");
            return;
        }
        
        if (!requestedPath.startsWith(uploadDirPath)) {
            logger.warn("访问被拒绝，文件路径不在审核目录内。请求路径: {}, 上传目录: {}", requestedPath, uploadDirPath);
            response.sendError(403, "访问被拒绝");
            return;
        }
        
        // 检查文件是否存在
        if (!Files.exists(requestedPath) || !Files.isRegularFile(requestedPath)) {
            logger.warn("预览文件不存在: {}", filePath);
            response.sendError(404, "文件不存在");
            return;
        }
        
        // 获取文件名并设置Content-Type
        String fileName = requestedPath.getFileName().toString();
        String contentType = getContentType(fileName);
        
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentLengthLong(Files.size(requestedPath));
        
        // 输出文件内容
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(requestedPath, out);
            out.flush();
        }
        
        logger.info("管理员预览文件成功: {}", filePath);
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 设置CORS响应头
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
    
    private String getContentType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "application/octet-stream";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "application/octet-stream";
        }
        
        String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        return switch (extension) {
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "lrc" -> "text/plain; charset=utf-8";
            case "txt" -> "text/plain; charset=utf-8";
            default -> "application/octet-stream";
        };
    }
}