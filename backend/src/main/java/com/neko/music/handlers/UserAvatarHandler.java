package com.neko.music.handlers;

import com.neko.music.Main;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserAvatarHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserAvatarHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/default")) {
            // 如果路径为空或请求默认头像，返回默认图标
            sendDefaultIcon(response);
            return;
        }
        
        // 解析用户ID (路径格式: /{userId} 或 /{userId}/avatar)
        String[] pathParts = pathInfo.split("/");
        String userIdStr = pathParts.length > 1 ? pathParts[1] : null;
        
        if (userIdStr == null || userIdStr.isEmpty()) {
            sendDefaultIcon(response);
            return;
        }
        
        // 尝试解析用户ID
        int userId;
        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            logger.warn("无效的用户ID: " + userIdStr);
            sendDefaultIcon(response);
            return;
        }
        
        // 从数据库获取用户头像路径
        String avatarPath = getUserAvatarPath(userId);
        
        if (avatarPath != null && !avatarPath.isEmpty()) {
            // 如果用户有头像，发送用户头像
            Path avatarFile = Paths.get(avatarPath);
            if (Files.exists(avatarFile) && Files.isRegularFile(avatarFile)) {
                sendImageFile(avatarFile, response);
                return;
            } else {
                logger.warn("用户头像文件不存在: {}", avatarPath);
            }
        }
        
        // 如果没有头像或文件不存在，返回默认头像
        sendDefaultIcon(response);
    }
    
    /**
     * 从数据库获取用户头像路径
     */
    private String getUserAvatarPath(int userId) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT avatar FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("avatar");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("获取用户头像路径时出错", e);
        }
        return null;
    }
    
    /**
     * 发送默认图标
     */
    private void sendDefaultIcon(HttpServletResponse response) throws IOException {
        // 尝试从类路径加载默认图标
        InputStream defaultIconStream = getClass().getClassLoader().getResourceAsStream("DefaultIcon.png");
        if (defaultIconStream == null) {
            // 如果类路径也找不到，返回404
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("Default icon not found");
            return;
        }

        response.setContentType("image/png");
        response.setStatus(HttpStatus.OK_200);

        try (InputStream inputStream = defaultIconStream;
             OutputStream outputStream = response.getOutputStream()) {
            copyStream(inputStream, outputStream);
        } catch (IOException e) {
            if (isClientAbort(e)) {
                logger.debug("客户端在默认头像发送完成前断开连接");
                return;
            }
            logger.error("发送默认图标时出错", e);
        }
    }
    
    /**
     * 发送图片文件
     */
    private void sendImageFile(Path imagePath, HttpServletResponse response) throws IOException {
        String mimeType = getMimeType(imagePath.toString());
        response.setContentType(mimeType);
        response.setStatus(HttpStatus.OK_200);
        
        try (InputStream inputStream = Files.newInputStream(imagePath);
             OutputStream outputStream = response.getOutputStream()) {
            copyStream(inputStream, outputStream);
        } catch (IOException e) {
            if (isClientAbort(e)) {
                logger.debug("客户端在头像发送完成前断开连接: {}", imagePath);
                return;
            }
            throw e;
        }
    }

    private void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192]; // 8KB buffer
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        outputStream.flush();
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof EofException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("broken pipe")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
    
    /**
     * 根据文件扩展名获取MIME类型
     */
    private String getMimeType(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();
        
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            default:
                return "image/png"; // 默认使用png
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}
