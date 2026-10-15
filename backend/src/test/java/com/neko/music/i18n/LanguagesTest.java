package com.neko.music.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语言注册表：标签唯一、前缀互不遮蔽、回落链必然终止于源语言。
 *
 * <p>注册表是「加语言」的唯一入口，写错一行就会让某门语言永远匹配不到、或者匹配到别的语言，
 * 所以这些不变量单独锁住。</p>
 */
class LanguagesTest {

    @Test
    void sourceLanguageIsRegistered() {
        assertTrue(Languages.tags().contains(Languages.SOURCE), "注册表里必须有源语言");
        assertEquals(Languages.SOURCE, Languages.tags().get(0), "源语言应排在最前，作为兜底基准");
    }

    @Test
    void everyTagHasATableResource() {
        for (String tag : Languages.tags()) {
            String path = Languages.tablePath(tag);
            assertNotNull(Languages.class.getResourceAsStream(path), "缺少资源 " + path);
        }
    }

    @Test
    void everyLanguageNameIsNonEmpty() {
        for (String tag : Languages.tags()) {
            assertTrue(!Languages.nativeName(tag).isBlank(), tag + " 缺少自称");
        }
    }

    @Test
    void fallbackChainTerminatesAtSource() {
        for (String tag : Languages.tags()) {
            String next = Languages.fallbackOf(tag);
            assertTrue(Languages.tags().contains(next), next + " 不在注册表里");
            // 顺着回落链走下去必须收敛到源语言（最多两跳）
            String current = tag;
            int hops = 0;
            while (!Languages.SOURCE.equals(current) && hops++ < Languages.tags().size()) {
                current = Languages.fallbackOf(current);
            }
            assertEquals(Languages.SOURCE, current, tag + " 的回落链没有收敛到源语言");
        }
    }

    @Test
    void matchesFullTagsAndRegionsOnly() {
        assertEquals(Languages.SOURCE, Languages.match("zh"));
        assertEquals(Languages.SOURCE, Languages.match("zh-Hans"));
        assertEquals("zh-TW", Languages.match("zh-Hant"));
        assertEquals("en", Languages.match("en-US"));
        assertEquals("ja", Languages.match("ja"));
        assertEquals("ko", Languages.match("ko-KR"));
        // 结束端必须锚定：不能让 "eng" / "zho" 这类前缀冒充 en / zh
        assertNull(Languages.match("eng"));
        assertNull(Languages.match("zhx"));
        assertNull(Languages.match("fr"));
    }
}
