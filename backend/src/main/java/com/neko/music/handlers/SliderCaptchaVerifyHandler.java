package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * POST /api/captcha/slider/verify — 提交位移，成功返回短时 captchaPassToken（供发送邮箱验证码接口消费）。
 */
@WebServlet("/api/captcha/slider/verify")
public class SliderCaptchaVerifyHandler extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SliderCaptchaVerifyHandler.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        try {
            JsonNode root = Main.getObjectMapper().readTree(body.toString());
            if (root == null) {
                sendJson(response, false, "请求体不能为空喵", null);
                return;
            }
            String token = root.has("captchaToken") ? root.get("captchaToken").asText().trim() : "";
            if (token.isEmpty()) {
                sendJson(response, false, "缺少 captchaToken 喵", null);
                return;
            }
            if (!root.has("captchaOffsetX") || !root.get("captchaOffsetX").isNumber()) {
                sendJson(response, false, "缺少 captchaOffsetX 喵", null);
                return;
            }
            int offsetX = root.get("captchaOffsetX").asInt();

            Optional<String> pass = Main.getSliderCaptchaService().verifySlideAndIssuePass(token, offsetX);
            if (pass.isEmpty()) {
                sendJson(response, false, "拼图位置不正确或已失效，请换一张重试喵", null);
                return;
            }
            sendJson(response, true, "验证通过喵", Map.of("captchaPassToken", pass.get()));
        } catch (Exception e) {
            logger.error("滑块校验接口异常", e);
            sendJson(response, false, "服务器内部错误喵", null);
        }
    }

    private void sendJson(HttpServletResponse response, boolean success, String message, Object data) throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("message", message);
        map.put("data", data);
        try (PrintWriter out = response.getWriter()) {
            out.print(Main.getObjectMapper().writeValueAsString(map));
            out.flush();
        }
    }
}
