package com.neko.music.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.util.HtmlEscaper;
import com.neko.music.util.MusicAssetLocator;
import com.neko.music.util.PublicMusicLookup.PublicMusic;

/** 生成可在无 JS 环境下被 curl / 爬虫直接读取的音乐详情 HTML */
public final class MusicDetailPageRenderer {
    private static final String SITE_NAME = "Neko云音乐";
    private static final ObjectMapper JSON = new ObjectMapper();

    public String render(PublicMusic music, String siteBaseUrl) {
        String base = trimTrailingSlash(siteBaseUrl);
        String pageUrl = base + "/detail/" + music.id;
        String coverUrl = base + MusicAssetLocator.coverApiUrl(music.id);

        String title = nullToDefault(music.title, "未知歌曲");
        String artist = nullToDefault(music.artist, "未知艺术家");
        String album = music.album != null ? music.album.trim() : "";

        String pageTitle = escape(title) + " - " + escape(artist) + " | 免费在线播放 | " + SITE_NAME;
        String albumPart = album.isEmpty() ? "" : "，专辑《" + escape(album) + "》";
        String description = "免费在线播放《" + escape(title) + "》— " + escape(artist) + albumPart
                + "。在 " + SITE_NAME + " 收听高品质音频，支持收藏与分享。";
        String keywords = escape(title) + "," + escape(artist)
                + (album.isEmpty() ? "" : "," + escape(album))
                + ",免费音乐,在线播放,Neko云音乐";

        String jsonLd = buildMusicJsonLd(music, base, pageUrl, coverUrl, title, artist, album);

        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>%s</title>
    <meta name="description" content="%s">
    <meta name="keywords" content="%s">
    <meta name="robots" content="index, follow">
    <link rel="canonical" href="%s">
    <meta property="og:type" content="music.song">
    <meta property="og:url" content="%s">
    <meta property="og:title" content="%s">
    <meta property="og:description" content="%s">
    <meta property="og:image" content="%s">
    <meta property="og:site_name" content="%s">
    <meta property="og:locale" content="zh_CN">
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:url" content="%s">
    <meta name="twitter:title" content="%s">
    <meta name="twitter:description" content="%s">
    <meta name="twitter:image" content="%s">
    <link rel="icon" href="/favicon.ico">
    <script type="application/ld+json">%s</script>
</head>
<body>
    <main id="main-content">
        <article itemscope itemtype="https://schema.org/MusicRecording">
            <h1 itemprop="name">%s</h1>
            <p><span>艺术家：</span><span itemprop="byArtist">%s</span></p>
            %s
            <p><img src="%s" alt="%s 封面" width="320" height="320" itemprop="image" loading="eager"></p>
            <p>在 <a href="%s">%s</a> 免费在线播放、收藏与分享。</p>
        </article>
    </main>
</body>
</html>
""".formatted(
                pageTitle,
                description,
                keywords,
                pageUrl,
                pageUrl,
                escape(title) + " - " + escape(artist),
                description,
                coverUrl,
                SITE_NAME,
                pageUrl,
                escape(title) + " - " + escape(artist),
                description,
                coverUrl,
                jsonLd,
                escape(title),
                escape(artist),
                album.isEmpty() ? "" : "<p><span>专辑：</span><span itemprop=\"inAlbum\">" + escape(album) + "</span></p>\n            ",
                coverUrl,
                escape(title),
                base,
                SITE_NAME
        );
    }

    public String renderNotFound(String siteBaseUrl) {
        String base = trimTrailingSlash(siteBaseUrl);
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>音乐不存在 - %s</title>
    <meta name="robots" content="noindex, nofollow">
    <link rel="canonical" href="%s/">
</head>
<body>
    <main><h1>音乐不存在</h1><p><a href="%s/">返回首页</a></p></main>
</body>
</html>
""".formatted(SITE_NAME, base, base);
    }

    private static String buildMusicJsonLd(PublicMusic music, String base, String pageUrl, String coverUrl,
                                           String title, String artist, String album) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("@context", "https://schema.org");
            root.put("@type", "MusicRecording");
            root.put("name", title);
            root.put("url", pageUrl);
            root.put("image", coverUrl);
            if (music.duration > 0) {
                root.put("duration", "PT" + music.duration + "S");
            }
            ObjectNode byArtist = root.putObject("byArtist");
            byArtist.put("@type", "MusicGroup");
            byArtist.put("name", artist);
            if (album != null && !album.isBlank()) {
                ObjectNode inAlbum = root.putObject("inAlbum");
                inAlbum.put("@type", "MusicAlbum");
                inAlbum.put("name", album);
            }
            ObjectNode publisher = root.putObject("publisher");
            publisher.put("@type", "Organization");
            publisher.put("name", SITE_NAME);
            publisher.put("url", base);
            ObjectNode action = root.putObject("potentialAction");
            action.put("@type", "ListenAction");
            action.put("target", pageUrl);
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String escape(String s) {
        return HtmlEscaper.escape(s);
    }

    private static String nullToDefault(String s, String def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        return s.trim();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
