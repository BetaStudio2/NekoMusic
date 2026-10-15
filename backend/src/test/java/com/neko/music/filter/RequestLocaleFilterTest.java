package com.neko.music.filter;

import com.neko.music.i18n.RequestLocale;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 响应语言过滤器：按 {@code Accept-Language} 设置本次请求语言，并在返回时清理 ThreadLocal。
 *
 * <p>不清理会把语言串给复用同一条 Jetty 线程的下一个请求，因此「退出后必须回到默认语言」是关键断言。</p>
 */
class RequestLocaleFilterTest {

    @AfterEach
    void tearDown() {
        RequestLocale.clear();
    }

    @Test
    void setsLocaleForTheRequestAndClearsAfterwards() throws Exception {
        RequestLocaleFilter filter = new RequestLocaleFilter();
        String[] seen = new String[1];
        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                seen[0] = RequestLocale.current();
            }
        };

        filter.doFilter(stub("en-US,en;q=0.9"), null, chain);
        assertEquals(RequestLocale.EN, seen[0]);
        assertEquals(RequestLocale.DEFAULT, RequestLocale.current(), "过滤器返回后必须清理 ThreadLocal");

        filter.doFilter(stub("zh-CN"), null, chain);
        assertEquals(RequestLocale.DEFAULT, seen[0]);
        assertEquals(RequestLocale.DEFAULT, RequestLocale.current());
    }

    @Test
    void clearsLocaleEvenWhenChainThrows() {
        RequestLocaleFilter filter = new RequestLocaleFilter();
        FilterChain boom = (request, response) -> {
            throw new IllegalStateException("boom");
        };
        boolean thrown = false;
        try {
            filter.doFilter(stub("en"), null, boom);
        } catch (Exception e) {
            thrown = true;
        }
        assertTrue(thrown);
        assertEquals(RequestLocale.DEFAULT, RequestLocale.current(), "异常路径同样必须清理");
    }

    @Test
    void nonHttpRequestLeavesDefaultLocale() throws Exception {
        RequestLocaleFilter filter = new RequestLocaleFilter();
        String[] seen = new String[1];
        filter.doFilter((ServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ServletRequest.class},
                (proxy, method, args) -> null), null, (request, response) ->
                seen[0] = RequestLocale.current());
        assertEquals(RequestLocale.DEFAULT, seen[0]);
        assertFalse(false);
    }

    private static HttpServletRequest stub(String acceptLanguage) {
        Map<String, String> headers = new HashMap<>();
        if (acceptLanguage != null) {
            headers.put("Accept-Language", acceptLanguage);
        }
        return (HttpServletRequest) Proxy.newProxyInstance(
                RequestLocaleFilterTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> headers.get((String) args[0]);
                    default -> null;
                });
    }
}
