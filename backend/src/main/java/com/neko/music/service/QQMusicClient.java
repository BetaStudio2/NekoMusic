package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * QQ 音乐歌单只读客户端：封装 c.y.qq.com 的歌单详情接口，供代理接口与歌单导入共用。
 */
public class QQMusicClient {
    private static final String UPSTREAM = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public QQMusicClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record QqTrack(String mid, String title, String artist) {
    }

    public record QqPlaylist(String disstid, String name, List<QqTrack> tracks) {
    }

    /** 上游返回非 2xx 时抛出，携带状态码供上层映射响应。 */
    public static class UpstreamException extends IOException {
        private final int statusCode;

        public UpstreamException(int statusCode) {
            super("QQ 音乐接口 HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    /** 原始上游响应（不包装），供 /loser/qq/getSongListDetail 代理。 */
    public JsonNode fetchPlaylistDetailRaw(String disstid) throws IOException {
        HttpRequest upstreamRequest = HttpRequest.newBuilder()
                .uri(URI.create(buildUpstreamUrl(disstid)))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://c.y.qq.com/")
                .header("User-Agent", "Mozilla/5.0 (NekoMusic QQMusic playlist proxy)")
                .GET()
                .build();
        try {
            HttpResponse<String> upstreamResponse = httpClient.send(
                    upstreamRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (upstreamResponse.statusCode() < 200 || upstreamResponse.statusCode() >= 300) {
                throw new UpstreamException(upstreamResponse.statusCode());
            }
            JsonNode json = objectMapper.readTree(upstreamResponse.body());
            if (json == null || json.isMissingNode()) {
                throw new IOException("QQ 音乐接口返回为空");
            }
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求 QQ 音乐接口被中断", e);
        }
    }

    /** 解析歌单名与曲目（仅元数据，QQ 歌单详情不含可下载直链）。 */
    public QqPlaylist fetchPlaylist(String disstid) throws IOException {
        JsonNode root = fetchPlaylistDetailRaw(disstid);
        JsonNode cdlist = root.path("cdlist");
        JsonNode first = cdlist.isArray() && !cdlist.isEmpty() ? cdlist.get(0) : null;
        String name = first == null ? "" : first.path("dissname").asText("");

        List<QqTrack> tracks = new ArrayList<>();
        if (first != null) {
            for (JsonNode song : first.path("songlist")) {
                String mid = song.path("mid").asText(song.path("songmid").asText(""));
                String title = song.path("name").asText(song.path("songname").asText("")).trim();
                String artist = joinSingers(song.path("singer"));
                if (!title.isEmpty()) {
                    tracks.add(new QqTrack(mid, title, artist));
                }
            }
        }
        return new QqPlaylist(disstid, name, tracks);
    }

    private static String joinSingers(JsonNode singers) {
        if (!singers.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode singer : singers) {
            String candidate = singer.path("name").asText("").trim();
            if (!candidate.isEmpty()) {
                names.add(candidate);
            }
        }
        return String.join(" / ", names);
    }

    private static String buildUpstreamUrl(String disstid) {
        String encodedDisstid = URLEncoder.encode(disstid, StandardCharsets.UTF_8);
        return UPSTREAM
                + "?format=json"
                + "&outCharset=utf-8"
                + "&type=1"
                + "&json=1"
                + "&utf8=1"
                + "&onlysong=0"
                + "&new_format=1"
                + "&g_tk=1124214810"
                + "&loginUin=0"
                + "&hostUin=0"
                + "&inCharset=utf8"
                + "&notice=0"
                + "&platform=yqq.json"
                + "&needNewCode=0"
                + "&disstid=" + encodedDisstid;
    }
}
