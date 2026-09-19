package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.util.NicknameValidator;
import com.neko.music.util.RequestAuthUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 修改当前登录用户的昵称：{@code POST /api/user/nickname/change}。
 * 请求体 {@code {"nickname": "新昵称"}}，昵称规则与注册保持一致。
 */
public class UserNicknameChangeHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserNicknameChangeHandler.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = RequestAuthUtil.authenticate(request);
        if (userId == null) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED_401, "未授权访问，请先登录");
            return;
        }

        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        try {
            NicknameChangeRequest changeRequest =
                    Main.getObjectMapper().readValue(requestBody.toString(), NicknameChangeRequest.class);
            String nickname = changeRequest.getNickname() == null ? null : changeRequest.getNickname().trim();

            String validationError = NicknameValidator.validate(nickname);
            if (validationError != null) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, validationError);
                return;
            }

            boolean success = Main.getUserAuthService().changeNickname(userId, nickname);
            if (success) {
                logger.info("用户 {} 修改昵称成功: {}", userId, nickname);
                sendSuccessResponse(response, "昵称修改成功喵！", nickname);
            } else {
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR_500, "昵称修改失败，请稍后重试喵");
            }
        } catch (Exception e) {
            logger.error("修改昵称时出错", e);
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "请求格式错误");
        }
    }

    private void sendSuccessResponse(HttpServletResponse response, String message, String nickname) throws IOException {
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", Map.of("nickname", nickname));
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(body));
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=utf-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(body));
    }

    private static class NicknameChangeRequest {
        private String nickname;

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }
}
