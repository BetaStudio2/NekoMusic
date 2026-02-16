package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChartDataHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ChartDataHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            Map<String, Object> chartData = getChartData();
            
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("data", chartData);
            
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter out = response.getWriter();
            out.print(objectMapper.writeValueAsString(successResponse));
            out.flush();
        } catch (Exception e) {
            logger.error("获取图表数据错误", e);
            sendErrorResponse(response, 500, "服务器内部错误");
        }
    }



    private Map<String, Object> getChartData() throws Exception {
        Map<String, Object> chartData = new HashMap<>();
        
        // 获取最近7天的日期
        String[] dates = new String[7];
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dates[6 - i] = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        
        chartData.put("dates", dates);
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 获取最近7天的用户注册数据
            String userTrendQuery = "SELECT DATE(created_at) as date, COUNT(*) as count FROM users WHERE DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE(created_at) ORDER BY DATE(created_at)";
            try (PreparedStatement stmt = conn.prepareStatement(userTrendQuery);
                 ResultSet rs = stmt.executeQuery()) {
                Map<String, Integer> userTrendData = new HashMap<>();
                while (rs.next()) {
                    userTrendData.put(rs.getString("date"), rs.getInt("count"));
                }
                chartData.put("userTrendData", userTrendData);
            }
            
            // 获取最近7天的音乐添加数据
            String musicTrendQuery = "SELECT DATE(created_at) as date, COUNT(*) as count FROM music WHERE DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE(created_at) ORDER BY DATE(created_at)";
            try (PreparedStatement stmt = conn.prepareStatement(musicTrendQuery);
                 ResultSet rs = stmt.executeQuery()) {
                Map<String, Integer> musicTrendData = new HashMap<>();
                while (rs.next()) {
                    musicTrendData.put(rs.getString("date"), rs.getInt("count"));
                }
                chartData.put("musicTrendData", musicTrendData);
            }
            
            // 获取最近7天的访问量数据 - 已移除访问日志功能
            chartData.put("visitTrendData", new HashMap<>());
        } catch (Exception e) {
            logger.error("查询图表数据时出错", e);
            // 出错时返回空数据
            chartData.put("userTrendData", new HashMap<>());
            chartData.put("musicTrendData", new HashMap<>());
            chartData.put("visitTrendData", new HashMap<>()); // 保留此值以便界面兼容，但不再使用访问日志
        }
        
        return chartData;
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);

        response.setStatus(statusCode);
        PrintWriter out = response.getWriter();
        out.print(objectMapper.writeValueAsString(errorResponse));
        out.flush();
    }
}