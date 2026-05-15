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
    private static final int FFMPEG_TIMEOUT_MINUTES = 15;
    private static final String FONT = BundledRenderFontSupport.FONT_FAMILY;

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
        ass.append("Dialogue: 0,0:00:00.00,").append(end).append(",Title,,0,0,0,,{\\an8\\pos(960,100)}").append(safeTitle).append('\n');
        ass.append("Dialogue: 0,0:00:00.00,").append(end).append(",Artist,,0,0,0,,{\\an8\\pos(960,165)}").append(safeArtist).append('\n');
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
        double clipEnd = clipStart + duration;
        for (int i = 0; i < lyrics.size(); i++) {
            LrcParser.Line line = lyrics.get(i);
            if (line.getTimeSec() >= clipEnd) {
                break;
            }
            double relStart = line.getTimeSec() - clipStart;
            if (relStart >= duration) {
                continue;
            }
            double relEnd = duration;
            for (int j = i + 1; j < lyrics.size(); j++) {
                double nextRel = lyrics.get(j).getTimeSec() - clipStart;
                if (nextRel > relStart) {
                    relEnd = Math.min(duration, nextRel);
                    break;
                }
            }
            if (relEnd <= relStart) {
                relEnd = Math.min(duration, relStart + 2.0);
            }
            String startTs = formatAssTime(Math.max(0, relStart));
            String endTs = formatAssTime(relEnd);

            if (i > 0) {
                String prev = VideoRenderPaths.escapeAssText(
                        VideoRenderPaths.truncateText(lyrics.get(i - 1).getText(), 64));
                ass.append("Dialogue: 0,").append(startTs).append(',').append(endTs)
                        .append(",LyricDim,,0,0,0,,{\\an5\\pos(960,640)\\fad(250,250)}").append(prev).append('\n');
            }
            String active = VideoRenderPaths.escapeAssText(VideoRenderPaths.truncateText(line.getText(), 64));
            ass.append("Dialogue: 0,").append(startTs).append(',').append(endTs)
                    .append(",LyricActive,,0,0,0,,{\\an5\\pos(960,720)\\fad(300,300)\\move(960,750,960,720,0,350)}")
                    .append(active).append('\n');
            if (line.hasTranslation()) {
                String trans = VideoRenderPaths.escapeAssText(
                        VideoRenderPaths.truncateText(line.getTranslation(), 80));
                ass.append("Dialogue: 0,").append(startTs).append(',').append(endTs)
                        .append(",LyricTrans,,0,0,0,,{\\an5\\pos(960,790)\\fad(300,300)}").append(trans).append('\n');
            }
            if (i + 1 < lyrics.size()) {
                String next = VideoRenderPaths.escapeAssText(
                        VideoRenderPaths.truncateText(lyrics.get(i + 1).getText(), 64));
                ass.append("Dialogue: 0,").append(startTs).append(',').append(endTs)
                        .append(",LyricDim,,0,0,0,,{\\an5\\pos(960,860)\\fad(250,250)}").append(next).append('\n');
            }
        }
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
     * 横屏 1920×1080：模糊背景 + 居中封面 + 毛玻璃歌词区 + 波形 + ASS 字幕。
     */
    private static String buildLandscapeFilter(boolean hasCover, int durFrames, int fps, String subtitles) {
        String sizeWxH = WIDTH + "x" + HEIGHT;
        String sizeColon = WIDTH + ":" + HEIGHT;
        StringBuilder fc = new StringBuilder();
        if (hasCover) {
            fc.append("[1:v]scale=").append(sizeColon)
                    .append(":force_original_aspect_ratio=increase,crop=").append(sizeColon)
                    .append(",setsar=1[c0];");
            fc.append("[c0]split=2[c_bg][c_fg];");
            fc.append("[c_bg]gblur=sigma=28[blur_bg];");
            fc.append("[c_fg]scale=440:440:force_original_aspect_ratio=decrease,")
                    .append("pad=440:440:(ow-iw)/2:(oh-ih)/2:color=0x00000000,format=rgba[art];");
            fc.append("[blur_bg][art]overlay=(W-w)/2:120:format=auto[composed];");
        } else {
            fc.append("color=c=0x0f172a:s=").append(sizeWxH).append(":d=")
                    .append(durFrames).append(":r=").append(fps).append("[composed];");
        }
        fc.append("color=c=0x1e293b@0.55:s=1760x360:d=").append(durFrames).append(":r=").append(fps).append("[panel];");
        fc.append("[composed][panel]overlay=80:520:format=auto[with_panel];");
        fc.append("[0:a]showwaves=s=1740x130:mode=line:rate=").append(fps)
                .append(":colors=0xA78BFA@0.92:scale=lin[waves];");
        fc.append("[with_panel][waves]overlay=90:870:format=auto[base];");
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
