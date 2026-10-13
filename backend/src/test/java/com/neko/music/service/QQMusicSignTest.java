package com.neko.music.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 QQ 桌面协议签名（{@link QQMusicClient#zzcSign(String)}）与 QQ 系哈希
 * （{@link QQMusicClient#hash33(String, long)}）的实现，避免后续重构悄然改坏取链。
 *
 * <p>期望值由独立实现（Python 按算法说明复算）得出，作为交叉校验，而非重抄 Java 逻辑。</p>
 */
class QQMusicSignTest {

    @Test
    @DisplayName("zzcSign 对小写英文文本的结果与独立实现一致")
    void zzcSignMatchesReferenceForAscii() {
        assertEquals("zzcaa5d2f8uhxkywtaffvf4xpstzzaiyofu238584b7",
                QQMusicClient.zzcSign("hello-qq-sign"));
    }

    @Test
    @DisplayName("zzcSign 对带 JSON 体的结果与独立实现一致，且恒以小写 zzc 开头")
    void zzcSignMatchesReferenceForJson() {
        String body = "{\"comm\":{\"ct\":\"19\"},\"req_0\":{\"module\":\"music.search.SearchCgiService\"}}";
        String sign = QQMusicClient.zzcSign(body);
        assertEquals("zzcdc980c7b821cl2oyzm7qzvglc6oa7stu06757e86", sign);
        assertTrue(sign.startsWith("zzc"));
        assertEquals(sign.toLowerCase(), sign);
    }

    @Test
    @DisplayName("hash33 与独立实现一致（ptqrtoken / g_tk 共用）")
    void hash33MatchesReference() {
        assertEquals(1337762608L, QQMusicClient.hash33("qrsig-value", 0));
        assertEquals(1442717039L, QQMusicClient.hash33("musickey", 5381));
    }
}
