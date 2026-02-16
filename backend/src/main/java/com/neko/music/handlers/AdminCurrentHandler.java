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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AdminCurrentHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminCurrentHandler.class);
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
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
        
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("admin", Map.of(
            "id", currentAdmin.getId(),
            "username", currentAdmin.getUsername(),
            "email", currentAdmin.getEmail(),
            "role", currentAdmin.getRole(),
            "createdAt", currentAdmin.getCreatedAt(),
            "lastLoginAt", currentAdmin.getLastLoginAt()
        ));
        response.getWriter().println(objectMapper.writeValueAsString(result));
    }
    
    private boolean isAdminAuthorized(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String token = authHeader.substring(7);
        return Main.getAdminAuthService().validateAdminToken(token);
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