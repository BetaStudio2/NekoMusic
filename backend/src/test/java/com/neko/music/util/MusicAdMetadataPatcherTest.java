package com.neko.music.util;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Frames;
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM;
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MusicAdMetadataPatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void mergeLyricsWithBanner_prependsToExisting() {
        String merged = MusicAdMetadataPatcher.mergeLyricsWithBanner("[00:12.00]原歌词");
        assertTrue(merged.startsWith(MusicAdMetadataPatcher.LYRICS_BANNER_FIRST));
        assertTrue(merged.contains("[00:12.00]原歌词"));
        assertFalse(merged.contains(MusicAdMetadataPatcher.LYRICS_BANNER + MusicAdMetadataPatcher.LYRICS_BANNER));
    }

    @Test
    void mergeLyricsWithBanner_emptyGetsBannerOnly() {
        assertEquals(MusicAdMetadataPatcher.LYRICS_BANNER.trim(),
                MusicAdMetadataPatcher.mergeLyricsWithBanner(""));
    }

    @Test
    void hasBannerFirstLine_detectsDuplicate() {
        String already = MusicAdMetadataPatcher.mergeLyricsWithBanner("[00:01.00]x");
        assertTrue(MusicAdMetadataPatcher.hasBannerFirstLine(already));
    }

    @Test
    void patchMp3_replacesCommentAndPrependsLyrics(@TempDir Path dir) throws Exception {
        assumeTrue(hasFfmpeg(), "需要 ffmpeg 生成测试 MP3");

        Path mp3 = dir.resolve("test.mp3");
        runFfmpeg(mp3, "mp3");

        seedMp3Tags(mp3, "旧评论应被替换", "[00:15.00]原有歌词行");

        MusicAdMetadataPatcher.patch(mp3);

        AudioFile after = AudioFileIO.read(mp3.toFile());
        AbstractID3v2Tag id3 = (AbstractID3v2Tag) after.getTag();
        String comment = id3.getFirst(ID3v24Frames.FRAME_ID_COMMENT);
        assertEquals(MusicAdMetadataPatcher.COMMENT, comment);
        assertFalse(comment.contains("旧评论"));

        String lyrics = id3.getFirst(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS);
        assertTrue(MusicAdMetadataPatcher.hasBannerFirstLine(lyrics));
        assertTrue(lyrics.contains("[00:15.00]原有歌词行"));

        // 二次打标不重复插入横幅
        MusicAdMetadataPatcher.patch(mp3);
        AudioFile again = AudioFileIO.read(mp3.toFile());
        String lyrics2 = ((AbstractID3v2Tag) again.getTag()).getFirst(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS);
        assertEquals(lyrics, lyrics2);
    }

    private static void seedMp3Tags(Path mp3, String oldComment, String oldLyrics) throws Exception {
        AudioFile audio = AudioFileIO.read(mp3.toFile());
        AbstractID3v2Tag id3 = (AbstractID3v2Tag) audio.getTagOrCreateAndSetDefault();

        ID3v24Frame comm = new ID3v24Frame(ID3v24Frames.FRAME_ID_COMMENT);
        comm.setBody(new FrameBodyCOMM((byte) 3, "zho", "", oldComment));
        id3.setFrame(comm);

        ID3v24Frame uslt = new ID3v24Frame(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS);
        uslt.setBody(new FrameBodyUSLT((byte) 3, "zho", "", oldLyrics));
        id3.setFrame(uslt);

        AudioFileIO.write(audio);
    }

    private static void runFfmpeg(Path out, String codec) throws Exception {
        Process p = new ProcessBuilder(
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=0.2",
                "-acodec", codec.equals("mp3") ? "libmp3lame" : codec,
                out.toString()
        ).start();
        assumeTrue(p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0);
        assumeTrue(Files.size(out) > 0);
    }

    private static boolean hasFfmpeg() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
