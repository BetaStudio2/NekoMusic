package com.neko.music.util;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Frames;
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTPUB;
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT;
import org.jaudiotagger.tag.mp4.Mp4FieldKey;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 为进入曲库的音频写入平台广告元数据。
 * <ul>
 *   <li>评论 / 发行方 / 出版者：删除旧值后写入平台文案（整段替换，不拼接）</li>
 *   <li>内嵌歌词：在已有歌词正文最上方插入 LRC 横幅；无内嵌歌词则仅写入横幅；已含同首行则跳过</li>
 * </ul>
 */
public final class MusicAdMetadataPatcher {
    private static final Logger logger = LoggerFactory.getLogger(MusicAdMetadataPatcher.class);

    public static final String COMMENT =
            "更多免费无损音乐就来Neko歌姬计划 https://music.cnmsb.xin "
                    + "For more free lossless music, visit Neko Cloud Music: https://music.cnmsb.xin";

    public static final String ORGANIZATION = "Neko Music";
    public static final String PUBLISHER = "music.cnmsb.xin";

    public static final String LYRICS_BANNER =
            "[00:00.00]资源来自Neko歌姬计划 Resources from Neko Cloud Music\n"
                    + "[00:00.00]获取更多无损音乐https://music.cnmsb.xin/ Get more lossless music at https://music.cnmsb.xin/";

    public static final String LYRICS_BANNER_FIRST =
            "[00:00.00]资源来自Neko歌姬计划 Resources from Neko Cloud Music";

    private MusicAdMetadataPatcher() {
    }

    public static void patchQuietly(Path audioPath) {
        if (audioPath == null || !Files.isRegularFile(audioPath)) {
            return;
        }
        try {
            patch(audioPath);
            logger.info("已写入曲库广告元数据: {}", audioPath);
        } catch (Exception e) {
            logger.warn("写入曲库广告元数据失败 path={}: {}", audioPath, e.toString());
        }
    }

    public static void patch(Path audioPath) throws Exception {
        ensureUserWritable(audioPath);

        String ext = extension(audioPath);
        if (".wav".equals(ext) || ".wave".equals(ext)) {
            patchWav(audioPath);
            return;
        }
        if (".mp3".equals(ext)) {
            try {
                patchMp3(audioPath);
            } catch (CannotReadException | InvalidAudioFrameException e) {
                patchMp4(audioPath);
            }
            return;
        }
        if (".flac".equals(ext)) {
            try {
                patchFlac(audioPath);
            } catch (CannotReadException e) {
                patchMp4(audioPath);
            }
            return;
        }
        throw new IllegalArgumentException("不支持的扩展名: " + ext);
    }

    private static void patchMp3(Path path) throws Exception {
        AudioFile audio = AudioFileIO.read(path.toFile());
        Tag tag = audio.getTagOrCreateAndSetDefault();
        applyId3AdMetadata(tag);
        AudioFileIO.write(audio);
    }

    private static void patchFlac(Path path) throws Exception {
        AudioFile audio = AudioFileIO.read(path.toFile());
        Tag tag = audio.getTagOrCreateAndSetDefault();
        replaceComment(tag);
        if (tag instanceof FlacTag flacTag) {
            flacTag.setField("ORGANIZATION", ORGANIZATION);
            flacTag.setField("PUBLISHER", PUBLISHER);
            prependFlacEmbeddedLyrics(flacTag);
        } else {
            prependEmbeddedLyrics(tag);
        }
        AudioFileIO.write(audio);
    }

    private static void patchMp4(Path path) throws Exception {
        AudioFile audio = AudioFileIO.read(path.toFile());
        Tag tag = audio.getTagOrCreateAndSetDefault();
        if (!(tag instanceof Mp4Tag mp4)) {
            replaceComment(tag);
            prependEmbeddedLyrics(tag);
            AudioFileIO.write(audio);
            return;
        }
        mp4.setField(Mp4FieldKey.COMMENT, COMMENT);
        mp4.setField(Mp4FieldKey.LABEL, ORGANIZATION);
        mp4.setField(Mp4FieldKey.MM_PUBLISHER, PUBLISHER);
        prependMp4Lyrics(mp4);
        AudioFileIO.write(audio);
    }

    private static void patchWav(Path path) throws Exception {
        String ffmpeg = findFfmpeg();
        if (ffmpeg != null) {
            try {
                patchWavWithFfmpeg(path, ffmpeg);
                return;
            } catch (Exception e) {
                logger.debug("WAV ffmpeg 元数据写入失败，回退 ID3: {}", e.toString());
            }
        }
        patchWavId3Only(path);
    }

    private static void patchWavId3Only(Path path) throws Exception {
        AudioFile audio = AudioFileIO.read(path.toFile());
        Tag tag = audio.getTagOrCreateAndSetDefault();
        try {
            applyId3AdMetadata(tag);
            AudioFileIO.write(audio);
        } catch (Exception e) {
            if (isPermissionIssue(e)) {
                patchWavId3ViaTemp(path);
            } else {
                throw e;
            }
        }
    }

    private static void patchWavId3ViaTemp(Path path) throws Exception {
        Path tmp = path.getParent().resolve("." + path.getFileName() + ".ad." + ProcessHandle.current().pid() + ".wav");
        try {
            Files.copy(path, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            AudioFile audio = AudioFileIO.read(tmp.toFile());
            applyId3AdMetadata(audio.getTagOrCreateAndSetDefault());
            AudioFileIO.write(audio);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void patchWavWithFfmpeg(Path path, String ffmpeg) throws Exception {
        Path tmp = path.getParent().resolve("." + path.getFileName() + ".ff." + ProcessHandle.current().pid() + ".wav");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                    "-i", path.toString(),
                    "-c:a", "copy",
                    "-metadata", "comment=" + COMMENT,
                    tmp.toString());
            Process p = pb.start();
            int code = p.waitFor();
            if (code != 0) {
                String err = new String(p.getErrorStream().readAllBytes());
                throw new IOException("ffmpeg 退出码 " + code + (err.isBlank() ? "" : ": " + err.trim()));
            }
            patchWavId3Only(tmp);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void applyId3AdMetadata(Tag tag) throws TagException {
        replaceComment(tag);
        if (tag instanceof AbstractID3v2Tag id3) {
            replaceId3OrganizationAndPublisher(id3);
            prependId3Lyrics(id3);
        } else {
            prependEmbeddedLyrics(tag);
        }
    }

    /** 评论：删除全部旧 COMM/comment 后写入平台文案（不追加到旧评论后）。 */
    private static void replaceComment(Tag tag) throws TagException {
        tag.deleteField(FieldKey.COMMENT);
        if (tag instanceof AbstractID3v2Tag id3) {
            stripId3Frames(id3, ID3v24Frames.FRAME_ID_COMMENT);
            ID3v24Frame comm = new ID3v24Frame(ID3v24Frames.FRAME_ID_COMMENT);
            comm.setBody(new FrameBodyCOMM((byte) 3, "zho", "", COMMENT));
            id3.setFrame(comm);
        } else if (tag instanceof FlacTag flacTag) {
            flacTag.setField(FieldKey.COMMENT, COMMENT);
        } else {
            tag.setField(FieldKey.COMMENT, COMMENT);
        }
    }

    private static void replaceId3OrganizationAndPublisher(AbstractID3v2Tag id3) {
        stripId3TxxxOrganization(id3);
        stripId3Frames(id3, ID3v24Frames.FRAME_ID_PUBLISHER);

        ID3v24Frame tpub = new ID3v24Frame(ID3v24Frames.FRAME_ID_PUBLISHER);
        tpub.setBody(new FrameBodyTPUB((byte) 3, PUBLISHER));
        id3.setFrame(tpub);

        ID3v24Frame org = new ID3v24Frame(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO);
        org.setBody(new FrameBodyTXXX((byte) 3, "ORGANIZATION", ORGANIZATION));
        id3.setFrame(org);
    }

    private static void stripId3Frames(AbstractID3v2Tag id3, String frameId) {
        while (id3.hasFrame(frameId)) {
            id3.removeFrame(frameId);
        }
    }

    private static void stripId3TxxxOrganization(AbstractID3v2Tag id3) {
        boolean removed;
        do {
            removed = false;
            List<org.jaudiotagger.tag.TagField> frames =
                    id3.getFrame(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO);
            if (frames == null) {
                break;
            }
            for (org.jaudiotagger.tag.TagField f : frames) {
                if (f instanceof org.jaudiotagger.tag.id3.AbstractID3v2Frame frame
                        && frame.getBody() instanceof FrameBodyTXXX body
                        && "ORGANIZATION".equals(body.getDescription())) {
                    id3.removeFrame(frame.getIdentifier());
                    removed = true;
                    break;
                }
            }
        } while (removed);
    }

    /** 内嵌歌词：在已有正文最前插入横幅（不删除原歌词正文）。 */
    private static void prependId3Lyrics(AbstractID3v2Tag id3) {
        String existing = collectId3Lyrics(id3);
        if (hasBannerFirstLine(existing)) {
            return;
        }
        stripId3Frames(id3, ID3v24Frames.FRAME_ID_UNSYNC_LYRICS);
        String text = mergeLyricsWithBanner(existing);
        ID3v24Frame uslt = new ID3v24Frame(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS);
        uslt.setBody(new FrameBodyUSLT((byte) 3, "zho", "", text));
        id3.setFrame(uslt);
    }

    private static String collectId3Lyrics(AbstractID3v2Tag id3) {
        List<org.jaudiotagger.tag.TagField> frames =
                id3.getFrame(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS);
        if (frames == null || frames.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (org.jaudiotagger.tag.TagField f : frames) {
            if (!(f instanceof org.jaudiotagger.tag.id3.AbstractID3v2Frame frame)) {
                continue;
            }
            if (frame.getBody() instanceof FrameBodyUSLT body) {
                String lyric = body.getLyric();
                if (lyric != null && !lyric.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(lyric);
                }
            }
        }
        return sb.toString();
    }

    private static void prependEmbeddedLyrics(Tag tag) throws TagException {
        String existing = safeFirst(tag, FieldKey.LYRICS);
        if (hasBannerFirstLine(existing)) {
            return;
        }
        tag.deleteField(FieldKey.LYRICS);
        tag.setField(FieldKey.LYRICS, mergeLyricsWithBanner(existing));
    }

    private static void prependFlacEmbeddedLyrics(FlacTag flac) throws TagException {
        String existing = collectFlacLyrics(flac);
        if (hasBannerFirstLine(existing)) {
            return;
        }
        flac.deleteField("lyrics");
        flac.deleteField("LYRICS");
        flac.deleteField(FieldKey.LYRICS);
        flac.setField("lyrics", mergeLyricsWithBanner(existing));
    }

    private static String collectFlacLyrics(FlacTag flac) {
        String fromLyrics = flac.getFirst("lyrics");
        if (fromLyrics != null && !fromLyrics.isBlank()) {
            return fromLyrics;
        }
        String fromUpper = flac.getFirst("LYRICS");
        return fromUpper != null ? fromUpper : "";
    }

    private static void prependMp4Lyrics(Mp4Tag mp4) throws TagException {
        String existing = "";
        try {
            existing = mp4.getFirst(Mp4FieldKey.LYRICS);
        } catch (Exception ignored) {
            // no lyrics
        }
        if (hasBannerFirstLine(existing)) {
            return;
        }
        mp4.deleteField(Mp4FieldKey.LYRICS);
        mp4.setField(Mp4FieldKey.LYRICS, mergeLyricsWithBanner(existing));
    }

    /** 无旧歌词 → 仅横幅；有旧歌词 → 横幅 + 原正文（原样保留）。 */
    static String mergeLyricsWithBanner(String existingBody) {
        if (existingBody == null || existingBody.isBlank()) {
            return LYRICS_BANNER.trim();
        }
        return (LYRICS_BANNER + existingBody).trim();
    }

    private static String safeFirst(Tag tag, FieldKey key) {
        try {
            String v = tag.getFirst(key);
            return v != null ? v : "";
        } catch (Exception e) {
            return "";
        }
    }

    static boolean hasBannerFirstLine(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String first = body.replace("\uFEFF", "").lines().findFirst().orElse("").trim();
        return first.equals(LYRICS_BANNER_FIRST.trim());
    }

    private static void ensureUserWritable(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            perms = EnumSet.copyOf(perms);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignored) {
            path.toFile().setWritable(true);
        }
    }

    private static boolean isPermissionIssue(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof IOException io && io.getMessage() != null
                    && io.getMessage().toLowerCase(Locale.ROOT).contains("permission")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static String findFfmpeg() {
        String configured = System.getenv("FFMPEG_PATH");
        if (configured != null && !configured.isBlank() && Files.isExecutable(Path.of(configured))) {
            return configured;
        }
        for (String name : List.of("ffmpeg", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg")) {
            Path p = Path.of(name);
            if (Files.isExecutable(p)) {
                return p.toString();
            }
        }
        return null;
    }
}
