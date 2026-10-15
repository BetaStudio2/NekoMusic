package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.util.SensitiveWordUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.neko.music.i18n.ApiMessages;

/**
 * 违禁词检测 API：供前端在上传、注册等提交前预校验文案。
 */
public class SensitiveWordCheckHandler extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordCheckHandler.class);
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_BATCH_SIZE = 20;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCorsHeaders(response);
        response.setContentType("application/json;charset=UTF-8");

        String body = readBody(request);
        if (body.isBlank()) {
            writeError(response, HttpStatus.BAD_REQUEST_400, "sensitive.body_required");
            return;
        }

        try {
            JsonNode root = Main.getObjectMapper().readTree(body);
            if (root.has("texts") && root.get("texts").isArray()) {
                handleBatch(root.get("texts"), response);
                return;
            }
            if (root.has("text") && root.get("text").isTextual()) {
                handleSingle(root.get("text").asText(), response);
                return;
            }
            writeError(response, HttpStatus.BAD_REQUEST_400, "sensitive.text_required");
        } catch (Exception e) {
            logger.warn("违禁词检测请求解析失败: {}", e.toString());
            writeError(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        setCorsHeaders(response);
        response.setStatus(HttpStatus.OK_200);
    }

    private void handleSingle(String text, HttpServletResponse response) throws IOException {
        String validationError = validateText(text);
        if (validationError != null) {
            writeError(response, HttpStatus.BAD_REQUEST_400, validationError);
            return;
        }

        List<String> words = SensitiveWordUtil.findAll(text);
        boolean contains = !words.isEmpty();
        CheckItem item = new CheckItem(text, contains, words);
        CheckResponse checkResponse = new CheckResponse(true, "sensitive.check_ok", item, contains);
        response.setStatus(HttpStatus.OK_200);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(checkResponse));
    }

    private void handleBatch(JsonNode textsNode, HttpServletResponse response) throws IOException {
        if (textsNode.size() > MAX_BATCH_SIZE) {
            writeError(response, HttpStatus.BAD_REQUEST_400,
                    ApiMessages.translate("sensitive.batch.too_many", MAX_BATCH_SIZE));
            return;
        }

        List<CheckItem> items = new ArrayList<>();
        boolean anyContains = false;
        for (JsonNode node : textsNode) {
            if (!node.isTextual()) {
                writeError(response, HttpStatus.BAD_REQUEST_400, "sensitive.texts_not_string");
                return;
            }
            String text = node.asText();
            String validationError = validateText(text);
            if (validationError != null) {
                writeError(response, HttpStatus.BAD_REQUEST_400, validationError);
                return;
            }
            List<String> words = SensitiveWordUtil.findAll(text);
            boolean contains = !words.isEmpty();
            anyContains = anyContains || contains;
            items.add(new CheckItem(text, contains, words));
        }

        BatchCheckResponse batchResponse = new BatchCheckResponse(true, "sensitive.check_ok", items, anyContains);
        response.setStatus(HttpStatus.OK_200);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(batchResponse));
    }

    private static String validateText(String text) {
        if (text == null) {
            return ApiMessages.translate("sensitive.text_null");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            return ApiMessages.translate("sensitive.text_too_long", MAX_TEXT_LENGTH);
        }
        return null;
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void setCorsHeaders(HttpServletResponse response) {
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        ErrorResponse errorResponse = new ErrorResponse(message);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
    }

    public record CheckItem(String text, boolean contains, List<String> words) {
    }
    public record CheckResponse(boolean success, String message, CheckItem data, boolean contains) {
    }
    public record BatchCheckResponse(boolean success, String message, List<CheckItem> data, boolean contains) {
    }
    public record ErrorResponse(String message) {
    }
}
