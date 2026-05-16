package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.util.HtmlEscaper;
import com.neko.music.util.SiteUrlResolver;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 站点完整 sitemap（固定页 + 全部 /detail/{id}） */
public class SitemapHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(SitemapHandler.class);
    private static final int MAX_MUSIC_URLS = 50_000;
    private static final DateTimeFormatter LASTMOD = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String siteBase = SiteUrlResolver.resolvePublicSiteBase(request);
        String today = LASTMOD.format(Instant.now());
        List<MusicEntry> music = loadMusicEntries();
        String xml = buildXml(siteBase, today, music);

        response.setStatus(HttpStatus.OK_200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/xml;charset=utf-8");
        response.setHeader("Cache-Control", "public, max-age=3600");
        response.getWriter().write(xml);
    }

    private List<MusicEntry> loadMusicEntries() {
        List<MusicEntry> list = new ArrayList<>();
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, updated_at FROM music ORDER BY updated_at DESC LIMIT ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, MAX_MUSIC_URLS);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    MusicEntry e = new MusicEntry();
                    e.id = rs.getInt("id");
                    Timestamp ts = rs.getTimestamp("updated_at");
                    e.lastmod = ts != null ? LASTMOD.format(ts.toInstant()) : LASTMOD.format(Instant.now());
                    list.add(e);
                }
            }
        } catch (Exception e) {
            logger.error("生成 sitemap 时查询音乐失败", e);
        }
        return list;
    }

    private static String buildXml(String siteBase, String today, List<MusicEntry> musicEntries) {
        String base = siteBase.endsWith("/") ? siteBase.substring(0, siteBase.length() - 1) : siteBase;
        StringBuilder sb = new StringBuilder(musicEntries.size() * 120 + 1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(sb, base + "/", today, "weekly", "1.0");
        appendUrl(sb, base + "/search", today, "weekly", "0.9");
        appendUrl(sb, base + "/ranking", today, "daily", "0.85");
        appendUrl(sb, base + "/latest", today, "daily", "0.85");
        appendUrl(sb, base + "/download", today, "monthly", "0.7");

        for (MusicEntry e : musicEntries) {
            appendUrl(sb, base + "/detail/" + e.id, e.lastmod, "weekly", "0.8");
        }

        sb.append("</urlset>\n");
        return sb.toString();
    }

    private static void appendUrl(StringBuilder sb, String loc, String lastmod, String changefreq, String priority) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(HtmlEscaper.escape(loc)).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private static final class MusicEntry {
        int id;
        String lastmod;
    }
}
