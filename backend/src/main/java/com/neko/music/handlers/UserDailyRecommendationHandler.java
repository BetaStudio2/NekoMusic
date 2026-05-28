package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import com.neko.music.service.DailyRecommendationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserDailyRecommendationHandler extends HttpServlet {
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper MAPPER = Main.getObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String token = req.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            write(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("success", false, "message", "未提供认证令牌"));
            return;
        }
        Integer userId = Main.getUserAuthService().validateToken(token).orElse(null);
        if (userId == null) {
            write(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("success", false, "message", "无效的认证令牌"));
            return;
        }

        DailyRecommendationService service = Main.getDailyRecommendationService();
        if (service == null) {
            write(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, Map.of("success", false, "message", "推荐服务未初始化"));
            return;
        }

        String forceRefresh = req.getParameter("refresh");
        if ("true".equalsIgnoreCase(forceRefresh)) {
            service.regenerateForUser(userId, LocalDate.now(CN_ZONE), true);
        }

        List<Map<String, Object>> data = service.getOrBuildTodayRecommendations(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("date", LocalDate.now(CN_ZONE).toString());
        body.put("count", data.size());
        body.put("data", data);
        write(resp, HttpServletResponse.SC_OK, body);
    }

    private void write(HttpServletResponse resp, int status, Map<String, ?> body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
