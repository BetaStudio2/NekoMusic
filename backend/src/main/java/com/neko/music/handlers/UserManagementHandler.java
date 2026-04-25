package com.neko.music.handlers;

import com.neko.music.Main;
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

public class UserManagementHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否有用户查看权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.USER_VIEW)) {
            logger.warn("权限不足，无用户查看权限");
            return;
        }

        String pathInfo = request.getPathInfo();
        
        // 如果 pathInfo 为 null 或空，说明是访问 /api/users，返回所有用户
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("")) {
            // 获取所有普通用户
            List<RegularUser> regularUsers = getAllRegularUsers();

            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            RegularUsersResponse regularUsersResponse = new RegularUsersResponse(true, "获取用户列表成功", regularUsers);
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(regularUsersResponse));
            return;
        }

        // 其他路径处理（如果需要）
        response.setStatus(HttpStatus.NOT_FOUND_404);
        response.setContentType("application/json;charset=utf-8");
        ErrorResponse errorResponse = new ErrorResponse("未找到请求的资源");
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否有用户编辑权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.USER_EDIT)) {
            logger.warn("权限不足，无用户编辑权限");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.endsWith("/edit")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的请求路径");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        // 解析用户ID
        String pathWithoutEdit = pathInfo.substring(0, pathInfo.indexOf("/edit"));
        String idStr = pathWithoutEdit.replace("/", "");
        int userId;
        try {
            userId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的用户ID");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            requestBody.append(line);
        }

        UserEditRequest editRequest;
        try {
            editRequest = Main.getObjectMapper().readValue(requestBody.toString(), UserEditRequest.class);
        } catch (Exception e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的请求格式");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        // 修改用户信息
        boolean success = updateUserInfo(userId, editRequest);
        
        if (!success) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("修改用户信息失败");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        SuccessResponse successResponse = new SuccessResponse(true, "修改用户信息成功");
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(successResponse));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("未授权访问");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 检查是否有用户删除权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.USER_DELETE)) {
            logger.warn("权限不足，无用户删除权限");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("用户ID不能为空");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        // 解析用户ID
        String idStr = pathInfo.replace("/", "");
        int userId;
        try {
            userId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的用户ID");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        // 删除用户
        boolean success = deleteUser(userId);
        
        if (!success) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("删除用户失败");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        SuccessResponse successResponse = new SuccessResponse(true, "删除用户成功");
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(successResponse));
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

    // 获取所有普通用户
    private List<RegularUser> getAllRegularUsers() {
        List<RegularUser> regularUsers = new ArrayList<>();
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, username, email, created_at FROM users ORDER BY created_at DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    RegularUser regularUser = new RegularUser();
                    regularUser.setId(rs.getInt("id"));
                    regularUser.setUsername(rs.getString("username"));
                    regularUser.setEmail(rs.getString("email"));
                    regularUser.setRegisterTime(rs.getTimestamp("created_at").toString());
                    
                    regularUsers.add(regularUser);
                }
            }
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
        }
        
        return regularUsers;
    }

    // 删除用户
    private boolean deleteUser(int userId) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "DELETE FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (Exception e) {
            logger.error("删除用户失败", e);
            return false;
        }
    }

    // 修改用户信息
    private boolean updateUserInfo(int userId, UserEditRequest editRequest) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 如果提供了新密码，则修改密码
            if (editRequest.getPassword() != null && !editRequest.getPassword().trim().isEmpty()) {
                // 使用Argon2加密密码
                de.mkammerer.argon2.Argon2 argon2 = de.mkammerer.argon2.Argon2Factory.create();
                String passwordHash = argon2.hash(10, 65536, 1, editRequest.getPassword().toCharArray());
                
                String sql = "UPDATE users SET password = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, passwordHash);
                    stmt.setInt(2, userId);
                    int rowsAffected = stmt.executeUpdate();
                    return rowsAffected > 0;
                }
            }
            
            return true; // 如果没有提供密码，也返回成功
        } catch (Exception e) {
            logger.error("修改用户信息失败", e);
            return false;
        }
    }

    // 内部类：普通用户
    public static class RegularUser {
        private int id;
        private String username;
        private String email;
        private String registerTime;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRegisterTime() { return registerTime; }
        public void setRegisterTime(String registerTime) { this.registerTime = registerTime; }
    }

    // 内部类：用户编辑请求
    private static class UserEditRequest {
        private String password;

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // 内部类：普通用户列表响应
    private static class RegularUsersResponse {
        private boolean success;
        private String message;
        private List<RegularUser> data;

        public RegularUsersResponse(boolean success, String message, List<RegularUser> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<RegularUser> getData() { return data; }
        public void setData(List<RegularUser> data) { this.data = data; }
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