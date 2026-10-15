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
import java.util.Map;
import com.neko.music.i18n.ApiMessages;

public class UserDailyRecommendationHandler extends HttpServlet {
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper MAPPER = Main.getObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String token = req.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            write(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("success", false, "message", "auth.token_missing"));
            return;
        }
        Integer userId = Main.getUserAuthService().validateToken(token).orElse(null);
        if (userId == null) {
            write(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("success", false, "message", "auth.invalid_bearer"));
            return;
        }

        DailyRecommendationService service = Main.getDailyRecommendationService();
        if (service == null) {
            write(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, Map.of("success", false, "message", ApiMessages.translate("recommend.service_unavailable")));
            return;
        }

        String forceRefresh = req.getParameter("refresh");
        if ("true".equalsIgnoreCase(forceRefresh)) {
            service.regenerateForUser(userId, LocalDate.now(CN_ZONE), true);
        }

        byte[] body = service.getOrBuildTodayBody(userId);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getOutputStream().write(body);
        resp.getOutputStream().flush();
    }

    private void write(HttpServletResponse resp, int status, Map<String, ?> body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
