package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.model.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AdminStatsHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminStatsHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // 处理预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // 验证管理员登录状态
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(response, 401, "需要管理员权限");
            return;
        }
        
        String token = authHeader.substring(7); // 移除 "Bearer " 前缀
        if (!Main.getAdminAuthService().validateAdminToken(token)) {
            sendErrorResponse(response, 401, "需要管理员权限");
            return;
        }
        
        // 检查是否有统计查看权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.STATS_VIEW)) {
            logger.warn("权限不足，无统计查看权限");
            return;
        }

        try {
            Map<String, Object> stats = getPlatformStats();
            
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("data", stats);
            
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter out = response.getWriter();
            out.print(Main.getObjectMapper().writeValueAsString(successResponse));
            out.flush();
        } catch (Exception e) {
            logger.error("获取统计信息错误", e);
            sendErrorResponse(response, 500, "服务器内部错误");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 也可以通过POST请求获取统计信息（需要管理员登录验证）
        doGet(request, response);
    }



    private Map<String, Object> getPlatformStats() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 获取总音乐数
            String musicQuery = "SELECT COUNT(*) FROM music"; // 正确的音乐表
            try (PreparedStatement stmt = conn.prepareStatement(musicQuery);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalMusic", rs.getInt(1));
                } else {
                    stats.put("totalMusic", 0);
                }
            }
            
            // 获取总用户数
            String userQuery = "SELECT COUNT(*) FROM users"; // 正确的用户表
            try (PreparedStatement stmt = conn.prepareStatement(userQuery);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalUsers", rs.getInt(1));
                } else {
                    stats.put("totalUsers", 0);
                }
            }
            
            // 获取今日访问量 - 已移除访问日志功能
            stats.put("todayVisits", 0);
            
            // 获取总搜索次数 - 已移除搜索日志功能
            stats.put("totalSearches", 0);
            
            // 获取最近7天的用户注册趋势数据
            String userTrendQuery = "SELECT DATE(created_at) as date, COUNT(*) as count FROM users WHERE DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) GROUP BY DATE(created_at) ORDER BY DATE(created_at)";
            try (PreparedStatement stmt = conn.prepareStatement(userTrendQuery);
                 ResultSet rs = stmt.executeQuery()) {
                // 创建一个包含最近7天数据的列表
                Map<String, Integer> userTrendMap = new HashMap<>();
                while (rs.next()) {
                    userTrendMap.put(rs.getString("date"), rs.getInt("count"));
                }
                stats.put("userTrendData", userTrendMap);
            }
            
            // 获取最近7天的音乐添加趋势数据
            String musicTrendQuery = "SELECT DATE(created_at) as date, COUNT(*) as count FROM music WHERE DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) GROUP BY DATE(created_at) ORDER BY DATE(created_at)";
            try (PreparedStatement stmt = conn.prepareStatement(musicTrendQuery);
                 ResultSet rs = stmt.executeQuery()) {
                Map<String, Integer> musicTrendMap = new HashMap<>();
                while (rs.next()) {
                    musicTrendMap.put(rs.getString("date"), rs.getInt("count"));
                }
                stats.put("musicTrendData", musicTrendMap);
            }
            
            // 获取最近7天的访问量趋势数据 - 已移除访问日志功能
            stats.put("visitTrendData", new HashMap<>());
            
            // 获取最近7天的搜索量趋势数据 - 已移除搜索日志功能
            stats.put("searchTrendData", new HashMap<>());
        } catch (Exception e) {
            logger.error("查询统计数据时出错", e);
            // 出错时返回默认值
            stats.put("totalMusic", 0);
            stats.put("totalUsers", 0);
            stats.put("todayVisits", 0); // 保留此值以便界面兼容，但不再使用访问日志
            stats.put("totalSearches", 0); // 保留此值以便界面兼容，但不再使用搜索日志
            stats.put("userTrendData", new HashMap<>());
            stats.put("musicTrendData", new HashMap<>());
            stats.put("visitTrendData", new HashMap<>()); // 保留此值以便界面兼容，但不再使用访问日志
            stats.put("searchTrendData", new HashMap<>()); // 保留此值以便界面兼容，但不再使用搜索日志
        }
        
        return stats;
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);

        response.setStatus(statusCode);
        PrintWriter out = response.getWriter();
        out.print(Main.getObjectMapper().writeValueAsString(errorResponse));
        out.flush();
    }
}