package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.model.VideoRenderJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 横屏视频渲染任务元数据仅存 Redis（JSON + TTL），不写入 MySQL。
 */
public class VideoRenderJobStore {
    private static final Logger logger = LoggerFactory.getLogger(VideoRenderJobStore.class);

    static final String KEY_PREFIX = "video_render:job:";
    /** 任务记录在 Redis 中的保留时间（含 pending / done，便于邮件下载链接有效期内查询） */
    private static final int JOB_TTL_SECONDS = 7 * 86400;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    public VideoRenderJobStore(RedisService redisService, ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    public void insertPending(VideoRenderJob job) {
        job.setStatus("pending");
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        }
        save(job);
    }

    public void markProcessing(String jobId) {
        update(jobId, job -> job.setStatus("processing"));
    }

    public void markDone(String jobId, String outputRelPath) {
        update(jobId, job -> {
            job.setStatus("done");
            job.setOutputRelPath(outputRelPath);
            job.setErrorMessage(null);
            job.setFinishedAt(new Timestamp(System.currentTimeMillis()));
        });
    }

    public void markFailed(String jobId, String errorMessage) {
        try {
            update(jobId, job -> {
                job.setStatus("failed");
                job.setErrorMessage(truncateError(errorMessage));
                job.setFinishedAt(new Timestamp(System.currentTimeMillis()));
            });
        } catch (Exception e) {
            logger.error("标记视频任务失败状态时出错 jobId={}", jobId, e);
        }
    }

    public Optional<VideoRenderJob> findById(String jobId) {
        return findByIdAndUserId(jobId, null);
    }

    public Optional<VideoRenderJob> findByIdAndUserId(String jobId, Integer userId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        Optional<VideoRenderJob> jobOpt = load(jobId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }
        VideoRenderJob job = jobOpt.get();
        if (userId != null && job.getUserId() != userId) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    private void update(String jobId, Consumer<VideoRenderJob> mutator) {
        VideoRenderJob job = load(jobId).orElseThrow(
                () -> new IllegalStateException("视频任务不存在: " + jobId));
        mutator.accept(job);
        save(job);
    }

    private void save(VideoRenderJob job) {
        try {
            String json = objectMapper.writeValueAsString(toNode(job));
            redisService.setWithExpiry(KEY_PREFIX + job.getId(), json, JOB_TTL_SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("保存视频任务到 Redis 失败 jobId=" + job.getId(), e);
        }
    }

    private Optional<VideoRenderJob> load(String jobId) {
        try {
            String json = redisService.get(KEY_PREFIX + jobId);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(fromNode(objectMapper.readTree(json)));
        } catch (Exception e) {
            logger.error("从 Redis 读取视频任务失败 jobId={}", jobId, e);
            return Optional.empty();
        }
    }

    private ObjectNode toNode(VideoRenderJob job) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("id", job.getId());
        n.put("userId", job.getUserId());
        n.put("musicId", job.getMusicId());
        n.put("startSec", job.getStartSec());
        n.put("durationSec", job.getDurationSec());
        n.put("watermarked", job.isWatermarked());
        n.put("status", job.getStatus() != null ? job.getStatus() : "pending");
        if (job.getErrorMessage() != null) {
            n.put("errorMessage", job.getErrorMessage());
        } else {
            n.putNull("errorMessage");
        }
        if (job.getOutputRelPath() != null) {
            n.put("outputRelPath", job.getOutputRelPath());
        } else {
            n.putNull("outputRelPath");
        }
        if (job.getCreatedAt() != null) {
            n.put("createdAtMs", job.getCreatedAt().getTime());
        } else {
            n.putNull("createdAtMs");
        }
        if (job.getFinishedAt() != null) {
            n.put("finishedAtMs", job.getFinishedAt().getTime());
        } else {
            n.putNull("finishedAtMs");
        }
        return n;
    }

    private static VideoRenderJob fromNode(JsonNode n) {
        VideoRenderJob job = new VideoRenderJob();
        job.setId(n.path("id").asText(null));
        job.setUserId(n.path("userId").asInt(0));
        job.setMusicId(n.path("musicId").asInt(0));
        job.setStartSec(n.path("startSec").asDouble(0));
        job.setDurationSec(n.path("durationSec").asDouble(0));
        job.setWatermarked(n.path("watermarked").asBoolean(false));
        job.setStatus(n.path("status").asText("pending"));
        if (n.has("errorMessage") && !n.get("errorMessage").isNull()) {
            job.setErrorMessage(n.get("errorMessage").asText());
        }
        if (n.has("outputRelPath") && !n.get("outputRelPath").isNull()) {
            job.setOutputRelPath(n.get("outputRelPath").asText());
        }
        if (n.has("createdAtMs") && !n.get("createdAtMs").isNull()) {
            job.setCreatedAt(new Timestamp(n.get("createdAtMs").asLong()));
        }
        if (n.has("finishedAtMs") && !n.get("finishedAtMs").isNull()) {
            job.setFinishedAt(new Timestamp(n.get("finishedAtMs").asLong()));
        }
        return job;
    }

    private static String truncateError(String message) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        if (m.length() <= 512) {
            return m;
        }
        return m.substring(0, 512);
    }
}
