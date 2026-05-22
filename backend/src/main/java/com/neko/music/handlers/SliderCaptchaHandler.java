package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.service.SliderCaptchaService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/** GET /api/captcha/slider — 签发滑块挑战（注册页应始终拉取并展示） */
@WebServlet("/api/captcha/slider")
public class SliderCaptchaHandler extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SliderCaptchaHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            SliderCaptchaService svc = Main.getSliderCaptchaService();
            Map<String, Object> data = svc.createChallengePayload();
            sendJson(response, true, "ok", data);
        } catch (Exception e) {
            logger.error("生成滑块验证码失败", e);
            sendJson(response, false, "生成验证码失败喵", null);
        }
    }

    private void sendJson(HttpServletResponse response, boolean success, String message, Object data) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("success", success);
        body.put("message", message);
        body.put("data", data);
        try (PrintWriter out = response.getWriter()) {
            out.print(Main.getObjectMapper().writeValueAsString(body));
            out.flush();
        }
    }
}
