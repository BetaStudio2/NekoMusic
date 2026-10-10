package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.util.HttpTransport;
import com.neko.music.util.OutboundUrlGuard;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 酷狗音乐歌单只读客户端：解析歌单 ID / 分享链接并拉取曲目，供代理接口与歌单导入共用。
 *
 * <p>曲目元数据通过网关 {@code /pubsongs/v2/get_other_list_file_nofilt}（android 签名）分页获取；
 * 当输入为 PC 分享链接或数字 specialid 时，先用 mobilecdn 的 special/info 换取
 * {@code global_collection_id}（即 global_specialid）。移动端接口无需签名，作为兜底。</p>
 *
 * <p>酷狗只提供元数据（歌名 / 歌手），不含可下载直链；导入时由上层站内匹配或从网易云补全。</p>
 */
public class KugouMusicClient {
    private static final String GATEWAY = "https://gateway.kugou.com";
    private static final String MOBILECDN = "http://mobilecdn.kugou.com";
    private static final String USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi";
    private static final String SIGN_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA";
    private static final String APPID = "3116";
    private static final String CLIENTVER = "11440";
    private static final String KG_RF = "B9EDA08A64250DEFFBCADDEE00F8F25F";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 60;
    /** 分享链接里 gcid 为不可逆的短码，只能靠 uid 扫描公开歌单 listid 反查（上限）。 */
    private static final int MAX_LIST_ID_SCAN = 200;
    private static final int SCAN_THREADS = 8;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final Set<String> AUDIO_EXTS = Set.of(
            "mp3", "flac", "m4a", "aac", "ogg", "ape", "wav", "wma", "ac3", "aiff", "alac", "opus");

    private static final Pattern GID_PATTERN = Pattern.compile("^collection_[A-Za-z0-9_]+$");
    private static final Pattern SPECIAL_LINK_PATTERN =
            Pattern.compile("(?:special/single/|specialid=|/special/)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS_PATTERN = Pattern.compile("^\\d+$");
    /** 移动端分享链接里的编码歌单 ID（形如 gcid_3zmi8f5nz5z0c4）。 */
    private static final Pattern GCID_PATTERN = Pattern.compile("gcid_[A-Za-z0-9]+");
    /** 裸 token（gcid / 单曲 hash）：只允许字母数字下划线，直接当路径片段使用。 */
    private static final Pattern BARE_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_]+");
    /** 移动端分享页（songlist）链接。 */
    private static final Pattern SHARE_URL_PATTERN =
            Pattern.compile("kugou\\.com/songlist/", Pattern.CASE_INSENSITIVE);
    /** 移动端单曲分享链接（{@code /share/?action=single&hash=...}）。 */
    private static final Pattern SINGLE_SHARE_PATTERN =
            Pattern.compile("(?:/share/|action=single)", Pattern.CASE_INSENSITIVE);
    /**
     * 唯一接受的歌单分享链接路径：{@code /songlist/<token>}（结尾斜杠可选）。
     * token 只允许字母数字下划线，出站路径由它重新拼出，链接里的其它路径与查询串一律丢弃。
     */
    private static final Pattern SONGLIST_PATH_PATTERN =
            Pattern.compile("^/songlist/([A-Za-z0-9_]{3,64})/?$");
    /** 单曲分享链接路径：{@code /share} 或 {@code /share/}，具体形态由查询参数校验。 */
    private static final Pattern SINGLE_SHARE_PATH_PATTERN = Pattern.compile("^/share/?$");
    private static final String WINDOW_OUTPUT_MARKER = "window.$output";

    /**
     * 允许访问的上游域名：网关 gateway.kugou.com、移动端 mobilecdn.kugou.com、分享页 m.kugou.com
     * 都属于 kugou.com 子域。其它地址一律按非法入参处理，不做任何请求。
     */
    private static final Set<String> ALLOWED_HOSTS = Set.of("kugou.com");
    /**
     * 分享页固定走移动端官方域名：主机名写死在代码里；入参（含用户粘贴的分享链接）只允许
     * {@code /songlist/<token>} 与单曲分享两种形态，出站路径由校验过的 token 重新拼接，
     * 因此入参既影响不了出站主机，也无法向出站请求里夹带任何自己的路径或查询串。
     */
    private static final String SHARE_PAGE_BASE = "https://m.kugou.com/";
    /** 分享页重定向（含短链）跟随上限。 */
    private static final int MAX_SHARE_REDIRECTS = 3;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpTransport.create(REQUEST_TIMEOUT);
    private final HttpClient sharePageClient = HttpTransport.createWithoutRedirects(REQUEST_TIMEOUT);
    private final SecureRandom secureRandom = new SecureRandom();

    public KugouMusicClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record KugouTrack(String hash, String title, String artist) {
    }

    public record KugouPlaylist(String listId, String name, List<KugouTrack> tracks) {
    }

    /** 上游返回非 2xx 时抛出，携带状态码供上层映射响应。 */
    public static class UpstreamException extends IOException {
        private final int statusCode;

        public UpstreamException(int statusCode) {
            super("酷狗接口 HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    /** 入参不是受支持的酷狗歌单 ID / 官方域名分享链接时抛出（对外映射 400）。 */
    public static class InvalidInputException extends IOException {
        public InvalidInputException(String message) {
            super(message);
        }

        public InvalidInputException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 歌单详情，整理成 {@code {code, listid, name, songnum, songlist[]}} 结构，
     * 供 /loser/kugou/getSongListDetail 代理与导入共用。
     */
    public JsonNode fetchPlaylistDetailRaw(String input) throws IOException {
        KugouPlaylist playlist = fetchPlaylist(input);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("code", 0);
        response.put("listid", playlist.listId());
        response.put("name", playlist.name());
        response.put("songnum", playlist.tracks().size());

        ArrayNode songlist = response.putArray("songlist");
        for (KugouTrack track : playlist.tracks()) {
            ObjectNode song = songlist.addObject();
            song.put("hash", track.hash());
            song.put("name", track.title());
            ArrayNode singers = song.putArray("singer");
            for (String artist : splitArtists(track.artist())) {
                singers.addObject().put("name", artist);
            }
        }
        return response;
    }

    /** 解析歌单名与曲目（仅元数据，酷狗歌单详情不含可下载直链）。 */
    public KugouPlaylist fetchPlaylist(String input) throws IOException {
        if (input == null || input.isBlank()) {
            throw new InvalidInputException("酷狗歌单链接或 ID 无效");
        }
        String trimmed = input.trim();

        String globalId = resolveGlobalId(trimmed);
        if (globalId != null) {
            return fetchByGlobalId(globalId);
        }

        // 移动端单曲分享链接：解析出单曲元数据，下载交由上层已有解析器处理
        if (SINGLE_SHARE_PATTERN.matcher(trimmed).find()) {
            return fetchSingleSong(trimmed);
        }

        // 移动端分享链接 / gcid：公网无全量接口，退化为解析分享页内嵌数据
        if (GCID_PATTERN.matcher(trimmed).find() || SHARE_URL_PATTERN.matcher(trimmed).find()) {
            return fetchFromSharePage(trimmed);
        }

        String specialId = extractSpecialId(trimmed);
        if (specialId != null) {
            return fetchBySpecialId(specialId);
        }
        throw new InvalidInputException("酷狗歌单链接或 ID 无效");
    }

    /** 输入若已是 global_collection_id 直接返回；数字 / 链接先换取。失败返回 null。 */
    private String resolveGlobalId(String input) {
        if (GID_PATTERN.matcher(input).matches()) {
            return input;
        }
        String specialId = extractSpecialId(input);
        if (specialId == null) {
            return null;
        }
        try {
            JsonNode data = mobilecdnGet("/api/v3/special/info",
                    "specialid=" + specialId + "&version=9108").path("data");
            String gid = data.path("global_specialid").asText("").trim();
            return gid.isEmpty() ? null : gid;
        } catch (IOException e) {
            return null;
        }
    }

    private String extractSpecialId(String input) {
        if (DIGITS_PATTERN.matcher(input).matches()) {
            return input;
        }
        Matcher link = SPECIAL_LINK_PATTERN.matcher(input);
        if (link.find()) {
            return link.group(1);
        }
        return null;
    }

    /**
     * 解析移动端分享页（{@code m.kugou.com/songlist/gcid_xxx/}）内嵌的
     * {@code window.$output}。
     *
     * <p>gcid 是酷狗不可逆的短码，公网没有直接解析接口；分享页内嵌
     * {@code list_create_userid}，据此扫描该用户的公开歌单
     * {@code collection_3_<uid>_<listid>_0}（网关接口）反查全量曲目；
     * 扫描失败时退化为分享页内嵌的前若干首。</p>
     */
    private KugouPlaylist fetchFromSharePage(String input) throws IOException {
        String relative = isBareShareToken(input)
                ? "songlist/" + input + "/"
                : shareRelativePath(input);
        String html = httpGetString(relative);
        JsonNode output = extractWindowOutput(html);
        if (output == null) {
            throw new IOException("酷狗分享页解析失败");
        }

        JsonNode listInfo = output.path("info").path("listinfo");
        String name = listInfo.path("name").asText("").trim();
        long uid = listInfo.path("list_create_userid").asLong(0);

        String gcid = output.path("encode_gic").asText("").trim();
        if (gcid.isEmpty()) {
            Matcher matcher = GCID_PATTERN.matcher(input);
            gcid = matcher.find() ? matcher.group() : input;
        }

        // 分享页直接带 global_collection_id（公开歌单）时优先走网关全量
        String globalId = output.path("global_collection_id").asText("").trim();
        if (globalId.isEmpty()) {
            globalId = listInfo.path("global_collection_id").asText("").trim();
        }
        if (globalId.startsWith("collection_")) {
            return fetchByGlobalId(globalId);
        }

        // 分享页内嵌的曲目 hash：用于精确锁定「被分享的那一个」歌单，避免同名误匹配
        Set<String> shareHashes = new HashSet<>();
        for (JsonNode song : output.path("info").path("songs")) {
            String hash = song.path("hash").asText("").trim().toUpperCase(Locale.ROOT);
            if (!hash.isEmpty()) {
                shareHashes.add(hash);
            }
        }

        // gcid 无法解码：按 uid 扫描公开歌单，用「歌名 + hash」精确匹配后走网关全量
        if (uid > 0 && (!name.isEmpty() || !shareHashes.isEmpty())) {
            String matched = scanUserListId(uid, name, shareHashes);
            if (matched != null) {
                return fetchByGlobalId(matched);
            }
        }

        List<KugouTrack> tracks = new ArrayList<>();
        for (JsonNode song : output.path("info").path("songs")) {
            String merged = song.path("name").asText("").trim();
            if (merged.isEmpty()) {
                merged = song.path("filename").asText(song.path("songname").asText("")).trim();
            }
            addMergedTrack(tracks, song.path("hash").asText(""), merged);
        }
        if (tracks.isEmpty()) {
            throw new IOException("酷狗歌单为空或不可访问");
        }
        return new KugouPlaylist(gcid, name, tracks);
    }

    /**
     * 并发扫描 {@code collection_3_<uid>_<listid>_0}，返回首个命中歌单的 gid。
     *
     * <p>命中判定：歌名一致（若分享页有名字）且候选歌单首页曲目 hash 与分享页
     * 内嵌 hash 有交集（若分享页有曲目）。hash 用于精确锁定被分享的那一个歌单，
     * 避免用户存在多个同名歌单时误选。</p>
     */
    private String scanUserListId(long uid, String expectedName, Set<String> shareHashes) {
        AtomicInteger threadNo = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(SCAN_THREADS, r -> {
            Thread thread = new Thread(r, "kugou-list-scan-" + threadNo.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<String>> futures = new ArrayList<>(MAX_LIST_ID_SCAN);
            for (int listId = 1; listId <= MAX_LIST_ID_SCAN; listId++) {
                final int lid = listId;
                futures.add(pool.submit(() -> matchesList(uid, lid, expectedName, shareHashes)
                        ? globalCollectionId(uid, lid) : null));
            }
            for (Future<String> future : futures) {
                try {
                    String matched = future.get();
                    if (matched != null) {
                        for (Future<String> other : futures) {
                            other.cancel(true);
                        }
                        return matched;
                    }
                } catch (Exception ignored) {
                    // 单个 listid 查询失败不影响整体扫描
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return null;
    }

    private boolean matchesList(long uid, int listId, String expectedName, Set<String> shareHashes) {
        try {
            JsonNode data = gatewayGet("/pubsongs/v2/get_other_list_file_nofilt",
                    listParams(globalCollectionId(uid, listId), 30)).path("data");
            String name = data.path("list_info").path("name").asText("").trim();
            if (!expectedName.isEmpty() && !expectedName.equals(name)) {
                return false;
            }
            if (shareHashes.isEmpty()) {
                return true;
            }
            for (JsonNode song : data.path("songs")) {
                String hash = song.path("hash").asText("").trim().toUpperCase(Locale.ROOT);
                if (shareHashes.contains(hash)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String globalCollectionId(long uid, int listId) {
        return "collection_3_" + uid + "_" + listId + "_0";
    }

    private static TreeMap<String, String> listParams(String globalId, int pageSize) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("area_code", "1");
        params.put("begin_idx", "0");
        params.put("plat", "1");
        params.put("type", "1");
        params.put("mode", "1");
        params.put("personal_switch", "1");
        params.put("extend_fields", "abtags,hot_cmt,popularization");
        params.put("pagesize", Integer.toString(pageSize));
        params.put("global_collection_id", globalId);
        return params;
    }

    /** 从分享页 HTML 中截取 {@code window.$output = {...};} 的 JSON 对象。 */
    private JsonNode extractWindowOutput(String html) {
        return extractAssignedObject(html, WINDOW_OUTPUT_MARKER);
    }

    /** 从 HTML 中截取 {@code <marker> = {...};} 的 JSON 对象（花括号配平）。 */
    private JsonNode extractAssignedObject(String html, String marker) {
        int markerIndex = html.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int brace = html.indexOf('{', markerIndex);
        if (brace < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = brace; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        return objectMapper.readTree(html.substring(brace, i + 1));
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /** 网关分页拉取 global_collection_id 对应的全部曲目。 */
    private KugouPlaylist fetchByGlobalId(String globalId) throws IOException {
        List<KugouTrack> tracks = new ArrayList<>();
        String name = "";
        int total = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            TreeMap<String, String> params = listParams(globalId, PAGE_SIZE);
            params.put("begin_idx", Integer.toString(page * PAGE_SIZE));

            JsonNode root = gatewayGet("/pubsongs/v2/get_other_list_file_nofilt", params);
            JsonNode data = root.path("data");
            JsonNode listInfo = data.path("list_info");
            if (name.isEmpty()) {
                name = listInfo.path("name").asText("").trim();
            }
            if (total <= 0) {
                total = listInfo.path("count").asInt(0);
            }

            JsonNode songs = data.path("songs");
            if (!songs.isArray() || songs.isEmpty()) {
                break;
            }
            for (JsonNode song : songs) {
                addMergedTrack(tracks, song.path("hash").asText(""), song.path("name").asText(""));
            }
            if (songs.size() < PAGE_SIZE || (total > 0 && tracks.size() >= total)) {
                break;
            }
        }
        if (tracks.isEmpty()) {
            throw new IOException("酷狗歌单为空或不可访问");
        }
        return new KugouPlaylist(globalId, name, tracks);
    }

    /** mobilecdn 兜底：数字 specialid 直接分页拉取。 */
    private KugouPlaylist fetchBySpecialId(String specialId) throws IOException {
        JsonNode info = mobilecdnGet("/api/v3/special/info",
                "specialid=" + specialId + "&version=9108").path("data");
        String name = info.path("specialname").asText("").trim();
        int total = info.path("songcount").asInt(0);

        List<KugouTrack> tracks = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode root = mobilecdnGet("/api/v3/special/song",
                    "specialid=" + specialId + "&page=" + page + "&pagesize=" + PAGE_SIZE + "&version=9108");
            JsonNode data = root.path("data");
            if (total <= 0) {
                total = data.path("total").asInt(0);
            }
            JsonNode infoList = data.path("info");
            if (!infoList.isArray() || infoList.isEmpty()) {
                break;
            }
            for (JsonNode song : infoList) {
                addMergedTrack(tracks, song.path("hash").asText(""), song.path("filename").asText(""));
            }
            if (infoList.size() < PAGE_SIZE || (total > 0 && tracks.size() >= total)) {
                break;
            }
        }
        if (tracks.isEmpty()) {
            throw new IOException("酷狗歌单为空或不可访问");
        }
        return new KugouPlaylist(specialId, name, tracks);
    }

    private void addMergedTrack(List<KugouTrack> tracks, String hash, String merged) {
        String[] split = splitMergedName(merged);
        if (!split[0].isEmpty()) {
            tracks.add(new KugouTrack(hash, split[0], split[1]));
        }
    }

    /** 把酷狗合并名 {@code "歌手 - 歌名.mp3"} 拆成 {title, artist}。 */
    private static String[] splitMergedName(String merged) {
        String raw = merged == null ? "" : merged.trim();
        if (raw.isEmpty()) {
            return new String[]{"", ""};
        }
        String body = raw;
        int dot = body.lastIndexOf('.');
        if (dot > 0 && dot > body.lastIndexOf(' ')) {
            String ext = body.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (AUDIO_EXTS.contains(ext)) {
                body = body.substring(0, dot);
            }
        }
        String title = body;
        String artist = "";
        int sep = body.indexOf(" - ");
        if (sep > 0) {
            artist = body.substring(0, sep).trim();
            title = body.substring(sep + 3).trim();
        }
        return new String[]{title, artist};
    }

    /**
     * 解析移动端单曲分享链接（{@code m.kugou.com/share/?action=single&hash=...}）。
     *
     * <p>只提取单曲元数据（歌名 / 歌手 / hash），返回单曲歌单；实际下载由上层
     * 站内匹配或网易云补全的既有解析器完成，不复用酷狗直链。</p>
     */
    private KugouPlaylist fetchSingleSong(String input) throws IOException {
        String relative = isBareShareToken(input)
                ? "share/?action=single&hash=" + input
                : shareRelativePath(input);
        String html = httpGetString(relative);
        JsonNode phpParam = extractAssignedObject(html, "var phpParam");
        if (phpParam == null) {
            throw new IOException("酷狗单曲分享页解析失败");
        }
        JsonNode song = phpParam.path("song_info").path("data");
        if (song.isMissingNode() || song.isEmpty()) {
            song = phpParam;
        }

        String hash = firstNonBlank(song.path("hash").asText(""), phpParam.path("hash").asText(""));
        String title = song.path("songName").asText("").trim();
        String artist = song.path("singerName").asText("").trim();
        if (artist.isEmpty()) {
            JsonNode authors = song.path("authors");
            if (authors.isArray() && !authors.isEmpty()) {
                artist = authors.get(0).path("author_name").asText("").trim();
            }
        }
        if (title.isEmpty() || artist.isEmpty()) {
            String[] split = splitMergedName(song.path("fileName").asText(""));
            if (title.isEmpty()) {
                title = split[0];
            }
            if (artist.isEmpty()) {
                artist = split[1];
            }
        }
        if (title.isEmpty()) {
            throw new IOException("酷狗单曲信息解析失败");
        }

        List<KugouTrack> tracks = new ArrayList<>(1);
        tracks.add(new KugouTrack(hash, title, artist));
        return new KugouPlaylist(hash, title, tracks);
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    private static List<String> splitArtists(String artist) {
        List<String> names = new ArrayList<>();
        if (artist == null || artist.isBlank()) {
            return names;
        }
        for (String part : artist.split("\\s*/\\s*")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }

    /** 带 android 签名的网关 GET，返回已校验 status/error_code 的响应体。 */
    private JsonNode gatewayGet(String path, TreeMap<String, String> extraParams) throws IOException {
        long clienttime = System.currentTimeMillis() / 1000L;
        String mid = calculateMid();

        TreeMap<String, String> params = new TreeMap<>();
        params.put("dfid", "-");
        params.put("mid", mid);
        params.put("uuid", "-");
        params.put("appid", APPID);
        params.put("clientver", CLIENTVER);
        params.put("clienttime", Long.toString(clienttime));
        params.putAll(extraParams);
        params.put("signature", signature(params));

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(entry.getKey()).append('=').append(urlEncode(entry.getValue()));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY + path + "?" + query))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("dfid", "-")
                .header("clienttime", Long.toString(clienttime))
                .header("mid", mid)
                .header("kg-rc", "1")
                .header("kg-thash", "5d816a0")
                .header("kg-rec", "1")
                .header("kg-rf", KG_RF)
                .GET()
                .build();
        JsonNode root = send(request);
        int status = root.path("status").asInt(0);
        int errorCode = root.path("error_code").asInt(0);
        if (status != 1 || errorCode != 0) {
            throw new IOException("酷狗接口返回错误（status=" + status + "）");
        }
        return root;
    }

    private JsonNode mobilecdnGet(String path, String query) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MOBILECDN + path + "?" + query))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return send(request);
    }

    /**
     * 抓取分享页 HTML（用于解析 {@code window.$output}）。
     *
     * <p>主机名固定为 {@link #SHARE_PAGE_BASE}，入参只提供相对路径与查询串，因此入参无法决定
     * 出站目标主机；每一跳仍先过 {@link OutboundUrlGuard}（协议、域名白名单、解析地址必须是
     * 公网），重定向不自动跟随、逐跳重新校验。</p>
     */
    private String httpGetString(String relativePathAndQuery) throws IOException {
        String current = SHARE_PAGE_BASE + relativePathAndQuery;
        for (int hop = 0; hop <= MAX_SHARE_REDIRECTS; hop++) {
            URI uri;
            try {
                uri = OutboundUrlGuard.requireAllowedHttpUrl(current, ALLOWED_HOSTS);
            } catch (OutboundUrlGuard.BlockedUrlException e) {
                throw new InvalidInputException("酷狗分享链接无效或不受支持（仅支持酷狗官方域名链接或歌单 ID）", e);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent",
                            "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15")
                    .header("Referer", "https://m.kugou.com/")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    HttpTransport.sendString(sharePageClient, request, "请求酷狗分享页被中断");
            if (isRedirect(response.statusCode())) {
                String location = response.headers().firstValue("location").orElse("").trim();
                if (location.isEmpty()) {
                    throw new IOException("酷狗分享页重定向缺少目标地址");
                }
                current = nextShareHop(uri, location).toString();
                continue;
            }
            if (!HttpTransport.isSuccess(response.statusCode())) {
                throw new UpstreamException(response.statusCode());
            }
            return response.body();
        }
        throw new IOException("酷狗分享页重定向次数过多");
    }

    /**
     * 计算分享页重定向的下一跳：目标同样必须先过 {@link OutboundUrlGuard}（协议、酷狗域名白名单、
     * 解析地址必须是公网）。合法站点不能借 3xx 把请求引到内网 / 云元数据地址，因此重定向目标
     * 一旦离开酷狗官方域名，就按非法入参拒绝，而不是继续跟随。
     */
    static URI nextShareHop(URI current, String location) throws IOException {
        URI next;
        try {
            next = current.resolve(location);
        } catch (IllegalArgumentException e) {
            throw new IOException("酷狗分享页重定向地址无效");
        }
        try {
            return OutboundUrlGuard.requireAllowedHttpUrl(next.toString(), ALLOWED_HOSTS);
        } catch (OutboundUrlGuard.BlockedUrlException e) {
            throw new InvalidInputException(
                    "酷狗分享链接无效或不受支持（仅支持酷狗官方域名链接或歌单 ID）", e);
        }
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    /** 入参是裸 token（gcid / 单曲 hash）而不是分享链接时返回 true。 */
    private static boolean isBareShareToken(String input) {
        return BARE_TOKEN_PATTERN.matcher(input).matches();
    }

    /**
     * 用户粘贴的分享链接 → 出站相对路径。
     *
     * <p>先按官方域名白名单校验整条链接（协议 / 主机名 / 解析地址），再只认两种形态：
     * 歌单分享 {@code /songlist/<token>}、单曲分享 {@code /share/?action=single&hash=<hash>}。
     * 主机名由 {@link #SHARE_PAGE_BASE} 固定，路径由校验过的 token 重新拼出，链接里其余
     * 内容（额外路径段、任意查询串）一律丢弃：入参能构造出的出站请求只有这两种。</p>
     */
    static String shareRelativePath(String input) throws IOException {
        String candidate = input;
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }
        URI uri;
        try {
            uri = OutboundUrlGuard.requireAllowedHttpUrl(candidate, ALLOWED_HOSTS);
        } catch (OutboundUrlGuard.BlockedUrlException e) {
            throw new InvalidInputException(
                    "酷狗分享链接无效或不受支持（仅支持酷狗官方域名链接或歌单 ID）", e);
        }
        return shareRelativeFromUri(uri);
    }

    /**
     * 纯结构解析：只接受 {@code /songlist/<token>} 与 {@code /share/?action=single&hash=<hash>}，
     * 其它路径 / 参数组合一律按非法入参拒绝（不再把链接里的路径与查询串原样转发给上游）。
     */
    static String shareRelativeFromUri(URI uri) throws InvalidInputException {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        Matcher songlist = SONGLIST_PATH_PATTERN.matcher(path);
        if (songlist.matches()) {
            return "songlist/" + songlist.group(1) + "/";
        }
        if (SINGLE_SHARE_PATH_PATTERN.matcher(path).matches()) {
            Map<String, String> params = parseRawQuery(uri.getRawQuery());
            String hash = params.get("hash");
            if ("single".equalsIgnoreCase(params.getOrDefault("action", ""))
                    && hash != null && isBareShareToken(hash)) {
                return "share/?action=single&hash=" + hash;
            }
        }
        throw new InvalidInputException("酷狗分享链接无效或不受支持（仅支持酷狗官方域名链接或歌单 ID）");
    }

    /** 解析原始查询串（不做过 URL 解码，取值必须原样通过白名单校验）。 */
    private static Map<String, String> parseRawQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Map.of();
        }
        Map<String, String> params = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            params.put(pair.substring(0, separator), pair.substring(separator + 1));
        }
        return params;
    }

    private JsonNode send(HttpRequest request) throws IOException {
        // 网关 / mobilecdn 请求同样只允许酷狗官方域名，防止常量被改错或后续引入变量地址
        HttpTransport.requireAllowedTarget(request, ALLOWED_HOSTS);
        HttpResponse<String> response =
                HttpTransport.sendString(httpClient, request, "请求酷狗接口被中断");
        if (!HttpTransport.isSuccess(response.statusCode())) {
            throw new UpstreamException(response.statusCode());
        }
        JsonNode json = objectMapper.readTree(response.body());
        if (json == null || json.isMissingNode()) {
            throw new IOException("酷狗接口返回为空");
        }
        return json;
    }

    /** android 签名：md5(salt + 按 key 升序的 k=v 串 + salt)。 */
    private static String signature(TreeMap<String, String> params) {
        StringBuilder source = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            source.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return md5(SIGN_SALT + source + SIGN_SALT);
    }

    private String calculateMid() {
        byte[] seed = new byte[16];
        secureRandom.nextBytes(seed);
        return new BigInteger(1, md5Bytes(seed)).toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }


    private static String md5(String plain) {
        return toHex(md5Bytes(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] md5Bytes(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }
}
