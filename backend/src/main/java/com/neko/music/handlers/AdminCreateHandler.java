package com.neko.music.handlers;

import com.neko.music.util.PermissionHelper;

import com.neko.music.Main;
import com.neko.music.model.Admin;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class AdminCreateHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminCreateHandler.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        
        // 检查管理员权限
        if (!PermissionHelper.isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            sendErrorResponse(response, "未授权访问");
            return;
        }
        
        // 获取当前管理员信息
        Admin currentAdmin = com.neko.music.util.PermissionHelper.getAdminFromRequest(request);
        if (currentAdmin == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            sendErrorResponse(response, "未授权访问");
            return;
        }
        
        // 检查是否为超级管理员
        if (!com.neko.music.util.AdminPermissionUtil.isSuperAdmin(currentAdmin)) {
            logger.warn("权限不足，只有超级管理员可以创建管理员账号");
            response.setStatus(HttpStatus.FORBIDDEN_403);
            sendErrorResponse(response, "权限不足，只有超级管理员可以创建管理员账号");
            return;
        }
        
        // 读取请求体
        String requestBody = readBody(request);
        
        CreateAdminRequest createRequest;
        try {
            createRequest = Main.getObjectMapper().readValue(requestBody.toString(), CreateAdminRequest.class);
        } catch (Exception e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            sendErrorResponse(response, "无效的请求格式");
            return;
        }
        
        // 验证请求参数
        if (createRequest.username() == null || createRequest.username().trim().isEmpty()) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            sendErrorResponse(response, "用户名不能为空");
            return;
        }
        
        if (createRequest.password() == null || createRequest.password().length() < 6) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            sendErrorResponse(response, "密码长度不能少于6位");
            return;
        }
        
        if (createRequest.role() == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            sendErrorResponse(response, "角色不能为空");
            return;
        }
        
        // 检查用户名是否已存在
        if (Main.getAdminAuthService().adminExists(createRequest.username())) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            sendErrorResponse(response, "用户名已存在");
            return;
        }
        
        // 创建管理员账号
        boolean success = Main.getAdminAuthService().createAdmin(
            createRequest.username(),
            createRequest.password(),
            createRequest.email()
        );
        
        if (success) {
            // 更新角色
            if (!"admin".equals(createRequest.role())) {
                try (Connection conn = Main.getDatabaseManager().getConnection()) {
                    String sql = "UPDATE admins SET role = ? WHERE username = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, createRequest.role());
                        stmt.setString(2, createRequest.username());
                        stmt.executeUpdate();
                    }
                } catch (Exception e) {
                    logger.error("更新管理员角色失败: {}", e.getMessage(), e);
                }
            }
            
            logger.info("成功创建管理员账号: {}, 角色: {}", createRequest.username(), createRequest.role());
            response.setStatus(HttpStatus.OK_200);
            sendSuccessResponse(response, "管理员账号创建成功");
        } else {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            sendErrorResponse(response, "创建管理员账号失败");
        }
    }
    
    
    
    
    // 内部类：创建管理员请求
    // 创建管理员请求（Jackson 反序列化，只读）
    private record CreateAdminRequest(String username, String email, String password, String role) {
    }
}