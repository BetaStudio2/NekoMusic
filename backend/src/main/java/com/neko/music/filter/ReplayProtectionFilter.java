package com.neko.music.filter;

import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.service.ReplayNonceService;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
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
import java.util.Set;

/**
 * 通用请求防重放过滤器：把「一次性 nonce」机制应用到全部动态接口（{@code /api/*}、{@code /loser/*}）。
 *
 * <p>它拦截的请求必须带 {@code X-Neko-Nonce}，且该 nonce 只能被消费一次；重复发送同一个请求（重放）
 * 会因为 nonce 已被删除而得到 {@code 409}。nonce 的签发见 {@link com.neko.music.handlers.ReplayNonceHandler}
 * （{@code GET /api/replay/nonce}），绑定与消费见 {@link ReplayNonceService}。</p>
 *
 * <p>为什么只保护动态接口：静态资源（{@code /media/*}、{@code /assets/*}、安装包）声明了公共缓存、由
 * CDN 共享命中，协议上就无法逐请求校验；它们的重复访问也不产生服务端成本。真正有成本 / 有副作用的是
 * 动态接口（音质解析、评论、下单、收藏……），因此保护面收敛到这里。CDN 缓存语义不清洗、不介入。</p>
 *
 * <h2>豁免清单（全部为类常量，不新增配置）</h2>
 * <ul>
 *   <li>{@link #EXEMPT_PATHS}：nonce 签发接口本身、公开可缓存的 latest/ranking（重放无副作用）、
 *       支付平台服务器异步回调（第三方无法携带自定义头）。</li>
 *   <li>{@link #EXEMPT_PREFIXES}：浏览器原生请求无法附加请求头 —— 封面 / 头像走 {@code <img src>} /
 *       {@code srcset}。扫码登录的 SSE 状态流（{@code /api/user/qrlogin/status}）在
 *       {@link #EXEMPT_PATHS} 中单独豁免（EventSource 无法带头且会自动重连）。</li>
 *   <li>上传类 multipart 请求：转发前无法在不缓冲整个请求体的情况下做校验，统一豁免（已由鉴权与
 *       IP 限流覆盖）。</li>
 *   <li>{@code OPTIONS} 预检与 {@code HEAD} 请求。</li>
 * </ul>
 *
 * <h2>失败策略</h2>
 * <p>{@link #FAIL_OPEN_ON_REDIS_ERROR}=true 时，Redis 故障只记录 WARN 并放行（可用性优先）；
 * 置为 false 则直接 503（安全优先）。非 Redis 的判定失败（缺失 / 非法 / 重放）一律拒绝。</p>
 *
 * <p>需在 {@link com.neko.music.Main} 中显式注册（嵌入式 Jetty 不处理 {@code @WebFilter}）。</p>
 */
public class ReplayProtectionFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ReplayProtectionFilter.class);

    /** 是否启用反重放；置 false 可一键停用（不新增配置项）。 */
    static final boolean ENFORCED = true;

    /** Redis 故障时是否放行：true=可用性优先，false=安全优先（503）。 */
    static final boolean FAIL_OPEN_ON_REDIS_ERROR = true;

    /** 响应头：告知前端失败原因，便于自动换取新 nonce 重试。 */
    public static final String REPLAY_STATUS_HEADER = "X-Neko-Replay-Status";
    public static final String STATUS_MISSING = "missing";
    public static final String STATUS_INVALID = "invalid";
    public static final String STATUS_ERROR = "error";

    /** 需要 nonce 的动态路径前缀。 */
    private static final List<String> PROTECTED_PREFIXES = List.of("/api/", "/loser/");

    /** 精确豁免路径。 */
    static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/replay/nonce",
            "/api/replay/challenge", // 领 nonce 的第一步，同样必须自举
            "/api/music/latest",
            "/api/music/ranking",
            "/api/payment/zpay/notify",
            "/api/user/qrlogin/status"); // EventSource(SSE) 且浏览器会自动重连

    /** 前缀豁免路径（浏览器原生请求 / SSE）。 */
    static final List<String> EXEMPT_PREFIXES = List.of(
            "/api/music/cover/",  // 封面由 <img src>/srcset 直接加载，且磁盘缓存 6 个月
            "/api/user/avatar/"); // 头像由 <img src> 直接加载

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!ENFORCED
                || !(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String path = pathOf(httpRequest);
        if (!isProtected(path, httpRequest.getMethod(), httpRequest.getContentType())) {
            chain.doFilter(request, response);
            return;
        }

        String scope = ReplayNonceService.scopeOf(httpRequest.getMethod());
        if (scope == null) {
            chain.doFilter(request, response);
            return;
        }

        String nonce = httpRequest.getHeader(ReplayNonceService.NONCE_HEADER);
        if (nonce == null || nonce.isBlank()) {
            logger.debug("缺少防重放 nonce: {} {}", httpRequest.getMethod(), path);
            reject(httpResponse, STATUS_MISSING, "请求缺少防重放校验，请刷新后重试");
            return;
        }

        String clientIp = ClientIpResolver.clientIp(httpRequest);
        ReplayNonceService.Result result = ReplayNonceService.consume(nonce, scope, clientIp);
        switch (result) {
            case OK -> chain.doFilter(request, response);
            case REJECTED -> {
                // nonce 已存在但校验失败：典型的重放（或过期 / 换 IP / 跨类别使用）。
                logger.warn("疑似重放请求被拒: {} {} ip={}", httpRequest.getMethod(), path, clientIp);
                reject(httpResponse, STATUS_INVALID, "请求已失效，请刷新后重试");
            }
            case ERROR -> {
                if (FAIL_OPEN_ON_REDIS_ERROR) {
                    logger.warn("防重放校验因 Redis 故障放行: {} {}", httpRequest.getMethod(), path);
                    chain.doFilter(request, response);
                } else {
                    reject(httpResponse, STATUS_ERROR, "服务繁忙，请稍后重试");
                }
            }
            default -> reject(httpResponse, STATUS_ERROR, "服务繁忙，请稍后重试");
        }
    }

    /** 是否需要对本次请求做 nonce 校验。 */
    static boolean isProtected(String path, String method, String contentType) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // 归一化尾部斜杠，避免 /api/replay/nonce/ 之类写法绕过豁免判定
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String verb = method == null ? "" : method.toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(verb) || "HEAD".equals(verb)) {
            return false;
        }
        // multipart 上传：转发前无法整体校验请求体，统一豁免
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return false;
        }
        if (EXEMPT_PATHS.contains(path)) {
            return false;
        }
        for (String prefix : EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        // /loser/*/pull 为 SSE 进度流（EventSource / 长连接），无法逐请求携带 nonce
        if (path.endsWith("/pull")) {
            return false;
        }
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void reject(HttpServletResponse response, String statusCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        response.setHeader(REPLAY_STATUS_HEADER, statusCode);
        JsonObject body = new JsonObject();
        body.addProperty("success", false);
        body.addProperty("message", message);
        try (PrintWriter out = response.getWriter()) {
            out.print(Main.getGson().toJson(body));
            out.flush();
        }
    }

    /** 归一化请求路径：去掉 context path 与 {@code ;jessionid} 之类的路径参数。 */
    static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        int semicolon = uri.indexOf(';');
        if (semicolon >= 0) {
            uri = uri.substring(0, semicolon);
        }
        return uri;
    }
}
