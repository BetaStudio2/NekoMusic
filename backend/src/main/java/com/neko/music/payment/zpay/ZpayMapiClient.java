package com.neko.music.payment.zpay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        if (trimmed.startsWith("{")) {
            try {
                return JsonParser.parseString(trimmed).getAsJsonObject();
            } catch (Exception e) {
                logger.warn("ZPay mapi JSON 解析失败: {}", trimmed);
                throw new IOException("ZPay mapi 响应非 JSON", e);
            }
        }
        logger.warn("ZPay mapi 非 JSON: {}", trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed);
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
