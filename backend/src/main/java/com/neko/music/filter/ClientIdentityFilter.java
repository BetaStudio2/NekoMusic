package com.neko.music.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import com.neko.music.i18n.ApiMessages;
import com.neko.music.service.ClientIdentityService;
import com.neko.music.util.ClientIdentityProtocol;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.RequestPath;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 客户端实例身份门禁：所有需要业务鉴权的动态请求必须携带平台绑定的 client_id、实例令牌与
 * 每请求 ECDSA 签名。
 *
 * <p>本过滤器位于防爬之后、防重放之前。签名串包含一次性 nonce，但 nonce 的原子消费仍由
 * {@link ReplayProtectionFilter} 完成；两者合起来同时约束「谁在请求」与「请求是否被重放」。</p>
 */
public class ClientIdentityFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ClientIdentityFilter.class);
    private static final int MAX_JSON_BODY_BYTES = 2 * 1024 * 1024;
    public static final String STATUS_HEADER = "X-Neko-Client-Status";
    public static final String STATUS_INVALID = "invalid";

    /** 自举接口：注册 / 续签必须能在还没有实例令牌时访问。 */
    static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/client/challenge",
            "/api/client/register",
            "/api/client/token",
            "/api/replay/challenge",
            "/api/replay/nonce",
            "/api/music/latest",
            "/api/music/ranking",
            "/api/payment/zpay/notify",
            "/api/user/qrlogin/status",
            "/api/user/notifications/stream",
            "/api/admin/logs/access/stream",
            "/api/user/upload/preview"
    );

    /** 浏览器原生 <img> / EventSource 无法附加请求头，与防重放豁免保持一致。 */
    static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/api/music/cover/",
            "/api/user/avatar/"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        String method = httpRequest.getMethod();
        String path = RequestPath.of(httpRequest);
        if (!isProtected(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        ClientIdentityService service = Main.getClientIdentityService();
        if (service == null) {
            reject(httpResponse);
            return;
        }
        ClientIdentityProtocol.ReportedClient reported = ClientIdentityProtocol.parseReportedClient(
                httpRequest.getHeader("X-Neko-Client"));
        if (reported == null) {
            reject(httpResponse);
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(defaultString(httpRequest.getHeader(
                    ClientIdentityProtocol.HEADER_TIMESTAMP), "0"));
        } catch (NumberFormatException e) {
            reject(httpResponse);
            return;
        }

        String contentType = httpRequest.getContentType();
        boolean multipart = contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
        String bodyHash = ClientIdentityProtocol.STREAMED_BODY_HASH;
        HttpServletRequest downstream = httpRequest;
        if (!multipart && hasRequestBody(method, httpRequest)) {
            byte[] body = readBody(httpRequest);
            if (body == null) {
                httpResponse.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                rejectBodyTooLarge(httpResponse);
                return;
            }
            bodyHash = ClientIdentityProtocol.sha256Hex(body);
            downstream = new CachedBodyRequest(httpRequest, body);
        } else if (!multipart) {
            bodyHash = ClientIdentityProtocol.sha256Hex(new byte[0]);
        }

        ClientIdentityService.Credentials credentials = new ClientIdentityService.Credentials(
                httpRequest.getHeader(ClientIdentityProtocol.HEADER_CLIENT_ID),
                httpRequest.getHeader(ClientIdentityProtocol.HEADER_INSTANCE),
                httpRequest.getHeader(ClientIdentityProtocol.HEADER_TOKEN),
                reported.platform(),
                timestamp,
                httpRequest.getHeader(ClientIdentityProtocol.HEADER_SIGNATURE),
                bodyHash,
                httpRequest.getHeader("X-Neko-Nonce"),
                method,
                path,
                httpRequest.getQueryString(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getHeader("Origin"),
                httpRequest.getHeader(ClientIdentityProtocol.HEADER_SECRET));
        if (service.verify(credentials).isEmpty()) {
            logger.debug("客户端身份校验失败: {} {} clientId={} instanceId={}", method, path,
                    credentials.clientId(), credentials.instanceId());
            reject(httpResponse);
            return;
        }
        chain.doFilter(downstream, response);
    }

    static boolean isProtected(String path, String method) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String verb = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(verb) || "HEAD".equals(verb)) {
            return false;
        }
        String normalized = path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1) : path;
        if (EXEMPT_PATHS.contains(normalized)) {
            return false;
        }
        for (String prefix : EXEMPT_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return false;
            }
        }
        if (normalized.endsWith("/pull")) {
            return false;
        }
        return normalized.startsWith("/api/") || normalized.startsWith("/loser/");
    }

    private static boolean hasRequestBody(String method, HttpServletRequest request) {
        String verb = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if (!("POST".equals(verb) || "PUT".equals(verb) || "PATCH".equals(verb)
                || "DELETE".equals(verb))) {
            return false;
        }
        return request.getContentLengthLong() != 0;
    }

    private static byte[] readBody(HttpServletRequest request) throws IOException {
        long declared = request.getContentLengthLong();
        if (declared > MAX_JSON_BODY_BYTES) {
            return null;
        }
        try (var input = request.getInputStream();
             var output = new ByteArrayOutputStream(declared > 0 ? (int) declared : 1024)) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_JSON_BODY_BYTES) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        response.setHeader(STATUS_HEADER, STATUS_INVALID);
        response.setHeader("WWW-Authenticate", "NekoClient");
        ObjectMapper mapper = Main.getObjectMapper();
        var body = mapper.createObjectNode();
        body.put("success", false);
        body.put("message", ApiMessages.translate("security.client.invalid"));
        body.putNull("data");
        mapper.writeValue(response.getWriter(), body);
    }

    private static void rejectBodyTooLarge(HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        response.setContentType("application/json;charset=UTF-8");
        var body = Main.getObjectMapper().createObjectNode();
        body.put("success", false);
        body.put("message", ApiMessages.translate("net.invalid_json"));
        body.putNull("data");
        Main.getObjectMapper().writeValue(response.getWriter(), body);
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** 缓存已读 body，下游 handler 仍能按原样读取。 */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
