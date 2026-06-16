package com.neko.music.seo;

import com.neko.music.util.PublicMusicLookup.PublicMusic;

/** 生成可在无 JS 环境下被 curl / 爬虫直接读取的双语音乐详情 HTML */
public final class MusicDetailPageRenderer {

    public String render(PublicMusic music, String siteBaseUrl) {
        MusicDetailSeoContent c = MusicDetailSeoContent.from(music, siteBaseUrl);
        String jsonLd = MusicDetailJsonLdBuilder.build(c, music);

        String albumZh = c.album.isEmpty()
                ? ""
                : "    <p><span>专辑 Album：</span><span itemprop=\"inAlbum\">" + c.esc(c.album) + "</span></p>\n";
        String albumEn = c.album.isEmpty()
                ? ""
                : "    <p><span>Album：</span><span>" + c.esc(c.album) + "</span></p>\n";
        String durationZh = c.durationZh.isEmpty()
                ? ""
                : "    <p><span>时长 Duration：</span><time itemprop=\"duration\" datetime=\"PT" + c.durationSec + "S\">"
                        + c.esc(c.durationZh) + " / " + c.esc(c.durationEn) + "</time></p>\n";
        String tagsBlock = c.tags.isEmpty()
                ? ""
                : "    <p><span>标签 Tags：</span>" + c.esc(c.tags) + "</p>\n";
        String langBlock = c.language.isEmpty()
                ? ""
                : "    <p><span>语言 Language：</span>" + c.esc(c.language) + "</p>\n";
        String audioBlock = """
    <p>
      <a href="%s" rel="nofollow">▶ 在线播放 Stream online</a>
      · <a href="%s">🔍 搜索相关 Search related</a>
    </p>
""".formatted(c.pageUrl, c.searchUrl);

        return """
<!DOCTYPE html>
<html lang="zh-CN" prefix="og: https://ogp.me/ns# music: http://ogp.me/ns/music#">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>%s</title>
    <meta name="description" lang="zh-CN" content="%s">
    <meta name="description" lang="en" content="%s">
    <meta name="keywords" content="%s">
    <meta name="author" content="NekoMusic">
    <meta name="publisher" content="Neko歌姬计划 / Neko Music">
    <meta name="robots" content="index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1">
    <meta name="googlebot" content="index, follow">
    <meta name="bingbot" content="index, follow">
    <meta name="theme-color" content="#6a5acd">
    <style>
        .skip-link{position:absolute;left:-9999px;top:0;z-index:999;padding:8px 16px;background:#6a5acd;color:#fff}
        .skip-link:focus{left:8px;top:8px}
        body{font-family:system-ui,sans-serif;line-height:1.6;max-width:52rem;margin:0 auto;padding:1rem;color:#222}
        header nav,footer nav{margin-bottom:1rem;font-size:.95rem}
        h1{font-size:1.75rem;margin:.25rem 0}
        .subtitle{color:#555;font-size:1.1rem}
        figure img{border-radius:12px;max-width:100%%;height:auto}
        section{margin:1.25rem 0;padding:1rem;border:1px solid #e8e8f0;border-radius:8px}
        section h2{font-size:1.15rem;margin:0 0 .5rem}
    </style>
    <meta name="application-name" content="Neko Music">
    <link rel="canonical" href="%s">
    <link rel="alternate" hreflang="zh-CN" href="%s">
    <link rel="alternate" hreflang="en" href="%s">
    <link rel="alternate" hreflang="x-default" href="%s">
    <link rel="icon" href="/favicon.ico">
    <link rel="preconnect" href="%s">
    <link rel="dns-prefetch" href="%s">
    <meta property="og:type" content="music.song">
    <meta property="og:url" content="%s">
    <meta property="og:title" content="%s">
    <meta property="og:description" content="%s">
    <meta property="og:image" content="%s">
    <meta property="og:image:alt" content="%s - %s cover | 封面">
    <meta property="og:site_name" content="%s / %s">
    <meta property="og:locale" content="zh_CN">
    <meta property="og:locale:alternate" content="en_US">
    <meta property="og:audio" content="%s">
    <meta property="og:audio:type" content="audio/mpeg">
    <meta property="og:audio:secure_url" content="%s">
    <meta property="music:musician" content="%s">
    %s
    %s
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:url" content="%s">
    <meta name="twitter:title" content="%s">
    <meta name="twitter:description" content="%s">
    <meta name="twitter:image" content="%s">
    <meta name="twitter:image:alt" content="%s - %s">
    <script type="application/ld+json">%s</script>
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to content / 跳到正文</a>
    <header>
        <nav aria-label="Site navigation">
            <a href="%s/">%s</a> |
            <a href="%s/search">搜索 Search</a> |
            <a href="%s/ranking">排行榜 Ranking</a> |
            <a href="%s/latest">最新 Latest</a>
        </nav>
    </header>
    <main id="main-content">
        <nav aria-label="Breadcrumb">
            <ol itemscope itemtype="https://schema.org/BreadcrumbList">
                <li itemprop="itemListElement" itemscope itemtype="https://schema.org/ListItem">
                    <a itemprop="item" href="%s/"><span itemprop="name">首页 Home</span></a>
                    <meta itemprop="position" content="1">
                </li>
                <li itemprop="itemListElement" itemscope itemtype="https://schema.org/ListItem">
                    <a itemprop="item" href="%s"><span itemprop="name">%s</span></a>
                    <meta itemprop="position" content="2">
                </li>
                <li itemprop="itemListElement" itemscope itemtype="https://schema.org/ListItem">
                    <span itemprop="name">%s - %s</span>
                    <meta itemprop="position" content="3">
                </li>
            </ol>
        </nav>
        <article itemscope itemtype="https://schema.org/MusicRecording" itemid="%s#recording">
            <meta itemprop="url" content="%s">
            <header>
                <h1 itemprop="name">%s</h1>
                <p class="subtitle" itemprop="byArtist" itemscope itemtype="https://schema.org/MusicGroup">
                    <span itemprop="name">%s</span>
                </p>
            </header>
            <figure>
                <img src="%s" alt="%s - %s | %s cover 封面" width="480" height="480"
                     itemprop="image" loading="eager" fetchpriority="high">
            </figure>
%s
            <section lang="zh-CN" aria-labelledby="sec-zh">
                <h2 id="sec-zh">歌曲信息</h2>
                <p>在 <strong>%s</strong> 免费在线播放高品质音频，支持歌词同步、收藏与分享链接。</p>
%s%s%s%s
            </section>
            <section lang="en" aria-labelledby="sec-en">
                <h2 id="sec-en">Track info</h2>
                <p>Stream in high quality for <strong>free</strong> on <strong>%s</strong> — lyrics, favorites, and shareable links.</p>
%s%s%s%s
            </section>
            <footer class="track-actions">
%s                <p><small>Music ID: %d · <a href="%s">Permanent link 永久链接</a></small></p>
            </footer>
        </article>
    </main>
    <footer>
        <p>© %s — Free open-source online music / 免费开源在线音乐</p>
    </footer>
</body>
</html>
""".formatted(
                c.esc(c.pageTitle),
                c.esc(c.metaDescription),
                c.esc(c.metaDescription),
                c.esc(c.metaKeywords),
                c.pageUrl,
                c.pageUrl,
                c.pageUrl,
                c.pageUrl,
                c.siteBase,
                c.siteBase,
                c.pageUrl,
                c.esc(c.ogTitle),
                c.esc(c.ogDescription),
                c.coverUrl,
                c.esc(c.title),
                c.esc(c.artist),
                MusicDetailSeoContent.SITE_NAME_ZH,
                MusicDetailSeoContent.SITE_NAME_EN,
                c.audioUrl,
                c.audioUrl,
                c.esc(c.artist),
                c.album.isEmpty() ? "" : "<meta property=\"music:album\" content=\"" + c.esc(c.album) + "\">",
                c.durationSec > 0 ? "<meta property=\"music:duration\" content=\"" + c.durationSec + "\">" : "",
                c.pageUrl,
                c.esc(c.twitterTitle),
                c.esc(c.twitterDescription),
                c.coverUrl,
                c.esc(c.title),
                c.esc(c.artist),
                jsonLd,
                c.siteBase,
                MusicDetailSeoContent.SITE_NAME_ZH,
                c.siteBase,
                c.siteBase,
                c.siteBase,
                c.siteBase,
                c.searchUrl,
                c.esc(c.title),
                c.esc(c.title),
                c.esc(c.artist),
                c.pageUrl,
                c.pageUrl,
                c.esc(c.title),
                c.esc(c.artist),
                c.coverUrl,
                c.esc(c.title),
                c.esc(c.artist),
                c.esc(c.title),
                audioBlock,
                MusicDetailSeoContent.SITE_NAME_ZH,
                "    <p><span>艺术家 Artist：</span>" + c.esc(c.artist) + "</p>\n",
                albumZh,
                durationZh,
                tagsBlock + langBlock,
                MusicDetailSeoContent.SITE_NAME_EN,
                "    <p><span>Artist：</span>" + c.esc(c.artist) + "</p>\n",
                albumEn,
                durationZh,
                tagsBlock + langBlock,
                audioBlock,
                music.id,
                c.pageUrl,
                MusicDetailSeoContent.SITE_NAME_EN
        );
    }

    public String renderNotFound(String siteBaseUrl) {
        String base = trimTrailingSlash(siteBaseUrl);
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>音乐不存在 Music not found | %s / %s</title>
    <meta name="description" content="该歌曲不存在或已下架。This track was not found on Neko Music.">
    <meta name="robots" content="noindex, nofollow">
    <link rel="canonical" href="%s/">
    <link rel="alternate" hreflang="zh-CN" href="%s/">
    <link rel="alternate" hreflang="en" href="%s/">
</head>
<body>
    <main>
        <h1>音乐不存在</h1>
        <p lang="en">Music not found</p>
        <p><a href="%s/">返回首页 Home</a></p>
    </main>
</body>
</html>
""".formatted(
                MusicDetailSeoContent.SITE_NAME_ZH,
                MusicDetailSeoContent.SITE_NAME_EN,
                base,
                base,
                base,
                base
        );
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
