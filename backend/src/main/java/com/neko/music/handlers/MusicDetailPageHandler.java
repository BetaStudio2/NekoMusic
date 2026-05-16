package com.neko.music.handlers;

import com.neko.music.seo.MusicDetailPageRenderer;
import com.neko.music.util.PublicMusicLookup;
import com.neko.music.util.SiteUrlResolver;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为 /detail/{id} 返回含歌曲 meta 与正文的服务端 HTML（curl / 爬虫无需执行 JS）。
 */
public class MusicDetailPageHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicDetailPageHandler.class);
    private static final Pattern ID_PATTERN = Pattern.compile("^/?([0-9]+)/?$");

    private final MusicDetailPageRenderer renderer = new MusicDetailPageRenderer();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
        response.setHeader("Cache-Control", "public, max-age=300");
        sendHtml(response, HttpStatus.OK_200, html);
    }

    private static void sendHtml(HttpServletResponse response, int status, String html) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/html;charset=utf-8");
        response.getWriter().write(html);
    }
}
