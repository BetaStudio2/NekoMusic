package com.neko.music.seo;

import com.neko.music.util.PublicMusicLookup.PublicMusic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicDetailJsonLdBuilderTest {

    @Test
    void linksWebPageRecordingAndAudioIntoOneGraph() {
        PublicMusic music = new PublicMusic();
        music.id = 42;
        music.title = "夜曲";
        music.artist = "周杰伦";
        music.album = "十一月的萧邦";
        music.duration = 227;
        music.updatedAt = "2026-10-27T12:00:00Z";

        MusicDetailSeoContent content = MusicDetailSeoContent.from(music, "https://music.example.com/");
        String jsonLd = MusicDetailJsonLdBuilder.build(content, music);

        assertTrue(jsonLd.contains("\"mainEntity\":{\"@id\":\"https://music.example.com/detail/42#recording\"}"));
        assertTrue(jsonLd.contains("\"audio\":{\"@id\":\"https://music.example.com/detail/42#audio\"}"));
        assertTrue(jsonLd.contains("https://music.example.com/icons/icon-512.png"));
        assertTrue(jsonLd.contains("\"dateModified\":\"2026-10-27T12:00:00Z\""));
    }
}
