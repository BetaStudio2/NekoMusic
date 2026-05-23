package com.neko.music.handlers;

import com.neko.music.Main;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class UserAvatarUploadHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserAvatarUploadHandler.class);
    
    // 定义头像上传目录（相对于JAR运行目录）
    private static final String AVATAR_DIR = "avatars";
    
    // 允许的图片格式
    private static final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"};
    
    // 允许的 MIME 类型
    private static final String[] ALLOWED_MIME_TYPES = {
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/bmp"
    };
    
    // 最大文件大小 10MiB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 验证用户Token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED_401, "未授权访问");
            return;
        }
        
        token = token.substring(7); // 移除 "Bearer " 前缀
        
        Integer userId = validateToken(token);
        if (userId == null) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED_401, "无效的Token");
            return;
        }
        
        try {
            // 设置请求为multipart类型，用于文件上传
            request.setCharacterEncoding("UTF-8");
            
            // 获取上传的文件部分
            Part avatarPart = request.getPart("avatar");
            
            if (avatarPart == null || avatarPart.getSize() == 0) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "未上传头像文件");
                return;
            }
            
            // 检查文件大小
            if (avatarPart.getSize() > MAX_FILE_SIZE) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "文件大小超过10MiB限制");
                return;
            }
            
            // 检查文件类型 - 严格验证 MIME 类型
            String contentType = avatarPart.getContentType();
            if (contentType == null || !isAllowedMimeType(contentType)) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "只支持图片文件（jpg, jpeg, png, gif, webp, bmp）");
                return;
            }
            
            // 获取文件名和扩展名
            String fileName = avatarPart.getSubmittedFileName();
            String fileExtension = getFileExtension(fileName);
            
            // 验证文件扩展名
            if (!isAllowedExtension(fileExtension)) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "不支持的图片格式，只支持: jpg, jpeg, png, gif, webp, bmp");
                return;
            }
            
            // 创建上传目录
            Path uploadDir = Paths.get(AVATAR_DIR);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            
            // 生成唯一文件名
            String uniqueFileName = userId + "_" + UUID.randomUUID().toString() + fileExtension;
            Path filePath = uploadDir.resolve(uniqueFileName);
            
            // 保存文件
            try (InputStream inputStream = avatarPart.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 更新数据库中的头像路径
            String avatarPath = AVATAR_DIR + "/" + uniqueFileName;
            boolean success = updateUserAvatar(userId, avatarPath);
            
            if (success) {
                logger.info("用户 {} 上传头像成功: {}", userId, uniqueFileName);
                sendSuccessResponse(response, "头像上传成功", avatarPath);
            } else {
                // 如果数据库更新失败，删除已上传的文件
                Files.deleteIfExists(filePath);
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR_500, "更新数据库失败");
            }
            
        } catch (Exception e) {
            logger.error("上传头像时出错", e);
            sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR_500, "上传头像失败: " + e.getMessage());
        }
    }
    
    private Integer validateToken(String token) {
        return Main.getUserAuthService().validateToken(token).orElse(null);
    }
    
    /**
     * 更新用户头像路径
     */
    private boolean updateUserAvatar(int userId, String avatarPath) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 首先检查用户是否有旧头像，如果有则删除
            String checkSql = "SELECT avatar FROM users WHERE id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, userId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        String oldAvatarPath = rs.getString("avatar");
                        if (oldAvatarPath != null && !oldAvatarPath.isEmpty()) {
                            // 删除旧头像文件
                            try {
                                Files.deleteIfExists(Paths.get(oldAvatarPath));
                                logger.info("删除旧头像文件: {}", oldAvatarPath);
                            } catch (IOException e) {
                                logger.warn("删除旧头像文件失败: {}", oldAvatarPath, e);
                            }
                        }
                    }
                }
            }
            
            // 更新数据库
            String sql = "UPDATE users SET avatar = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, avatarPath);
                stmt.setInt(2, userId);
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (Exception e) {
            logger.error("更新用户头像时出错", e);
            return false;
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex).toLowerCase();
        }
        return "";
    }
    
    /**
     * 检查文件扩展名是否允许
     */
    private boolean isAllowedExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查 MIME 类型是否允许
     */
    private boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }
        for (String allowed : ALLOWED_MIME_TYPES) {
            if (allowed.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(HttpServletResponse response, String message, String avatarPath) throws IOException {
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        
        SuccessResponse successResponse = new SuccessResponse(true, message, avatarPath);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(successResponse));
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=utf-8");
        
        ErrorResponse errorResponse = new ErrorResponse(message);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
    }
    
    // 内部类：成功响应
    private static class SuccessResponse {
        private boolean success;
        private String message;
        private String avatarPath;
        
        public SuccessResponse(boolean success, String message, String avatarPath) {
            this.success = success;
            this.message = message;
            this.avatarPath = avatarPath;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getAvatarPath() { return avatarPath; }
    }
    
    // 内部类：错误响应
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }
}