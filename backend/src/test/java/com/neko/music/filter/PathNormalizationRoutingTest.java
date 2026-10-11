package com.neko.music.filter;

import com.neko.music.util.RequestPath;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「门禁判定 == 实际路由」的对齐回归测试。
 *
 * <p>用 Jetty 自己的请求解析与 servlet 映射（进程内 {@link LocalConnector}，不监听端口）跑一批
 * 等价路径写法，断言：只要请求最终落到受保护的 handler，两个门禁（防爬、防重放）就必须判定为
 * 受保护。</p>
 *
 * <p>同时断言「按原始 URI 判定」在这些写法上会失手——这正是修复前被绕过的原因，防止将来有人把
 * 判定改回 {@code getRequestURI()} 而测试仍然全绿。</p>
 */
class PathNormalizationRoutingTest {

    /** 会被 Jetty 规范化后路由到受保护 handler 的等价写法。 */
    private static final List<String> PROTECTED_VARIANTS = List.of(
            "/api/music/search",
            "/./api/music/search",
            "/a/../api/music/search",
            "/api/./music/search",
            "/api;/music/search",
            "/api/music/search;jsessionid=abc",
            "/%61pi/music/search",
            "/api/music/../music/search",
            "/loser/kugou/getSongListDetail",
            "/./loser/kugou/getSongListDetail",
            "/a/../loser/qq/getSongListDetail",
            "/loser;/kugou/getSongListDetail");

    /** Jetty 自身直接 400 的非规范形态：根本不该到达任何 handler。 */
    private static final List<String> REJECTED_BY_JETTY = List.of(
            "//api/music/search",
            "/api%2fmusic/search");

    private static Server server;
    private static LocalConnector connector;
    private static final List<String> handled = new ArrayList<>();
    private static final List<String> filterSawCanonical = new ArrayList<>();
    private static final List<String> filterSawRaw = new ArrayList<>();

    /** 记录门禁实际拿到的路径：规范化路径与原始 URI 各记一份，便于断言二者差异。 */
    private static final class PathRecordingFilter implements Filter {
        @Override
        public void init(FilterConfig filterConfig) {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            filterSawCanonical.add(RequestPath.of(httpRequest));
            filterSawRaw.add(httpRequest.getRequestURI());
            chain.doFilter(request, response);
        }
    }

    private static final class ProbeServlet extends HttpServlet {
        private final String tag;

        ProbeServlet(String tag) {
            this.tag = tag;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            handled.add(tag);
            response.setStatus(200);
            response.getWriter().println("HANDLED:" + tag);
        }
    }

    @BeforeAll
    static void startServer() throws Exception {
        server = new Server();
        connector = new LocalConnector(server);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addFilter(new FilterHolder(new PathRecordingFilter()), "/*",
                EnumSet.allOf(DispatcherType.class));
        context.addServlet(new ServletHolder(new ProbeServlet("api-exact")), "/api/music/search");
        context.addServlet(new ServletHolder(new ProbeServlet("api-prefix")), "/api/music/*");
        context.addServlet(new ServletHolder(new ProbeServlet("loser")), "/loser/kugou/getSongListDetail");
        context.addServlet(new ServletHolder(new ProbeServlet("loser-qq")), "/loser/qq/getSongListDetail");
        context.addServlet(new ServletHolder(new ProbeServlet("default-site")), "/");
        server.setHandler(context);
        server.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    private String send(String path) throws Exception {
        handled.clear();
        filterSawCanonical.clear();
        filterSawRaw.clear();
        String raw = "POST " + path + " HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: application/json\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        return connector.getResponse(raw, 5, TimeUnit.SECONDS);
    }

    @Test
    void protectedHandlersAreAlwaysSeenAsProtectedByBothGates() throws Exception {
        for (String path : PROTECTED_VARIANTS) {
            String response = send(path);

            assertTrue(response.contains("HANDLED"), path + " 应被路由到 handler：" + response);
            assertFalse(handled.isEmpty(), path);

            // 门禁拿到的路径必须与路由一致：每个受保护 handler 都对应一条受保护判定
            for (String canonical : filterSawCanonical) {
                assertTrue(CrawlerProtectionFilter.isGuardedPath(canonical),
                        path + " 防爬门禁判定为不受保护，实际却被路由到 " + handled + "：canonical=" + canonical);
                assertTrue(ReplayProtectionFilter.isProtected(canonical, "POST", "application/json"),
                        path + " 防重放门禁判定为不受保护，实际却被路由到 " + handled + "：canonical=" + canonical);
            }
        }
    }

    @Test
    void rawUriBasedDecisionWouldHaveMissedTheBypassShapes() throws Exception {
        // 修复前门禁读的就是这份原始 URI：这些写法在它上面全都判定为「不受保护」，
        // 于是请求被放行、随后被 Jetty 规范化并命中真实 handler —— 漏洞成立。
        for (String path : PROTECTED_VARIANTS) {
            send(path);
            assertTrue(filterSawRaw.contains(path), path + " 过滤器应看到原始 URI");

            boolean rawGuarded = CrawlerProtectionFilter.isGuardedPath(path);
            boolean rawProtected = ReplayProtectionFilter.isProtected(path, "POST", "application/json");
            boolean canonicalIsGuarded = filterSawCanonical.stream()
                    .anyMatch(CrawlerProtectionFilter::isGuardedPath);
            if (!rawGuarded) {
                assertTrue(canonicalIsGuarded, path + " 规范化后必须回到受保护范围");
                assertFalse(rawProtected, path + " 该写法在原始 URI 上正是判定不到的绕过写法");
            }
        }
    }

    @Test
    void ambiguousSeparatorsAreRejectedBeforeReachingAnyHandler() throws Exception {
        // Jetty 自己对歧义分隔符 / 空段直接 400，请求不会进入过滤器，也不会命中 handler
        for (String path : REJECTED_BY_JETTY) {
            String response = send(path);
            assertTrue(response.startsWith("HTTP/1.1 400"), path + " 应被 Jetty 拒绝：" + response);
            assertTrue(handled.isEmpty(), path + " 不应命中 handler：" + handled);
            assertTrue(filterSawCanonical.isEmpty(), path + " 不应进入过滤器");
        }
    }

    @Test
    void exemptPathsUnderEquivalentSpellingsLandBackOnTheExemptCanonicalPath() throws Exception {
        // 豁免清单也按规范化路径匹配：/./api/replay/nonce 必须落回豁免路径，否则客户端连 nonce 都领不到
        send("/./api/replay/nonce");
        assertFalse(filterSawCanonical.isEmpty());
        assertTrue(filterSawCanonical.contains("/api/replay/nonce"),
                "规范化后应落回豁免路径：" + filterSawCanonical);
        assertFalse(ReplayProtectionFilter.isProtected("/api/replay/nonce", "GET", null));
    }
}
