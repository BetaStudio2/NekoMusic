package com.neko.music.util;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 外部音乐客户端共用的 HTTP 传输辅助：统一的客户端构造（指定连接超时 + 跟随重定向）
 * 与「send 时把 InterruptedException 转成 IOException 并恢复中断标志」的固定语义。
 *
 * <p>只做纯粹的机械抽取，不改变超时、重定向策略、状态码判定或异常消息。</p>
 */
public final class HttpTransport {

    private HttpTransport() {
    }

    /** 各客户端此前各自构造的 HttpClient：connectTimeout + {@link HttpClient.Redirect#NORMAL}。 */
    public static HttpClient create(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 以 UTF-8 读取字符串响应体；中断语义见 {@link #send}。 */
    public static HttpResponse<String> sendString(HttpClient client, HttpRequest request, String interruptMessage)
            throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(interruptMessage, e);
        }
    }

    /** 2xx 视为成功。 */
    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
