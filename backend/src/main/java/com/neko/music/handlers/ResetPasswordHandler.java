package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.service.SourceBanService;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.ClientIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * 重置密码处理器
 */
@WebServlet("/user/reset-password")
public class ResetPasswordHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(ResetPasswordHandler.class);
    private UserAuthService userAuthService;
    private SourceBanService sourceBanService;

    @Override
    public void init() throws ServletException {
        userAuthService = Main.getUserAuthService();
        sourceBanService = Main.getSourceBanService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");

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
                sendResponse(response, false, "auth.password_reset.email_required", null);
                return;
            }

            if (code == null || code.trim().isEmpty()) {
                sendResponse(response, false, "auth.password_reset.captcha_required", null);
                return;
            }

            if (newPassword == null || newPassword.trim().isEmpty()) {
                sendResponse(response, false, "auth.password_reset.new_required", null);
                return;
            }

            // 验证邮箱格式
            if (!isValidEmail(email)) {
                sendResponse(response, false, "auth.password_reset.email_invalid", null);
                return;
            }

            // 验证密码长度
            if (newPassword.length() < 6 || newPassword.length() > 30) {
                sendResponse(response, false, "auth.password_reset.length_invalid", null);
                return;
            }

            // 验证验证码
            if (!userAuthService.verifyResetPasswordCode(email, code)) {
                logger.warn("验证码验证失败: {}", email);
                // 重置验证码是可猜的短秘密：失败按来源计入递增退避封禁，防「换邮箱猜码」
                if (sourceBanService != null) {
                    sourceBanService.recordAuthFailure(ClientIpResolver.clientIp(request), "reset-password");
                }
                sendResponse(response, false, "auth.password_reset.captcha_invalid", null);
                return;
            }

            // 重置密码
            boolean success = userAuthService.resetPassword(email, newPassword);

            if (success) {
                logger.info("密码重置成功: {}", email);
                sendResponse(response, true, "auth.password_reset.ok", null);
            } else {
                logger.error("密码重置失败: {}", email);
                sendResponse(response, false, "auth.password_reset.failed", null);
            }

        } catch (Exception e) {
            logger.error("处理重置密码请求时发生错误: {}", e.getMessage(), e);
            sendResponse(response, false, "common.internal_error", null);
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
}
