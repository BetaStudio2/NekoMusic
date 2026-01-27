package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserPasswordChangeHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserPasswordChangeHandler.class);
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
        
        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }
        
        try {
            // 解析JSON请求体
            PasswordChangeRequest changeRequest = objectMapper.readValue(requestBody.toString(), PasswordChangeRequest.class);
            
            // 验证请求参数
            if (changeRequest.getOldPassword() == null || changeRequest.getOldPassword().trim().isEmpty()) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "原密码不能为空");
                return;
            }
            
            if (changeRequest.getNewPassword() == null || changeRequest.getNewPassword().trim().isEmpty()) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "新密码不能为空");
                return;
            }
            
            if (changeRequest.getNewPassword().length() < 6) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "新密码长度不能少于6位");
                return;
            }
            
            if (changeRequest.getOldPassword().equals(changeRequest.getNewPassword())) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "新密码不能与原密码相同");
                return;
            }
            
            // 验证原密码
            if (!verifyOldPassword(userId, changeRequest.getOldPassword())) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "原密码错误");
                return;
            }
            
            // 更新密码
            boolean success = updatePassword(userId, changeRequest.getNewPassword());
            
            if (success) {
                logger.info("用户 {} 修改密码成功", userId);
                sendSuccessResponse(response, "密码修改成功");
            } else {
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR_500, "密码修改失败");
            }
            
        } catch (Exception e) {
            logger.error("修改密码时出错", e);
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "请求格式错误: " + e.getMessage());
        }
    }
    
    /**
     * 验证Token并返回用户ID
     */
    private Integer validateToken(String token) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT user_id FROM user_tokens WHERE token = ? AND expires_at > NOW()";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, token);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("user_id");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("验证Token时出错", e);
        }
        return null;
    }
    
    /**
     * 验证原密码
     */
    private boolean verifyOldPassword(int userId, String oldPassword) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT password FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedPassword = rs.getString("password");
                        String hashedOldPassword = hashPassword(oldPassword);
                        return hashedOldPassword.equals(storedPassword);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("验证原密码时出错", e);
        }
        return false;
    }
    
    /**
     * 更新密码
     */
    private boolean updatePassword(int userId, String newPassword) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "UPDATE users SET password = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hashPassword(newPassword));
                stmt.setInt(2, userId);
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (Exception e) {
            logger.error("更新密码时出错", e);
            return false;
        }
    }
    
    /**
     * 密码哈希
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("密码哈希失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        
        SuccessResponse successResponse = new SuccessResponse(true, message);
        response.getWriter().println(objectMapper.writeValueAsString(successResponse));
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=utf-8");
        
        ErrorResponse errorResponse = new ErrorResponse(message);
        response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
    }
    
    // 内部类：密码修改请求
    private static class PasswordChangeRequest {
        private String oldPassword;
        private String newPassword;
        
        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
    
    // 内部类：成功响应
    private static class SuccessResponse {
        private boolean success;
        private String message;
        
        public SuccessResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
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