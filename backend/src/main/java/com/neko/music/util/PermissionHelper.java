package com.neko.music.util;

import com.neko.music.Main;
import com.neko.music.model.Admin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 权限验证辅助类
 * 用于在各个 Handler 中验证管理员权限
 */
public class PermissionHelper {
    private static final Logger logger = LoggerFactory.getLogger(PermissionHelper.class);
    
    /**
     * 从请求中获取管理员信息
     * @param request HTTP请求
     * @return 管理员对象，如果未登录或令牌无效则返回null
     */
    public static Admin getAdminFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        
        String token = authHeader.substring(7);
        return Main.getAdminAuthService().getAdminByToken(token);
    }
    
    /**
     * 验证管理员是否有指定权限
     * @param request HTTP请求
     * @param response HTTP响应
     * @param permission 需要的权限
     * @return 是否有权限
     */
    public static boolean checkPermission(HttpServletRequest request, HttpServletResponse response, AdminPermissionUtil.Permission permission) {
        Admin admin = getAdminFromRequest(request);
        
        if (admin == null) {
            sendUnauthorizedResponse(response);
            return false;
        }
        
        if (!AdminPermissionUtil.hasPermission(admin, permission)) {
            sendForbiddenResponse(response);
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证管理员是否为超级管理员
     * @param request HTTP请求
     * @param response HTTP响应
     * @return 是否为超级管理员
     */
    public static boolean checkSuperAdmin(HttpServletRequest request, HttpServletResponse response) {
        Admin admin = getAdminFromRequest(request);
        
        if (admin == null) {
            sendUnauthorizedResponse(response);
            return false;
        }
        
        if (!AdminPermissionUtil.isSuperAdmin(admin)) {
            sendForbiddenResponse(response);
            return false;
        }
        
        return true;
    }
    
    /**
     * 发送未授权响应
     * @param response HTTP响应
     */
    private static void sendUnauthorizedResponse(HttpServletResponse response) {
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"未授权访问\"}");
        } catch (IOException e) {
            logger.error("发送未授权响应失败", e);
        }
    }
    
    /**
     * 发送禁止访问响应
     * @param response HTTP响应
     */
    private static void sendForbiddenResponse(HttpServletResponse response) {
        try {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"权限不足\"}");
        } catch (IOException e) {
            logger.error("发送禁止访问响应失败", e);
        }
    }
}
