package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * QQ 音乐歌单只读客户端：封装 musicu.fcg 的歌单详情接口，供代理接口与歌单导入共用。
 *
 * <p>使用 {@code music.srfDissInfo.DissInfo/CgiGetDiss}，它对新版歌单有效；
 * 老的 qzone 接口（fcg_ucc_getcdinfo_byids_cp.fcg）对不少公开歌单会直接返回
 * {@code check privacy error!}。</p>
 */
public class QQMusicClient {
    private static final String UPSTREAM = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String MODULE = "music.srfDissInfo.DissInfo";
    private static final String METHOD = "CgiGetDiss";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

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

    /**
     * 歌单详情，整理成与传统 qzone 接口一致的 {@code {code, subcode, msg, cdlist[]}} 结构，
     * 供 /loser/qq/getSongListDetail 代理与导入共用。
     */
    public JsonNode fetchPlaylistDetailRaw(String disstid) throws IOException {
        long numericDisstid;
        try {
            numericDisstid = Long.parseLong(disstid);
        } catch (NumberFormatException e) {
            throw new IOException("QQ 歌单 ID 无效");
        }
        if (numericDisstid <= 0) {
            throw new IOException("QQ 歌单 ID 无效");
        }

        ObjectNode param = objectMapper.createObjectNode();
        param.put("disstid", numericDisstid);
        param.put("dirid", 0);
        param.put("tag", 1);
        param.put("song_begin", 0);
        param.put("song_num", 0); // 0 表示返回歌单内全部曲目
        param.put("userinfo", 1);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("module", MODULE);
        request.put("method", METHOD);
        request.set("param", param);

        ObjectNode comm = objectMapper.createObjectNode();
        comm.put("ct", 6);
        comm.put("cv", "80600");
        comm.put("mn", 0);
        comm.put("format", "json");
        comm.put("inCharset", "utf-8");
        comm.put("outCharset", "utf-8");
        comm.put("notice", 0);
        comm.put("platform", "yqq.json");
        comm.put("needNewCode", 0);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("comm", comm);
        body.set("req_0", request);

        HttpRequest upstreamRequest = HttpRequest.newBuilder()
                .uri(URI.create(UPSTREAM))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0 (NekoMusic QQMusic playlist proxy)")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
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
            return toLegacyPlaylistNode(json);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求 QQ 音乐接口被中断", e);
        }
    }

    /** 把 musicu.fcg 的响应转换成旧接口的 cdlist 结构，保持对上层与客户端的兼容。 */
    private ObjectNode toLegacyPlaylistNode(JsonNode root) {
        JsonNode data = root.path("req_0").path("data");

        ObjectNode response = objectMapper.createObjectNode();
        int code = data.path("code").asInt(0);
        int subcode = data.path("subcode").asInt(0);
        String msg = data.path("msg").asText("");
        response.put("code", code);
        response.put("subcode", subcode);
        if (!msg.isEmpty()) {
            response.put("msg", msg);
        }
        if (code != 0) {
            // 业务错误（歌单不存在 / 不可访问等）只回错误信息，不带 cdlist
            return response;
        }

        JsonNode dirinfo = data.path("dirinfo");
        ObjectNode cd = objectMapper.createObjectNode();
        cd.put("disstid", dirinfo.path("id").asLong(0));
        cd.put("dissid", dirinfo.path("id").asLong(0));
        cd.put("dirid", dirinfo.path("dirid").asInt(0));
        cd.put("dissname", dirinfo.path("title").asText(""));
        cd.put("desc", dirinfo.path("desc").asText(""));
        cd.put("logo", dirinfo.path("picurl").asText(""));
        cd.put("songnum", data.path("total_song_num").asInt(0));

        ArrayNode songlist = cd.putArray("songlist");
        for (JsonNode song : data.path("songlist")) {
            ObjectNode entry = songlist.addObject();
            entry.put("mid", song.path("mid").asText(song.path("songmid").asText("")));
            entry.put("name", song.path("name").asText(song.path("title").asText("")).trim());
            ArrayNode singers = entry.putArray("singer");
            for (JsonNode singer : song.path("singer")) {
                ObjectNode item = singers.addObject();
                item.put("name", singer.path("name").asText("").trim());
            }
        }

        response.putArray("cdlist").add(cd);
        return response;
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

}
