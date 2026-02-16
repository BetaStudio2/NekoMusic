package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import com.neko.music.model.Admin;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

public class AdminCreateHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminCreateHandler.class);
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 获取当前管理员信息
        Admin currentAdmin = com.neko.music.util.PermissionHelper.getAdminFromRequest(request);
        if (currentAdmin == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否为超级管理员
        if (!com.neko.music.util.AdminPermissionUtil.isSuperAdmin(currentAdmin)) {
            logger.warn("权限不足，只有超级管理员可以创建管理员账号");
            response.setStatus(HttpStatus.FORBIDDEN_403);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("权限不足，只有超级管理员可以创建管理员账号");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            requestBody.append(line);
        }
        
        CreateAdminRequest createRequest;
        try {
            createRequest = objectMapper.readValue(requestBody.toString(), CreateAdminRequest.class);
        } catch (Exception e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的请求格式");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 验证请求参数
        if (createRequest.getUsername() == null || createRequest.getUsername().trim().isEmpty()) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("用户名不能为空");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        if (createRequest.getPassword() == null || createRequest.getPassword().length() < 6) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("密码长度不能少于6位");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        if (createRequest.getRole() == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("角色不能为空");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 检查用户名是否已存在
        if (Main.getAdminAuthService().adminExists(createRequest.getUsername())) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("用户名已存在");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 创建管理员账号
        boolean success = Main.getAdminAuthService().createAdmin(
            createRequest.getUsername(),
            createRequest.getPassword(),
            createRequest.getEmail()
        );
        
        if (success) {
            // 更新角色
            if (!"admin".equals(createRequest.getRole())) {
                try (Connection conn = Main.getDatabaseManager().getConnection()) {
                    String sql = "UPDATE admins SET role = ? WHERE username = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, createRequest.getRole());
                        stmt.setString(2, createRequest.getUsername());
                        stmt.executeUpdate();
                    }
                } catch (Exception e) {
                    logger.error("更新管理员角色失败: {}", e.getMessage(), e);
                }
            }
            
            logger.info("成功创建管理员账号: {}, 角色: {}", createRequest.getUsername(), createRequest.getRole());
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            SuccessResponse successResponse = new SuccessResponse(true, "管理员账号创建成功");
            response.getWriter().println(objectMapper.writeValueAsString(successResponse));
        } else {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("创建管理员账号失败");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
        }
    }
    
    private boolean isAdminAuthorized(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String token = authHeader.substring(7);
        return Main.getAdminAuthService().validateAdminToken(token);
    }
    
    // 内部类：创建管理员请求
    private static class CreateAdminRequest {
        private String username;
        private String email;
        private String password;
        private String role;
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
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
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
    
    // 内部类：错误响应
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}