package com.neko.music.handlers;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 请求体解析失败时的错误响应不得回显内部实现细节。
 *
 * <p>Jackson 反序列化异常的 message 里带全限定处理器/DTO 类名与「已知字段」清单，一旦原样返回，
 * 攻击者仅凭畸形 JSON 就能测绘后端包结构、handler 与 DTO 字段。这里锁定「畸形请求体 → 400 +
 * 泛化文案」这一不变量。
 */
class JsonParseErrorSanitizationTest {

    @Test
    void malformedBodyOnAnonymousSearchDoesNotLeakInternals() throws Exception {
        HttpServletRequest request = stubPostRequest("[]");
        StringWriter body = new StringWriter();
        HttpServletResponse response = stubResponse(new PrintWriter(body));

        new MusicSearchHandler().doPost(request, response);
        String text = body.toString();

        assertEquals(400, Holder.status, "畸形请求体应返回 400，实际：" + text);
        assertFalse(text.contains("com."), "错误响应泄露了全限定类名：" + text);
        assertFalse(text.contains("$"), "错误响应泄露了内部类名：" + text);
        assertFalse(text.contains("known properties"), "错误响应泄露了 DTO 字段清单：" + text);
        assertFalse(text.contains("Exception"), "错误响应泄露了异常类型：" + text);
    }

    /** 记录 setStatus 值的响应替身。 */
    private static final class Holder {
        static int status;
    }

    private static HttpServletResponse stubResponse(PrintWriter writer) {
        Holder.status = 0;
        return (HttpServletResponse) Proxy.newProxyInstance(
                JsonParseErrorSanitizationTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("setStatus".equals(method.getName())) {
                        Holder.status = (int) args[0];
                        return null;
                    }
                    if ("getWriter".equals(method.getName())) {
                        return writer;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private static HttpServletRequest stubPostRequest(String body) {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ServletInputStream in = new ServletInputStream() {
            private final ByteArrayInputStream source = new ByteArrayInputStream(bytes);

            @Override
            public int read() {
                return source.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return source.read(b, off, len);
            }

            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                JsonParseErrorSanitizationTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getInputStream" -> in;
                    case "getRequestURI" -> "/api/music/search";
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }
}
