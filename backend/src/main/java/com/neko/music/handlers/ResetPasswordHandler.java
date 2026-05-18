package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
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

/**
 * 重置密码处理器
 */
@WebServlet("/user/reset-password")
public class ResetPasswordHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ResetPasswordHandler.class);
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

        logger.info("收到重置密码请求");

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
            String code = null;
            String newPassword = null;

            if (requestData != null) {
                if (requestData.has("email")) {
                    email = requestData.get("email").asText();
                }
                if (requestData.has("code")) {
                    code = requestData.get("code").asText();
                }
                if (requestData.has("newPassword")) {
                    newPassword = requestData.get("newPassword").asText();
                }
            }

            // 验证请求参数
            if (email == null || email.trim().isEmpty()) {
                sendResponse(response, false, "邮箱不能为空", null);
                return;
            }

            if (code == null || code.trim().isEmpty()) {
                sendResponse(response, false, "验证码不能为空", null);
                return;
            }

            if (newPassword == null || newPassword.trim().isEmpty()) {
                sendResponse(response, false, "新密码不能为空", null);
                return;
            }

            // 验证邮箱格式
            if (!isValidEmail(email)) {
                sendResponse(response, false, "邮箱格式不正确", null);
                return;
            }

            // 验证密码长度
            if (newPassword.length() < 6 || newPassword.length() > 30) {
                sendResponse(response, false, "密码长度必须在6-30位之间", null);
                return;
            }

            // 验证验证码
            if (!userAuthService.verifyCode(email, code)) {
                logger.warn("验证码验证失败: {}", email);
                sendResponse(response, false, "验证码错误或已过期", null);
                return;
            }

            // 重置密码
            boolean success = userAuthService.resetPassword(email, newPassword);

            if (success) {
                logger.info("密码重置成功: {}", email);
                sendResponse(response, true, "密码重置成功，请使用新密码重新登录", null);
            } else {
                logger.error("密码重置失败: {}", email);
                sendResponse(response, false, "密码重置失败，请稍后重试", null);
            }

        } catch (Exception e) {
            logger.error("处理重置密码请求时发生错误: {}", e.getMessage(), e);
            sendResponse(response, false, "服务器内部错误", null);
        }
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
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