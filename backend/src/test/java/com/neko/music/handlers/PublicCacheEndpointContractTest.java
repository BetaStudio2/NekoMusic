package com.neko.music.handlers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公开缓存接口（{@code Cache-Control: public}）的契约回归：响应内容必须与查询串无关。
 *
 * <p>{@code /api/music/latest}、{@code /api/music/ranking} 的响应会被共享缓存（CDN）缓存，而缓存键
 * 未必包含查询串。一旦响应体随查询参数变化，同一份缓存条目就会被其它查询串的请求写坏（Web 缓存
 * 投毒）。因此这两个接口禁止读取任何查询参数——本测试用一个「读参数即失败」的请求替身锁住该不变量。
 */
class PublicCacheEndpointContractTest {

    @Test
    void latestMusicIgnoresQueryParameters() throws Exception {
        assertPubliclyCacheableAndQueryAgnostic(new LatestMusicHandler());
    }

    @Test
    void musicRankingIgnoresQueryParameters() throws Exception {
        assertPubliclyCacheableAndQueryAgnostic(new MusicRankingHandler());
    }

    private void assertPubliclyCacheableAndQueryAgnostic(jakarta.servlet.http.HttpServlet handler)
            throws Exception {
        Map<String, String> headers = new HashMap<>();
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getWriter" -> {
                            return writer;
                        }
                        case "setHeader" -> {
                            headers.put(((String) args[0]).toLowerCase(Locale.ROOT), (String) args[1]);
                            return null;
                        }
                        default -> {
                        }
                    }
                    return defaultValue(method.getReturnType());
                });

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    // 读取任何查询串/参数都视为违约：命中即让测试失败。
                    case "getParameter", "getParameterValues", "getParameterMap", "getParameterNames",
                         "getQueryString" -> throw new AssertionError(
                            handler.getClass().getSimpleName() + " 读取了查询参数：" + method.getName());
                    default -> defaultValue(method.getReturnType());
                });

        handler.service(request, response);
        writer.flush();

        assertTrue(headers.getOrDefault("cache-control", "").startsWith("public, max-age="),
                "公开缓存接口应声明 public 缓存，实际：" + headers.get("cache-control"));
        assertTrue(body.toString().contains("\"success\":true"),
                "响应应为成功 JSON，实际：" + body);
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
}
