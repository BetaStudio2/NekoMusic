package com.neko.music.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.util.HttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 汽水公开歌单客户端，只负责歌单识别、读取和导入元数据。 */
public class QishuiMusicClient {

    private static final Logger logger = LoggerFactory.getLogger(QishuiMusicClient.class);
    private static final String AID = "386088";
    private static final String DEFAULT_DEVICE_ID = "7000000000000000001";
    private static final String DEFAULT_INSTALL_ID = "7000000000000000002";
    private static final String PC_API_BASE = "https://api.qishui.com/luna/pc";
    private static final String WEB_PLAYLIST_SHARE = "https://music.douyin.com/qishui/share/playlist";
    private static final String PC_UA = "LunaPC/3.2.1(343009595)";
    private static final String PC_VERSION_NAME = "3.2.1";
    private static final String PC_VERSION_CODE = "30020100";
    private static final String WEB_SHARE_UA =
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
    private static final int PLAYLIST_PAGE_SIZE = 100;
    private static final int PLAYLIST_MAX_PAGES = 100;

    private static final Pattern PLAYLIST_PATH_PATTERN =
            Pattern.compile("playlist/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYLIST_ID_PARAM_PATTERN =
            Pattern.compile("[?&]playlist_id=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_PARAM_PATTERN =
            Pattern.compile("[?&]id=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d{1,20}");
    private static final Pattern LONG_ID_PATTERN = Pattern.compile("\\b\\d{10,}\\b");
    private static final Pattern SHORT_LINK_PATTERN =
            Pattern.compile("qishui\\.douyin\\.com|douyin\\.com/s/", Pattern.CASE_INSENSITIVE);

    public record QishuiTrack(String id, String title, String artist, String album,
                              long durationMs, String coverUrl) {
    }

    public record QishuiPlaylist(String id, String name, String owner, String coverUrl,
                                 int trackCount, List<QishuiTrack> tracks) {
    }

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final HttpClient redirectClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final String deviceId;
    private final String installId;

    public QishuiMusicClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.deviceId = resolve("qishui.device_id", "QISHUI_DEVICE_ID", DEFAULT_DEVICE_ID);
        this.installId = resolve("qishui.install_id", "QISHUI_INSTALL_ID", DEFAULT_INSTALL_ID);
        this.httpClient = HttpTransport.create(Duration.ofSeconds(10));
    }

    public String extractPlaylistId(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;
        if (DIGITS_PATTERN.matcher(trimmed).matches()) return trimmed;
        String direct = extractIdFromUrl(trimmed);
        if (direct != null) return direct;
        Matcher longId = LONG_ID_PATTERN.matcher(trimmed);
        if (longId.find()) return longId.group();
        Matcher urls = URL_PATTERN.matcher(trimmed);
        while (urls.find()) {
            String url = urls.group();
            String urlId = extractIdFromUrl(url);
            if (urlId != null) return urlId;
            if (SHORT_LINK_PATTERN.matcher(url).find()) {
                String resolved = resolveRedirectId(url, 0);
                if (resolved != null) return resolved;
            }
        }
        return null;
    }

    private static String extractIdFromUrl(String value) {
        Matcher path = PLAYLIST_PATH_PATTERN.matcher(value);
        if (path.find()) return path.group(1);
        Matcher param = PLAYLIST_ID_PARAM_PATTERN.matcher(value);
        if (param.find()) return param.group(1);
        Matcher id = ID_PARAM_PATTERN.matcher(value);
        return id.find() ? id.group(1) : null;
    }

    private String resolveRedirectId(String url, int depth) {
        if (depth > 3) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("user-agent", WEB_SHARE_UA)
                    .GET().build();
            HttpResponse<String> response = HttpTransport.sendString(redirectClient, request,
                    "请求汽水分享链接被中断");
            String location = response.headers().firstValue("location").orElse("");
            if (location.isEmpty()) return null;
            String id = extractIdFromUrl(location);
            if (id != null) return id;
            return SHORT_LINK_PATTERN.matcher(location).find()
                    ? resolveRedirectId(location, depth + 1) : null;
        } catch (IllegalArgumentException | IOException e) {
            logger.debug("解析汽水短链失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    public QishuiPlaylist fetchPlaylist(String input) throws IOException {
        String playlistId = extractPlaylistId(input);
        if (playlistId == null) throw new IOException("汽水歌单链接或 ID 无效");
        QishuiPlaylist fromApi = fetchPlaylistFromApi(playlistId);
        if (fromApi != null && !fromApi.tracks().isEmpty()) return fromApi;
        QishuiPlaylist fromWeb = fetchPlaylistFromWeb(playlistId);
        if (fromWeb != null && !fromWeb.tracks().isEmpty()) return fromWeb;
        throw new IOException("汽水歌单为空或不可访问");
    }

    public JsonNode fetchPlaylistDetailRaw(String input) throws IOException {
        QishuiPlaylist playlist = fetchPlaylist(input);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("code", 0);
        response.put("playlist_id", playlist.id());
        response.put("name", playlist.name());
        response.put("owner", playlist.owner());
        response.put("cover", playlist.coverUrl());
        response.put("songnum", playlist.tracks().size());
        ArrayNode songlist = response.putArray("songlist");
        for (QishuiTrack track : playlist.tracks()) {
            ObjectNode song = songlist.addObject();
            song.put("id", track.id());
            song.put("name", track.title());
            song.put("album", track.album());
            song.put("duration", track.durationMs());
            song.put("cover", track.coverUrl());
            ArrayNode singers = song.putArray("singer");
            for (String artist : splitArtists(track.artist())) {
                singers.addObject().put("name", artist);
            }
        }
        return response;
    }

    private QishuiPlaylist fetchPlaylistFromApi(String playlistId) throws IOException {
        List<QishuiTrack> tracks = new ArrayList<>();
        JsonNode playlistNode = null;
        String cursor = "";
        Set<String> visitedCursors = new LinkedHashSet<>();
        for (int page = 0; page < PLAYLIST_MAX_PAGES; page++) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("playlist_id", playlistId);
            query.put("cursor", cursor);
            query.put("count", Integer.toString(PLAYLIST_PAGE_SIZE));
            JsonNode data;
            try {
                data = pcGet("/playlist/detail", query);
            } catch (IOException e) {
                if (page == 0) {
                    logger.warn("汽水 PC 歌单接口首页失败 playlistId={}: {}", playlistId, e.getMessage());
                    return null;
                }
                throw e;
            }
            if (playlistNode == null && data.path("playlist").isObject()) {
                playlistNode = data.path("playlist");
            }
            JsonNode resources = data.path("media_resources");
            if (!resources.isArray()) {
                if (page == 0) return null;
                throw new IOException("汽水歌单分页数据异常");
            }
            for (JsonNode resource : resources) {
                QishuiTrack track = parsePlaylistTrack(resource);
                if (track != null) tracks.add(track);
            }
            String next = data.path("next_cursor").asText("");
            boolean hasMore = data.path("has_more").asBoolean(false);
            if (resources.isEmpty() || !hasMore || next.isEmpty()
                    || next.equals(cursor) || !visitedCursors.add(next)) break;
            cursor = next;
        }
        return buildPlaylist(playlistId, playlistNode, tracks);
    }

    private QishuiPlaylist fetchPlaylistFromWeb(String playlistId) {
        try {
            String url = WEB_PLAYLIST_SHARE + "?playlist_id="
                    + URLEncoder.encode(playlistId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("user-agent", WEB_SHARE_UA)
                    .GET().build();
            HttpResponse<String> response = HttpTransport.sendString(httpClient, request,
                    "请求汽水歌单分享页被中断");
            if (!HttpTransport.isSuccess(response.statusCode())) return null;
            JsonNode router = extractRouterData(response.body());
            JsonNode page = router == null ? null : router.path("loaderData").path("playlist_page");
            if (page == null || !page.isObject()) return null;
            List<QishuiTrack> tracks = new ArrayList<>();
            for (JsonNode media : page.path("medias")) {
                QishuiTrack track = parsePlaylistTrack(media);
                if (track != null) tracks.add(track);
            }
            return buildPlaylist(playlistId, page.path("playlistInfo"), tracks);
        } catch (IOException e) {
            logger.warn("汽水歌单分享页解析失败 playlistId={}: {}", playlistId, e.getMessage());
            return null;
        }
    }

    JsonNode extractRouterData(String html) {
        if (html == null) return null;
        String marker = "_ROUTER_DATA = ";
        int start = html.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = html.indexOf(";\nfunction runWindowFn", start);
        if (end < 0) end = html.indexOf(";</script>", start);
        if (end < 0) return null;
        try {
            return objectMapper.readTree(html.substring(start, end));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    QishuiTrack parsePlaylistTrack(JsonNode resource) {
        if (resource == null || !resource.isObject()) return null;
        JsonNode entity = resource.path("entity");
        JsonNode track = entity.path("track_wrapper").path("track");
        if (!isObjectNode(track)) track = entity.path("track");
        if (!isObjectNode(track)) track = entity.path("video");
        if (!isObjectNode(track)) track = entity.path("ugc_video");
        if (!isObjectNode(track)) track = resource.path("track");
        if (!isObjectNode(track)) track = resource;

        String title = firstNonEmpty(track.path("name").asText(""), track.path("title").asText(""),
                track.path("desc").asText(""));
        if (title.isEmpty()) return null;
        String id = firstNonEmpty(track.path("id").asText(""), track.path("video_id").asText(""));
        String artist = joinArtistNames(track.path("artists"), track.path("author_info"));
        String album = track.path("album").path("name").asText("");
        long duration = track.path("duration").asLong(0);
        String cover = buildCoverUrl(track.path("album").path("url_cover"));
        if (cover.isEmpty()) cover = buildCoverUrl(track.path("cover_url"));
        return new QishuiTrack(id, title, artist, album, duration, cover);
    }

    private static QishuiPlaylist buildPlaylist(String playlistId, JsonNode node, List<QishuiTrack> tracks) {
        JsonNode playlist = isObjectNode(node) ? node : null;
        if (playlist == null) return new QishuiPlaylist(playlistId, "", "", "", tracks.size(), tracks);
        String id = firstNonEmpty(playlist.path("id").asText(""), playlistId);
        String name = firstNonEmpty(playlist.path("title").asText(""),
                playlist.path("public_title").asText(""), playlist.path("name").asText(""));
        String owner = firstNonEmpty(playlist.path("owner").path("nickname").asText(""),
                playlist.path("user_artist_info").path("user_brief").path("nickname").asText(""));
        String cover = buildCoverUrl(playlist.path("url_cover"));
        int count = playlist.path("count_tracks").asInt(tracks.size());
        return new QishuiPlaylist(id, name, owner, cover, count, tracks);
    }

    static String buildCoverUrl(JsonNode cover) {
        if (cover == null || cover.isMissingNode() || cover.isNull()) return "";
        if (cover.isTextual()) return cover.asText("").trim();
        String uri = cover.path("uri").asText("").trim();
        JsonNode urls = cover.path("urls");
        String base = urls.isArray() && !urls.isEmpty() ? urls.get(0).asText("").trim() : "";
        if (base.isEmpty()) return "";
        if (uri.isEmpty()) return base;
        String prefix = cover.path("template_prefix").asText("").trim();
        String url = base + uri;
        return prefix.isEmpty() ? url : url + "~" + prefix + "-resize:960:960.png";
    }

    private static String joinArtistNames(JsonNode artists, JsonNode authorInfo) {
        List<String> names = new ArrayList<>();
        if (artists != null && artists.isArray()) {
            for (JsonNode artist : artists) {
                String name = firstNonEmpty(artist.path("name").asText(""),
                        artist.path("user_info").path("nickname").asText(""));
                if (!name.isEmpty()) names.add(name);
            }
        }
        if (names.isEmpty() && isObjectNode(authorInfo)) {
            String name = authorInfo.path("name").asText("").trim();
            if (!name.isEmpty()) names.add(name);
        }
        return String.join(" / ", names);
    }

    private static boolean isObjectNode(JsonNode node) {
        return node != null && node.isObject() && !node.isEmpty();
    }

    private static List<String> splitArtists(String artist) {
        List<String> names = new ArrayList<>();
        if (artist == null || artist.isBlank()) return names;
        for (String part : artist.split("\\s*/\\s*")) {
            if (!part.isBlank()) names.add(part.trim());
        }
        return names;
    }

    private JsonNode pcGet(String path, Map<String, String> extraQuery) throws IOException {
        Map<String, String> query = pcCommonQuery();
        if (extraQuery != null) query.putAll(extraQuery);
        String url = PC_API_BASE + path + "?" + encodeForm(query);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("accept", "*/*")
                .header("user-agent", PC_UA)
                .header("x-luna-background-type", "foreground")
                .header("x-luna-is-background-req", "0")
                .header("x-luna-is-local-user", "0")
                .GET().build();
        HttpResponse<String> response = HttpTransport.sendString(httpClient, request,
                "请求汽水音乐接口被中断: " + path);
        if (!HttpTransport.isSuccess(response.statusCode())) {
            throw new IOException("汽水音乐接口 HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) throw new IOException("汽水音乐接口返回为空");
        JsonNode parsed = objectMapper.readTree(body);
        if (parsed == null || parsed.isMissingNode()) throw new IOException("汽水音乐接口返回为空");
        return parsed;
    }

    private Map<String, String> pcCommonQuery() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("aid", AID);
        query.put("app_name", "luna_pc");
        query.put("region", "cn");
        query.put("geo_region", "cn");
        query.put("os_region", "cn");
        query.put("sim_region", "");
        query.put("device_id", deviceId);
        query.put("cdid", "");
        query.put("iid", installId);
        query.put("version_name", PC_VERSION_NAME);
        query.put("version_code", PC_VERSION_CODE);
        query.put("channel", "official");
        query.put("build_mode", "master");
        query.put("network_carrier", "");
        query.put("ac", "wifi");
        query.put("tz_name", "Asia/Shanghai");
        query.put("resolution", "");
        query.put("device_platform", "windows");
        query.put("device_type", "Windows");
        query.put("os_version", "Windows 11 Home China");
        query.put("fp", deviceId);
        return query;
    }

    private static String encodeForm(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!result.isEmpty()) result.append('&');
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                            StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static String resolve(String property, String env, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(env);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
