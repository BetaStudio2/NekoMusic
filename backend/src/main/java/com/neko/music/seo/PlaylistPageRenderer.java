package com.neko.music.seo;

import com.neko.music.util.PublicPlaylistLookup.PublicPlaylist;
import com.neko.music.util.PublicPlaylistLookup.Track;

/**
 * 生成可在无 JS 环境下被 curl / 爬虫 / 生成式引擎直接读取的歌单页 HTML。
 *
 * <p>与 {@link MusicDetailPageRenderer} 成对：{@code PlaylistDetailPageHandler} 只在
 * 请求来自爬虫时调用本类，普通浏览器仍拿到 SPA 外壳。</p>
 */
public final class PlaylistPageRenderer {

    /** 正文里列出的曲目上限，避免超大歌单生成超长 HTML。 */
    private static final int MAX_TRACKS_IN_HTML = 100;

    public String render(PublicPlaylist playlist, String siteBaseUrl) {
        PlaylistSeoContent c = PlaylistSeoContent.from(playlist, siteBaseUrl);
        String jsonLd = PlaylistJsonLdBuilder.build(c);
        return html(c, jsonLd);
    }

    public String renderNotFound(String siteBaseUrl) {
        String base = siteBaseUrl == null ? "" : siteBaseUrl.replaceAll("/+$", "");
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>歌单不存在 - Neko歌姬计划 | Neko Music</title>
    <meta name="robots" content="noindex, follow">
    <link rel="canonical" href="%s/">
    <link rel="icon" href="/favicon.ico">
</head>
<body>
    <main id="main-content">
        <h1>歌单不存在</h1>
        <p>该歌单可能已被删除或从未存在。可返回 <a href="%s/">首页 / Web 播放器</a>继续浏览。</p>
    </main>
</body>
</html>
""".formatted(base, base);
    }

    private static String html(PlaylistSeoContent c, String jsonLd) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"zh-CN\" prefix=\"og: https://ogp.me/ns# music: http://ogp.me/ns/music#\">\n<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("    <title>").append(c.esc(c.pageTitle)).append("</title>\n");
        sb.append("    <meta name=\"description\" content=\"").append(c.esc(c.metaDescription)).append("\">\n");
        sb.append("    <meta name=\"keywords\" content=\"").append(c.esc(c.metaKeywords)).append("\">\n");
        sb.append("    <meta name=\"author\" content=\"").append(c.esc(c.creator)).append("\">\n");
        sb.append("    <meta name=\"publisher\" content=\"Neko歌姬计划 / Neko Music\">\n");
        sb.append("    <meta name=\"robots\" content=\"index, follow, max-image-preview:large, max-snippet:-1\">\n");
        sb.append("    <meta name=\"theme-color\" content=\"#04090b\">\n");
        sb.append("    <meta name=\"application-name\" content=\"Neko Music\">\n");
        sb.append("    <link rel=\"canonical\" href=\"").append(c.esc(c.pageUrl)).append("\">\n");
        sb.append("    <link rel=\"alternate\" hreflang=\"zh-CN\" href=\"").append(c.esc(c.pageUrl)).append("\">\n");
        sb.append("    <link rel=\"alternate\" hreflang=\"en\" href=\"").append(c.esc(c.pageUrl)).append("\">\n");
        sb.append("    <link rel=\"alternate\" hreflang=\"x-default\" href=\"").append(c.esc(c.pageUrl)).append("\">\n");
        sb.append("    <link rel=\"icon\" href=\"/favicon.ico\">\n");
        sb.append("    <link rel=\"preconnect\" href=\"").append(c.esc(c.siteBase)).append("\">\n");
        sb.append("    <meta property=\"og:type\" content=\"music.playlist\">\n");
        sb.append("    <meta property=\"og:url\" content=\"").append(c.esc(c.pageUrl)).append("\">\n");
        sb.append("    <meta property=\"og:title\" content=\"").append(c.esc(c.pageTitle)).append("\">\n");
        sb.append("    <meta property=\"og:description\" content=\"").append(c.esc(c.metaDescription)).append("\">\n");
        sb.append("    <meta property=\"og:image\" content=\"").append(c.esc(c.coverUrl)).append("\">\n");
        sb.append("    <meta property=\"og:site_name\" content=\"Neko歌姬计划 / Neko Music\">\n");
        sb.append("    <meta property=\"og:locale\" content=\"zh_CN\">\n");
        sb.append("    <meta property=\"og:locale:alternate\" content=\"en_US\">\n");
        sb.append("    <meta name=\"twitter:card\" content=\"summary_large_image\">\n");
        sb.append("    <meta name=\"twitter:title\" content=\"").append(c.esc(c.pageTitle)).append("\">\n");
        sb.append("    <meta name=\"twitter:description\" content=\"").append(c.esc(c.metaDescription)).append("\">\n");
        sb.append("    <meta name=\"twitter:image\" content=\"").append(c.esc(c.coverUrl)).append("\">\n");
        sb.append("    <script type=\"application/ld+json\">").append(jsonLd).append("</script>\n");
        sb.append("    <style>\n");
        sb.append("        .skip-link{position:absolute;left:-9999px;top:0;z-index:999;padding:8px 16px;background:#04090b;color:#fff}\n");
        sb.append("        .skip-link:focus{left:8px;top:8px}\n");
        sb.append("        body{font-family:system-ui,'PingFang SC','Microsoft YaHei',sans-serif;line-height:1.6;max-width:52rem;margin:0 auto;padding:1rem;color:#222}\n");
        sb.append("        header nav,footer nav{margin-bottom:1rem;font-size:.95rem}\n");
        sb.append("        h1{font-size:1.75rem;margin:.25rem 0}\n");
        sb.append("        .subtitle{color:#555;font-size:1.05rem;margin:.25rem 0}\n");
        sb.append("        figure{margin:1rem 0}\n");
        sb.append("        figure img{border-radius:12px;max-width:280px;height:auto}\n");
        sb.append("        section{margin:1.25rem 0;padding:1rem;border:1px solid #e8e8f0;border-radius:8px}\n");
        sb.append("        section h2{font-size:1.15rem;margin:0 0 .5rem}\n");
        sb.append("        ol.tracks{padding-left:1.5rem}\n");
        sb.append("        ol.tracks li{margin:.3rem 0}\n");
        sb.append("        .muted{color:#777;font-size:.9rem}\n");
        sb.append("    </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("    <a class=\"skip-link\" href=\"#main-content\">Skip to content / 跳到正文</a>\n");
        sb.append("    <header>\n        <nav aria-label=\"面包屑 Breadcrumb\">\n");
        sb.append("            <a href=\"").append(c.esc(c.siteBase)).append("/\">Neko歌姬计划 / Neko Music</a>");
        sb.append(" › <a href=\"").append(c.esc(c.searchUrl)).append("\">搜索 Search</a>");
        sb.append(" › <span>").append(c.esc(c.name)).append("</span>\n");
        sb.append("        </nav>\n    </header>\n");
        sb.append("    <main id=\"main-content\">\n");
        sb.append("        <article itemscope itemtype=\"https://schema.org/MusicPlaylist\" itemid=\"").append(c.esc(c.pageUrl)).append("#playlist\">\n");
        sb.append("            <h1 itemprop=\"name\">").append(c.esc(c.name)).append("</h1>\n");
        sb.append("            <p class=\"subtitle\">创建者 <span itemprop=\"creator\">").append(c.esc(c.creator)).append("</span>");
        sb.append(" · <span itemprop=\"numTracks\">").append(c.musicCount > 0 ? c.musicCount : c.trackCount).append("</span> 首</p>\n");
        sb.append("            <figure>\n");
        sb.append("                <img itemprop=\"image\" src=\"").append(c.esc(c.coverUrl)).append("\" alt=\"").append(c.esc(c.name)).append(" 封面\" width=\"280\" height=\"280\" loading=\"lazy\">\n");
        sb.append("            </figure>\n");
        sb.append("            <section lang=\"zh-CN\" aria-labelledby=\"sec-zh\">\n");
        sb.append("                <h2 id=\"sec-zh\">歌单简介</h2>\n");
        sb.append("                <p itemprop=\"description\">").append(c.esc(c.description.isEmpty() ? "该歌单暂无简介。" : c.description)).append("</p>\n");
        sb.append("            </section>\n");
        sb.append("            <section lang=\"en\" aria-labelledby=\"sec-en\">\n");
        sb.append("                <h2 id=\"sec-en\">Playlist info</h2>\n");
        sb.append("                <p>").append(c.esc(c.name)).append(" — a free playlist on Neko Music, created by ").append(c.esc(c.creator)).append(".</p>\n");
        sb.append("            </section>\n");
        appendTracks(sb, c);
        sb.append("            <footer class=\"track-actions\">\n");
        sb.append("                <nav aria-label=\"歌单操作\">\n");
        sb.append("                    <a href=\"").append(c.esc(c.pageUrl)).append("\">▶ 在 Web 播放器打开 Open in player</a>");
        sb.append(" · <a href=\"").append(c.esc(c.searchUrl)).append("\">🔍 搜索相关 Search related</a>\n");
        sb.append("                </nav>\n            </footer>\n");
        sb.append("        </article>\n    </main>\n");
        sb.append("    <footer>\n        <p class=\"muted\">Neko歌姬计划（Neko Music）· 永久免费、开源、无广告。</p>\n    </footer>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private static void appendTracks(StringBuilder sb, PlaylistSeoContent c) {
        if (c.tracks.isEmpty()) {
            sb.append("            <section aria-labelledby=\"sec-tracks\">\n");
            sb.append("                <h2 id=\"sec-tracks\">曲目列表 / Tracks</h2>\n");
            sb.append("                <p>该歌单暂时没有曲目。</p>\n");
            sb.append("            </section>\n");
            return;
        }
        int limit = Math.min(MAX_TRACKS_IN_HTML, c.tracks.size());
        sb.append("            <section aria-labelledby=\"sec-tracks\">\n");
        sb.append("                <h2 id=\"sec-tracks\">曲目列表 / Tracks（").append(c.tracks.size()).append("）</h2>\n");
        sb.append("                <ol class=\"tracks\" itemprop=\"track\">\n");
        for (int i = 0; i < limit; i++) {
            Track t = c.tracks.get(i);
            String detailUrl = c.siteBase + "/detail/" + t.id;
            sb.append("                    <li><a href=\"").append(c.esc(detailUrl)).append("\">")
                    .append(c.esc(t.title == null ? "" : t.title)).append(" - ")
                    .append(c.esc(t.artist == null ? "" : t.artist)).append("</a></li>\n");
        }
        sb.append("                </ol>\n");
        if (c.tracks.size() > limit) {
            sb.append("                <p class=\"muted\">仅列出前 ").append(limit).append(" 首，共 ").append(c.tracks.size()).append(" 首。</p>\n");
        }
        sb.append("            </section>\n");
    }
}
