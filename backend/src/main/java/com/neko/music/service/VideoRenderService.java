package com.neko.music.service;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.database.VideoRenderDatabaseManager;
import com.neko.music.model.VideoRenderJob;
import com.neko.music.util.BundledFfmpegSupport;
import com.neko.music.util.BundledRenderFontSupport;
import com.neko.music.util.LrcParser;
import com.neko.music.util.MusicAssetLocator;
import com.neko.music.util.VideoRenderPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 横屏短视频 FFmpeg 渲染：独立线程池异步执行，HTTP 请求仅入队后立即返回。
 * 文字叠加使用 ASS + subtitles 滤镜；内嵌 Noto Sans SC 支持中日韩等非拉丁字符。
 */
public class VideoRenderService {
    private static final Logger logger = LoggerFactory.getLogger(VideoRenderService.class);

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int LEFT_WIDTH = 960;
    private static final int RIGHT_CENTER_X = 1440;
    private static final int LYRIC_CENTER_Y = 560;
    private static final int LINE_SPACING = 62;
    private static final int TRANS_OFFSET_Y = 36;
    private static final int SCROLL_MS = 450;
    private static final int FFMPEG_TIMEOUT_MINUTES = 15;
    private static final String FONT = BundledRenderFontSupport.FONT_FAMILY;
    private static final String LYRIC_CLIP = "\\clip(1000,300,1880,900)";

    private final ConfigManager configManager;
    private final VideoRenderDatabaseManager jobDb;
    private final ThreadPoolExecutor executor;

    public VideoRenderService(ConfigManager configManager, VideoRenderDatabaseManager jobDb) {
        this.configManager = configManager;
        this.jobDb = jobDb;
        int threads = Math.max(1, configManager.getVideoRenderWorkerThreads());
        AtomicInteger idx = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "video-render-" + idx.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(threads * 4),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
        logger.info("视频渲染线程池已启动 threads={}, queueCapacity={}", threads, threads * 4);
    }

    public void submit(VideoRenderJob job, String title, String artist, Path audioFile, Optional<Path> coverFile) {
        executor.execute(() -> runJob(job, title, artist, audioFile, coverFile));
    }

    public int queueSize() {
        return executor.getQueue().size();
    }

    private void runJob(VideoRenderJob job, String title, String artist, Path audioFile, Optional<Path> coverFile) {
        Path assFile = null;
        try {
            jobDb.markProcessing(job.getId());
            VideoRenderPaths.ensureVideoDir();
            Path output = VideoRenderPaths.outputFile(job.getId());
            assFile = VideoRenderPaths.assFile(job.getId());
            Path fontsDir = BundledRenderFontSupport.ensureFontsDir();
            List<LrcParser.Line> lyrics = loadLyrics(job.getMusicId());
            writeAssFile(assFile, job, title, artist, lyrics);
            runFfmpeg(job, audioFile, coverFile, assFile, fontsDir, output);
            if (!Files.isRegularFile(output) || Files.size(output) <= 0) {
                throw new IOException("渲染输出文件无效");
            }
            jobDb.markDone(job.getId(), VideoRenderPaths.outputRelPath(job.getId()));
            logger.info("视频渲染完成 jobId={} userId={} musicId={} lyricsLines={}",
                    job.getId(), job.getUserId(), job.getMusicId(), lyrics.size());
            notifyRenderCompleteByEmail(job, title, artist);
        } catch (Exception e) {
            logger.error("视频渲染失败 jobId={}: {}", job.getId(), e.getMessage(), e);
            jobDb.markFailed(job.getId(), shortenError(e.getMessage()));
            try {
                Files.deleteIfExists(VideoRenderPaths.outputFile(job.getId()));
            } catch (IOException ignored) {
            }
        } finally {
            if (assFile != null) {
                try {
                    Files.deleteIfExists(assFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private List<LrcParser.Line> loadLyrics(int musicId) {
        return MusicAssetLocator.findLyricsFile(musicId)
                .flatMap(LrcParser::parseFile)
                .orElse(List.of());
    }

    private void notifyRenderCompleteByEmail(VideoRenderJob job, String title, String artist) {
        try {
            String base = configManager.getVideoRenderNotifyFrontendBaseUrl();
            if (base == null || base.isEmpty()) {
                logger.warn("未配置 video_render.notify_frontend_base_url，跳过渲染完成邮件 jobId={}", job.getId());
                return;
            }
            String downloadUrl = base + "/api/video/render/" + job.getId() + "/download";
            Main.getUserAuthService().findEmailByUserId(job.getUserId()).ifPresent(email -> {
                boolean sent = Main.getEmailService().sendVideoRenderCompleteEmail(
                        email,
                        title == null ? "未知歌曲" : title,
                        artist == null ? "未知艺术家" : artist,
                        job.getDurationSec(),
                        downloadUrl,
                        job.isWatermarked());
                if (!sent) {
                    logger.warn("渲染完成邮件发送失败 jobId={} email={}", job.getId(), email);
                }
            });
        } catch (Exception e) {
            logger.warn("发送渲染完成邮件异常 jobId={}: {}", job.getId(), e.getMessage());
        }
    }

    private void writeAssFile(Path assFile, VideoRenderJob job, String title, String artist,
                              List<LrcParser.Line> lyrics) throws IOException {
        double duration = job.getDurationSec();
        double clipStart = job.getStartSec();
        String end = formatAssTime(duration);
        String safeTitle = VideoRenderPaths.escapeAssText(
                VideoRenderPaths.truncateText(title == null ? "未知歌曲" : title, 48));
        String safeArtist = VideoRenderPaths.escapeAssText(
                VideoRenderPaths.truncateText(artist == null ? "未知艺术家" : artist, 48));
        String watermark = VideoRenderPaths.escapeAssText(configManager.getVideoRenderWatermarkText());

        StringBuilder ass = new StringBuilder();
        ass.append("[Script Info]\n");
        ass.append("ScriptType: v4.00+\n");
        ass.append("PlayResX: ").append(WIDTH).append('\n');
        ass.append("PlayResY: ").append(HEIGHT).append('\n');
        ass.append("WrapStyle: 0\n\n");
        ass.append("[V4+ Styles]\n");
        ass.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, ")
                .append("Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, ")
                .append("Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
        ass.append("Style: Title,").append(FONT).append(",52,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,1,0,0,0,100,100,0,0,1,2,0,8,40,40,80,1\n");
        ass.append("Style: Artist,").append(FONT).append(",36,&H00D4D4D4,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,8,40,40,140,1\n");
        ass.append("Style: LyricActive,").append(FONT).append(",54,&H00FFFFFF,&H000000FF,&H00000000,&H66000000,1,0,0,0,100,100,0,0,3,0,0,5,40,40,0,1\n");
        ass.append("Style: LyricDim,").append(FONT).append(",40,&H80FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,5,40,40,0,1\n");
        ass.append("Style: LyricTrans,").append(FONT).append(",32,&H80C8C8C8,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,5,40,40,0,1\n");
        ass.append("Style: Mark,").append(FONT).append(",30,&H00FFFFFF,&H000000FF,&H00000000,&H66000000,0,0,0,0,100,100,0,0,3,0,0,3,48,48,48,1\n\n");
        ass.append("[Events]\n");
        ass.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
        ass.append("Dialogue: 0,0:00:00.00,").append(end).append(",Title,,0,0,0,,{\\an8\\pos(")
                .append(RIGHT_CENTER_X).append(",100)}").append(safeTitle).append('\n');
        ass.append("Dialogue: 0,0:00:00.00,").append(end).append(",Artist,,0,0,0,,{\\an8\\pos(")
                .append(RIGHT_CENTER_X).append(",165)}").append(safeArtist).append('\n');
        appendLyricEvents(ass, lyrics, clipStart, duration);
        if (job.isWatermarked()) {
            ass.append("Dialogue: 0,0:00:00.00,").append(end).append(",Mark,,0,0,0,,{\\an3\\pos(1840,48)}").append(watermark).append('\n');
        }
        Files.writeString(assFile, ass.toString(), StandardCharsets.UTF_8);
    }

    private void appendLyricEvents(StringBuilder ass, List<LrcParser.Line> lyrics, double clipStart, double duration) {
        if (lyrics.isEmpty()) {
            return;
        }
        List<LyricSegment> segments = buildLyricSegments(lyrics, clipStart, duration);
        for (LyricSegment seg : segments) {
            appendScrollSegment(ass, lyrics, seg);
        }
    }

    private record LyricSegment(int activeIndex, double relStart, double relEnd, boolean scrollToNext) {
    }

    private List<LyricSegment> buildLyricSegments(List<LrcParser.Line> lyrics, double clipStart, double duration) {
        List<LyricSegment> segments = new ArrayList<>();
        int active = 0;
        for (int k = 0; k < lyrics.size(); k++) {
            if (lyrics.get(k).getTimeSec() <= clipStart) {
                active = k;
            } else {
                break;
            }
        }
        double relStart = 0;
        while (active < lyrics.size() && relStart < duration) {
            double relEnd = duration;
            boolean scroll = false;
            if (active + 1 < lyrics.size()) {
                double nextRel = lyrics.get(active + 1).getTimeSec() - clipStart;
                if (nextRel > relStart && nextRel <= duration) {
                    relEnd = nextRel;
                    scroll = true;
                }
            }
            if (relEnd <= relStart) {
                break;
            }
            segments.add(new LyricSegment(active, relStart, relEnd, scroll));
            if (!scroll) {
                break;
            }
            active++;
            relStart = relEnd;
        }
        return segments;
    }

    private void appendScrollSegment(StringBuilder ass, List<LrcParser.Line> lyrics, LyricSegment seg) {
        String startTs = formatAssTime(seg.relStart);
        String endTs = formatAssTime(seg.relEnd);
        long segMs = Math.max(1, Math.round((seg.relEnd - seg.relStart) * 1000));
        long scrollMs = seg.scrollToNext ? Math.min(SCROLL_MS, segMs) : 0;
        long moveStart = Math.max(0, segMs - scrollMs);
        long moveEnd = segMs;

        int visibleFrom = Math.max(0, seg.activeIndex - 2);
        int visibleTo = Math.min(lyrics.size() - 1, seg.activeIndex + 3);
        for (int j = visibleFrom; j <= visibleTo; j++) {
            LrcParser.Line line = lyrics.get(j);
            int yHold = LYRIC_CENTER_Y + (j - seg.activeIndex) * LINE_SPACING;
            int yNext = yHold - LINE_SPACING;
            String style = j == seg.activeIndex ? "LyricActive" : "LyricDim";
            String text = VideoRenderPaths.escapeAssText(VideoRenderPaths.truncateText(line.getText(), 64));
            ass.append("Dialogue: 0,").append(startTs).append(',').append(endTs).append(',')
                    .append(style).append(",,0,0,0,,")
                    .append(buildScrollTags(yHold, yNext, moveStart, moveEnd, seg.scrollToNext))
                    .append(text).append('\n');

            if (j == seg.activeIndex && line.hasTranslation()) {
                String trans = VideoRenderPaths.escapeAssText(
                        VideoRenderPaths.truncateText(line.getTranslation(), 80));
                ass.append("Dialogue: 0,").append(startTs).append(',').append(endTs).append(",LyricTrans,,0,0,0,,")
                        .append(buildScrollTags(yHold + TRANS_OFFSET_Y, yNext + TRANS_OFFSET_Y, moveStart, moveEnd, seg.scrollToNext))
                        .append(trans).append('\n');
            }
        }
    }

    private String buildScrollTags(int yHold, int yNext, long moveStart, long moveEnd, boolean scroll) {
        if (scroll && moveEnd > moveStart) {
            return String.format(Locale.US, "{\\an5%s\\pos(%d,%d)\\move(%d,%d,%d,%d,%d,%d)}",
                    LYRIC_CLIP, RIGHT_CENTER_X, yHold,
                    RIGHT_CENTER_X, yHold, RIGHT_CENTER_X, yNext, moveStart, moveEnd);
        }
        return String.format(Locale.US, "{\\an5%s\\pos(%d,%d)}", LYRIC_CLIP, RIGHT_CENTER_X, yHold);
    }

    private void runFfmpeg(VideoRenderJob job, Path audioFile, Optional<Path> coverFile, Path assFile,
                           Path fontsDir, Path output) throws IOException, InterruptedException {
        String ffmpeg = BundledFfmpegSupport.resolve(
                configManager.getVideoRenderFfmpegPath(),
                configManager.isVideoRenderPreferBundledFfmpeg());
        double duration = job.getDurationSec();
        double start = job.getStartSec();
        int fps = 30;
        int durFrames = Math.max(1, (int) Math.ceil(duration * fps));

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-y");
        cmd.add("-ss");
        cmd.add(formatSec(start));
        cmd.add("-t");
        cmd.add(formatSec(duration));
        cmd.add("-i");
        cmd.add(audioFile.toAbsolutePath().toString());

        boolean hasCover = coverFile.isPresent() && Files.isRegularFile(coverFile.get());
        if (hasCover) {
            cmd.add("-loop");
            cmd.add("1");
            cmd.add("-framerate");
            cmd.add(String.valueOf(fps));
            cmd.add("-t");
            cmd.add(formatSec(duration));
            cmd.add("-i");
            cmd.add(coverFile.get().toAbsolutePath().toString());
        }

        String subtitles = VideoRenderPaths.subtitlesFilterArg(assFile, fontsDir);
        String filter = buildLandscapeFilter(hasCover, durFrames, fps, subtitles);
        cmd.add("-filter_complex");
        cmd.add(filter);
        cmd.add("-map");
        cmd.add("[vout]");
        cmd.add("-map");
        cmd.add("0:a");
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("veryfast");
        cmd.add("-threads");
        cmd.add("2");
        cmd.add("-crf");
        cmd.add("23");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("192k");
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add("-shortest");
        cmd.add(output.toAbsolutePath().toString());

        logger.info("异步 ffmpeg 开始 jobId={} durationSec={} watermarked={}", job.getId(), duration, job.isWatermarked());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder logOut = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logOut.append(line).append('\n');
            }
        }
        boolean finished = process.waitFor(FFMPEG_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new IOException("ffmpeg 超时（>" + FFMPEG_TIMEOUT_MINUTES + " 分钟）");
        }
        int code = process.exitValue();
        if (code != 0) {
            String detail = tail(logOut.toString(), 1200);
            throw new IOException("ffmpeg 退出码 " + code + (detail.isBlank() ? "" : ": " + detail.trim()));
        }
    }

    /**
     * 横屏 1920×1080：左封面 / 右歌词分屏 + 右侧波形 + ASS 字幕。
     */
    private static String buildLandscapeFilter(boolean hasCover, int durFrames, int fps, String subtitles) {
        String leftSize = LEFT_WIDTH + "x" + HEIGHT;
        String leftColon = LEFT_WIDTH + ":" + HEIGHT;
        String rightSize = LEFT_WIDTH + "x" + HEIGHT;
        StringBuilder fc = new StringBuilder();
        if (hasCover) {
            fc.append("[1:v]scale=").append(leftColon)
                    .append(":force_original_aspect_ratio=increase,crop=").append(leftColon)
                    .append(",setsar=1[c0];");
            fc.append("[c0]split=2[c_bg][c_fg];");
            fc.append("[c_bg]gblur=sigma=22[blur_left];");
            fc.append("[c_fg]scale=680:680:force_original_aspect_ratio=decrease,")
                    .append("pad=680:680:(ow-iw)/2:(oh-ih)/2:color=0x00000000,format=rgba[art_left];");
            fc.append("[blur_left][art_left]overlay=(W-w)/2:(H-h)/2:format=auto[left];");
        } else {
            fc.append("color=c=0x0f172a:s=").append(leftSize).append(":d=")
                    .append(durFrames).append(":r=").append(fps).append("[left];");
        }
        fc.append("color=c=0x111827:s=").append(rightSize).append(":d=")
                .append(durFrames).append(":r=").append(fps).append("[right_base];");
        fc.append("color=c=0x1e293b@0.42:s=").append(rightSize).append(":d=")
                .append(durFrames).append(":r=").append(fps).append("[right_glass];");
        fc.append("[right_base][right_glass]overlay=0:0:format=auto[right];");
        fc.append("color=c=0x334155@0.65:s=2x").append(HEIGHT).append(":d=")
                .append(durFrames).append(":r=").append(fps).append("[divider];");
        fc.append("[left][right]hstack=inputs=2[stacked];");
        fc.append("[stacked][divider]overlay=").append(LEFT_WIDTH - 1).append(":0:format=auto[composed];");
        fc.append("[0:a]showwaves=s=880x110:mode=line:rate=").append(fps)
                .append(":colors=0xA78BFA@0.92:scale=lin[waves];");
        fc.append("[composed][waves]overlay=").append(LEFT_WIDTH + 40).append(":930:format=auto[base];");
        fc.append("[base]").append(subtitles).append("[vout]");
        return fc.toString();
    }

    private static String formatSec(double sec) {
        return String.format(Locale.US, "%.3f", Math.max(0, sec));
    }

    private static String formatAssTime(double sec) {
        int totalCs = Math.max(0, (int) Math.round(sec * 100));
        int s = totalCs / 100;
        int cs = totalCs % 100;
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int ss = s % 60;
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, ss, cs);
    }

    private static String tail(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.length() <= max ? s : s.substring(s.length() - max);
    }

    private static String shortenError(String msg) {
        if (msg == null || msg.isBlank()) {
            return "渲染失败";
        }
        return msg.length() > 500 ? msg.substring(0, 499) + "…" : msg;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static Optional<Path> resolveCover(int musicId) {
        return MusicAssetLocator.findCoverFile(musicId);
    }

    public static Optional<Path> resolveAudio(int musicId) {
        return MusicAssetLocator.findAudioFile(musicId);
    }
}
