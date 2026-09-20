package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.model.User;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.VipUtil;
import jakarta.servlet.annotation.WebServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@WebServlet("/api/user/login")
public class UserLoginHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserLoginHandler.class);
    private UserAuthService userAuthService;

    @Override
    public void init() throws ServletException {
        userAuthService = Main.getUserAuthService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");

        logger.info("收到用户登录请求");

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
            String password = null;

            if (requestData != null) {
                if (requestData.has("username")) {
                    email = requestData.get("username").asText();
                }
                if (requestData.has("password")) {
                    password = requestData.get("password").asText();
                }
            }

            // 验证请求参数
            if (email == null || password == null ||
                email.trim().isEmpty() || password.trim().isEmpty()) {

                sendResponse(response, false, "邮箱和密码不能为空", null);
                return;
            }

            // 验证密码长度
            if (password.length() < 6 || password.length() > 30) {
                sendResponse(response, false, "密码长度不正确", null);
                return;
            }

            // 用户认证
            Optional<User> userOpt = userAuthService.authenticate(email.trim(), password);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                logger.info("用户登录成功: {}", user.email());

                // 生成 token 并写入 Redis
                String token = userAuthService.createTokenForUser(user.id());

                if (token == null) {
                    logger.error("生成token失败: {}", user.email());
                    sendResponse(response, false, "登录失败，请稍后重试", null);
                    return;
                }

                // 返回用户信息（不包含密码）和token
                Map<String, Object> userData = new HashMap<>();
                userData.put("id", user.id());
                userData.put("username", user.username());
                userData.put("email", user.email());
                userData.put("createdAt", user.createdAt());
                userData.put("isVip", VipUtil.isVipActiveNow(user.vipExpiresAt()));
                userData.put("vipExpiresAt",
                        user.vipExpiresAt() != null ? user.vipExpiresAt().toInstant().toString() : null);

                Map<String, Object> responseData = new HashMap<>();
                responseData.put("user", userData);
                responseData.put("token", token);

                sendResponse(response, true, "登录成功", responseData);
            } else {
                logger.warn("用户登录失败: {}", email);
                sendResponse(response, false, "邮箱或密码错误", null);
            }

        } catch (Exception e) {
            logger.error("处理用户登录请求时发生错误: {}", e.getMessage(), e);
            sendResponse(response, false, "服务器内部错误", null);
        }
    }

    /**
     * 发送JSON响应
     */
}