package com.neko.music.seo;

import com.neko.music.util.HtmlEscaper;
import com.neko.music.util.MusicAssetLocator;
import com.neko.music.util.PublicMusicLookup.PublicMusic;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 音乐详情页双语 SEO 文案与 URL */
public final class MusicDetailSeoContent {
    public static final String SITE_NAME_ZH = "Neko歌姬计划";
    public static final String SITE_NAME_EN = "Neko Music";

    public final int musicId;
    public final String siteBase;
    public final String pageUrl;
    public final String coverUrl;
    public final String audioUrl;
    public final String searchUrl;

    public final String title;
    public final String artist;
    public final String album;
    public final String tags;
    public final String language;
    public final int durationSec;
    public final String updatedAt;

    public final String pageTitle;
    public final String metaDescription;
    public final String metaKeywords;
    public final String ogTitle;
    public final String ogDescription;
    public final String twitterTitle;
    public final String twitterDescription;
    public final String durationZh;
    public final String durationEn;

    private MusicDetailSeoContent(
            int musicId, String siteBase, String pageUrl, String coverUrl, String audioUrl, String searchUrl,
            String title, String artist, String album, String tags, String language,
            int durationSec, String updatedAt,
            String pageTitle, String metaDescription, String metaKeywords,
            String ogTitle, String ogDescription, String twitterTitle, String twitterDescription,
            String durationZh, String durationEn) {
        this.musicId = musicId;
        this.siteBase = siteBase;
        this.pageUrl = pageUrl;
        this.coverUrl = coverUrl;
        this.audioUrl = audioUrl;
        this.searchUrl = searchUrl;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.tags = tags;
        this.language = language;
        this.durationSec = durationSec;
        this.updatedAt = updatedAt;
        this.pageTitle = pageTitle;
        this.metaDescription = metaDescription;
        this.metaKeywords = metaKeywords;
        this.ogTitle = ogTitle;
        this.ogDescription = ogDescription;
        this.twitterTitle = twitterTitle;
        this.twitterDescription = twitterDescription;
        this.durationZh = durationZh;
        this.durationEn = durationEn;
    }

    public static MusicDetailSeoContent from(PublicMusic music, String siteBaseUrl) {
        String base = trimSlash(siteBaseUrl);
        int id = music.id;
        String pageUrl = base + "/detail/" + id;
        String coverUrl = base + MusicAssetLocator.coverApiUrl(id);
        String audioUrl = base + MusicAssetLocator.fileApiUrl(id);

        String title = blank(music.title, "未知歌曲");
        String artist = blank(music.artist, "未知艺术家");
        String album = music.album != null ? music.album.trim() : "";
        String tags = music.tags != null ? music.tags.trim() : "";
        String language = music.language != null ? music.language.trim() : "";
        String updatedAt = music.updatedAt != null ? music.updatedAt : "";

        String searchQuery = URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8);
        String searchUrl = base + "/search/" + searchQuery;

        String albumZh = album.isEmpty() ? "" : "，专辑《" + album + "》";
        String albumEn = album.isEmpty() ? "" : ", album \"" + album + "\"";
        String durationZh = formatDurationZh(music.duration);
        String durationEn = formatDurationEn(music.duration);

        String descZh = "免费在线播放《" + title + "》— " + artist + albumZh
                + (durationZh.isEmpty() ? "" : "，时长 " + durationZh)
                + "。Neko歌姬计划提供高品质流媒体、歌词、收藏与分享，永久免费。";
        String descEn = "Stream \"" + title + "\" by " + artist + albumEn
                + (durationEn.isEmpty() ? "" : ", " + durationEn)
                + " free in HD on Neko Music — online player, lyrics, favorites, no paywall.";
        String metaDescription = descZh + " | " + descEn;

        String pageTitle = title + " - " + artist + " | "
                + title + " - " + artist + " Free Online | "
                + SITE_NAME_ZH + " / " + SITE_NAME_EN;

        String ogTitle = title + " - " + artist + " | " + SITE_NAME_ZH + " · " + SITE_NAME_EN;
        String ogDescription = metaDescription;

        String keywordCore = title + "," + artist
                + (album.isEmpty() ? "" : "," + album)
                + ",免费在线播放,free music stream,在线听歌,listen online,"
                + title + " " + artist + " 歌词," + title + " " + artist + " lyrics,"
                + SITE_NAME_ZH + "," + SITE_NAME_EN + ",NekoMusic,免费音乐,free music";
        if (!tags.isEmpty()) {
            keywordCore += "," + tags;
        }
        if (!language.isEmpty()) {
            keywordCore += "," + language;
        }
        keywordCore += ",音乐详情,music detail,歌曲,song,MP3,FLAC,stream";

        return new MusicDetailSeoContent(
                id, base, pageUrl, coverUrl, audioUrl, searchUrl,
                title, artist, album, tags, language, music.duration, updatedAt,
                pageTitle, metaDescription, keywordCore,
                ogTitle, ogDescription, ogTitle, ogDescription,
                durationZh, durationEn
        );
    }

    public String esc(String s) {
        return HtmlEscaper.escape(s);
    }

    private static String blank(String s, String def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        return s.trim();
    }

    private static String trimSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String formatDurationZh(int sec) {
        if (sec <= 0) {
            return "";
        }
        int m = sec / 60;
        int s = sec % 60;
        if (m > 0) {
            return m + "分" + s + "秒";
        }
        return s + "秒";
    }

    private static String formatDurationEn(int sec) {
        if (sec <= 0) {
            return "";
        }
        int m = sec / 60;
        int s = sec % 60;
        if (m > 0) {
            return m + " min " + s + " sec";
        }
        return s + " sec";
    }
}
