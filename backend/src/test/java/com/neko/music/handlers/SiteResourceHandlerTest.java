package com.neko.music.handlers;

import com.neko.music.util.SiteResourceStorage;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前端资源服务的判定测试：JAR 内直接读取、入口 no-cache + ETag 304、带哈希资源 immutable、
 * SPA 回退、目录穿越与未知路径一律 404。
 *
 * <p>站点资源来自 classpath（打包后的 {@code site/} 或 {@code target/classes/site}）；仓库里前端
 * 产物是 gitignore 的构建物，未构建时整类跳过。</p>
 */
class SiteResourceHandlerTest {

    @BeforeEach
    void requireBuiltFrontend() throws Exception {
        Assumptions.assumeFalse(SiteResourceStorage.index().isEmpty(),
                "未构建前端产物（site/），跳过前端资源服务测试");
    }

    private record Result(int status, String body, Map<String, String> headers, String contentType) {

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    @Test
    void indexIsServedWithRevalidatingCacheHeaders() throws Exception {
        Result result = run("/");

        assertEquals(200, result.status());
        assertEquals("no-cache", result.header("cache-control"));
        assertNotNull(result.header("etag"), "入口必须带 ETag 以便 304 再校验");
        assertTrue(result.body().toLowerCase(Locale.ROOT).contains("<html"));
    }

    @Test
    void indexSupportsConditionalRequest() throws Exception {
        Result first = run("/");
        String etag = first.header("etag");

        Result second = run("/", "If-None-Match", etag);

        assertEquals(304, second.status());
        assertTrue(second.body().isEmpty(), "304 不应带响应体");
    }

    @Test
    void hashedAssetIsImmutableAndActuallyExists() throws Exception {
        String asset = firstAssetPath();
        Result result = run(asset);

        assertEquals(200, result.status());
        assertEquals("public, max-age=15552000, immutable", result.header("cache-control"));
        assertFalse(result.body().isEmpty(), "资源内容不应为空");
    }

    @Test
    void hashedAssetSupportsConditionalRequest() throws Exception {
        String asset = firstAssetPath();
        String etag = run(asset).header("etag");

        assertNotNull(etag, "静态资源也应带 ETag 以支持 304");
        Result second = run(asset, "If-None-Match", etag);

        assertEquals(304, second.status());
        assertTrue(second.body().isEmpty(), "304 不应带响应体");
    }

    @Test
    void unknownExtensionlessRouteFallsBackToIndex() throws Exception {
        Result result = run("/some/spa/route");

        assertEquals(200, result.status());
        assertTrue(result.body().toLowerCase(Locale.ROOT).contains("<html"));
    }

    @Test
    void traversalAndUnknownFilesAreNotFound() throws Exception {
        assertEquals(404, run("/../application.yml").status());
        assertEquals(404, run("/assets/../../../config.yml").status());
        assertEquals(404, run("/assets/not-a-real-file.js").status());
    }

    /** 取一个真实的带哈希静态资源路径（入口 HTML 里引用过，必然存在于索引中）。 */
    private static String firstAssetPath() throws Exception {
        return SiteResourceStorage.index().keySet().stream()
                .filter(path -> path.startsWith("/assets/"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("站点索引里没有 /assets/ 资源"));
    }

    private Result run(String path) throws Exception {
        return run(path, null, null);
    }

    private Result run(String path, String headerName, String headerValue) throws Exception {
        SiteResourceHandler handler = new SiteResourceHandler();
        handler.init();

        int[] status = {200};
        Map<String, String> headers = new HashMap<>();
        String[] contentType = {null};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ServletOutputStream output = new ServletOutputStream() {
            @Override
            public void write(int value) {
                body.write(value);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };

        HttpServletResponse response = proxy(HttpServletResponse.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getOutputStream" -> {
                    return output;
                }
                case "setStatus" -> {
                    status[0] = (int) args[0];
                    return null;
                }
                case "sendError" -> {
                    status[0] = (int) args[0];
                    return null;
                }
                case "setHeader" -> {
                    headers.put(((String) args[0]).toLowerCase(Locale.ROOT), (String) args[1]);
                    return null;
                }
                case "setDateHeader" -> {
                    headers.put(((String) args[0]).toLowerCase(Locale.ROOT), Long.toString((long) args[1]));
                    return null;
                }
                case "setContentType" -> {
                    contentType[0] = (String) args[0];
                    return null;
                }
                case "setContentLength", "setContentLengthLong" -> {
                    headers.put("content-length", Long.toString(((Number) args[0]).longValue()));
                    return null;
                }
                default -> {
                }
            }
            return defaultValue(method);
        });

        Map<String, String> requestHeaders = new HashMap<>();
        if (headerName != null) {
            requestHeaders.put(headerName.toLowerCase(Locale.ROOT), headerValue);
        }
        HttpServletRequest request = proxy(HttpServletRequest.class, (proxy, method, args) -> switch (method.getName()) {
            case "getPathInfo" -> null; // 默认映射：servletPath 即整条路径
            case "getRequestURI" -> path;
            case "getContextPath" -> "";
            case "getHeader" -> requestHeaders.get(((String) args[0]).toLowerCase(Locale.ROOT));
            default -> defaultValue(method);
        });

        handler.doGet(request, response);
        return new Result(status[0], body.toString(StandardCharsets.UTF_8), headers, contentType[0]);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }
}
