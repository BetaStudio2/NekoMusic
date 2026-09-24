package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QishuiMusicClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsPlaylistIdFromIdAndShareLinks() {
        QishuiMusicClient client = client();
        assertEquals("7434476168507637799", client.extractPlaylistId("7434476168507637799"));
        assertEquals("7434476168507637799",
                client.extractPlaylistId("https://www.qishui.com/playlist/7434476168507637799"));
        assertEquals("7434476168507637799",
                client.extractPlaylistId(
                        "https://music.douyin.com/qishui/share/playlist?playlist_id=7434476168507637799"));
        assertEquals("7434476168507637799",
                client.extractPlaylistId(
                        "复制这段内容 https://www.qishui.com/share/playlist?id=7434476168507637799 打开汽水"));
        assertNull(client.extractPlaylistId("not-a-playlist"));
        assertNull(client.extractPlaylistId(""));
    }

    @Test
    void parsesPcAndWebPlaylistTrackShapes() throws Exception {
        QishuiMusicClient client = client();
        JsonNode pc = objectMapper.readTree("""
                {"id":"1","type":"track","entity":{"track_wrapper":{"track":{
                  "id":"7420006432714688528","name":"经过","duration":215493,
                  "artists":[{"name":"张杰"}],
                  "album":{"name":"原神四周年","url_cover":{"uri":"tos/a",
                    "urls":["https://p3-luna.douyinpic.com/img/"],"template_prefix":"tplv-x"}}}}}}
                """);
        QishuiMusicClient.QishuiTrack track = client.parsePlaylistTrack(pc);
        assertEquals("7420006432714688528", track.id());
        assertEquals("经过", track.title());
        assertEquals("张杰", track.artist());
        assertEquals("原神四周年", track.album());
        assertEquals(215493L, track.durationMs());
        assertEquals("https://p3-luna.douyinpic.com/img/tos/a~tplv-x-resize:960:960.png", track.coverUrl());

        JsonNode web = objectMapper.readTree("""
                {"id":"2","type":"track","entity":{"track":{
                  "id":"852","desc":"视频标题","artists":[{"name":"甲"},{"name":"乙"}]}}}
                """);
        QishuiMusicClient.QishuiTrack webTrack = client.parsePlaylistTrack(web);
        assertEquals("852", webTrack.id());
        assertEquals("视频标题", webTrack.title());
        assertEquals("甲 / 乙", webTrack.artist());
    }

    @Test
    void extractsRouterDataFromWebPage() throws Exception {
        QishuiMusicClient client = client();
        String html = "<script>window._ROUTER_DATA = "
                + "{\"loaderData\":{\"playlist_page\":{\"playlistInfo\":{\"id\":\"9\"}}}}"
                + ";\nfunction runWindowFn(){}</script>";
        JsonNode router = client.extractRouterData(html);
        assertEquals("9", router.path("loaderData").path("playlist_page")
                .path("playlistInfo").path("id").asText());
        assertNull(client.extractRouterData("no router data here"));
    }

    private QishuiMusicClient client() {
        return new QishuiMusicClient(objectMapper);
    }
}
