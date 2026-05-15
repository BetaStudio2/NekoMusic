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
    private static final int COVER_SIZE = 680;
    private static final int COVER_RADIUS = 44;
    private static final int COVER_X = 72;
    private static final int COVER_Y = (HEIGHT - COVER_SIZE) / 2;
    /** 右侧内容区中心（封面右缘至屏幕右缘的中点） */
    private static final int RIGHT_CENTER_X = COVER_X + COVER_SIZE + (WIDTH - COVER_X - COVER_SIZE) / 2;
    private static final int LYRIC_CENTER_Y = 620;
    /** 含翻译行（\\N）的单行歌词块垂直间距 */
    private static final int LINE_SPACING = 102;
    private static final int FADE_MS = 220;
    private static final int FFMPEG_TIMEOUT_MINUTES = 15;
    private static final String FONT = BundledRenderFontSupport.FONT_FAMILY;
    private static final String LYRIC_CLIP = "\\clip(" + (COVER_X + COVER_SIZE + 24) + ",340,1880,920)";
    private static final int LYRIC_VISIBLE_BEFORE = 2;
    private static final int LYRIC_VISIBLE_AFTER = 2;
    /** 圆角矩形 SDF alpha（避免四角圆形伪影） */
    private static final String ROUNDED_ALPHA = roundedRectAlphaExpr(COVER_RADIUS);

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
        ass.append("Style: Title,").append(FONT).append(",64,&H00FFFFFF,&H000000FF,&H80C4B5FD,&H00000000,1,0,0,0,100,100,0,0,1,0,0,8,40,40,80,1\n");
        ass.append("Style: Artist,").append(FONT).append(",42,&H00F0F0F0,&H000000FF,&H60000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,8,40,40,140,1\n");
        ass.append("Style: LyricBase,").append(FONT).append(",44,&H00FFFFFF,&H000000FF,&H60000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,5,40,40,0,1\n");
        ass.append("Style: LyricTrans,").append(FONT).append(",32,&H00E8E8E8,&H000000FF,&H60000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,5,40,40,0,1\n");
        ass.append("Style: Mark,").append(FONT).append(",26,&H00FFFFFF,&H000000FF,&H60000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,3,48,48,48,1\n\n");
        ass.append("[Events]\n");
        ass.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
        ass.append("Dialogue: 0,0:00:00.00,").append(end).append(",Title,,0,0,0,,{\\an8\\pos(")
                .append(RIGHT_CENTER_X).append(",120)\\blur1\\bord2\\3c&H60C4B5FD&\\shad1\\4c&H40000000&}")
                .append(safeTitle).append('\n');
        ass.append("Dialogue: 0,0:00:00.00,").append(end).append(",Artist,,0,0,0,,{\\an8\\pos(")
                .append(RIGHT_CENTER_X).append(",200)\\1c&H00F5F5F5&}").append(safeArtist).append('\n');
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
        List<ScrollSegment> segments = buildScrollSegments(lyrics, clipStart, duration);
        if (segments.isEmpty()) {
            return;
        }
        int firstActive = segments.get(0).activeIndex();
        int lastActive = segments.get(segments.size() - 1).activeIndex();
        int jFrom = Math.max(0, firstActive - LYRIC_VISIBLE_BEFORE);
        int jTo = Math.min(lyrics.size() - 1, lastActive + LYRIC_VISIBLE_AFTER);

        for (ScrollSegment seg : segments) {
            int nextActive = seg.scrollAtEnd() ? seg.activeIndex() + 1 : seg.activeIndex();
            for (int j = jFrom; j <= jTo; j++) {
                if (!isLyricLineVisible(j, seg.activeIndex())) {
                    continue;
                }
                LrcParser.Line line = lyrics.get(j);
                boolean active = j == seg.activeIndex();
                boolean fadeIn = j == seg.activeIndex() + LYRIC_VISIBLE_AFTER;
                boolean fadeOut = seg.scrollAtEnd() && !isLyricLineVisible(j, nextActive);
                appendLyricLineSegment(ass, line, j, seg, active, fadeIn, fadeOut);
            }
        }
    }

    private record ScrollSegment(double relStart, double relEnd, int activeIndex, boolean scrollAtEnd) {
    }

    private List<ScrollSegment> buildScrollSegments(List<LrcParser.Line> lyrics, double clipStart, double duration) {
        List<ScrollSegment> segments = new ArrayList<>();
        int active = activeIndexAt(lyrics, clipStart);
        double relStart = Math.max(0, lyrics.get(active).getTimeSec() - clipStart);
        while (active < lyrics.size() && relStart < duration - 1e-6) {
            double relEnd = duration;
            boolean scroll = false;
            if (active + 1 < lyrics.size()) {
                double nextRel = lyrics.get(active + 1).getTimeSec() - clipStart;
                if (nextRel > relStart + 1e-6 && nextRel <= duration) {
                    relEnd = nextRel;
                    scroll = true;
                }
            }
            if (relEnd <= relStart + 1e-6) {
                break;
            }
            segments.add(new ScrollSegment(relStart, relEnd, active, scroll));
            if (!scroll) {
                break;
            }
            relStart = relEnd;
            active++;
        }
        return segments;
    }

    private void appendLyricLineSegment(StringBuilder ass, LrcParser.Line line, int lineIndex, ScrollSegment seg,
                                        boolean active, boolean fadeIn, boolean fadeOut) {
        String text = formatLyricBlock(line, active);
        int yHold = LYRIC_CENTER_Y + (lineIndex - seg.activeIndex()) * LINE_SPACING;
        int yNext = yHold - LINE_SPACING;
        long segMs = Math.max(1, Math.round((seg.relEnd() - seg.relStart()) * 1000));

        String startTs = formatAssTime(seg.relStart());
        String endTs = formatAssTime(seg.relEnd());
        ass.append("Dialogue: 0,").append(startTs).append(',').append(endTs).append(",LyricBase,,0,0,0,,")
                .append(buildSegmentMotionTags(yHold, yNext, segMs, seg.scrollAtEnd(), active, fadeIn, fadeOut))
                .append(text).append('\n');
    }

    /** 主歌词 + 翻译合并为同一块（\\N），随主行一起滚动，避免翻译单独消失。 */
    private static String formatLyricBlock(LrcParser.Line line, boolean active) {
        String main = VideoRenderPaths.escapeAssText(VideoRenderPaths.truncateText(line.getText(), 64));
        if (!line.hasTranslation()) {
            return main;
        }
        String trans = VideoRenderPaths.escapeAssText(VideoRenderPaths.truncateText(line.getTranslation(), 80));
        if (active) {
            return main + "{\\r\\fs34\\1c&H00EEEEEE&\\b0\\bord0\\blur0\\shad0}\\N" + trans;
        }
        return main + "{\\r\\fs30\\1c&H00B8B8B8&\\b0\\bord0}\\N" + trans;
    }

    /**
     * 整段匀速 {@code \\move}（速度随 LRC 句间隔自动变化），比段末短促跳动更自然。
     */
    private String buildSegmentMotionTags(int yHold, int yNext, long segMs, boolean scroll, boolean active,
                                          boolean fadeIn, boolean fadeOut) {
        StringBuilder tags = new StringBuilder("{\\an5").append(LYRIC_CLIP);
        if (fadeIn) {
            tags.append("\\fad(").append(FADE_MS).append(",0)");
        } else if (fadeOut) {
            tags.append("\\fad(0,").append(FADE_MS).append(')');
        }
        appendLyricVisualState(tags, active);
        if (scroll) {
            tags.append(String.format(Locale.US, "\\move(%d,%d,%d,%d,0,%d)",
                    RIGHT_CENTER_X, yHold, RIGHT_CENTER_X, yNext, segMs));
        } else {
            tags.append(String.format(Locale.US, "\\pos(%d,%d)", RIGHT_CENTER_X, yHold));
        }
        tags.append('}');
        return tags.toString();
    }

    private static int activeIndexAt(List<LrcParser.Line> lyrics, double clipStart) {
        int active = 0;
        boolean matched = false;
        for (int k = 0; k < lyrics.size(); k++) {
            if (lyrics.get(k).getTimeSec() <= clipStart + 1e-3) {
                active = k;
                matched = true;
            } else {
                break;
            }
        }
        return matched ? active : 0;
    }

    private static boolean isLyricLineVisible(int lineIndex, int activeIndex) {
        return lineIndex >= activeIndex - LYRIC_VISIBLE_BEFORE
                && lineIndex <= activeIndex + LYRIC_VISIBLE_AFTER;
    }

    private static void appendLyricVisualState(StringBuilder tags, boolean active) {
        if (active) {
            tags.append("\\fs62\\1c&H00FFFFFF&\\1a&H00&\\b1\\bord3\\3c&H80C4B5FD&\\blur2\\shad0");
        } else {
            tags.append("\\fs44\\1c&H00E8E8E8&\\1a&H00&\\b0\\bord0");
        }
    }

    private static String roundedRectAlphaExpr(int radius) {
        return "if(lte(max(abs(X-W/2)-W/2+" + radius + ",abs(Y-H/2)-H/2+" + radius + "),0),255,"
                + "if(lte(hypot(max(abs(X-W/2)-W/2+" + radius + ",0),max(abs(Y-H/2)-H/2+" + radius + ",0)),"
                + radius + "),255,0))";
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
     * 横屏 1920×1080：全屏模糊毛玻璃底 + 左侧圆角封面 + 底部波形 + ASS 字幕（右侧无独立底色）。
     */
    private static String buildLandscapeFilter(boolean hasCover, int durFrames, int fps, String subtitles) {
        String sizeWxH = WIDTH + "x" + HEIGHT;
        String sizeColon = WIDTH + ":" + HEIGHT;
        String coverColon = COVER_SIZE + ":" + COVER_SIZE;
        StringBuilder fc = new StringBuilder();
        if (hasCover) {
            fc.append("[1:v]scale=").append(sizeColon)
                    .append(":force_original_aspect_ratio=increase,crop=").append(sizeColon)
                    .append(",setsar=1[bg_src];");
            fc.append("[bg_src]gblur=sigma=46,eq=brightness=0.02:saturation=1.05:contrast=1.06[blur_bg];");
            fc.append("color=c=white@0.26:s=").append(sizeWxH).append(":d=").append(durFrames)
                    .append(":r=").append(fps).append(",gblur=sigma=4[frost_blur];");
            fc.append("[blur_bg][frost_blur]overlay=0:0:format=auto[glass];");
            fc.append("color=c=0x7C3AED@0.08:s=").append(sizeWxH).append(":d=").append(durFrames)
                    .append(":r=").append(fps).append("[tint];");
            fc.append("[glass][tint]overlay=0:0:format=auto[glass_tint];");
            fc.append("color=c=black@0.14:s=").append(sizeWxH).append(":d=").append(durFrames)
                    .append(":r=").append(fps).append("[vign];");
            fc.append("[glass_tint][vign]overlay=0:0:format=auto[bg];");
            fc.append("[1:v]scale=").append(coverColon).append(":force_original_aspect_ratio=decrease,")
                    .append("pad=").append(coverColon).append(":(ow-iw)/2:(oh-ih)/2:color=black@0,")
                    .append("format=rgba,geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='").append(ROUNDED_ALPHA)
                    .append("'[cover_raw];");
            fc.append("[cover_raw]split[cover_main][cover_blur_src];");
            fc.append("[cover_blur_src]gblur=sigma=14,colorchannelmixer=aa=0.65[cover_shadow];");
            fc.append("[bg][cover_shadow]overlay=").append(COVER_X + 10).append(':').append(COVER_Y + 14)
                    .append(":format=auto[bg_shadow];");
            fc.append("[bg_shadow][cover_main]overlay=").append(COVER_X).append(':').append(COVER_Y)
                    .append(":format=auto[composed];");
        } else {
            fc.append("color=c=0x0f172a:s=").append(sizeWxH).append(":d=").append(durFrames)
                    .append(":r=").append(fps).append("[dark];");
            fc.append("color=c=white@0.12:s=").append(sizeWxH).append(":d=").append(durFrames)
                    .append(":r=").append(fps).append("[frost];");
            fc.append("[dark][frost]overlay=0:0:format=auto[composed];");
        }
        fc.append("[0:a]showwaves=s=1760x150:mode=cline:rate=").append(fps)
                .append(":colors=0xC4B5FD@0.92|0x67E8F9@0.88:scale=lin[waves];");
        fc.append("[composed][waves]overlay=80:900:format=auto[base];");
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
