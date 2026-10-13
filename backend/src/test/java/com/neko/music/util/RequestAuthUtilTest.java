package com.neko.music.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 令牌提取的**形态兼容**：官方客户端发的就是裸 token，不能只认 {@code Bearer } 前缀。
 *
 * <p>线上访问日志曾把 PC 客户端整片记成「未登录」，就是因为审计侧只按 {@code Bearer } 前缀判断，
 * 而认证层（这里）一直两种形态都收。这个用例把三种形态钉住，避免再有人按「标准写法」收窄。</p>
 */
class RequestAuthUtilTest {

    private static HttpServletRequest request(String authorization, String tokenParam) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> "Authorization".equals(args[0]) ? authorization : null;
                    case "getParameter" -> "token".equals(args[0]) ? tokenParam : null;
                    default -> null;
                });
    }

    @Test
    @DisplayName("裸 token 与 Bearer 前缀都算令牌（PC / Android 发的是前者）")
    void acceptsRawAndBearerForms() {
        assertEquals("abc123", RequestAuthUtil.extractToken(request("abc123", null)));
        assertEquals("abc123", RequestAuthUtil.extractToken(request("Bearer abc123", null)));
        assertEquals("abc123", RequestAuthUtil.extractToken(request("Bearer   abc123  ", null)));
        // 大小写不敏感，别把 `bearer` 当成令牌本身
        assertEquals("abc123", RequestAuthUtil.extractToken(request("bearer abc123", null)));
    }

    @Test
    @DisplayName("浏览器 EventSource 退化的 ?token= 也能取到，且请求头优先")
    void acceptsQueryToken() {
        assertEquals("fromQuery", RequestAuthUtil.extractToken(request(null, "fromQuery")));
        assertEquals("fromHeader", RequestAuthUtil.extractToken(request("fromHeader", "fromQuery")));
    }

    @Test
    @DisplayName("没有令牌 / 只有空白 → null，不会被当成「已登录」")
    void rejectsEmptyTokens() {
        assertNull(RequestAuthUtil.extractToken(request(null, null)));
        assertNull(RequestAuthUtil.extractToken(request("   ", null)));
        assertNull(RequestAuthUtil.extractToken(request("Bearer ", null)));
        assertNull(RequestAuthUtil.extractToken(request(null, "  ")));
    }
}
