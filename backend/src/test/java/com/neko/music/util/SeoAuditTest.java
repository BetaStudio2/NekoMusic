package com.neko.music.util;

import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 「本次响应是不是 SEO 服务端渲染」的请求属性标记。
 *
 * <p>关键约定：SEO 分支与浏览器分支都**显式**落值，只有与 SEO 无关的路径才是 null——否则
 * 「爬虫收录详情页」和「用户打开详情页」在日志里会长得一模一样。</p>
 */
class SeoAuditTest {

    @Test
    @DisplayName("默认未标记：与 SEO 无关的路径读到 null")
    void unmarkedIsNull() {
        assertNull(SeoAudit.seoRender(request()));
        assertNull(SeoAudit.seoRender(null));
    }

    @Test
    @DisplayName("mark / markNotSeo 分别落 true / false，后写的覆盖先写的")
    void marksRenderBranch() {
        ServletRequest request = request();
        SeoAudit.mark(request);
        assertEquals(Boolean.TRUE, SeoAudit.seoRender(request));

        SeoAudit.markNotSeo(request);
        assertEquals(Boolean.FALSE, SeoAudit.seoRender(request));
    }

    /** 只为 getAttribute/setAttribute 服务的轻量请求桩。 */
    private static ServletRequest request() {
        Map<String, Object> attributes = new HashMap<>();
        return (ServletRequest) Proxy.newProxyInstance(
                ServletRequest.class.getClassLoader(),
                new Class<?>[] { ServletRequest.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "setAttribute" -> {
                        attributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getAttribute" -> attributes.get((String) args[0]);
                    case "toString" -> "ServletRequestStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
