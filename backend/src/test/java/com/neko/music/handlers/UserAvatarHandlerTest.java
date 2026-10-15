package com.neko.music.handlers;

import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户头像 URL 的缓存契约回归。
 *
 * <p>默认头像是一张内嵌的大图（约 300KB），此前响应既没有 {@code Content-Length}、也没有任何缓存
 * 头，落到 {@code CacheControlFilter} 的 {@code private, no-store} 兜底上：每个页面加载都要把整张图
 * 重新回源一次，连 {@code ?v=} 的换图机制都失效。这里锁住两点：</p>
 * <ul>
 *   <li>{@code /default} 内容恒定 → 长缓存 + 显式长度；</li>
 *   <li>用户头像（含「暂无头像」）内容可能被替换 → 逐次再校验，命中 {@code If-None-Match} 回 304，
 *       不重发整张图。</li>
 * </ul>
 */
class UserAvatarHandlerTest {

    @Test
    void defaultAvatarIsLongCachedWithExplicitLength() throws Exception {
        Recorder first = run("/default", null);

        assertEquals(200, first.status);
        assertEquals(HttpResourceCache.DEFAULT_ICON_ETAG, first.headers.get("etag"));
        assertEquals(HttpResourceCache.CACHE_CONTROL_DEFAULT_ICON, first.headers.get("cache-control"));
        assertTrue(first.body.size() > 0, "默认头像应有响应体");
        assertEquals(String.valueOf(first.body.size()), first.headers.get("content-length"),
                "必须显式给 Content-Length，否则退回 chunked、按缓冲切成多次写");

        Recorder again = run("/default", HttpResourceCache.DEFAULT_ICON_ETAG);
        assertEquals(304, again.status);
        assertEquals(0, again.body.size(), "命中 If-None-Match 不应重发整张图");
    }

    @Test
    void userAvatarPathMustRevalidateInsteadOfBeingStoredForever() throws Exception {
        // 单测里没有主库：读头像路径必然失败，等价于「该用户暂无头像」，走的同样是默认图那条路
        Recorder first = run("/42", null);

        assertEquals(200, first.status);
        assertEquals(HttpResourceCache.CACHE_CONTROL_PRIVATE_REVALIDATE, first.headers.get("cache-control"),
                "用户头像 URL 之后可能换成真头像，不能长期缓存");
        assertEquals(String.valueOf(first.body.size()), first.headers.get("content-length"));

        Recorder again = run("/42", HttpResourceCache.DEFAULT_ICON_ETAG);
        assertEquals(304, again.status);
        assertEquals(0, again.body.size());
    }

    private static Recorder run(String pathInfo, String ifNoneMatch) throws Exception {
        Recorder rec = new Recorder();

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                UserAvatarHandlerTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getOutputStream" -> {
                            return outputStream(rec);
                        }
                        case "setHeader" -> {
                            rec.headers.put(((String) args[0]).toLowerCase(Locale.ROOT), (String) args[1]);
                            return null;
                        }
                        case "setContentLength" -> {
                            rec.headers.put("content-length", String.valueOf((int) args[0]));
                            return null;
                        }
                        case "setContentLengthLong" -> {
                            rec.headers.put("content-length", String.valueOf((long) args[0]));
                            return null;
                        }
                        case "setStatus" -> {
                            rec.status = (int) args[0];
                            return null;
                        }
                        default -> {
                        }
                    }
                    return defaultValue(method.getReturnType());
                });

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                UserAvatarHandlerTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getProtocol" -> "HTTP/1.1";
                    case "getPathInfo" -> pathInfo;
                    case "getHeader" -> "If-None-Match".equalsIgnoreCase((String) args[0]) ? ifNoneMatch : null;
                    default -> defaultValue(method.getReturnType());
                });

        new UserAvatarHandler().service(request, response);
        return rec;
    }

    private static ServletOutputStream outputStream(Recorder rec) {
        return new ServletOutputStream() {
            @Override
            public void write(int b) {
                rec.body.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return 0;
    }

    private static final class Recorder {
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        int status = 200;
    }
}
