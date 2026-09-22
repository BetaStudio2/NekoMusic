package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QishuiMusicClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void encryptMatchesAccountSdk() {
        // 账号 SDK：UTF-8 字节逐字节 XOR 0x05 后转十六进制（不补零）
        assertEquals("34363d353534363d353535", QishuiMusicClient.encrypt("13800138000"));
    }

    @Test
    void normalizesStatusCodes() {
        assertEquals("new", QishuiMusicClient.normalizeStatus(json("{\"status\":\"1\"}")));
        assertEquals("scanned", QishuiMusicClient.normalizeStatus(json("{\"status\":\"scanned\"}")));
        assertEquals("confirmed", QishuiMusicClient.normalizeStatus(json("{\"status\":\"3\"}")));
        assertEquals("expired", QishuiMusicClient.normalizeStatus(json("{\"status\":\"refused\"}")));
        assertEquals("", QishuiMusicClient.normalizeStatus(json("{\"account_flow\":\"verify\"}")));
    }

    @Test
    void extractsMfaFromNestedResponse() {
        String payload = """
                {"data":{"account_flow":"verify","error_code":2046,
                  "verify_info":{"encrypt_uid":"uid_123","channel_mobile":"138****8000",
                    "verify_way":"mobile_sms_verify",
                    "extra":"std_verify_token=tk1&std_verify_scene=qr_connect&passport_mfa_retry_tag=rt1"},
                  "std_verify_flow_id":"flow1"}}
                """;
        Optional<QishuiMusicClient.MfaChallenge> result = client().extractMfa(json(payload));
        assertTrue(result.isPresent());
        QishuiMusicClient.MfaChallenge mfa = result.get();
        assertEquals("uid_123", mfa.encryptUid());
        assertEquals("138****8000", mfa.mobile());
        assertTrue(mfa.verifyWays().contains("mobile_sms_verify"));
        assertEquals("tk1", mfa.verifyParams().get("std_verify_token"));
        assertEquals("qr_connect", mfa.verifyParams().get("std_verify_scene"));
        assertEquals("rt1", mfa.verifyParams().get("passport_mfa_retry_tag"));
        assertEquals("flow1", mfa.verifyParams().get("std_verify_flow_id"));
    }

    @Test
    void extractsOfficialWebVerifyChallenge() {
        // 客户端真实协议：2046 + url（官方验证组件）+ biz_params（重放时要原样带回）
        String payload = """
                {"data":{"error_code":2046,"account_flow":"verify",
                  "url":"https://lf-verify.example/uc-verify.js",
                  "biz_params":{"encrypt_uid":"uid_9","std_verify_token":"tk9","verify_reason":"login"}}}
                """;
        QishuiMusicClient.MfaChallenge mfa = client().extractMfa(json(payload)).orElseThrow();
        assertTrue(mfa.hasVerifyUrl());
        assertEquals("https://lf-verify.example/uc-verify.js", mfa.verifyUrl());
        assertEquals("uid_9", mfa.encryptUid());
        assertEquals("tk9", mfa.verifyParams().get("std_verify_token"));
        assertEquals("login", mfa.bizParams().get("verify_reason"));
    }

    @Test
    void ignoresResponseWithoutVerificationSignal() {
        assertTrue(client().extractMfa(json("{\"data\":{\"status\":\"new\"}}")).isEmpty());
    }

    @Test
    void persistsAndReloadsCookies(@TempDir Path dir) throws Exception {
        Path cookieFile = dir.resolve("qishui_cookies.json");
        Files.writeString(cookieFile, """
                [{"name":"sessionid","value":"sess-abc","domain":".qishui.com","path":"/",
                  "maxAge":-1,"secure":false,"httpOnly":false,"version":0},
                 {"name":"ttwid","value":"tw-1","domain":".qishui.com","path":"/",
                  "maxAge":-1,"secure":false,"httpOnly":false,"version":0}]
                """, StandardCharsets.UTF_8);

        QishuiMusicClient client = new QishuiMusicClient(objectMapper, cookieFile.toString());
        assertTrue(client.hasSession());
        assertTrue(client.cookieHeader().contains("sessionid=sess-abc"));
        assertTrue(client.cookieHeader().contains("ttwid=tw-1"));

        client.clearCookies();
        assertFalse(client.hasSession());
        assertTrue(client.cookieHeader().isEmpty());

        QishuiMusicClient reloaded = new QishuiMusicClient(objectMapper, cookieFile.toString());
        assertFalse(reloaded.hasSession());
    }

    @Test
    void absorbsRealPassportSetCookies(@TempDir Path dir) {
        // 线上真实响应头：Domain 不带前导点 + Max-Age + SameSite=None
        // 用 java.net.CookieManager 会因为这三点一条都存不下来（默认策略 ACCEPT_ORIGINAL_SERVER）
        QishuiMusicClient client = new QishuiMusicClient(objectMapper, dir.resolve("c.json").toString());
        client.absorbSetCookieHeaders(List.of(
                "passport_csrf_token=7ca7c9c9; Path=/; Domain=qishui.com; Max-Age=5184000; Secure; SameSite=None",
                "passport_csrf_token_default=7ca7c9c9; Path=/; Domain=qishui.com; Max-Age=5184000",
                "reg-store-region=; Path=/; Domain=qishui.com; Max-Age=0; HttpOnly; Secure"));

        assertEquals(List.of("passport_csrf_token", "passport_csrf_token_default"), client.cookieNames());
        assertTrue(client.cookieHeader().contains("passport_csrf_token=7ca7c9c9"));
        assertTrue(client.cookieHeader().contains("passport_csrf_token_default=7ca7c9c9"));
    }

    @Test
    void keepsSessionIdForSubdomainWithDottedAndPlainDomain(@TempDir Path dir) {
        QishuiMusicClient client = new QishuiMusicClient(objectMapper, dir.resolve("c.json").toString());
        client.absorbSetCookieHeaders(List.of("sessionid=sess-abc; Path=/; Domain=.qishui.com; HttpOnly; Secure"));
        assertTrue(client.hasSession());
        assertTrue(client.cookieHeader().contains("sessionid=sess-abc"));

        QishuiMusicClient plain = new QishuiMusicClient(objectMapper, dir.resolve("c2.json").toString());
        plain.absorbSetCookieHeaders(List.of("sessionid=sess-xyz; Path=/; Domain=qishui.com; HttpOnly"));
        assertTrue(plain.hasSession());
        assertTrue(plain.cookieHeader().contains("sessionid=sess-xyz"));
    }

    @Test
    void toleratesBrokenExpiresAndDropsExpiredCookies(@TempDir Path dir) {
        QishuiMusicClient client = new QishuiMusicClient(objectMapper, dir.resolve("c.json").toString());
        // 星期与日期对不上 / ISO 8601：解析不出来就当会话 Cookie 留着，绝不整条丢弃
        client.absorbSetCookieHeaders(List.of(
                "sessionid=sess-1; Path=/; Domain=qishui.com; Expires=Wed, 01 Jan 2030 00:00:00 GMT",
                "sid_tt=t1; Path=/; Domain=qishui.com; Expires=2030-01-01T00:00:00Z"));
        assertTrue(client.hasSession());
        assertTrue(client.cookieHeader().contains("sid_tt=t1"));

        // 真正过期的 cookie 要清掉
        client.absorbSetCookieHeaders(List.of(
                "sid_tt=; Path=/; Domain=qishui.com; Expires=Thu, 01 Jan 1970 00:00:00 GMT"));
        assertFalse(client.cookieHeader().contains("sid_tt"));
        assertTrue(client.hasSession());
    }

    @Test
    void parsesCookieDatesUsedByPassport() {
        assertTrue(QishuiMusicClient.parseCookieDate("Tue, 01 Jan 2030 00:00:00 GMT") > 0);
        // 星期写错也要能解析（java.net.HttpCookie 在这里会直接放弃整条）
        assertTrue(QishuiMusicClient.parseCookieDate("Wed, 01 Jan 2030 00:00:00 GMT") > 0);
        assertTrue(QishuiMusicClient.parseCookieDate("Tue Jan  1 00:00:00 2030") > 0);
        assertNull(QishuiMusicClient.parseCookieDate("not-a-date"));
    }

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
    void parsesPcAndWebPlaylistTrackShapes() {
        QishuiMusicClient client = client();
        // PC 接口：entity.track_wrapper.track
        JsonNode pc = json("""
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

        // 分享页：entity.track，标题兜底 desc，歌手用分隔符拼接
        JsonNode web = json("""
                {"id":"2","type":"track","entity":{"track":{
                  "id":"852","desc":"视频标题","artists":[{"name":"甲"},{"name":"乙"}]}}}
                """);
        QishuiMusicClient.QishuiTrack webTrack = client.parsePlaylistTrack(web);
        assertEquals("852", webTrack.id());
        assertEquals("视频标题", webTrack.title());
        assertEquals("甲 / 乙", webTrack.artist());
    }

    @Test
    void extractsRouterDataFromWebPage() {
        QishuiMusicClient client = client();
        String html = "<script>window._ROUTER_DATA = "
                + "{\"loaderData\":{\"playlist_page\":{\"playlistInfo\":{\"id\":\"9\"}}}}"
                + ";\nfunction runWindowFn(){}</script>";
        JsonNode router = client.extractRouterData(html);
        assertEquals("9",
                router.path("loaderData").path("playlist_page").path("playlistInfo").path("id").asText());
        assertNull(client.extractRouterData("no router data here"));
    }

    private QishuiMusicClient client() {
        return new QishuiMusicClient(objectMapper, "/tmp/qishui-test-cookies-unused.json");
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
