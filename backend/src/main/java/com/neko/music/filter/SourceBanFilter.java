package com.neko.music.filter;

import com.neko.music.Main;
import com.neko.music.service.SourceBanService;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.RequestPath;
import com.neko.music.util.SsePaths;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

/**
 * 认证失败来源封禁过滤器：把 {@link SourceBanService} 判定的「临时封禁来源」在进入路由前挡掉。
 *
 * <p>只作用于动态接口（{@code /api/*}、{@code /loser/*}），静态资源 / 媒体与 CDN 公共缓存语义不被
 * 干预；触发封禁的是<b>反复认证失败</b>，正常浏览请求不产生失败，因此不会平白被牵连。</p>
 *
 * <p>响应与 {@link IPRateLimitFilter} 保持一致：{@code 429} + {@code Retry-After}，文案保持通用，
 * 不向探测者区分「被封禁」与「请求过快」；{@code OPTIONS}/{@code HEAD}、第三方回调（支付异步
 * 通知）与 SSE 长连接（见 {@link SsePaths}）照常放行。需在 {@link com.neko.music.Main} 中显式
 * 注册（嵌入式 Jetty 不处理 {@code @WebFilter}）。</p>
 */
public class SourceBanFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(SourceBanFilter.class);

    /** 需要检查封禁的动态路径前缀。 */
    private static final List<String> PROTECTED_PREFIXES = List.of("/api/", "/loser/");

    /** 第三方服务器回调：既非浏览器也非官方客户端，不能因来源判定的误伤而丢失支付通知。 */
    private static final String ZPAY_NOTIFY_PATH = "/api/payment/zpay/notify";

    private SourceBanService sourceBanService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext servletContext = filterConfig.getServletContext();
        sourceBanService = (SourceBanService) servletContext.getAttribute("sourceBanService");
        if (sourceBanService == null) {
            logger.warn("SourceBanService 未找到，认证失败来源封禁功能将无法使用");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (sourceBanService == null
                || !(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)
                || !isProtected(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = ClientIpResolver.clientIp(httpRequest);
        if (!sourceBanService.isBanned(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        long remainingSec = sourceBanService.remainingBanSeconds(clientIp);
        logger.warn("被临时封禁的来源尝试访问: ip={} {} {}", clientIp,
                httpRequest.getMethod(), RequestPath.of(httpRequest));
        writeBannedResponse(httpResponse, clientIp, remainingSec);
    }

    /** 是否是需要检查封禁的动态请求。 */
    static boolean isProtected(HttpServletRequest request) {
        String method = request.getMethod();
        String verb = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(verb) || "HEAD".equals(verb)) {
            return false;
        }
        String path = RequestPath.of(request);
        if (path == null || path.isEmpty()) {
            return false;
        }
        // 归一化尾部斜杠，避免 /api/.../ 之类写法绕过判定
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (ZPAY_NOTIFY_PATH.equals(path)) {
            return false;
        }
        // SSE 长连接同样豁免：EventSource 断线后自动重连、又无法携带自定义请求头，一旦来源被误封，
        // 用户的实时流（扫码登录 / 站内消息 / 后台日志 / 歌单导入进度）会整体连接失败。
        if (SsePaths.isStreamPath(path)) {
            return false;
        }
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 返回 429 + {@code Retry-After}：文案与全局限流完全一致，不暴露判定规则。 */
    private void writeBannedResponse(HttpServletResponse response, String clientIp, long remainingSec)
            throws IOException {
        long sec = remainingSec > 0 ? remainingSec : 60;
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(sec));
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        var body = Main.getObjectMapper().createObjectNode();
        body.put("success", false);
        body.put("message", IPRateLimitFilter.formatRateLimitMessage(sec));
        body.putNull("data");

        try (PrintWriter out = response.getWriter()) {
            Main.getObjectMapper().writeValue(out, body);
            out.flush();
        } catch (Exception e) {
            logger.debug("写入来源封禁响应失败: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        logger.info("认证失败来源封禁过滤器已销毁");
    }
}
