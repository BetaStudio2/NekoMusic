package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import com.neko.music.model.Admin;
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
import java.util.Optional;

public class AdminLoginHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminLoginHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        // 处理预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        try {
            // 读取请求体
            StringBuilder requestBody = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    requestBody.append(line);
                }
            }

            // 解析JSON请求体
            Map<String, String> requestData = objectMapper.readValue(requestBody.toString(), Map.class);
            String username = requestData.get("username");
            String password = requestData.get("password");

            // 验证参数
            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                sendErrorResponse(response, 400, "用户名和密码不能为空");
                return;
            }

            // 验证管理员凭据
            Optional<Admin> adminOpt = Main.getAdminAuthService().authenticate(username.trim(), password);
            
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                
                // 创建会话令牌
                String sessionToken = Main.getAdminAuthService().createAdminSession(admin);
                if (sessionToken == null) {
                    sendErrorResponse(response, 500, "创建会话失败");
                    return;
                }
                
                // 创建成功响应
                Map<String, Object> successResponse = new HashMap<>();
                successResponse.put("success", true);
                successResponse.put("message", "登录成功");
                successResponse.put("token", sessionToken);  // 返回会话令牌
                successResponse.put("admin", Map.of(
                    "id", admin.getId(),
                    "username", admin.getUsername(),
                    "email", admin.getEmail(),
                    "role", admin.getRole(),
                    "createdAt", admin.getCreatedAt(),
                    "lastLoginAt", admin.getLastLoginAt()
                ));
                
                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();
                out.print(objectMapper.writeValueAsString(successResponse));
                out.flush();
            } else {
                sendErrorResponse(response, 401, "用户名或密码错误");
            }
        } catch (Exception e) {
            logger.error("管理员登录处理错误", e);
            sendErrorResponse(response, 500, "服务器内部错误");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);

        response.setStatus(statusCode);
        PrintWriter out = response.getWriter();
        out.print(new ObjectMapper().writeValueAsString(errorResponse));
        out.flush();
    }
}