package com.neko.music.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 通用安全加固过滤器（最外层，先于限流/防爬注册）。
 *
 * <ul>
 *   <li>禁用 {@code TRACE}/{@code TRACK}（防 XST），命中返回 405 并给出 Allow。</li>
 *   <li>补充常见安全响应头：{@code X-Content-Type-Options}、{@code Referrer-Policy}、
 *       {@code X-Frame-Options}、{@code Permissions-Policy}。</li>
 *   <li>覆盖 {@code Server} 为不含版本号的名称（配合连接器 {@code setSendServerVersion(false)}），
 *       避免泄露 Jetty 版本。</li>
 * </ul>
 *
 * <p>需在 {@link com.neko.music.Main} 中显式注册（嵌入式 Jetty 不处理 {@code @WebFilter}）。</p>
 */
public class SecurityHeadersFilter implements Filter {

    private static final String ALLOWED_METHODS = "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest
                && response instanceof HttpServletResponse httpResponse) {
            String method = httpRequest.getMethod();
            if ("TRACE".equalsIgnoreCase(method) || "TRACK".equalsIgnoreCase(method)) {
                httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                httpResponse.setHeader("Allow", ALLOWED_METHODS);
                httpResponse.setHeader("Cache-Control", "private, no-store");
                return;
            }

            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
            httpResponse.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            httpResponse.setHeader("Server", "NekoMusic");
        }
        chain.doFilter(request, response);
    }
}
