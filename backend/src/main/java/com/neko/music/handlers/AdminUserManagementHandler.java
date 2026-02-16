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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class AdminUserManagementHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminUserManagementHandler.class);
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

        String pathInfo = request.getPathInfo();
        
        // 如果 pathInfo 为 null 或空，说明是访问 /api/admin/users，返回所有管理员
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("")) {
            // 获取所有管理员
            List<AdminUser> adminUsers = getAllAdminUsers();

            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            AdminUsersResponse adminUsersResponse = new AdminUsersResponse(true, "获取管理员列表成功", adminUsers);
            response.getWriter().println(objectMapper.writeValueAsString(adminUsersResponse));
            return;
        }

        // 其他路径处理（如果需要）
        response.setStatus(HttpStatus.NOT_FOUND_404);
        response.setContentType("application/json;charset=utf-8");
        ErrorResponse errorResponse = new ErrorResponse("未找到请求的资源");
        response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("收到 PUT 请求，路径: {}", request.getRequestURI());
        logger.info("PathInfo: {}", request.getPathInfo());
        
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            logger.warn("未授权访问");
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否为超级管理员（只有超级管理员可以修改管理员密码）
        if (!isSuperAdminAuthorized(request)) {
            logger.warn("权限不足，非超级管理员");
            response.setStatus(HttpStatus.FORBIDDEN_403);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("权限不足，只有超级管理员可以修改管理员信息");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.endsWith("/edit")) {
            logger.warn("无效的请求路径: {}", pathInfo);
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的请求路径");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        // 解析管理员ID
        String pathWithoutEdit = pathInfo.substring(0, pathInfo.indexOf("/edit"));
        String idStr = pathWithoutEdit.replace("/", "");
        int adminId;
        try {
            adminId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的管理员ID");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            requestBody.append(line);
        }

        AdminEditRequest editRequest;
        try {
            editRequest = objectMapper.readValue(requestBody.toString(), AdminEditRequest.class);
        } catch (Exception e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的请求格式");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        // 修改管理员信息
        boolean success = updateAdminInfo(adminId, editRequest);
        
        if (!success) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("修改管理员信息失败");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        SuccessResponse successResponse = new SuccessResponse(true, "修改管理员信息成功");
        response.getWriter().println(objectMapper.writeValueAsString(successResponse));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否为超级管理员（只有超级管理员可以删除管理员）
        if (!isSuperAdminAuthorized(request)) {
            response.setStatus(HttpStatus.FORBIDDEN_403);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("权限不足，只有超级管理员可以删除管理员");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("管理员ID不能为空");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        // 解析管理员ID
        String idStr = pathInfo.replace("/", "");
        int adminId;
        try {
            adminId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的管理员ID");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        // 删除管理员
        boolean success = deleteAdminUser(adminId);
        
        if (!success) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("删除管理员失败");
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        SuccessResponse successResponse = new SuccessResponse(true, "删除管理员成功");
        response.getWriter().println(objectMapper.writeValueAsString(successResponse));
    }

    // 检查管理员权限
    private boolean isAdminAuthorized(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authHeader.substring(7);
        return Main.getAdminAuthService().validateAdminToken(token);
    }
    
    // 检查是否为超级管理员（用于管理员管理操作）
    private boolean isSuperAdminAuthorized(HttpServletRequest request) {
        com.neko.music.model.Admin admin = com.neko.music.util.PermissionHelper.getAdminFromRequest(request);
        return admin != null && com.neko.music.util.AdminPermissionUtil.isSuperAdmin(admin);
    }

    // 获取所有管理员用户
    private List<AdminUser> getAllAdminUsers() {
        List<AdminUser> adminUsers = new ArrayList<>();
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 先检查表是否存在
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "admins", null)) {
                if (!rs.next()) {
                    logger.error("admins表不存在");
                    return adminUsers;
                }
            }
            
            String sql = "SELECT id, username, email, role, created_at FROM admins ORDER BY created_at DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    AdminUser adminUser = new AdminUser();
                    adminUser.setId(rs.getInt("id"));
                    adminUser.setUsername(rs.getString("username"));
                    adminUser.setEmail(rs.getString("email"));
                    adminUser.setRole(rs.getString("role"));
                    
                    // created_at 是 BIGINT 类型
                    long createdAt = rs.getLong("created_at");
                    adminUser.setRegisterTime(new java.sql.Timestamp(createdAt).toString());
                    
                    adminUsers.add(adminUser);
                }
            }
            
            logger.info("成功获取 {} 个管理员账户", adminUsers.size());
        } catch (Exception e) {
            logger.error("获取管理员列表失败", e);
        }
        
        return adminUsers;
    }

    // 删除管理员用户
    private boolean deleteAdminUser(int adminId) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "DELETE FROM admins WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, adminId);
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (Exception e) {
            logger.error("删除管理员失败", e);
            return false;
        }
    }

    // 修改管理员信息
    private boolean updateAdminInfo(int adminId, AdminEditRequest editRequest) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 如果提供了新密码，则修改密码
            if (editRequest.getPassword() != null && !editRequest.getPassword().trim().isEmpty()) {
                // 使用Argon2加密密码
                de.mkammerer.argon2.Argon2 argon2 = de.mkammerer.argon2.Argon2Factory.create();
                String passwordHash = argon2.hash(10, 65536, 1, editRequest.getPassword().toCharArray());
                
                String sql = "UPDATE admins SET password_hash = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, passwordHash);
                    stmt.setInt(2, adminId);
                    int rowsAffected = stmt.executeUpdate();
                    return rowsAffected > 0;
                }
            }
            
            return true; // 如果没有提供密码，也返回成功
        } catch (Exception e) {
            logger.error("修改管理员信息失败", e);
            return false;
        }
    }

    // 内部类：管理员用户
    public static class AdminUser {
        private int id;
        private String username;
        private String email;
        private String role;
        private String registerTime;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getRegisterTime() { return registerTime; }
        public void setRegisterTime(String registerTime) { this.registerTime = registerTime; }
    }

    // 内部类：管理员编辑请求
    private static class AdminEditRequest {
        private String password;

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // 内部类：管理员用户列表响应
    private static class AdminUsersResponse {
        private boolean success;
        private String message;
        private List<AdminUser> data;

        public AdminUsersResponse(boolean success, String message, List<AdminUser> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<AdminUser> getData() { return data; }
        public void setData(List<AdminUser> data) { this.data = data; }
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