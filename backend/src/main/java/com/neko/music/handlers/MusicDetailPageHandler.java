package com.neko.music.handlers;

import com.neko.music.seo.MusicDetailPageRenderer;
import com.neko.music.util.PublicMusicLookup;
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
 * 为爬虫和链接预览返回含歌曲 meta 与正文的服务端 HTML；普通浏览器转发到 SPA。
 */
public class MusicDetailPageHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicDetailPageHandler.class);
    private static final Pattern ID_PATTERN = Pattern.compile("^/?([0-9]+)/?$");
    /**
     * Details are server-rendered for crawlers and link previews, but a normal
     * browser must receive the SPA shell so Vue Router can render PlayerView.
     * Keep this list deliberately conservative: non-browser clients (curl,
     * search fetchers, etc.) still get useful HTML for indexing/debugging.
     */
    private static final Pattern CRAWLER_PATTERN = Pattern.compile(
            "(?i)(?:bot|crawler|spider|slurp|bingpreview|facebookexternalhit|facebot|"
                    + "linkedinbot|twitterbot|discordbot|telegrambot|whatsapp|pinterest|"
                    + "bytespider|yandex|baiduspider|sogou|360spider|petalbot|semrush|"
                    + "ahrefs|mj12bot|applebot|google-inspectiontool|curl|wget|httpclient|okhttp)");

    private final MusicDetailPageRenderer renderer = new MusicDetailPageRenderer();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        // This URL intentionally has two representations.  Set Vary before
        // either branch so a proxy cannot reuse the crawler response for a
        // normal browser (or vice versa).
        response.setHeader("Vary", "User-Agent");
        if (!shouldRenderSeo(request.getHeader("User-Agent"))) {
            // Forward internally so the address bar remains /detail/{id}; the
            // SPA then reads that URL and loads the music through its API.
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }

        String siteBase = SiteUrlResolver.resolvePublicSiteBase(request);
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            sendHtml(response, HttpStatus.NOT_FOUND_404, renderer.renderNotFound(siteBase));
            return;
        }

        Matcher matcher = ID_PATTERN.matcher(pathInfo);
        if (!matcher.matches()) {
            sendHtml(response, HttpStatus.NOT_FOUND_404, renderer.renderNotFound(siteBase));
            return;
        }

        int musicId;
        try {
            musicId = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            sendHtml(response, HttpStatus.NOT_FOUND_404, renderer.renderNotFound(siteBase));
            return;
        }

        var musicOpt = PublicMusicLookup.findById(musicId);
        if (musicOpt.isEmpty()) {
            logger.debug("详情页 HTML: 音乐不存在 id={}", musicId);
            sendHtml(response, HttpStatus.NOT_FOUND_404, renderer.renderNotFound(siteBase));
            return;
        }

        String html = renderer.render(musicOpt.get(), siteBase);
        // Do not let a CDN cache this UA-dependent representation and serve it
        // to a browser that requested the SPA shell.
        response.setHeader("Cache-Control", "private, no-store");
        sendHtml(response, HttpStatus.OK_200, html);
    }

    static boolean shouldRenderSeo(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        String normalized = userAgent.trim();
        if (CRAWLER_PATTERN.matcher(normalized).find()) {
            return true;
        }
        // Real browsers conventionally identify themselves with Mozilla. A
        // non-Mozilla client is treated as a fetcher and receives SEO HTML.
        return !normalized.contains("Mozilla/");
    }

    private static void sendHtml(HttpServletResponse response, int status, String html) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/html;charset=utf-8");
        response.getWriter().write(html);
    }
}
