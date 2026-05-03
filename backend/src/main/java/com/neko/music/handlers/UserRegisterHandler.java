package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.SensitiveWordUtil;
import jakarta.servlet.annotation.WebServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

@WebServlet("/api/user/register")
public class UserRegisterHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserRegisterHandler.class);
    private UserAuthService userAuthService;

    // 欣悦可以在这里配置你的白名单域名喵！
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList("gmail.com", "outlook.com", "qq.com", "163.com");

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

        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        try {
            JsonNode requestData = Main.getObjectMapper().readTree(requestBody.toString());
            if (requestData == null) {
                sendResponse(response, false, "请求数据不能为空喵", null);
                return;
            }

            String username = requestData.has("username") ? requestData.get("username").asText().trim() : null;
            String password = requestData.has("password") ? requestData.get("password").asText() : null;
            String email = requestData.has("email") ? requestData.get("email").asText().trim() : null;
            String verificationCode = requestData.has("verificationCode") ? requestData.get("verificationCode").asText().trim() : null;

            // 1. 基础非空校验
            if (isEmpty(username) || isEmpty(password) || isEmpty(email)) {
                sendResponse(response, false, "用户名、密码和邮箱不能为空喵", null);
                return;
            }

            // 2. 【核心修复】强制校验验证码是否存在
            if (isEmpty(verificationCode)) {
                logger.warn("检测到恶意注册尝试：未提供验证码。IP: {}", request.getRemoteAddr());
                sendResponse(response, false, "必须提供验证码喵！", null);
                return;
            }

            // 3. 邮箱格式及白名单校验
            if (!isValidEmail(email)) {
                sendResponse(response, false, "邮箱格式不正确喵", null);
                return;
            }
            if (!isWhiteListed(email)) {
                logger.warn("非白名单邮箱注册尝试: {}", email);
                sendResponse(response, false, "该邮箱域名不在允许范围内喵", null);
                return;
            }

            // 4. 用户名与密码长度/合规校验
            if (username.length() < 3 || username.length() > 20) {
                sendResponse(response, false, "用户名长度需在3-20之间喵", null);
                return;
            }
            if (SensitiveWordUtil.contains(username)) {
                sendResponse(response, false, "用户名包含违禁词喵", null);
                return;
            }
            if (password.length() < 6 || password.length() > 30) {
                sendResponse(response, false, "密码长度需在6-30之间喵", null);
                return;
            }

            // 5. 验证码有效性校验
            boolean isValidCode = userAuthService.verifyCode(email, verificationCode);
            if (!isValidCode) {
                sendResponse(response, false, "验证码错误或已过期喵", null);
                return;
            }

            // 6. 执行注册
            boolean success = userAuthService.registerUser(username, password, email);
            if (success) {
                logger.info("用户注册成功: {}", username);
                sendResponse(response, true, "注册成功喵！", Map.of("username", username));
            } else {
                sendResponse(response, false, "注册失败，用户名或邮箱可能已存在喵", null);
            }

        } catch (Exception e) {
            logger.error("注册处理异常: {}", e.getMessage(), e);
            sendResponse(response, false, "服务器内部错误喵", null);
        }
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private boolean isWhiteListed(String email) {
        String domain = email.substring(email.lastIndexOf("@") + 1).toLowerCase();
        return ALLOWED_DOMAINS.contains(domain);
    }

    private void sendResponse(HttpServletResponse response, boolean success, String message, Object data) throws IOException {
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