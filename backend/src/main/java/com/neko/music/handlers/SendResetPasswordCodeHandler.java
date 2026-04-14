package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 发送重置密码验证码处理器
 */
@WebServlet("/user/send-reset-code")
public class SendResetPasswordCodeHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(SendResetPasswordCodeHandler.class);
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

        logger.info("收到发送重置密码验证码请求");

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

            String email = null;
            String username = "用户"; // 默认用户名

            if (requestData != null) {
                if (requestData.has("email")) {
                    email = requestData.get("email").asText();
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

            // 检查邮箱是否存在于系统中
            if (!userAuthService.userExistsByEmail(email)) {
                // 为了安全考虑，不直接告诉用户邮箱不存在
                // 但记录日志以便管理员查看
                logger.warn("尝试重置密码，但邮箱不存在: {}", email);
                sendResponse(response, true, "如果该邮箱已注册，验证码已发送", null);
                return;
            }

            // 发送验证码
            boolean success = userAuthService.sendVerificationCode(email, username);

            if (success) {
                logger.info("重置密码验证码发送成功: {}", email);
                sendResponse(response, true, "验证码已发送至您的邮箱", null);
            } else {
                logger.error("重置密码验证码发送失败: {}", email);
                sendResponse(response, false, "验证码发送失败，请稍后重试", null);
            }

        } catch (Exception e) {
            logger.error("处理发送重置密码验证码请求时发生错误: {}", e.getMessage(), e);
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
