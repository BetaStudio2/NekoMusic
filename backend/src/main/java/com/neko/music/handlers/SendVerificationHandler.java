package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.service.SendVerificationCodeResult;
import com.neko.music.service.UserAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/user/send-verification")
public class SendVerificationHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(SendVerificationHandler.class);
    private UserAuthService userAuthService;

    @Override
    public void init() throws ServletException {
        userAuthService = Main.getUserAuthService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        logger.info("收到发送验证码请求");

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        try {
            JsonNode requestData = Main.getObjectMapper().readTree(requestBody.toString());

            String email = null;
            String username = "用户"; // 默认用户名，实际应用中可能需要从请求中获取
            if (requestData != null) {
                if (requestData.has("email")) {
                    email = requestData.get("email").asText();
                }
                if (requestData.has("username")) {
                    username = requestData.get("username").asText();
                }
            }

            // 验证请求参数
            if (email == null || email.trim().isEmpty()) {
                sendResponse(response, false, "邮箱不能为空", null);
                return;
            }

            // 验证邮箱格式
            if (!isValidEmail(email)) {
                sendResponse(response, false, "邮箱格式不正确", null);
                return;
            }

            // 检查邮箱是否在白名单中
            if (!userAuthService.isWhitelistedEmail(email)) {
                logger.warn("邮箱不在白名单中: {}", email);
                sendResponse(response, false, "该邮箱域名不在允许的注册列表中", null);
                return;
            }

            SendVerificationCodeResult result = userAuthService.sendVerificationCode(email, username);

            if (result.rateLimited()) {
                sendCooldownResponse(response, result.retryAfterSec());
                return;
            }
            if (result.success()) {
                logger.info("验证码发送成功: {}", email);
                sendResponse(response, true, "验证码已发送至您的邮箱", null);
            } else {
                logger.error("验证码发送失败: {}", email);
                sendResponse(response, false, "验证码发送失败，请稍后重试", null);
            }

        } catch (Exception e) {
            logger.error("处理发送验证码请求时发生错误: {}", e.getMessage(), e);
            sendResponse(response, false, "服务器内部错误", null);
        }
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private void sendCooldownResponse(HttpServletResponse response, long retryAfterSec) throws IOException {
        long sec = Math.max(1, retryAfterSec);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(sec));

        Map<String, Object> data = new HashMap<>();
        data.put("retryAfterSec", sec);

        sendResponse(response, false, "发送过于频繁，请 " + sec + " 秒后再试", data);
    }

    /**
     * 发送JSON响应
     */
    private void sendResponse(HttpServletResponse response, boolean success, String message, Object data) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", success);
        responseMap.put("message", message);
        responseMap.put("data", data);

        try (PrintWriter out = response.getWriter()) {
            out.print(Main.getObjectMapper().writeValueAsString(responseMap));
            out.flush();
        }
    }
}