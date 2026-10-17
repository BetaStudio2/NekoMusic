package com.neko.music.filter;

import com.neko.music.i18n.ApiMessages;
import com.neko.music.i18n.RequestLocale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 429 文案：只保留有意义的单位（不出现「1 分 0 秒」），且句式完整（「请在 … 后重试」，
 * 不是「请 … 后可重试」这种两套句式拼在一起的写法）。
 *
 * <p>这条文案会直接展示给用户，也会被 {@code SourceBanFilter} 复用，因此逐档锁定。</p>
 */
class RateLimitMessageTest {

    @AfterEach
    void tearDown() {
        RequestLocale.clear();
    }

    @Test
    void dropsZeroMinorUnit() {
        assertEquals("请求过于频繁，请在 1 秒后重试", IPRateLimitFilter.formatRateLimitMessage(1));
        assertEquals("请求过于频繁，请在 45 秒后重试", IPRateLimitFilter.formatRateLimitMessage(45));
        assertEquals("请求过于频繁，请在 1 分钟后重试", IPRateLimitFilter.formatRateLimitMessage(60));
        assertEquals("请求过于频繁，请在 1 分 30 秒后重试", IPRateLimitFilter.formatRateLimitMessage(90));
        assertEquals("请求过于频繁，请在 1 小时后重试", IPRateLimitFilter.formatRateLimitMessage(3600));
        assertEquals("请求过于频繁，请在 1 小时 5 分钟后重试", IPRateLimitFilter.formatRateLimitMessage(3900));
        assertEquals("请求过于频繁，请在 1 天后重试", IPRateLimitFilter.formatRateLimitMessage(86400));
        assertEquals("请求过于频繁，请在 1 天 1 小时后重试", IPRateLimitFilter.formatRateLimitMessage(90000));
    }

    @Test
    void nonPositiveSecondsFallBackToOneSecond() {
        assertEquals("请求过于频繁，请在 1 秒后重试", IPRateLimitFilter.formatRateLimitMessage(0));
        assertEquals("请求过于频繁，请在 1 秒后重试", IPRateLimitFilter.formatRateLimitMessage(-5));
    }

    @Test
    void everyLanguageHasCompleteSentenceWithoutZeroUnit() {
        long[] samples = {1, 45, 60, 90, 3600, 3900, 86400, 90000};
        for (String tag : RequestLocale.supported()) {
            RequestLocale.set(tag);
            for (long sec : samples) {
                String message = IPRateLimitFilter.formatRateLimitMessage(sec);
                assertFalse(message.isBlank(), tag + " 文案为空: " + sec);
                assertFalse(message.contains(" 0 "), tag + " 出现了零值单位: " + message);
                // 「请 … 后可重试」把「请 … 后重试」与「… 后可重试」拼在了一起
                assertFalse(message.contains("后可重试") || message.contains("後可重試"),
                        tag + " 句式杂糅: " + message);
                // 键没翻译时 ApiMessages 会回退成键名，这里顺带兜住漏翻
                assertFalse(message.startsWith("security."), tag + " 缺译文: " + message);
            }
            assertTrue(ApiMessages.tableSize(tag) > 0, tag);
        }
    }
}
