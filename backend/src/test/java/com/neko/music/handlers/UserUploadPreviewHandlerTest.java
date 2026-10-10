package com.neko.music.handlers;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 审核试听接口的响应头契约：整包回源，不得声明 {@code Accept-Ranges}。
 *
 * <p>该接口由一次性防重放 nonce 保护。一旦声明支持 Range，CDN 的 Range 分片回源会把一次
 * 客户端请求拆成多次回源请求并复用同一个 nonce，第二个分片必然被 409 拒绝，客户端侧表现为
 * {@code ERR_HTTP2_PROTOCOL_ERROR}（HTTP/2 断流）。用动态代理桩替代 Servlet 容器。</p>
 */
class UserUploadPreviewHandlerTest {

    @Test
    @DisplayName("预览响应不声明 Accept-Ranges，且 Content-Type / Content-Length 正确")
    void previewResponseDoesNotAdvertiseRanges(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("music_1.flac");
        byte[] payload = new byte[4096];
        Files.write(file, payload);

        Map<String, String> headers = new HashMap<>();
        String[] contentType = {null};
        long[] contentLength = {-1};

        HttpServletResponse response = proxy(HttpServletResponse.class, (p, m, a) -> switch (m.getName()) {
            case "setHeader" -> {
                headers.put(((String) a[0]).toLowerCase(), (String) a[1]);
                yield null;
            }
            case "setContentType" -> {
                contentType[0] = (String) a[0];
                yield null;
            }
            case "setContentLengthLong" -> {
                contentLength[0] = (long) a[0];
                yield null;
            }
            default -> defaultValue(m);
        });

        UserUploadPreviewHandler.applyFileResponseHeaders(file, response);

        assertNull(headers.get("accept-ranges"),
                "预览接口不得声明 Accept-Ranges：会触发 CDN 分片回源，而分片会复用同一个一次性 nonce");
        assertNull(headers.get("cache-control"),
                "缓存策略交由 CacheControlFilter 兜底为 private, no-store，避免待审核文件被 CDN 公共缓存");
        assertEquals("audio/flac", contentType[0]);
        assertEquals(payload.length, contentLength[0]);
        assertEquals("inline; filename=\"music_1.flac\"", headers.get("content-disposition"));
    }

    @Test
    @DisplayName("预览文件按扩展名给出 Content-Type")
    void contentTypeByExtension() {
        assertEquals("audio/mpeg", UserUploadPreviewHandler.getContentType("a.mp3"));
        assertEquals("audio/flac", UserUploadPreviewHandler.getContentType("a.FLAC"));
        assertEquals("audio/wav", UserUploadPreviewHandler.getContentType("a.wav"));
        assertEquals("image/jpeg", UserUploadPreviewHandler.getContentType("cover.jpg"));
        assertEquals("text/plain; charset=utf-8", UserUploadPreviewHandler.getContentType("song.lrc"));
        assertEquals("application/octet-stream", UserUploadPreviewHandler.getContentType("noext"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    /** 代理的默认返回值：基本类型给零值，其余给 null。 */
    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == void.class) {
            return null;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return 0;
    }
}
