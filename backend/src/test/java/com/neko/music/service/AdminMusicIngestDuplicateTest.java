package com.neko.music.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 歌单导入的判重口径：只认「规范化后完整歌名一致」。
 *
 * <p>用例取自真实歌单「网易云音乐喜欢的音乐」（id=1，158 首）：其中这些曲目是同一首歌的不同版本
 * （Machine Ver. / Demo / Live），宽松合并（去掉括号备注再比核心标题）会把它们并成一条曲库记录，
 * 导致后出现的那首既不入库也进不了歌单，导入曲目数少于来源歌单。</p>
 */
class AdminMusicIngestDuplicateTest {

    @Test
    void keepsDifferentVersionsApart() {
        assertFalse(AdminMusicIngestService.sameTitleForImport(
                "她 (Machine Ver.）", "她 (Human Flawed Demo)"));
        assertFalse(AdminMusicIngestService.sameTitleForImport(
                "另外的路径", "另外的路径 (Machine Ver.）"));
        assertFalse(AdminMusicIngestService.sameTitleForImport(
                "害羞的侵略 (Machine Ver.）", "害羞的侵略"));
        assertFalse(AdminMusicIngestService.sameTitleForImport(
                "经济舱", "经济舱 (Live)"));
    }

    @Test
    void matchesIdenticalTitles() {
        assertTrue(AdminMusicIngestService.sameTitleForImport("晴天", "晴天"));
        assertTrue(AdminMusicIngestService.sameTitleForImport("经济舱 (Live)", "经济舱 (Live)"));
    }

    @Test
    void ignoresTraditionalChineseCaseAndSurroundingSpaces() {
        assertTrue(AdminMusicIngestService.sameTitleForImport("說謊", "说谎"));
        assertTrue(AdminMusicIngestService.sameTitleForImport("CIRCLES", "  circles  "));
    }

    @Test
    void rejectsBlankDatabaseTitle() {
        assertFalse(AdminMusicIngestService.sameTitleForImport("晴天", null));
        assertFalse(AdminMusicIngestService.sameTitleForImport("晴天", "   "));
    }
}
