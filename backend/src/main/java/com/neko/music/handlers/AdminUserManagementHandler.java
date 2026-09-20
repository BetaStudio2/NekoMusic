package com.neko.music.handlers;

import com.neko.music.util.PermissionHelper;

import com.neko.music.model.SuccessResponse;
import com.neko.music.model.ErrorResponse;
import com.neko.music.Main;
import com.neko.music.model.Admin;
import com.neko.music.util.HandlerResponses;
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

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
            return;
        }
        
        String pathInfo = request.getPathInfo();
        
        // 如果 pathInfo 为 null 或空，说明是访问 /api/admin/users
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("")) {
            // 获取当前管理员信息
            com.neko.music.model.Admin currentAdmin = com.neko.music.util.PermissionHelper.getAdminFromRequest(request);
            
            if (currentAdmin == null) {
                HandlerResponses.writeJson(response, HttpStatus.UNAUTHORIZED_401, new ErrorResponse("未授权访问"));
                return;
            }
            
            List<AdminUser> adminUsers = new ArrayList<>();
            
            // 审核员只能看到自己的信息
            if (com.neko.music.util.AdminPermissionUtil.isAuditor(currentAdmin)) {
                AdminUser self = new AdminUser(
                        currentAdmin.id(),
                        currentAdmin.username(),
                        currentAdmin.email(),
                        currentAdmin.role(),
                        new java.sql.Timestamp(currentAdmin.createdAt()).toString());
                adminUsers.add(self);
                logger.info("审核员 {} 查看自己的管理员信息", currentAdmin.username());
            } else if (com.neko.music.util.AdminPermissionUtil.isAdmin(currentAdmin)) {
                // 管理员可以看到所有管理员
                adminUsers = getAllAdminUsers();
                logger.info("管理员 {} 查看管理员列表", currentAdmin.username());
            } else if (com.neko.music.util.AdminPermissionUtil.isSuperAdmin(currentAdmin)) {
                // 超管可以看到所有管理员
                adminUsers = getAllAdminUsers();
                logger.info("超级管理员 {} 查看管理员列表", currentAdmin.username());
            }

            HandlerResponses.writeJson(response, HttpStatus.OK_200, new AdminUsersResponse(true, "获取管理员列表成功", adminUsers));
            return;
        }

        // 其他路径处理（如果需要）
        HandlerResponses.writeJson(response, HttpStatus.NOT_FOUND_404, new ErrorResponse("未找到请求的资源"));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("收到 PUT 请求，路径: {}", request.getRequestURI());
        logger.info("PathInfo: {}", request.getPathInfo());
        
        // 检查管理员权限
        if (!PermissionHelper.isAdminAuthorized(request)) {
            logger.warn("未授权访问");
            HandlerResponses.writeJson(response, HttpStatus.UNAUTHORIZED_401, new ErrorResponse("未授权访问"));
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.endsWith("/edit")) {
            logger.warn("无效的请求路径: {}", pathInfo);
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的请求路径"));
            return;
        }

        // 解析管理员ID
        String pathWithoutEdit = pathInfo.substring(0, pathInfo.indexOf("/edit"));
        String idStr = pathWithoutEdit.replace("/", "");
        int adminId;
        try {
            adminId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的管理员ID"));
            return;
        }
        
        // 获取当前管理员信息
        com.neko.music.model.Admin currentAdmin = com.neko.music.util.PermissionHelper.getAdminFromRequest(request);
        if (currentAdmin == null) {
            HandlerResponses.writeJson(response, HttpStatus.UNAUTHORIZED_401, new ErrorResponse("未授权访问"));
            return;
        }
        
        // 检查权限：超管可以编辑任何管理员，管理员只能编辑自己
        if (!com.neko.music.util.AdminPermissionUtil.isSuperAdmin(currentAdmin)) {
            // 如果不是超管，只能编辑自己的账号
            if (currentAdmin.id() != adminId) {
                logger.warn("权限不足，管理员只能编辑自己的账号");
                HandlerResponses.writeJson(response, HttpStatus.FORBIDDEN_403, new ErrorResponse("权限不足，管理员只能编辑自己的账号"));
                return;
            }
        }

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            requestBody.append(line);
        }

        AdminEditRequest editRequest;
        try {
            editRequest = Main.getObjectMapper().readValue(requestBody.toString(), AdminEditRequest.class);
        } catch (Exception e) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的请求格式"));
            return;
        }

        // 修改管理员信息
        boolean success = updateAdminInfo(adminId, editRequest);
        
        if (!success) {
            HandlerResponses.writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, new ErrorResponse("修改管理员信息失败"));
            return;
        }

        HandlerResponses.writeJson(response, HttpStatus.OK_200, new SuccessResponse(true, "修改管理员信息成功"));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
            return;
        }
        
        // 检查是否有管理员管理权限（只有超管有此权限）
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.ADMIN_DELETE)) {
            logger.warn("权限不足，无管理员管理权限");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("管理员ID不能为空"));
            return;
        }

        // 解析管理员ID
        String idStr = pathInfo.replace("/", "");
        int adminId;
        try {
            adminId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的管理员ID"));
            return;
        }

        // 删除管理员
        boolean success = deleteAdminUser(adminId);
        
        if (!success) {
            HandlerResponses.writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, new ErrorResponse("删除管理员失败"));
            return;
        }

        HandlerResponses.writeJson(response, HttpStatus.OK_200, new SuccessResponse(true, "删除管理员成功"));
    }

    // 检查管理员权限
    
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
                    // created_at 是 BIGINT 类型
                    long createdAt = rs.getLong("created_at");
                    AdminUser adminUser = new AdminUser(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("role"),
                            new java.sql.Timestamp(createdAt).toString());
                    
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
            if (editRequest.password() != null && !editRequest.password().trim().isEmpty()) {
                // 使用Argon2加密密码
                de.mkammerer.argon2.Argon2 argon2 = de.mkammerer.argon2.Argon2Factory.create();
                String passwordHash = argon2.hash(10, 65536, 1, editRequest.password().toCharArray());
                
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
    public record AdminUser(int id, String username, String email, String role, String registerTime) {
    }
    // 内部类：管理员编辑请求
    // 管理员编辑请求（Jackson 反序列化，只读）
    private record AdminEditRequest(String password) {
    }
    // 内部类：管理员用户列表响应
    private record AdminUsersResponse(boolean success, String message, List<AdminUser> data) {
    }
    // 内部类：成功响应

    // 内部类：错误响应
}
