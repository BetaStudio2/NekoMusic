package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON API 处理器基类。
 *
 * <p>此前每个处理器都在自己文件里复制一份 {@code sendSuccessResponse} /
 * {@code sendErrorResponse}（内容几乎完全相同）。这里收敛为一份，输出契约不变：
 *
 * <ul>
 *   <li>成功：调用方传入已构造好的 {@link JsonObject}，由 Gson 序列化写出</li>
 *   <li>错误：{@code {"success":false,"message":"..."}}</li>
 * </ul>
 *
 * <p>JSON 字段名、嵌套结构与 HTTP 状态码均与迁移前一致，前端无需改动。
 */
public abstract class ApiServlet extends HttpServlet {

    protected static final Gson GSON = Main.getGson();
    protected static final ObjectMapper MAPPER = Main.getObjectMapper();

    /** 统一成功响应（HTTP 200），body 由调用方构造，字段顺序保持不变。 */
    protected void sendSuccessResponse(HttpServletResponse resp, JsonObject response) throws IOException {
        writeJson(resp, HttpServletResponse.SC_OK, GSON.toJson(response));
    }

    /** 统一成功响应（HTTP 200），body 为 {@code {"success":true,"message":...}}。 */
    protected void sendSuccessResponse(HttpServletResponse resp, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", message);
        writeJson(resp, HttpServletResponse.SC_OK, GSON.toJson(body));
    }

    /** 统一错误响应：{@code {"success":false,"message":"..."}}。 */
    protected void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("success", false);
        body.addProperty("message", message);
        writeJson(resp, statusCode, GSON.toJson(body));
    }

    /**
     * 不带状态码的错误响应：保持调用方此前设置的状态码（历史行为）。
     * 例如调用方先 {@code response.setStatus(401)} 再调用本方法。
     */
    protected void sendErrorResponse(HttpServletResponse resp, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("success", false);
        body.addProperty("message", message);
        writeJsonPreservingStatus(resp, GSON.toJson(body));
    }

    /** 统一 JSON 写出：设置内容类型与状态码后一次性输出。 */
    protected void writeJson(HttpServletResponse resp, int statusCode, String json) throws IOException {
        resp.setStatus(statusCode);
        writeJsonPreservingStatus(resp, json);
    }

    /** 只设置内容类型与 body，不改动状态码。 */
    protected void writeJsonPreservingStatus(HttpServletResponse resp, String json) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.print(json);
            out.flush();
        }
    }

    /**
     * 读取请求体为字符串（UTF-8）。收敛各处理器里重复的
     * {@code StringBuilder + BufferedReader.readLine()} 样板。
     */
    protected String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    /** 通用响应：{@code {"success":...,"message":...,"data":...}}（保留调用方已设置的状态码）。 */
    protected void sendResponse(HttpServletResponse resp, boolean success, String message, Object data)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        body.put("data", data);
        writeJsonPreservingStatus(resp, MAPPER.writeValueAsString(body));
    }

    /** {@link #sendResponse} 的别名，供历史上用 sendJson 命名的处理器复用。 */
    protected void sendJson(HttpServletResponse resp, boolean success, String message, Object data)
            throws IOException {
        sendResponse(resp, success, message, data);
    }

    /** {@code {"success":...,"message":...}} 带状态码（Gson），供静态工具方法调用。 */
    protected static void sendJson(HttpServletResponse resp, int code, boolean success, String message)
            throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("success", success);
        o.addProperty("message", message);
        writeJsonStatic(resp, code, GSON.toJson(o));
    }

    /** {@code {"success":...,"message":...,"data":...}} 带状态码（Jackson），供静态工具方法调用。 */
    protected static void sendJson(HttpServletResponse resp, int status, boolean success, String message, Object data)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        body.put("data", data);
        writeJsonStatic(resp, status, MAPPER.writeValueAsString(body));
    }

    private static void writeJsonStatic(HttpServletResponse resp, int status, String json) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.print(json);
            out.flush();
        }
    }

    /** {@code {...map}} 原样输出（HTTP 200），供上传审核等接口复用。 */
    protected void sendJsonResponse(HttpServletResponse resp, Map<String, Object> data) throws IOException {
        writeJson(resp, HttpServletResponse.SC_OK, MAPPER.writeValueAsString(data));
    }

    /** 直接输出已构造好的 Gson JsonObject，带状态码。 */
    protected static void sendRawJson(HttpServletResponse resp, int code, JsonObject body) throws IOException {
        writeJsonStatic(resp, code, body.toString());
    }

    /** 发送频率限制响应：HTTP 429 + Retry-After。 */
    protected void sendCooldownResponse(HttpServletResponse resp, long retryAfterSec) throws IOException {
        long sec = Math.max(1, retryAfterSec);
        resp.setStatus(429);
        resp.setHeader("Retry-After", String.valueOf(sec));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retryAfterSec", sec);
        sendResponse(resp, false, "发送过于频繁，请 " + sec + " 秒后再试", data);
    }

    /** 兼容旧接口的 {@code {"error":"..."}} 错误体，带状态码。 */
    protected void sendErrorObject(HttpServletResponse resp, int status, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        writeJson(resp, status, GSON.toJson(body));
    }

    /** 兼容旧接口的 {@code {"success":false,"message":"...","data":null}} 错误体。 */
    protected void sendErrorData(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("data", null);
        writeJson(resp, status, MAPPER.writeValueAsString(body));
    }
}
