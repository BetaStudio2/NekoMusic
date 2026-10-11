package com.neko.music.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「路由视角」路径解析测试：getServletPath + pathInfo 优先（与 servlet 路由同一套解释），
 * 拿不到映射时自归一（去 {@code ;} 参数、解开百分号编码、消解 {@code .} / {@code ..}）。
 */
class RequestPathTest {

    private static HttpServletRequest request(String servletPath, String pathInfo, String uri, String contextPath) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getServletPath" -> servletPath;
                    case "getPathInfo" -> pathInfo;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> contextPath;
                    default -> null;
                });
    }

    @Test
    void usesServletMappingPathWhenContainerResolvedIt() {
        // 精确映射：等价写法在 servletPath 上已经是同一个规范路径
        assertEquals("/api/music/search",
                RequestPath.of(request("/api/music/search", null, "/./api/music/search", "")));
        assertEquals("/api/music/search",
                RequestPath.of(request("/api/music/search", null, "/a/../api/music/search", "")));
        assertEquals("/api/music/search",
                RequestPath.of(request("/api/music/search", null, "/api;/music/search", "")));

        // 前缀映射：servletPath 是前缀，路径信息在 pathInfo
        assertEquals("/api/music/abc",
                RequestPath.of(request("/api/music", "/abc", "/./api/music/abc", "")));

        // 默认（/）映射：servletPath 就是整条规范路径
        assertEquals("/api/unknown", RequestPath.of(request("/api/unknown", null, "/API/unknown", "")));

        // 根路径
        assertEquals("/", RequestPath.of(request("/", null, "/", "")));
    }

    @Test
    void stripsContextPath() {
        assertEquals("/api/music/search",
                RequestPath.of(request("/api/music/search", null, "/neko/api/music/search", "/neko")));
    }

    @Test
    void fallsBackToSelfCanonicalisingWhenMappingIsUnavailable() {
        // servletPath 为 null（容器未解析映射，例如错误分派）：自己归一，宁可多保护也不漏保护
        assertEquals("/api/music/search", RequestPath.of(request(null, null, "/./api/music/search", "")));
        assertEquals("/api/music/search", RequestPath.of(request(null, null, "/a/../api/music/search", "")));
        assertEquals("/api/music/search", RequestPath.of(request(null, null, "/api;/music/search", "")));
        assertEquals("/api/music/search", RequestPath.of(request(null, null, "/%61pi/music/search", "")));
        assertEquals("/api/music/search", RequestPath.of(request(null, null, "/api/music/search;jsessionid=x", "")));
        assertEquals("/a/b", RequestPath.of(request(null, null, "/a//b", "")));
        assertEquals("/", RequestPath.of(request(null, null, "/../..", "")));
        assertEquals("/api/music/search", RequestPath.of(request(null, null, "/neko/a/../api/music/search", "/neko")));
    }

    @Test
    void blankServletPathFallsBackInsteadOfReturningUnprotectedEmpty() {
        assertEquals("/api/music/search", RequestPath.of(request("", null, "/./api/music/search", "")));
    }
}
