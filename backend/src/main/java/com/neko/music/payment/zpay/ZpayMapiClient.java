package com.neko.music.payment.zpay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ZPay「API 接口支付」{@code mapi.php}，POST {@code application/x-www-form-urlencoded}。
 */
public final class ZpayMapiClient {
    private static final Logger logger = LoggerFactory.getLogger(ZpayMapiClient.class);

    private ZpayMapiClient() {
    }

    public static JsonObject requestMapi(ConfigManager config, Map<String, String> bizParams) throws IOException {
        Map<String, String> signParams = new LinkedHashMap<>(bizParams);
        String key = config.getZpayKey();
        String sign = ZpaySignUtil.sign(signParams, key);
        signParams.put("sign", sign);
        signParams.put("sign_type", "MD5");

        byte[] body = buildFormBody(signParams);
        URL url = new URL(config.getZpayMapiUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = readAll(in);
        if (text == null || text.isBlank()) {
            throw new IOException("ZPay mapi 空响应 HTTP " + code);
        }
        String trimmed = text.trim();
        return parseMapiJson(trimmed, 0);
    }

    private static final int ZPAY_JSON_MAX_DEPTH = 16;

    /**
     * ZPay 可能返回裸 JSON 对象，也可能把对象序列化成字符串后再 JSON 编码（甚至多层嵌套），此处循环解包直到得到 {@link JsonObject}。
     */
    static JsonObject parseMapiJson(String trimmed) throws IOException {
        return parseMapiJson(trimmed, 0);
    }

    private static JsonObject parseMapiJson(String trimmed, int depth) throws IOException {
        if (depth > ZPAY_JSON_MAX_DEPTH) {
            throw new IOException("ZPay mapi JSON 嵌套层数过多");
        }
        if (trimmed.isEmpty()) {
            throw new IOException("ZPay mapi 空响应体");
        }
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
            trimmed = trimmed.substring(1).trim();
        }
        String s = trimmed;
        Exception lastParseError = null;
        for (int i = 0; i < 10; i++) {
            JsonElement el;
            try {
                el = JsonParser.parseString(s);
            } catch (Exception e) {
                lastParseError = e;
                break;
            }
            if (el.isJsonObject()) {
                return el.getAsJsonObject();
            }
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String next = el.getAsString().trim();
                if (next.isEmpty() || next.equals(s)) {
                    break;
                }
                s = next;
                continue;
            }
            if (el.isJsonArray() && el.getAsJsonArray().size() == 1) {
                JsonElement first = el.getAsJsonArray().get(0);
                if (first.isJsonObject()) {
                    return first.getAsJsonObject();
                }
                if (first.isJsonPrimitive() && first.getAsJsonPrimitive().isString()) {
                    String next = first.getAsString().trim();
                    if (!next.isEmpty() && !next.equals(s)) {
                        s = next;
                        continue;
                    }
                }
            }
            break;
        }
        try {
            String layer = new com.google.gson.Gson().fromJson(trimmed, String.class);
            if (layer != null) {
                String inner = layer.trim();
                if (!inner.isEmpty() && !inner.equals(trimmed)) {
                    return parseMapiJson(inner, depth + 1);
                }
            }
        } catch (Exception e) {
            logger.debug("ZPay Gson 字符串解包未成功: {}", e.getMessage());
        }
        if (lastParseError != null) {
            logger.warn("ZPay mapi JSON 解析失败: {}", trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed);
            throw new IOException("ZPay mapi 响应非 JSON", lastParseError);
        }
        try (StringReader sr = new StringReader(trimmed); JsonReader jr = new JsonReader(sr)) {
            jr.setLenient(true);
            JsonElement el = JsonParser.parseReader(jr);
            if (el.isJsonObject()) {
                return el.getAsJsonObject();
            }
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String inner = el.getAsString().trim();
                if (!inner.equals(trimmed)) {
                    return parseMapiJson(inner, depth + 1);
                }
            }
        } catch (Exception e) {
            logger.debug("ZPay lenient 解析未成功: {}", e.getMessage());
        }
        logger.warn("ZPay mapi 非 JSON 对象: {}", trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed);
        throw new IOException("ZPay mapi 返回非 JSON: " + trimmed);
    }

    private static byte[] buildFormBody(Map<String, String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream input = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = input.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    public static boolean isApiSuccess(JsonObject root) {
        if (!root.has("code")) {
            return false;
        }
        JsonElement c = root.get("code");
        if (c.isJsonPrimitive()) {
            if (c.getAsJsonPrimitive().isNumber()) {
                return c.getAsInt() == 1;
            }
            return "1".equals(c.getAsString());
        }
        return false;
    }

    public static String moneyTwoDecimals(double yuan) {
        return String.format(Locale.US, "%.2f", yuan);
    }
}
