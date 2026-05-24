package com.neko.music.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SongLanguageInfererTest {

    @Test
    void infersChinese() {
        assertEquals("中文", SongLanguageInferer.infer("晴天", "周杰伦", "叶惠美", ""));
    }

    @Test
    void infersEnglish() {
        assertEquals("英文", SongLanguageInferer.infer("Stay Me", "NBR MUSIC", "Hi Res Edition", ""));
    }

    @Test
    void infersJapanese() {
        assertEquals("日语", SongLanguageInferer.infer("残酷な天使のテーゼ", "高橋洋子", "", ""));
    }

    @Test
    void infersKorean() {
        assertEquals("韩语", SongLanguageInferer.infer("봄날", "방탄소년단", "", ""));
    }
}
