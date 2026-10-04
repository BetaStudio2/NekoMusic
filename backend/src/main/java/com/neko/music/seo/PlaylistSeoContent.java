package com.neko.music.seo;

import com.neko.music.util.HtmlEscaper;
import com.neko.music.util.MusicAssetLocator;
import com.neko.music.util.PublicPlaylistLookup.PublicPlaylist;
import com.neko.music.util.PublicPlaylistLookup.Track;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 歌单页（{@code /playlist/{id}}）双语 SEO 文案与 URL。 */
public final class PlaylistSeoContent {
    public static final String SITE_NAME_ZH = "Neko歌姬计划";
    public static final String SITE_NAME_EN = "Neko Music";

    /** 出现在描述里的曲目名上限 */
    private static final int DESC_TRACK_SAMPLE = 5;

    public final int id;
    public final String siteBase;
    public final String pageUrl;
    public final String searchUrl;
    public final String coverUrl;
    public final String name;
    public final String description;
    public final String creator;
    public final int musicCount;
    public final int trackCount;
    public final String updatedAt;
    public final List<Track> tracks;
    public final String pageTitle;
    public final String metaDescription;
    public final String metaKeywords;

    private PlaylistSeoContent(int id, String siteBase, String pageUrl, String searchUrl, String coverUrl,
                               String name, String description, String creator, int musicCount, int trackCount,
                               String updatedAt, List<Track> tracks,
                               String pageTitle, String metaDescription, String metaKeywords) {
        this.id = id;
        this.siteBase = siteBase;
        this.pageUrl = pageUrl;
        this.searchUrl = searchUrl;
        this.coverUrl = coverUrl;
        this.name = name;
        this.description = description;
        this.creator = creator;
        this.musicCount = musicCount;
        this.trackCount = trackCount;
        this.updatedAt = updatedAt;
        this.tracks = tracks;
        this.pageTitle = pageTitle;
        this.metaDescription = metaDescription;
        this.metaKeywords = metaKeywords;
    }

    public static PlaylistSeoContent from(PublicPlaylist playlist, String siteBaseUrl) {
        String base = trimSlash(siteBaseUrl);
        String pageUrl = base + "/playlist/" + playlist.id;
        String name = blank(playlist.name, "未命名歌单");
        String description = blank(playlist.description, "");
        String creator = blank(playlist.creatorName, "Neko 用户");

        int trackCount = playlist.tracks.size();
        int shownCount = playlist.musicCount > 0 ? playlist.musicCount : trackCount;
        String coverUrl = trackCount > 0
                ? base + MusicAssetLocator.coverApiUrl(playlist.tracks.get(0).id)
                : base + "/og-image.jpg";

        String searchUrl = base + "/search?q="
                + URLEncoder.encode(name, StandardCharsets.UTF_8);

        String trackSample = sampleTracks(playlist.tracks);
        String countZh = shownCount > 0 ? "共 " + shownCount + " 首" : "曲目整理中";
        String descZh = "免费在线收听歌单《" + name + "》— " + creator + " 创建，" + countZh
                + (trackSample.isEmpty() ? "" : "，包含 " + trackSample + " 等曲目")
                + "。Neko歌姬计划提供高品质流媒体、歌词与收藏，永久免费。";
        String descEn = "Listen to the playlist \"" + name + "\" by " + creator
                + " free on Neko Music — online streaming, lyrics and favorites, no paywall.";

        String pageTitle = name + " - 歌单 Playlist | " + creator + " | "
                + SITE_NAME_ZH + " / " + SITE_NAME_EN;

        String keywords = name + ",歌单,playlist," + creator + ","
                + "免费在线播放,free music stream,在线听歌,listen online," + SITE_NAME_ZH + ","
                + SITE_NAME_EN + ",NekoMusic,免费音乐,free music";
        if (!description.isEmpty()) {
            keywords += "," + description;
        }

        return new PlaylistSeoContent(
                playlist.id, base, pageUrl, searchUrl, coverUrl,
                name, description, creator, shownCount, trackCount, playlist.updatedAt, playlist.tracks,
                pageTitle, descZh + " | " + descEn, keywords);
    }

    /** 取前若干首曲名拼成一句，用于 meta description。 */
    private static String sampleTracks(List<Track> tracks) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(DESC_TRACK_SAMPLE, tracks.size());
        for (int i = 0; i < limit; i++) {
            String title = blank(tracks.get(i).title, "");
            if (title.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(title);
        }
        if (tracks.size() > limit) {
            sb.append(" 等");
        }
        return sb.toString();
    }

    public String esc(String s) {
        return HtmlEscaper.escape(s);
    }

    private static String blank(String s, String def) {
        return (s == null || s.isBlank()) ? def : s.trim();
    }

    private static String trimSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
