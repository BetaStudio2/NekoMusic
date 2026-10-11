package com.neko.music.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 未捕获异常的错误页不得回显异常类名、异常消息与请求 URL。
 *
 * <p>回归背景：Jetty 默认 {@code ErrorHandler} 会把 {@code throwable.toString()}（含 java.lang.* 类名，
 * 消息里又常常原样带上攻击者提交的字段值）与请求 URL 一起写进错误页，一个未捕获的
 * {@code NumberFormatException} 就泄露了后端实现语言与用户原始输入（CWE-209）。这里锁定「错误页只给
 * 泛化文案」这一不变量。</p>
 */
class SanitizedErrorHandlerTest {

    private static final String LEAKY_MESSAGE = "java.lang.NumberFormatException: For input string: \"1 or 1=1\"";

    @Test
    @DisplayName("JSON 错误页只返回泛化文案，不含异常类名与原始输入")
    void jsonErrorBodyIsSanitized() {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        new SanitizedErrorHandler().writeErrorJson(null, writer, 500, LEAKY_MESSAGE,
                new RuntimeException(LEAKY_MESSAGE));
        writer.flush();
        String body = out.toString();

        assertEquals("{\"success\":false,\"message\":\"服务器内部错误\"}\n", body);
        assertNoInternals(body);
    }

    @Test
    @DisplayName("纯文本错误页同样脱敏")
    void plainErrorBodyIsSanitized() {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        new SanitizedErrorHandler().writeErrorPlain(null, writer, 500, LEAKY_MESSAGE,
                new RuntimeException(LEAKY_MESSAGE));
        writer.flush();
        String body = out.toString();

        assertFalse(body.contains("NumberFormatException"), body);
        assertNoInternals(body);
    }

    @Test
    @DisplayName("5xx 一律泛化，4xx 用通用原因短语")
    void publicMessageNeverLeaksInternals() {
        assertEquals("服务器内部错误", SanitizedErrorHandler.publicMessage(500));
        assertEquals("资源不存在", SanitizedErrorHandler.publicMessage(404));
        assertEquals("请求方法不被支持", SanitizedErrorHandler.publicMessage(405));
        assertNoInternals(SanitizedErrorHandler.publicMessage(502));
        assertNoInternals(SanitizedErrorHandler.jsonBody(500));
    }

    private static void assertNoInternals(String text) {
        assertFalse(text.contains("java."), "错误页泄露了异常类名：" + text);
        assertFalse(text.contains("Exception"), "错误页泄露了异常类型：" + text);
        assertFalse(text.contains("1 or 1=1"), "错误页回显了原始输入：" + text);
        assertFalse(text.contains("url"), "错误页泄露了请求 URL 字段：" + text);
    }
}
