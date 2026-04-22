package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.model.User;
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

@WebServlet("/api/user/register")
public class UserRegisterHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserRegisterHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
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

        logger.info("收到用户注册请求");

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        try {
            JsonNode requestData = objectMapper.readTree(requestBody.toString());

            String username = null;
            String password = null;
            String email = null;

            if (requestData != null) {
                if (requestData.has("username")) {
                    username = requestData.get("username").asText();
                }
                if (requestData.has("password")) {
                    password = requestData.get("password").asText();
                }
                if (requestData.has("email")) {
                    email = requestData.get("email").asText();
                }
            }

            // 验证请求参数
            if (username == null || password == null || email == null || 
                username.trim().isEmpty() || password.trim().isEmpty() || email.trim().isEmpty()) {
                
                sendResponse(response, false, "用户名、密码和邮箱不能为空", null);
                return;
            }

            // 验证邮箱格式
            if (!isValidEmail(email)) {
                sendResponse(response, false, "邮箱格式不正确", null);
                return;
            }

            // 验证用户名长度
            if (username.length() < 3 || username.length() > 20) {
                sendResponse(response, false, "用户名长度必须在3-20个字符之间", null);
                return;
            }

            // 验证用户名是否包含违禁词
            if (SensitiveWordUtil.contains(username)) {
                sendResponse(response, false, "用户名包含违禁词", null);
                logger.warn("用户名包含违禁词");
                return;
            }
            else logger.info("用户名不包含违禁词");

            // 验证密码长度
            if (password.length() < 6 || password.length() > 30) {
                sendResponse(response, false, "密码长度必须在6-30个字符之间", null);
                return;
            }

            String verificationCode = null;
            if (requestData.has("verificationCode")) {
                verificationCode = requestData.get("verificationCode").asText();
            }
            // 如果有提供验证码，则验证
            if (verificationCode != null && !verificationCode.trim().isEmpty()) {
                boolean isValidCode = userAuthService.verifyCode(email.trim(), verificationCode.trim());
                if (!isValidCode) {
                    sendResponse(response, false, "验证码错误或已过期", null);
                    return;
                }
            }
            // 注册用户
            boolean success = userAuthService.registerUser(username.trim(), password, email.trim());
            if (success) {
                logger.info("用户注册成功: {}", username);
                sendResponse(response, true, "注册成功", Map.of("username", username));
            } else {
                logger.warn("用户注册失败: {}", username);
                sendResponse(response, false, "注册失败，用户名或邮箱可能已存在", null);
            }
        } catch (Exception e) {
            logger.error("处理用户注册请求时发生错误: {}", e.getMessage(), e);
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
            out.print(objectMapper.writeValueAsString(responseMap));
            out.flush();
        }
    }
}