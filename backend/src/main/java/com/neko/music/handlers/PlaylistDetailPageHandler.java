package com.neko.music.handlers;

import com.neko.music.seo.BrowserEvidence;
import com.neko.music.seo.PlaylistPageRenderer;
import com.neko.music.seo.UserAgentClassifier;
import com.neko.music.util.PublicPlaylistLookup;
import com.neko.music.util.SiteUrlResolver;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为爬虫与链接预览返回含歌单元数据与曲目列表的服务端 HTML；普通浏览器转发到 SPA。
 *
 * <p>与 {@code MusicDetailPageHandler} 同构：URL 有意存在两种表现，因此先声明
 * {@code Vary: User-Agent}，再按 UA 分支，避免 CDN 把爬虫版页面喂给浏览器。</p>
 */
public class PlaylistDetailPageHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistDetailPageHandler.class);
    private static final Pattern ID_PATTERN = Pattern.compile("^/?([0-9]+)/?$");
    /** SEO 渲染时读取的曲目上限 */
    private static final int MAX_TRACKS = 200;

    private final PlaylistPageRenderer renderer = new PlaylistPageRenderer();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        response.setHeader("Vary", "User-Agent");

        String pathInfo = request.getPathInfo();
        // /playlist/create 等非数字路径不是歌单详情页，交回 SPA 处理。
        Matcher matcher = pathInfo == null ? null : ID_PATTERN.matcher(pathInfo);
        if (matcher == null || !matcher.matches() || !shouldRenderSeo(request)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }

        // UA 相关，且歌单内容随时会被创建者修改：SEO 分支一律不缓存。
        response.setHeader("Cache-Control", "private, no-store");

        int playlistId;
        try {
            playlistId = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            sendHtml(response, HttpStatus.NOT_FOUND_404,
                    renderer.renderNotFound(SiteUrlResolver.resolvePublicSiteBase(request)));
            return;
        }

        String siteBase = SiteUrlResolver.resolvePublicSiteBase(request);
        var playlistOpt = PublicPlaylistLookup.findById(playlistId, MAX_TRACKS);
        if (playlistOpt.isEmpty()) {
            logger.debug("歌单页 HTML: 歌单不存在 id={}", playlistId);
            sendHtml(response, HttpStatus.NOT_FOUND_404, renderer.renderNotFound(siteBase));
            return;
        }

        sendHtml(response, HttpStatus.OK_200, renderer.render(playlistOpt.get(), siteBase));
    }

    /** 结合浏览器特征头判定：UA 像浏览器但缺少特征头的（伪造）也走 SEO。 */
    static boolean shouldRenderSeo(HttpServletRequest request) {
        return UserAgentClassifier.shouldRenderSeo(
                request.getHeader("User-Agent"), BrowserEvidence.hasFetchEvidence(request));
    }

    static boolean shouldRenderSeoByUserAgent(String userAgent) {
        return UserAgentClassifier.shouldRenderSeo(userAgent);
    }

    private static void sendHtml(HttpServletResponse response, int status, String html) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/html;charset=utf-8");
        response.getWriter().write(html);
    }
}
