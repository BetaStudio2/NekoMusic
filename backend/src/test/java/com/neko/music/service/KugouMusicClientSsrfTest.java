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
    @DisplayName("分享链接只贡献路径与查询串：内网地址只能当查询参数，出站主机固定")
    void shareLinkOnlyContributesPathAndQuery() throws Exception {
        // 线上被扫描器用过的形态：官方域名 + 查询串里塞内网地址（jump/to 都是酷狗分享页的普通参数）
        String[] payloads = {
            "https://www.kugou.com/share/x?jump=http%3A%2F%2F127.0.0.1%3A22%2F",
            "https://www.kugou.com/share/x?to=http%3A%2F%2F127.0.0.1%3A22%2F",
        };
        for (String payload : payloads) {
            String relative = KugouMusicClient.relativePathAndQuery(URI.create(payload));
            assertEquals("share/x?" + payload.substring(payload.indexOf('?') + 1), relative, payload);
            // 相对路径里不可能再拼出第二个 authority：主机名恒等于常量里的酷狗移动端域名
            assertFalse(relative.startsWith("//"), relative);
            assertFalse(relative.contains("://"), relative);
            assertFalse(relative.contains("kugou.com"), relative);
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
