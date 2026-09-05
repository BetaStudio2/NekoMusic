package com.neko.music.handlers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.service.MusicRecognitionService;
import com.neko.music.service.RecognitionRateLimiter;
import com.neko.music.util.MusicAssetLocator;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Accepts a short recording and matches it against the locally hosted music library. */
public final class MusicRecognitionHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicRecognitionHandler.class);
    private static final long MULTIPART_OVERHEAD_ALLOWANCE = 1024L * 1024L;

    private final RecognitionRateLimiter rateLimiter = new RecognitionRateLimiter();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!Main.getConfigManager().isMusicRecognitionEnabled()) {
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE_503, "听歌识曲功能未启用");
            return;
        }

        RecognitionRateLimiter.Decision rateDecision = rateLimiter.tryAcquire(
                clientAddress(request),
                Main.getConfigManager().getMusicRecognitionRateLimitPerMinute());
        if (!rateDecision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(rateDecision.retryAfterSeconds()));
            writeError(response, HttpStatus.TOO_MANY_REQUESTS_429, "识曲请求过于频繁，请稍后重试");
            return;
        }

        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            writeError(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE_415,
                    "请使用 multipart/form-data，并通过 audio 字段上传录音");
            return;
        }
        long maxUploadBytes = Main.getConfigManager().getMusicRecognitionMaxUploadBytes();
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxUploadBytes + MULTIPART_OVERHEAD_ALLOWANCE) {
            writeError(response, HttpStatus.PAYLOAD_TOO_LARGE_413, maxUploadMessage(maxUploadBytes));
            return;
        }

        Part audioPart;
        try {
            audioPart = request.getPart("audio");
        } catch (IllegalStateException e) {
            writeError(response, HttpStatus.PAYLOAD_TOO_LARGE_413, maxUploadMessage(maxUploadBytes));
            return;
        } catch (ServletException e) {
            writeError(response, HttpStatus.BAD_REQUEST_400, "无法解析上传的录音");
            return;
        }
        if (audioPart == null || audioPart.getSize() <= 0) {
            writeError(response, HttpStatus.BAD_REQUEST_400, "请通过 audio 字段上传录音");
            return;
        }
        if (audioPart.getSize() > maxUploadBytes) {
            writeError(response, HttpStatus.PAYLOAD_TOO_LARGE_413, maxUploadMessage(maxUploadBytes));
            return;
        }

        Path uploaded = Files.createTempFile("music-recognize-", safeSuffix(audioPart.getSubmittedFileName()));
        try {
            try (InputStream input = audioPart.getInputStream()) {
                copyWithLimit(input, uploaded, maxUploadBytes);
            }
            Optional<MusicRecognitionService.RecognitionResult> result =
                    Main.getMusicRecognitionService().recognize(uploaded);
            if (result.isEmpty()) {
                writeNoMatch(response);
            } else {
                writeMatch(response, result.get());
            }
        } catch (UploadTooLargeException e) {
            writeError(response, HttpStatus.PAYLOAD_TOO_LARGE_413, maxUploadMessage(maxUploadBytes));
        } catch (MusicRecognitionService.AudioTooLongException
                 | MusicRecognitionService.InvalidAudioException e) {
            writeError(response, HttpStatus.BAD_REQUEST_400, e.getMessage());
        } catch (MusicRecognitionService.BusyException e) {
            response.setHeader("Retry-After", "2");
            writeError(response, HttpStatus.TOO_MANY_REQUESTS_429, e.getMessage());
        } catch (MusicRecognitionService.IndexUnavailableException e) {
            logger.warn("声纹索引暂不可用: {}", e.getMessage());
            response.setHeader("Retry-After", "5");
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE_503, e.getMessage());
        } catch (IOException e) {
            logger.error("识曲音频处理失败", e);
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE_503, "识曲服务暂不可用，请稍后重试");
        } finally {
            Files.deleteIfExists(uploaded);
        }
    }

    private static void writeMatch(
            HttpServletResponse response,
            MusicRecognitionService.RecognitionResult result) throws IOException {
        MusicRecognitionService.Track track = result.track();
        ObjectNode data = Main.getObjectMapper().createObjectNode();
        data.put("id", track.id());
        data.put("title", track.title());
        data.put("artist", track.artist());
        data.put("album", track.album());
        data.put("duration", track.duration());
        data.put("language", track.language());
        data.put("tags", track.tags());
        data.put("filePath", MusicAssetLocator.fileApiUrl(track.id()));
        data.put("coverFilePath", MusicAssetLocator.coverApiUrl(track.id()));
        data.put("coverUrl", MusicAssetLocator.coverApiUrl(track.id()));
        data.put("confidence", round(result.confidence(), 4));
        data.put("matchedLandmarks", result.matchedLandmarks());
        data.put("offsetSeconds", round(result.offsetSeconds(), 2));
        data.put("sampleDurationSeconds", round(result.sampleDurationSeconds(), 2));

        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("success", true);
        root.put("matched", true);
        root.put("message", "识别成功");
        root.set("data", data);
        writeJson(response, HttpStatus.OK_200, root);
    }

    private static void writeNoMatch(HttpServletResponse response) throws IOException {
        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("success", true);
        root.put("matched", false);
        root.put("message", "未在当前曲库中识别到歌曲");
        root.putNull("data");
        writeJson(response, HttpStatus.OK_200, root);
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("success", false);
        root.put("matched", false);
        root.put("message", message == null || message.isBlank() ? "请求失败" : message);
        root.putNull("data");
        writeJson(response, status, root);
    }

    private static void writeJson(HttpServletResponse response, int status, ObjectNode body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=utf-8");
        Main.getObjectMapper().writeValue(response.getWriter(), body);
    }

    private static void copyWithLimit(InputStream input, Path target, long maxBytes) throws IOException {
        long total = 0;
        byte[] buffer = new byte[16 * 1024];
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new UploadTooLargeException();
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static String safeSuffix(String submittedName) {
        if (submittedName == null) {
            return ".upload";
        }
        String name = Path.of(submittedName).getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || name.length() - dot > 12) {
            return ".upload";
        }
        String suffix = name.substring(dot).toLowerCase(Locale.ROOT);
        return suffix.matches("\\.[a-z0-9]{1,10}") ? suffix : ".upload";
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp.trim();
    }

    private static String maxUploadMessage(long maxBytes) {
        return "录音文件不得超过 " + Math.max(1, maxBytes / (1024 * 1024)) + " MiB";
    }

    private static double round(double value, int decimalPlaces) {
        double scale = Math.pow(10d, decimalPlaces);
        return Math.round(value * scale) / scale;
    }

    private static final class UploadTooLargeException extends IOException {
    }
}
