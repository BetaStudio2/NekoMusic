package com.neko.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    private void assertRejected(String input) {
        assertThrows(KugouMusicClient.InvalidInputException.class,
                () -> client.fetchPlaylist(input), input);
    }
}
