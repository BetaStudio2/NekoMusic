package com.neko.music.filter;

import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 默认缓存策略过滤器：把动态接口兜底为「禁止缓存」。
 *
 * <p>只有 {@code /api/*}、{@code /loser/*}、{@code /detail/*} 这类动态路由会被写入
 * {@code Cache-Control: private, no-store}。需要公开缓存的接口（音乐封面/音频、
 * latest/ranking、sitemap 等）在处理函数里再次 {@code setHeader} 覆盖即可。</p>
 *
 * <p>磁盘媒体（{@code /media/*}）、安装包（{@code /update/*}）、站点静态资源（{@code /}
 * 前缀）不在兜底范围内，走各自更长的缓存时长。</p>
 */
public class CacheControlFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest
                && response instanceof HttpServletResponse httpResponse
                && isDynamicPath(httpRequest.getRequestURI())) {
            httpResponse.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        }
        chain.doFilter(request, response);
    }

    private static boolean isDynamicPath(String uri) {
        return uri.startsWith("/api/") || uri.equals("/api")
                || uri.startsWith("/loser/")
                || uri.startsWith("/detail/") || uri.equals("/detail");
    }
}
