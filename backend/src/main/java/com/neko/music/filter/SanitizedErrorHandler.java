package com.neko.music.filter;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * 统一错误页：对外只给「状态码 + 泛化文案」，绝不回显异常类名、异常消息、原始请求输入与请求 URL。
 *
 * <p>用来替换 Jetty 默认 {@link ErrorHandler}。默认实现会把 {@code throwable.toString()}（含异常类名，
 * 异常消息里又常常原样带上攻击者提交的字段值）和请求 URL 一起写进 JSON / HTML / 纯文本错误页，于是
 * 一个未捕获的 {@code NumberFormatException} 就能把后端 Java 类名与用户原始输入泄露给调用方
 * （CWE-209）。异常细节只写服务端日志，排障信息不丢，对外一个字段都不给。</p>
 */
public class SanitizedErrorHandler extends ErrorHandler {
    private static final Logger logger = LoggerFactory.getLogger(SanitizedErrorHandler.class);

    /**
     * 5xx 一律泛化为「服务器内部错误」；4xx 用与「错误码说明」表一致的通用文案。
     *
     * <p>只输出固定文案，绝不拼接异常类名、异常消息或用户输入，避免错误页成为信息泄露面。</p>
     */
    static String publicMessage(int status) {
        if (status >= 500) {
            return "服务器内部错误";
        }
        return switch (status) {
            case 400 -> "请求参数错误";
            case 401 -> "未授权";
            case 403 -> "禁止访问";
            case 404 -> "资源不存在";
            case 405 -> "请求方法不被支持";
            case 409 -> "资源状态冲突";
            case 413 -> "请求体过大";
            case 415 -> "不支持的媒体类型";
            case 429 -> "请求过于频繁";
            default -> {
                String reason = HttpStatus.getMessage(status);
                yield reason == null || reason.isBlank() ? "请求无法处理" : reason;
            }
        };
    }

    /** 与业务错误体保持同一契约：{@code {"success":false,"message":"..."}}。 */
    static String jsonBody(int status) {
        return "{\"success\":false,\"message\":\"" + publicMessage(status) + "\"}\n";
    }

    @Override
    protected void generateResponse(Request request, Response response, int status, String message,
                                    Throwable throwable, Callback callback) throws IOException {
        if (throwable != null) {
            // 类名、异常消息、堆栈只进服务端日志
            logger.error("请求处理失败，对外仅返回泛化错误: {} {} -> {}", request.getMethod(),
                    request.getHttpURI(), status, throwable);
        } else if (status >= 500) {
            logger.error("请求处理失败，对外仅返回泛化错误: {} {} -> {} {}",
                    request.getMethod(), request.getHttpURI(), status, message);
        }
        super.generateResponse(request, response, status, message, throwable, callback);
    }

    @Override
    protected void writeErrorJson(Request request, PrintWriter writer, int status, String message,
                                  Throwable throwable) {
        writer.write(jsonBody(status));
    }

    @Override
    protected void writeErrorPlain(Request request, PrintWriter writer, int status, String message,
                                   Throwable throwable) {
        writer.write(status + " " + publicMessage(status) + "\n");
    }

    @Override
    protected void writeErrorHtml(Request request, Writer writer, Charset charset, int status,
                                  String message, Throwable throwable) throws IOException {
        writer.write("<html><head><meta charset=\"" + charset.name() + "\"><title>" + status
                + "</title></head><body><h1>" + status + " " + publicMessage(status)
                + "</h1></body></html>\n");
    }
}
