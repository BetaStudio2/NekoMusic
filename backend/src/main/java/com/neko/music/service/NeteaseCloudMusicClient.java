package com.neko.music.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 访问 NeteaseCloudMusicApi（如 /search、/song/url/v1、/lyric）。
 */
public class NeteaseCloudMusicClient {
    private static final Logger logger = LoggerFactory.getLogger(NeteaseCloudMusicClient.class);

    private final ConfigManager config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NeteaseCloudMusicClient(ConfigManager config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(config.getNeteaseHttpTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public record NeteaseSongCandidate(long id, String title, String artist, String album) {}

    public record SongPlayUrl(String url, String type, int durationMs, String level) {}

    public record SongDetail(String title, String artist, String album, String coverUrl, int durationMs) {}

    /** /lyric 接口返回：用于校验的主歌词 + 供管理员邮件展示的可读原文（去重，无 JSON）。 */
    public record LyricApiPayload(String primaryLrc, String lyricsEmailBody) {}

    /**
     * 调用 NeteaseCloudMusicApi {@code /login/status}。
     * {@code profile == null} 表示未登录或 Cookie 已失效（与官方文档一致，HTTP 仍为 200）。
     */
    public boolean isLoggedIn() {
        try {
            JsonNode root = getJson("/login/status");
            JsonNode profile = root.path("data").path("profile");
            return !profile.isNull() && !profile.isMissingNode();
        } catch (IOException e) {
            logger.warn("查询网易云登录状态失败: {}", e.getMessage());
            return false;
        }
    }

    public List<NeteaseSongCandidate> searchSongs(String keywords, int limit) throws IOException {
        String encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
        String path = "/cloudsearch?keywords=" + encoded + "&type=1&limit=" + Math.max(1, limit);
        JsonNode root = getJson(path);
        JsonNode songs = root.path("result").path("songs");
        if (!songs.isArray()) {
            return List.of();
        }
        List<NeteaseSongCandidate> out = new ArrayList<>();
        for (JsonNode song : songs) {
            long id = song.path("id").asLong(0);
            if (id <= 0) {
                continue;
            }
            String title = textOrEmpty(song.path("name"));
            String artist = joinArtists(song.path("ar"));
            String album = textOrEmpty(song.path("al").path("name"));
            if (!title.isEmpty()) {
                out.add(new NeteaseSongCandidate(id, title, artist, album));
            }
        }
        return out;
    }

    public Optional<SongDetail> fetchSongDetail(long songId) throws IOException {
        JsonNode root = getJson("/song/detail?ids=" + songId);
        JsonNode songs = root.path("songs");
        if (!songs.isArray() || songs.isEmpty()) {
            return Optional.empty();
        }
        JsonNode song = songs.get(0);
        String title = textOrEmpty(song.path("name"));
        String artist = joinArtists(song.path("ar"));
        String album = textOrEmpty(song.path("al").path("name"));
        String coverUrl = textOrEmpty(song.path("al").path("picUrl"));
        int durationMs = song.path("dt").asInt(0);
        return Optional.of(new SongDetail(title, artist, album, coverUrl, durationMs));
    }

    /**
     * 从配置的 preferredLevel 起逐级降档，直到 API 实际返回的 level 与请求 level 一致（避免要 hires 却拿到 320k）。
     */
    public Optional<SongPlayUrl> resolvePlayUrl(long songId, String preferredLevel) throws IOException {
        List<String> levels = qualityFallbackChain(preferredLevel);
        for (String requestedLevel : levels) {
            JsonNode root = getJson("/song/url/v1?id=" + songId + "&level=" + requestedLevel);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                continue;
            }
            JsonNode item = data.get(0);
            if (item.path("code").asInt(0) != 200) {
                continue;
            }
            String url = textOrEmpty(item.path("url"));
            if (url.isEmpty()) {
                continue;
            }
            String actualLevel = textOrEmpty(item.path("level")).toLowerCase(Locale.ROOT);
            if (!actualLevel.isEmpty() && !actualLevel.equals(requestedLevel)) {
                logger.debug("网易云 songId={} 请求 {} 实际返回 {}，尝试更低档位", songId, requestedLevel, actualLevel);
                continue;
            }
            String type = textOrEmpty(item.path("type"));
            if (type.isEmpty()) {
                type = "mp3";
            }
            int durationMs = item.path("time").asInt(0);
            String resolvedLevel = actualLevel.isEmpty() ? requestedLevel : actualLevel;
            logger.info("网易云取链成功 songId={} level={} type={}", songId, resolvedLevel, type);
            return Optional.of(new SongPlayUrl(url, type, durationMs, resolvedLevel));
        }
        return Optional.empty();
    }

    public String fetchLyricLrc(long songId) throws IOException {
        return fetchLyricPayload(songId).primaryLrc();
    }

    public LyricApiPayload fetchLyricPayload(long songId) throws IOException {
        JsonNode standardRoot = getJson("/lyric?id=" + songId);
        JsonNode newRoot = fetchLyricNewRoot(songId);
        String primary = textOrEmpty(standardRoot.path("lrc").path("lyric"));
        if (primary.isBlank()) {
            primary = textOrEmpty(standardRoot.path("tlyric").path("lyric"));
        }
        if (primary.isBlank() && newRoot != null) {
            primary = textOrEmpty(newRoot.path("lrc").path("lyric"));
        }
        return new LyricApiPayload(primary, buildLyricsEmailBody(standardRoot, newRoot));
    }

    private JsonNode fetchLyricNewRoot(long songId) {
        try {
            return getJson("/lyric/new?id=" + songId);
        } catch (IOException e) {
            logger.debug("lyric/new 请求失败 songId={}: {}", songId, e.getMessage());
            return null;
        }
    }

    /**
     * 管理员邮件：仅可读歌词文本，按类型分段，内容去重（不含接口路径、不含原始 JSON）。
     */
    private String buildLyricsEmailBody(JsonNode standardRoot, JsonNode newRoot) {
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();

        appendLyricsPart(sb, seen, "原文（LRC）", standardRoot.path("lrc").path("lyric"));
        appendLyricsPart(sb, seen, "翻译", standardRoot.path("tlyric").path("lyric"));
        appendLyricsPart(sb, seen, "罗马音", standardRoot.path("romalrc").path("lyric"));
        appendLyricsPart(sb, seen, "逐字歌词", standardRoot.path("yrc").path("lyric"));

        if (newRoot != null) {
            String newLrc = textOrEmpty(newRoot.path("lrc").path("lyric"));
            String newText = flattenLyricBlob(newLrc);
            if (!newText.isBlank() && seen.add(dedupKey(newText))) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("【新版歌词格式】\n").append(newText);
            }
        }

        if (sb.isEmpty()) {
            return "（网易云未返回歌词文本）";
        }
        return sb.toString().strip();
    }

    private void appendLyricsPart(StringBuilder sb, Set<String> seen, String label, JsonNode lyricNode) {
        String flat = flattenLyricBlob(textOrEmpty(lyricNode));
        if (flat.isBlank() || !seen.add(dedupKey(flat))) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append('【').append(label).append("】\n").append(flat);
    }

    /** 将 LRC 行或 lyric/new 的 JSON 行转为纯文本。 */
    private String flattenLyricBlob(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("{") && t.contains("\"tx\"")) {
                t = parseNewLyricJsonLine(t);
            }
            if (t.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(t);
        }
        return out.toString();
    }

    private String parseNewLyricJsonLine(String jsonLine) {
        try {
            JsonNode node = objectMapper.readTree(jsonLine);
            JsonNode chars = node.path("c");
            if (!chars.isArray()) {
                return jsonLine;
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : chars) {
                sb.append(textOrEmpty(part.path("tx")));
            }
            return sb.toString().trim();
        } catch (JsonProcessingException e) {
            return jsonLine;
        }
    }

    /** 去重：去掉时间轴后比较，避免 LRC 行与新版 JSON 解析结果重复展示。 */
    private static String dedupKey(String flatText) {
        StringBuilder key = new StringBuilder();
        for (String line : flatText.split("\\r?\\n")) {
            String l = line.trim();
            l = l.replaceFirst("^\\[\\d{2}:\\d{2}\\.\\d{1,5}[^\\]]*\\]\\s*", "");
            if (l.isEmpty()) {
                continue;
            }
            if (!key.isEmpty()) {
                key.append('|');
            }
            l = l.replaceAll("\\s*:\\s*", ":").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            key.append(l);
        }
        return key.toString();
    }

    public void downloadToFile(String url, Path destination) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getNeteaseHttpTimeoutSeconds()))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("下载失败 HTTP " + response.statusCode());
            }
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = response.body()) {
                Files.copy(in, destination);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载被中断", e);
        }
    }

    private JsonNode getJson(String pathWithQuery) throws IOException {
        String base = config.getNeteaseApiBaseUrl();
        String url = base + (pathWithQuery.startsWith("/") ? pathWithQuery : "/" + pathWithQuery);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getNeteaseHttpTimeoutSeconds()))
                .GET();
        String cookie = config.getNeteaseCookie();
        if (cookie != null && !cookie.isBlank()) {
            builder.header("Cookie", cookie);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Netease API HTTP " + response.statusCode() + " url=" + url);
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    private static List<String> qualityFallbackChain(String preferred) {
        String p = preferred == null ? "" : preferred.trim().toLowerCase(Locale.ROOT);
        List<String> chain = new ArrayList<>();
        if (!p.isEmpty()) {
            chain.add(p);
        }
        // 从高到低依次尝试
        for (String level : List.of(
                "jymaster", "hires", "jyeffect", "sky",
                "lossless", "exhigh", "higher", "standard")) {
            if (!chain.contains(level)) {
                chain.add(level);
            }
        }
        return chain;
    }

    private static String joinArtists(JsonNode artists) {
        if (!artists.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode ar : artists) {
            String name = textOrEmpty(ar.path("name"));
            if (name.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        return node.asText("").trim();
    }
}
