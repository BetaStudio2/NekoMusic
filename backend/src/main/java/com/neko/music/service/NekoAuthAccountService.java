package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.config.ConfigManager;
import com.neko.music.util.HttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NekoAuth 服务端账号桥接客户端。
 *
 * <p>账号、密码、验证码、改密与找回密码全部以 NekoAuth 为准。本类只复用 NekoMusic
 * 现有的 {@code oauth.issuer}、{@code oauth.client_id}、{@code oauth.client_secret}
 * 设置，不写死客户端 ID，也不会自动注册客户端。凭据只存在于服务端配置中。</p>
 */
public final class NekoAuthAccountService {

    private static final Logger logger = LoggerFactory.getLogger(NekoAuthAccountService.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final ConfigManager configManager;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpTransport.createWithoutRedirects(HTTP_TIMEOUT);

    public NekoAuthAccountService(ConfigManager configManager, ObjectMapper objectMapper) {
        this.configManager = configManager;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return !configManager.getOAuthIssuer().isBlank()
                && !configManager.getOAuthClientId().isBlank()
                && !configManager.getOAuthClientSecret().isBlank();
    }

    public Account login(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return postAccount("/login", body, "登录");
    }

    public void sendRegistrationCode(String email) {
        postNoContent("/register/code", Map.of("email", email), "发送注册验证码");
    }

    public Account register(String email, String password, String verificationCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("verificationCode", verificationCode);
        return postAccount("/register", body, "注册");
    }

    public Account adminCreate(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return postAccount("/admin-create", body, "创建账号");
    }

    public void sendPasswordResetCode(String email) {
        postNoContent("/password/reset-code", Map.of("email", email), "发送找回密码验证码");
    }

    public void resetPassword(String email, String code, String newPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("code", code);
        body.put("newPassword", newPassword);
        postNoContent("/password/reset", body, "重置密码");
    }

    public void changePassword(String email, String currentPassword, String newPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);
        postNoContent("/password/change", body, "修改密码");
    }

    public void adminResetPassword(String email, String newPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("newPassword", newPassword);
        postNoContent("/password/admin-reset", body, "管理员重置密码");
    }

    private Account postAccount(String path, Map<String, Object> payload, String operation) {
        HttpResponse<String> response = post(path, payload, operation);
        JsonNode json = parseJson(response.body());
        JsonNode id = json == null ? null : json.get("id");
        JsonNode email = json == null ? null : json.get("email");
        if (id == null || !id.canConvertToLong() || id.asLong() <= 0 || email == null || !email.isTextual()) {
            throw new AccountBridgeException(502, "NEKO_AUTH_BAD_RESPONSE",
                    "NekoAuth 返回的账号信息格式不正确", 0);
        }
        return new Account(
                id.asLong(),
                email.asText(),
                text(json, "username"),
                text(json, "role"),
                json.path("enabled").asBoolean(true));
    }

    private void postNoContent(String path, Map<String, Object> payload, String operation) {
        post(path, payload, operation);
    }

    private HttpResponse<String> post(String path, Map<String, Object> payload, String operation) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new AccountBridgeException(500, "NEKO_AUTH_REQUEST_ENCODE_FAILED",
                    "无法构造 NekoAuth 请求", 0);
        }

        String clientId = requireConfigured(configManager.getOAuthClientId(), "oauth.client_id");
        String clientSecret = requireConfigured(configManager.getOAuthClientSecret(), "oauth.client_secret");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuerUri() + "/api/internal/accounts" + path))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", basic(clientId, clientSecret))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = HttpTransport.sendString(httpClient, request, "NekoAuth 账号请求被中断");
        } catch (IOException exception) {
            logger.warn("NekoAuth 账号桥接请求失败: operation={} path={}", operation, path);
            throw new AccountBridgeException(503, "NEKO_AUTH_UNAVAILABLE",
                    "暂时无法连接 NekoAuth", 0);
        }

        if (!HttpTransport.isSuccess(response.statusCode())) {
            JsonNode error = parseJson(response.body());
            String code = text(error, "code");
            String message = text(error, "message");
            if (code.isBlank()) {
                code = "NEKO_AUTH_ERROR";
            }
            if (message.isBlank()) {
                message = "NekoAuth 拒绝了账号请求";
            }
            long retryAfter = retryAfterSeconds(response);
            if (response.statusCode() == 401 && "INVALID_CLIENT".equals(code)) {
                logger.error("NekoAuth 账号桥接客户端认证失败: code={} message={}；"
                                + "请检查 oauth.client_id/oauth.client_secret 以及客户端是否包含 account:bridge scope",
                        code, message);
            } else {
                logger.warn("NekoAuth 账号桥接失败: operation={} status={} code={}",
                        operation, response.statusCode(), code);
            }
            throw new AccountBridgeException(response.statusCode(), code, message, retryAfter);
        }
        return response;
    }

    private URI issuerUri() {
        String value = requireConfigured(configManager.getOAuthIssuer(), "oauth.issuer");
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new AccountBridgeException(503, "NEKO_AUTH_NOT_CONFIGURED",
                    "NekoAuth 根地址格式不正确", 0);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (uri.getHost() == null || (!scheme.equals("https") && !scheme.equals("http"))
                || uri.getRawQuery() != null || uri.getFragment() != null || uri.getRawUserInfo() != null) {
            throw new AccountBridgeException(503, "NEKO_AUTH_NOT_CONFIGURED",
                    "NekoAuth 根地址格式不正确", 0);
        }
        return URI.create(value);
    }

    private String requireConfigured(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new AccountBridgeException(503, "NEKO_AUTH_NOT_CONFIGURED",
                    "缺少 NekoAuth 配置：" + key, 0);
        }
        return value.trim();
    }

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static long retryAfterSeconds(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Math.max(1, Long.parseLong(value.trim()));
                    } catch (NumberFormatException ignored) {
                        return 60L;
                    }
                })
                .orElse(60L);
    }

    private static String basic(String clientId, String clientSecret) {
        String encoded = URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + ":" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(encoded.getBytes(StandardCharsets.UTF_8));
    }

    public record Account(long id, String email, String username, String role, boolean enabled) {
    }

    public static final class AccountBridgeException extends RuntimeException {
        private final int status;
        private final String code;
        private final long retryAfterSeconds;

        public AccountBridgeException(int status, String code, String message, long retryAfterSeconds) {
            super(message);
            this.status = status;
            this.code = code;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }

        public boolean invalidCredentials() {
            return status == 401 && "INVALID_CREDENTIALS".equals(code);
        }

        public boolean rateLimited() {
            return status == 429 || "EMAIL_CODE_COOLDOWN".equals(code)
                    || "EMAIL_CODE_DAILY_LIMIT".equals(code);
        }

        public boolean notConfigured() {
            return "NEKO_AUTH_NOT_CONFIGURED".equals(code);
        }

        public boolean unavailable() {
            return status >= 500 || "NEKO_AUTH_UNAVAILABLE".equals(code);
        }
    }
}
