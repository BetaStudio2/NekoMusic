package com.neko.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 酷狗歌单解析的入参校验：分享链接地址来自用户输入，非酷狗官方域名 / 内网地址必须在
 * 发请求之前就被拒绝（SSRF 回归测试，全部用例不需要联网）。
 */
class KugouMusicClientSsrfTest {

    private final KugouMusicClient client = new KugouMusicClient(new ObjectMapper());

    @Test
    @DisplayName("站外 / 内网 / 云元数据分享链接一律按非法入参拒绝")
    void rejectsForeignAndInternalShareUrls() {
        assertRejected("http://127.0.0.1:6379/share/");
        assertRejected("http://127.0.0.1:8080/share/?action=single&hash=x");
        assertRejected("http://169.254.169.254/latest/meta-data/share/");
        assertRejected("http://100.100.100.200/latest/meta-data/share/");
        assertRejected("http://10.0.0.1:80/share/");
        assertRejected("http://2130706433/share/");
        assertRejected("http://localhost/share/");
        assertRejected("http://example.com/kugou.com/songlist/gcid_3zmi8f5nz5z0c4/");
        assertRejected("https://kugou.com.evil.com/songlist/gcid_3zmi8f5nz5z0c4/");
    }

    @Test
    @DisplayName("识别不了的输入按非法入参拒绝，不再落到上游请求")
    void rejectsUnrecognizedInput() {
        assertRejected("");
        assertRejected("   ");
        assertRejected("not-a-playlist");
        assertRejected("http://example.com/");
    }

    @Test
    @DisplayName("分享链接只认 /songlist/<token> 与单曲分享两种形态，出站路径由 token 重新拼出")
    void shareLinkOnlyAcceptsKnownSonglistAndSingleShapes() throws Exception {
        // 客户实际会粘贴的形态：www 与 m 主机都接受，结尾斜杠可选
        assertEquals("songlist/gcid_3zmi8f5nz5z0c4/",
                KugouMusicClient.shareRelativeFromUri(
                        URI.create("https://www.kugou.com/songlist/gcid_3zmi8f5nz5z0c4/")));
        assertEquals("songlist/gcid_3zmi8f5nz5z0c4/",
                KugouMusicClient.shareRelativeFromUri(
                        URI.create("https://www.kugou.com/songlist/gcid_3zmi8f5nz5z0c4")));
        assertEquals("songlist/gcid_3zmi8f5nz5z0c4/",
                KugouMusicClient.shareRelativeFromUri(
                        URI.create("https://m.kugou.com/songlist/gcid_3zmi8f5nz5z0c4/")));

        // 多带的查询串会被丢掉：内网地址既进不了出站主机，也进不了出站路径
        String relative = KugouMusicClient.shareRelativeFromUri(URI.create(
                "https://www.kugou.com/songlist/gcid_3zmi8f5nz5z0c4/?jump=http%3A%2F%2F127.0.0.1%3A22%2F"));
        assertEquals("songlist/gcid_3zmi8f5nz5z0c4/", relative);
        assertFalse(relative.contains("127.0.0.1"), relative);
        assertFalse(relative.contains("jump"), relative);

        // 单曲分享（客户端 UI 仍在用的形态）只按白名单里的两个参数重建
        assertEquals("share/?action=single&hash=ABCDEF0123456789",
                KugouMusicClient.shareRelativeFromUri(URI.create(
                        "https://m.kugou.com/share/?action=single&hash=ABCDEF0123456789")));
        assertEquals("share/?action=single&hash=ABCDEF0123456789",
                KugouMusicClient.shareRelativeFromUri(URI.create(
                        "https://m.kugou.com/share/?hash=ABCDEF0123456789&action=single"
                                + "&jump=http%3A%2F%2F127.0.0.1%3A22%2F")));
    }

    @Test
    @DisplayName("官方域名下的其它路径 / 参数组合一律按非法入参拒绝")
    void rejectsOfficialDomainLinksOutsideKnownShapes() {
        String[] rejected = {
            // 日志里扫描器用过的形态：/share/x 带 jump（to）参数，以前会被当分享页抓取并解析失败
            "https://www.kugou.com/share/x?jump=http%3A%2F%2F127.0.0.1%3A22%2F",
            "https://www.kugou.com/share/x?to=http%3A%2F%2F127.0.0.1%3A22%2F",
            "https://m.kugou.com/share/?action=single",              // 缺 hash
            "https://m.kugou.com/share/?action=single&hash=../x",    // hash 非法
            "https://m.kugou.com/share/?hash=ABCDEF&action=other",   // action 不是 single
            "https://www.kugou.com/songlist/",                       // 没有 token
            "https://www.kugou.com/songlist/gcid_x/extra/",          // 多余路径段
            "https://www.kugou.com/songlist/%2e%2e%2fetc/",          // 编码穿越
            "https://www.kugou.com/yy/special/single/1234567.html",  // 其它路径形态
        };
        for (String payload : rejected) {
            assertThrows(KugouMusicClient.InvalidInputException.class,
                    () -> KugouMusicClient.shareRelativeFromUri(URI.create(payload)), payload);
        }
    }

    @Test
    @DisplayName("分享页 3xx 目标离开酷狗官方域名一律拒绝，不会跟到内网")
    void rejectsRedirectHopsLeavingKugou() {
        URI current = URI.create("https://m.kugou.com/share/x");
        for (String location : new String[]{
                "http://127.0.0.1:22/",
                "http://127.0.0.1:6379/",
                "http://169.254.169.254/latest/meta-data/",
                "http://100.100.100.200/latest/meta-data/",
                "http://2130706433/",
                "http://[::1]:22/",
                "//evil.com/x",
                "https://kugou.com.evil.com/x",
                "file:///etc/passwd"}) {
            assertThrows(KugouMusicClient.InvalidInputException.class,
                    () -> KugouMusicClient.nextShareHop(current, location), location);
        }
    }

    private void assertRejected(String input) {
        assertThrows(KugouMusicClient.InvalidInputException.class,
                () -> client.fetchPlaylist(input), input);
    }
}
