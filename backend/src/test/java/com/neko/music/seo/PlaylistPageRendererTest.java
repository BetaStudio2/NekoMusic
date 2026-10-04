package com.neko.music.seo;

import com.neko.music.util.PublicPlaylistLookup.PublicPlaylist;
import com.neko.music.util.PublicPlaylistLookup.Track;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 歌单 SEO 页面渲染的纯逻辑校验（不依赖数据库）。 */
class PlaylistPageRendererTest {

    private static PublicPlaylist samplePlaylist() {
        PublicPlaylist p = new PublicPlaylist();
        p.id = 42;
        p.userId = 7;
        p.name = "深夜循环 <歌单>";
        p.description = "适合夜晚收听的曲目";
        p.musicCount = 2;
        p.creatorName = "喵";
        p.updatedAt = "2026-01-29T12:00:00Z";
        Track t1 = new Track();
        t1.id = 100;
        t1.title = "夜曲";
        t1.artist = "周杰伦";
        t1.duration = 227;
        p.tracks.add(t1);
        Track t2 = new Track();
        t2.id = 101;
        t2.title = "Moonlight";
        t2.artist = "Neko";
        t2.duration = 180;
        p.tracks.add(t2);
        return p;
    }

    @Test
    void rendersPlaylistContentAndStructuredData() {
        String html = new PlaylistPageRenderer().render(samplePlaylist(), "https://music.example.com/");

        assertTrue(html.contains("<h1 itemprop=\"name\">深夜循环 &lt;歌单&gt;</h1>"), "标题需转义并作为 h1");
        assertTrue(html.contains("喵"), "应包含创建者");
        assertTrue(html.contains("/detail/100"), "曲目应链接到详情页");
        assertTrue(html.contains("/detail/101"));
        assertTrue(html.contains("https://music.example.com/playlist/42"), "应含 canonical/结构化 URL");
        assertTrue(html.contains("\"@type\":\"MusicPlaylist\""), "应输出 MusicPlaylist JSON-LD");
        assertTrue(html.contains("\"@type\":\"BreadcrumbList\""), "应输出面包屑");
    }

    @Test
    void jsonLdCannotBreakOutOfScriptTag() {
        PublicPlaylist p = samplePlaylist();
        p.name = "恶意</script><script>alert(1)</script>";
        String html = new PlaylistPageRenderer().render(p, "https://music.example.com/");

        assertFalse(html.contains("</script><script>alert(1)</script>"), "JSON-LD 必须转义尖括号，禁止闭合脚本标签");
        assertTrue(html.contains("\\u003c/script\\u003e"), "应使用 JSON Unicode 转义输出裸尖括号");
    }

    @Test
    void notFoundPageIsNoindex() {
        String html = new PlaylistPageRenderer().renderNotFound("https://music.example.com/");
        assertTrue(html.contains("歌单不存在"));
        assertTrue(html.contains("noindex"));
    }
}
