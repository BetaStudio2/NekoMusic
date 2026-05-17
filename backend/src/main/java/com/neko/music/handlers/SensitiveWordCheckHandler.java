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
            writeError(response, HttpStatus.BAD_REQUEST_400, "请求体不能为空");
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
            writeError(response, HttpStatus.BAD_REQUEST_400, "请提供 text 或 texts 字段");
        } catch (Exception e) {
            logger.warn("违禁词检测请求解析失败: {}", e.toString());
            writeError(response, HttpStatus.BAD_REQUEST_400, "请求格式错误");
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
        CheckResponse checkResponse = new CheckResponse(true, "检测完成", item, contains);
        response.setStatus(HttpStatus.OK_200);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(checkResponse));
    }

    private void handleBatch(JsonNode textsNode, HttpServletResponse response) throws IOException {
        if (textsNode.size() > MAX_BATCH_SIZE) {
            writeError(response, HttpStatus.BAD_REQUEST_400,
                    "单次最多检测 " + MAX_BATCH_SIZE + " 条文本");
            return;
        }

        List<CheckItem> items = new ArrayList<>();
        boolean anyContains = false;
        for (JsonNode node : textsNode) {
            if (!node.isTextual()) {
                writeError(response, HttpStatus.BAD_REQUEST_400, "texts 数组元素必须为字符串");
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

        BatchCheckResponse batchResponse = new BatchCheckResponse(true, "检测完成", items, anyContains);
        response.setStatus(HttpStatus.OK_200);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(batchResponse));
    }

    private static String validateText(String text) {
        if (text == null) {
            return "文本不能为 null";
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            return "单条文本长度不能超过 " + MAX_TEXT_LENGTH;
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
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        ErrorResponse errorResponse = new ErrorResponse(message);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
    }

    public static class CheckItem {
        public String text;
        public boolean contains;
        public List<String> words;

        public CheckItem() {
        }

        public CheckItem(String text, boolean contains, List<String> words) {
            this.text = text;
            this.contains = contains;
            this.words = words;
        }
    }

    public static class CheckResponse {
        public boolean success;
        public String message;
        public CheckItem data;
        public boolean contains;

        public CheckResponse(boolean success, String message, CheckItem data, boolean contains) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.contains = contains;
        }
    }

    public static class BatchCheckResponse {
        public boolean success;
        public String message;
        public List<CheckItem> data;
        public boolean contains;

        public BatchCheckResponse(boolean success, String message, List<CheckItem> data, boolean contains) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.contains = contains;
        }
    }

    public static class ErrorResponse {
        public String message;

        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}
