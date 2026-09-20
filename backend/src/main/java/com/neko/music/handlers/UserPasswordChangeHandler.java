package com.neko.music.handlers;

import com.neko.music.Main;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserPasswordChangeHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserPasswordChangeHandler.class);
    private final Argon2 argon2 = Argon2Factory.create();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 验证用户Token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            sendErrorObject(response, HttpStatus.UNAUTHORIZED_401, "未授权访问");
            return;
        }
        
        token = token.substring(7); // 移除 "Bearer " 前缀
        
        Integer userId = validateToken(token);
        if (userId == null) {
            sendErrorObject(response, HttpStatus.UNAUTHORIZED_401, "无效的Token");
            return;
        }
        
        // 读取请求体
        String requestBody = readBody(request);
        
        try {
            // 解析JSON请求体
            PasswordChangeRequest changeRequest = Main.getObjectMapper().readValue(requestBody.toString(), PasswordChangeRequest.class);
            
            // 验证请求参数
            if (changeRequest.oldPassword() == null || changeRequest.oldPassword().trim().isEmpty()) {
                sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "原密码不能为空");
                return;
            }
            
            if (changeRequest.newPassword() == null || changeRequest.newPassword().trim().isEmpty()) {
                sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "新密码不能为空");
                return;
            }
            
            if (changeRequest.newPassword().length() < 6) {
                sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "新密码长度不能少于6位");
                return;
            }
            
            if (changeRequest.oldPassword().equals(changeRequest.newPassword())) {
                sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "新密码不能与原密码相同");
                return;
            }
            
            // 验证原密码
            if (!verifyOldPassword(userId, changeRequest.oldPassword())) {
                sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "原密码错误");
                return;
            }
            
            boolean success = Main.getUserAuthService().changePassword(userId, changeRequest.newPassword());

            if (success) {
                logger.info("用户 {} 修改密码成功，已注销全部会话", userId);
                sendSuccessResponse(response, "密码修改成功，请使用新密码重新登录");
            } else {
                sendErrorObject(response, HttpStatus.INTERNAL_SERVER_ERROR_500, "密码修改失败");
            }
            
        } catch (Exception e) {
            logger.error("修改密码时出错", e);
            sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "请求格式错误: " + e.getMessage());
        }
    }
    
    private Integer validateToken(String token) {
        return Main.getUserAuthService().validateToken(token).orElse(null);
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
                        String storedPasswordHash = rs.getString("password");
                        return argon2.verify(storedPasswordHash, oldPassword.toCharArray());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("验证原密码时出错", e);
        }
        return false;
    }
    
    /**
     * 发送成功响应
     */
    
    /**
     * 发送错误响应
     */
    
    // 内部类：密码修改请求
    // 密码修改请求（Jackson 反序列化，只读）
    private record PasswordChangeRequest(String oldPassword, String newPassword) {
    }
    
    // 内部类：成功响应
    
    // 内部类：错误响应
}