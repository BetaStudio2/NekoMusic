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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 汽水音乐（抖音音乐）账号客户端：封装 {@code api.qishui.com} 的 passport 登录接口。
 *
 * <p>参数与端点逆向自 PC 客户端 Soda Music 3.1.2 内打包的账号 SDK
 * （{@code @byted/douyin-login-new} + {@code @byted-sdk/account-api}），详见研究文档第四部分。</p>
 *
 * <ul>
 *   <li>扫码登录：{@code /passport/web/get_qrcode/} → {@code /passport/web/check_qrconnect/}（推荐）</li>
 *   <li>扫码后的二次验证（MFA）：{@code send_code(type=3737)} → {@code validate_code} /
 *       {@code /passport/upsms/verify/}，验证通过后把 {@code std_verify_*} 带回轮询</li>
 *   <li>登录态查询：{@code /passport/account/info/v2/}（未登录返回 {@code error_code 13}）</li>
 * </ul>
 *
 * <p>登录成功后服务器下发 {@code sessionid} / {@code sessionid_ss}（域 {@code qishui.com}），
 * 本类自己解析 {@code Set-Cookie} 并持久化到 {@code qishui_cookies.json}，供重启后或其它汽水接口复用。
 * <b>不能用 {@link java.net.CookieManager}</b>：它的默认策略 {@code ACCEPT_ORIGINAL_SERVER} 走的是
 * {@link java.net.HttpCookie#domainMatches}，而后者不认 {@code Domain=qishui.com} 这种不带前导点的域
 * （对请求主机 {@code api.qishui.com} 判定为不匹配），抖音 passport 恰好就是这么下发的，
 * 于是 {@code passport_csrf_token}/{@code sessionid} 会被静默丢掉，表现为「手机已确认登录但后端仍是未登录」。</p>
 */
public class QishuiMusicClient {

    private static final Logger logger = LoggerFactory.getLogger(QishuiMusicClient.class);

    private static final String HOST = "https://api.qishui.com";
    /** 客户端 appId：汽水音乐 / luna_pc */
    private static final String AID = "386088";
    /** 登录流程的 version_code 必须形如 3.1.2，传数字版本号会被判「版本过低」 */
    private static final String VERSION_CODE = "3.1.2";
    /** 二次验证（MFA）三个接口用的版本号，比登录流程新 */
    private static final String MFA_VERSION_CODE = "3.3.0";
    /** 二次验证发码的 type（独立发码是 24，那条有 1204 风控） */
    private static final String MFA_CODE_TYPE = "3737";
    private static final String MFA_SDK_VERSION = "1.0.0.404-web";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "Chrome/126.0.0.0 Safari/537.36";
    /** 扫码确认后客户端跳转的 next */
    private static final String QR_NEXT = "https://api.qishui.com";

    private static final String DEFAULT_DEVICE_ID = "7000000000000000001";
    private static final String DEFAULT_INSTALL_ID = "7000000000000000002";
    private static final String DEFAULT_COOKIE_FILE = "qishui_cookies.json";

    // ------------------------------------------------------------------ 歌单（PC luna 接口）

    /** PC 端 luna 只读接口基址（歌单 / 榜单等）。 */
    private static final String PC_API_BASE = "https://api.qishui.com/luna/pc";
    /** 歌单网页分享页：PC 接口取不到时解析内嵌的 {@code _ROUTER_DATA} 兜底。 */
    private static final String WEB_PLAYLIST_SHARE = "https://music.douyin.com/qishui/share/playlist";
    private static final String PC_UA = "LunaPC/3.2.1(343009595)";
    private static final String PC_VERSION_NAME = "3.2.1";
    private static final String PC_VERSION_CODE = "30020100";
    /** 分享页只对搜索引擎 UA 输出内嵌数据，浏览器 UA 不带。 */
    private static final String WEB_SHARE_UA =
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
    /** 单页曲目数；上游会在返回 has_more/next_cursor 时继续翻页。 */
    private static final int PLAYLIST_PAGE_SIZE = 100;
    /** 分页安全上限（100 页 × 100 首），防止异常游标导致死循环。 */
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
    /** 汽水 / 抖音短链：需要跟随一次跳转才能拿到歌单 ID。 */
    private static final Pattern SHORT_LINK_PATTERN =
            Pattern.compile("qishui\\.douyin\\.com|douyin\\.com/s/", Pattern.CASE_INSENSITIVE);

    /** 从 check_qrconnect 响应里抠出来的二次验证参数（键名在响应里的位置不固定，需要归一化搜索） */
    private static final List<String> VERIFY_PARAM_KEYS = List.of(
            "passport_mfa_retry_tag", "std_verify_flow_id", "std_verify_scene",
            "std_verify_template", "std_verify_token", "std_verify_type", "std_verify_way");

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 轮询状态：待扫码 */
    public static final String STATUS_NEW = "new";
    /** 轮询状态：已扫码待确认 */
    public static final String STATUS_SCANNED = "scanned";
    /** 轮询状态：手机已确认，此时服务端下发 sessionid */
    public static final String STATUS_CONFIRMED = "confirmed";
    /** 轮询状态：二维码失效 / 被拒绝（响应里通常带一张新码） */
    public static final String STATUS_EXPIRED = "expired";
    /** 轮询状态：需要二次验证（对应上游 error_code 2046） */
    public static final String STATUS_VERIFY = "verify";

    /**
     * 二次验证挑战（对应上游 {@code error_code 2046}）。
     *
     * <p>客户端有两条并行协议，这里都保留：</p>
     * <ul>
     *   <li><b>官方二次验证组件</b>：响应里带 {@code url}（要 <code>loadScript</code> 的验证组件）
     *       与 {@code biz_params}；用户验证完成后原请求带 {@code isResend=true} 重放。</li>
     *   <li><b>短信验证码</b>（SugarPlayer 做法）：响应里能搜到 {@code encrypt_uid} +
     *       {@code std_verify_*}，走 {@code send_code(type=3737)} / {@code validate_code}，
     *       再把 {@code std_verify_*} 带回原请求。</li>
     * </ul>
     */
    public record MfaChallenge(String encryptUid, String mobile, List<String> verifyWays,
                               Map<String, String> verifyParams, Map<String, String> bizParams,
                               String verifyUrl) {

        /** 是否具备发短信二次验证的条件（需要 encrypt_uid）。 */
        public boolean canSendSms() {
            return encryptUid != null && !encryptUid.isEmpty();
        }

        /** 是否是官方验证组件（需要用户去 url 完成验证，再带 isResend 重放）。 */
        public boolean hasVerifyUrl() {
            return verifyUrl != null && !verifyUrl.isEmpty();
        }
    }

    /**
     * 歌单内的一首曲目（只含元数据，不含可下载直链）。
     *
     * @param durationMs 时长（毫秒），未知为 0
     */
    public record QishuiTrack(String id, String title, String artist, String album,
                             long durationMs, String coverUrl) {
    }

    /** 汽水歌单：元信息 + 曲目列表（可能来自 PC 接口或分享页兜底）。 */
    public record QishuiPlaylist(String id, String name, String owner, String coverUrl,
                                 int trackCount, List<QishuiTrack> tracks) {
    }

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    /** 短链解析用：禁止自动跟随，手动读取 Location 以提取歌单 ID。 */
    private final HttpClient redirectClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Path cookieFile;
    private final String deviceId;
    private final String installId;
    /** 自己维护的 Cookie 表（键为 cookie 名，同名以最后一次为准）。 */
    private final Map<String, StoredCookie> cookies = new LinkedHashMap<>();

    public QishuiMusicClient(ObjectMapper objectMapper) {
        this(objectMapper, resolve("qishui.cookie_file", "QISHUI_COOKIE_FILE", DEFAULT_COOKIE_FILE));
    }

    public QishuiMusicClient(ObjectMapper objectMapper, String cookieFilePath) {
        this.objectMapper = objectMapper;
        this.deviceId = resolve("qishui.device_id", "QISHUI_DEVICE_ID", DEFAULT_DEVICE_ID);
        this.installId = resolve("qishui.install_id", "QISHUI_INSTALL_ID", DEFAULT_INSTALL_ID);
        this.cookieFile = Path.of(cookieFilePath).toAbsolutePath();
        this.httpClient = HttpTransport.create(Duration.ofSeconds(10));
        loadCookies();
    }

    // ------------------------------------------------------------------ 登录流程

    /** 取扫码登录二维码：返回上游 data 节点（token / qrcode / qrcode_index_url / expire_time）。 */
    public JsonNode fetchQrcode() throws IOException {
        Map<String, String> query = new LinkedHashMap<>(commonQuery());
        query.put("next", QR_NEXT);
        query.put("need_logo", "false");
        query.put("need_short_url", "false");
        query.put("is_new_login", "1");
        query.put("is_from_ttaccountsdk", "1");
        return request("GET", "/passport/web/get_qrcode/", query, null);
    }

    /** 轮询扫码状态；{@code extra} 为二次验证通过后要带回的 std_verify_* / biz_params。 */
    public JsonNode checkQrConnect(String token, Map<String, String> extra) throws IOException {
        return checkQrConnect(token, extra, false);
    }

    /**
     * 轮询扫码状态。
     *
     * @param extra   二次验证参数（{@code std_verify_*} 与 {@code biz_params}）
     * @param isResend 二次验证完成后重放原请求：客户端把 {@code isResend=true} 放在 query 上，
     *                 并把 {@code biz_params} 合进 body
     */
    public JsonNode checkQrConnect(String token, Map<String, String> extra, boolean isResend) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("token", token);
        form.put("next", QR_NEXT);
        form.put("need_logo", "false");
        form.put("need_short_url", "false");
        form.put("is_frontier", "false");
        form.put("is_new_login", "1");
        if (extra != null && !extra.isEmpty()) {
            form.putAll(extra);
        }
        Map<String, String> query = commonQuery();
        if (isResend) {
            query.put("isResend", "true");
        }
        return request("POST", "/passport/web/check_qrconnect/", query, form);
    }

    /** 主动作废二维码（服务器通常会在响应里补一张新码）。 */
    public JsonNode expireQrcode(String token) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("token", token);
        return request("POST", "/passport/web/expire_qrcode/", commonQuery(), form);
    }

    /** 二次验证发码（type=3737，短信发到扫码账号绑定的手机）。 */
    public JsonNode sendMfaCode(String encryptUid, Map<String, String> verifyParams) throws IOException {
        Map<String, String> form = mfaBaseForm(encryptUid, verifyParams);
        form.put("type", MFA_CODE_TYPE);
        form.put("std_verify_way", "mobile_sms_verify");
        form.put("is6Digits", "1");
        return request("POST", "/passport/web/send_code/", liteQuery(), form);
    }

    /** 二次验证校验码；注意 code 是 hex(验证码) 而不是 XOR 0x05 的 encrypt。 */
    public JsonNode validateMfaCode(String encryptUid, String code, Map<String, String> verifyParams)
            throws IOException {
        Map<String, String> form = mfaBaseForm(encryptUid, verifyParams);
        form.put("type", MFA_CODE_TYPE);
        form.put("std_verify_way", "mobile_sms_verify");
        form.put("code", HexFormat.of().formatHex(code.trim().getBytes(StandardCharsets.UTF_8)));
        return request("POST", "/passport/web/validate_code/", liteQuery(), form);
    }

    /** 二次验证走上行短信（注意没有 {@code /web} 段）。 */
    public JsonNode upsmsVerify(String encryptUid, Map<String, String> verifyParams) throws IOException {
        Map<String, String> form = mfaBaseForm(encryptUid, verifyParams);
        form.put("std_verify_way", "mobile_up_sms_verify");
        return request("POST", "/passport/upsms/verify/", liteQuery(), form);
    }

    /** 查询登录态（{@code /passport/account/info/v2/}，未登录返回 error_code 13）。 */
    public JsonNode checkLogin() throws IOException {
        return request("GET", "/passport/account/info/v2/", commonQuery(), null);
    }

    // ------------------------------------------------------------------ 歌单

    /**
     * 从用户输入解析歌单 ID：支持纯数字 ID、{@code .../playlist/{id}}、
     * {@code ?playlist_id=} 参数，以及 {@code qishui.douyin.com/s/...} 短链（跟随一次跳转）。
     *
     * @return 歌单 ID；无法识别时返回 {@code null}
     */
    public String extractPlaylistId(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (DIGITS_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        String direct = extractIdFromUrl(trimmed);
        if (direct != null) {
            return direct;
        }
        Matcher longId = LONG_ID_PATTERN.matcher(trimmed);
        if (longId.find()) {
            return longId.group();
        }
        Matcher urls = URL_PATTERN.matcher(trimmed);
        while (urls.find()) {
            String url = urls.group();
            String urlId = extractIdFromUrl(url);
            if (urlId != null) {
                return urlId;
            }
            if (SHORT_LINK_PATTERN.matcher(url).find()) {
                String resolved = resolveRedirectId(url, 0);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    /** 从单个 URL / 文本里按 路径 → playlist_id → id 的顺序提取数字歌单 ID。 */
    private static String extractIdFromUrl(String value) {
        Matcher path = PLAYLIST_PATH_PATTERN.matcher(value);
        if (path.find()) {
            return path.group(1);
        }
        Matcher param = PLAYLIST_ID_PARAM_PATTERN.matcher(value);
        if (param.find()) {
            return param.group(1);
        }
        Matcher id = ID_PARAM_PATTERN.matcher(value);
        if (id.find()) {
            return id.group(1);
        }
        return null;
    }

    /** 手动跟随一次短链跳转，从 Location 里提取歌单 ID（最多 3 跳）。 */
    private String resolveRedirectId(String url, int depth) {
        if (depth > 3) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("user-agent", WEB_SHARE_UA)
                    .GET()
                    .build();
            HttpResponse<String> response = HttpTransport.sendString(redirectClient, request,
                    "请求汽水分享链接被中断");
            String location = response.headers().firstValue("location").orElse("");
            if (location.isEmpty()) {
                return null;
            }
            String id = extractIdFromUrl(location);
            if (id != null) {
                return id;
            }
            if (SHORT_LINK_PATTERN.matcher(location).find()) {
                return resolveRedirectId(location, depth + 1);
            }
        } catch (IllegalArgumentException | IOException e) {
            logger.debug("解析汽水短链失败 url={}: {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * 拉取歌单全量曲目：优先 PC {@code /playlist/detail} 分页接口，
     * 失败或为空时回退解析网页分享页，仍为空则抛异常。
     *
     * <p>公开歌单无需登录；若本地已扫码登录，会自动带上 sessionid，
     * 以便读取自己的私密歌单。</p>
     */
    public QishuiPlaylist fetchPlaylist(String input) throws IOException {
        String playlistId = extractPlaylistId(input);
        if (playlistId == null) {
            throw new IOException("汽水歌单链接或 ID 无效");
        }
        QishuiPlaylist fromApi = fetchPlaylistFromApi(playlistId);
        if (fromApi != null && !fromApi.tracks().isEmpty()) {
            return fromApi;
        }
        QishuiPlaylist fromWeb = fetchPlaylistFromWeb(playlistId);
        if (fromWeb != null && !fromWeb.tracks().isEmpty()) {
            return fromWeb;
        }
        throw new IOException("汽水歌单为空或不可访问");
    }

    /**
     * 歌单详情，整理成 {@code {code, playlist_id, name, owner, cover, songnum, songlist[]}} 结构，
     * 供 {@code /loser/qishui/getSongListDetail} 代理与歌单导入共用。
     *
     * <p>歌单只提供元数据（歌名 / 歌手 / 专辑），不含可下载直链；导入时由上层站内匹配
     * 或从网易云补全。</p>
     */
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

    /** PC 接口分页拉取：首页失败返回 {@code null}（交给网页兜底），后续页失败向上抛，避免交付半份歌单。 */
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
                if (page == 0) {
                    return null;
                }
                throw new IOException("汽水歌单分页数据异常");
            }
            for (JsonNode resource : resources) {
                QishuiTrack track = parsePlaylistTrack(resource);
                if (track != null) {
                    tracks.add(track);
                }
            }

            String next = data.path("next_cursor").asText("");
            boolean hasMore = data.path("has_more").asBoolean(false);
            if (resources.isEmpty() || !hasMore || next.isEmpty()
                    || next.equals(cursor) || !visitedCursors.add(next)) {
                break;
            }
            cursor = next;
        }
        return buildPlaylist(playlistId, playlistNode, tracks);
    }

    /** 网页分享页兜底：解析 {@code _ROUTER_DATA.loaderData.playlist_page}。 */
    private QishuiPlaylist fetchPlaylistFromWeb(String playlistId) {
        try {
            String url = WEB_PLAYLIST_SHARE + "?playlist_id="
                    + URLEncoder.encode(playlistId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("user-agent", WEB_SHARE_UA)
                    .GET()
                    .build();
            HttpResponse<String> response = HttpTransport.sendString(httpClient, request,
                    "请求汽水歌单分享页被中断");
            if (!HttpTransport.isSuccess(response.statusCode())) {
                return null;
            }
            JsonNode router = extractRouterData(response.body());
            if (router == null) {
                return null;
            }
            JsonNode page = router.path("loaderData").path("playlist_page");
            if (!page.isObject()) {
                return null;
            }
            List<QishuiTrack> tracks = new ArrayList<>();
            for (JsonNode media : page.path("medias")) {
                QishuiTrack track = parsePlaylistTrack(media);
                if (track != null) {
                    tracks.add(track);
                }
            }
            return buildPlaylist(playlistId, page.path("playlistInfo"), tracks);
        } catch (IOException e) {
            logger.warn("汽水歌单分享页解析失败 playlistId={}: {}", playlistId, e.getMessage());
            return null;
        }
    }

    /** 截取分享页里 {@code _ROUTER_DATA = {...};} 的 JSON 对象。 */
    JsonNode extractRouterData(String html) {
        if (html == null) {
            return null;
        }
        String marker = "_ROUTER_DATA = ";
        int markerIndex = html.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int start = markerIndex + marker.length();
        int end = html.indexOf(";\nfunction runWindowFn", start);
        if (end < 0) {
            end = html.indexOf(";</script>", start);
        }
        if (end < 0) {
            return null;
        }
        try {
            return objectMapper.readTree(html.substring(start, end));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 把 media_resource（或分享页 media）归一化成曲目；两种结构的实体层级不同，依次尝试。 */
    QishuiTrack parsePlaylistTrack(JsonNode resource) {
        if (resource == null || !resource.isObject()) {
            return null;
        }
        JsonNode entity = resource.path("entity");
        JsonNode track = entity.path("track_wrapper").path("track");
        if (!isObjectNode(track)) {
            track = entity.path("track");
        }
        if (!isObjectNode(track)) {
            track = entity.path("video");
        }
        if (!isObjectNode(track)) {
            track = entity.path("ugc_video");
        }
        if (!isObjectNode(track)) {
            track = resource.path("track");
        }
        if (!isObjectNode(track)) {
            track = resource;
        }

        String title = firstNonEmpty(track.path("name").asText(""), track.path("title").asText(""),
                track.path("desc").asText(""));
        if (title.isEmpty()) {
            return null;
        }
        String id = firstNonEmpty(track.path("id").asText(""), track.path("video_id").asText(""));
        String artist = joinArtistNames(track.path("artists"), track.path("author_info"));
        String album = track.path("album").path("name").asText("");
        long duration = track.path("duration").asLong(0);
        String cover = buildCoverUrl(track.path("album").path("url_cover"));
        if (cover.isEmpty()) {
            cover = buildCoverUrl(track.path("cover_url"));
        }
        return new QishuiTrack(id, title, artist, album, duration, cover);
    }

    private static QishuiPlaylist buildPlaylist(String playlistId, JsonNode node, List<QishuiTrack> tracks) {
        JsonNode playlist = isObjectNode(node) ? node : null;
        if (playlist == null) {
            return new QishuiPlaylist(playlistId, "", "", "", tracks.size(), tracks);
        }
        String id = firstNonEmpty(playlist.path("id").asText(""), playlistId);
        String name = firstNonEmpty(playlist.path("title").asText(""),
                playlist.path("public_title").asText(""), playlist.path("name").asText(""));
        String owner = firstNonEmpty(playlist.path("owner").path("nickname").asText(""),
                playlist.path("user_artist_info").path("user_brief").path("nickname").asText(""));
        String cover = buildCoverUrl(playlist.path("url_cover"));
        int count = playlist.path("count_tracks").asInt(tracks.size());
        return new QishuiPlaylist(id, name, owner, cover, count, tracks);
    }

    /** 专辑 / 单曲 / 歌单封面：{@code urls[0] + uri + "~" + template_prefix + "-resize:960:960.png"}。 */
    static String buildCoverUrl(JsonNode cover) {
        if (cover == null || cover.isMissingNode() || cover.isNull()) {
            return "";
        }
        if (cover.isTextual()) {
            return cover.asText("").trim();
        }
        String uri = cover.path("uri").asText("").trim();
        JsonNode urls = cover.path("urls");
        String base = urls.isArray() && !urls.isEmpty() ? urls.get(0).asText("").trim() : "";
        if (base.isEmpty()) {
            return "";
        }
        if (uri.isEmpty()) {
            return base;
        }
        String prefix = cover.path("template_prefix").asText("").trim();
        String url = base + uri;
        if (!prefix.isEmpty()) {
            url = url + "~" + prefix + "-resize:960:960.png";
        }
        return url;
    }

    private static String joinArtistNames(JsonNode artists, JsonNode authorInfo) {
        List<String> names = new ArrayList<>();
        if (artists != null && artists.isArray()) {
            for (JsonNode artist : artists) {
                String name = firstNonEmpty(artist.path("name").asText(""),
                        artist.path("user_info").path("nickname").asText(""));
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        if (names.isEmpty() && isObjectNode(authorInfo)) {
            String name = authorInfo.path("name").asText("").trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return String.join(" / ", names);
    }

    private static boolean isObjectNode(JsonNode node) {
        return node != null && node.isObject() && !node.isEmpty();
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

    /** PC luna 接口 GET：带完整 PC 参数与可选登录 Cookie，非 2xx 抛 IOException。 */
    private JsonNode pcGet(String path, Map<String, String> extraQuery) throws IOException {
        Map<String, String> query = pcCommonQuery();
        if (extraQuery != null) {
            query.putAll(extraQuery);
        }
        String url = PC_API_BASE + path + "?" + encodeForm(query);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("accept", "*/*")
                .header("user-agent", PC_UA)
                .header("x-luna-background-type", "foreground")
                .header("x-luna-is-background-req", "0")
                .header("x-luna-is-local-user", "0")
                .GET();
        String cookie = cookieHeader();
        if (!cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }
        HttpResponse<String> response = HttpTransport.sendString(httpClient, builder.build(),
                "请求汽水音乐接口被中断: " + path);
        absorbCookies(response);
        if (!HttpTransport.isSuccess(response.statusCode())) {
            throw new IOException("汽水音乐接口 HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException("汽水音乐接口返回为空");
        }
        JsonNode parsed = objectMapper.readTree(body);
        if (parsed == null || parsed.isMissingNode()) {
            throw new IOException("汽水音乐接口返回为空");
        }
        return parsed;
    }

    /** PC luna 接口的公共参数（设备标识沿用登录客户端的配置，保持同一设备会话）。 */
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

    // ------------------------------------------------------------------ 状态与 Cookie

    /**
     * 一条 Cookie。只保留转发需要的字段，不碰 JDK 的解析器：
     * <ul>
     *   <li>{@code Domain=qishui.com}（无前导点）也要能用于 {@code api.qishui.com}；</li>
     *   <li>{@code Expires} 解析失败时按会话 Cookie 保留，绝不因为日期格式丢掉整条；</li>
     *   <li>{@code Max-Age<=0} / 过期时间已过 => 删除同名 Cookie。</li>
     * </ul>
     *
     * @param expiresAt 过期时间（epoch 毫秒），{@code -1} 表示会话 Cookie
     */
    record StoredCookie(String name, String value, String domain, String path,
                        boolean secure, boolean httpOnly, long expiresAt) {

        boolean expired(long nowMillis) {
            return expiresAt > 0 && expiresAt <= nowMillis;
        }
    }

    /** 是否已经持有 sessionid（登录态的判据）。 */
    public synchronized boolean hasSession() {
        long now = System.currentTimeMillis();
        for (StoredCookie cookie : cookies.values()) {
            if (("sessionid".equals(cookie.name()) || "sessionid_ss".equals(cookie.name()))
                    && !cookie.value().isEmpty() && !cookie.expired(now)) {
                return true;
            }
        }
        return false;
    }

    /** 当前保存的 Cookie 名（含过期信息概要），用于接口排查，不含值。 */
    public synchronized List<String> cookieNames() {
        long now = System.currentTimeMillis();
        List<String> names = new ArrayList<>();
        for (StoredCookie cookie : cookies.values()) {
            names.add(cookie.name() + (cookie.expired(now) ? "(已过期)" : ""));
        }
        return names;
    }

    /** 供其它汽水接口复用的 Cookie 串（{@code name=value; name2=value2}）。 */
    public synchronized String cookieHeader() {
        return cookieHeaderFor(URI.create(HOST).getHost());
    }

    /** 按域匹配拼 Cookie 头：{@code Domain=qishui.com} 可以发给 {@code api.qishui.com}。 */
    synchronized String cookieHeaderFor(String host) {
        long now = System.currentTimeMillis();
        StringBuilder result = new StringBuilder();
        for (StoredCookie cookie : cookies.values()) {
            if (cookie.value().isEmpty() || cookie.expired(now) || !domainMatches(cookie.domain(), host)) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append("; ");
            }
            result.append(cookie.name()).append('=').append(cookie.value());
        }
        return result.toString();
    }

    /** RFC 6265 语义的域匹配：主机名相同，或是域名的子域。 */
    static boolean domainMatches(String domain, String host) {
        if (domain == null || domain.isEmpty() || host == null || host.isEmpty()) {
            return true;
        }
        String cookieDomain = domain.startsWith(".") ? domain.substring(1) : domain;
        if (host.equalsIgnoreCase(cookieDomain)) {
            return true;
        }
        return host.toLowerCase(Locale.ROOT).endsWith("." + cookieDomain.toLowerCase(Locale.ROOT));
    }

    /** 收下响应里的 Set-Cookie（HTTP/2 下同名头会有多条）。 */
    public synchronized void absorbCookies(HttpResponse<?> response) {
        if (response == null) {
            return;
        }
        String host = response.uri().getHost();
        absorbSetCookieHeaders(response.headers().allValues("set-cookie"), host == null ? "" : host);
    }

    /**
     * 解析 Set-Cookie 原文并写入 Cookie 表。
     *
     * <p>容错优先：只跳过单条解析不了的 Cookie，不影响其它条，也不因为未知属性（SameSite、
     * Partitioned…）或日期格式异常而丢弃。</p>
     */
    synchronized void absorbSetCookieHeaders(List<String> headers) {
        absorbSetCookieHeaders(headers, "");
    }

    /**
     * @param defaultDomain 没有 {@code Domain} 属性的 host-only Cookie 归属（调用方传请求主机名）
     */
    synchronized void absorbSetCookieHeaders(List<String> headers, String defaultDomain) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                continue;
            }
            String[] parts = header.split(";");
            int eq = parts[0].indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = parts[0].substring(0, eq).trim();
            String value = parts[0].substring(eq + 1).trim();
            if (name.isEmpty() || name.startsWith("$")) {
                continue;
            }
            String domain = defaultDomain == null ? "" : defaultDomain;
            String path = "/";
            boolean secure = false;
            boolean httpOnly = false;
            boolean remove = false;
            long expiresAt = -1;
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                int sep = part.indexOf('=');
                String key = (sep < 0 ? part : part.substring(0, sep)).trim().toLowerCase(Locale.ROOT);
                String attribute = sep < 0 ? "" : part.substring(sep + 1).trim();
                switch (key) {
                    case "domain" -> domain = attribute;
                    case "path" -> {
                        if (!attribute.isEmpty()) {
                            path = attribute;
                        }
                    }
                    case "max-age" -> {
                        Long seconds = parseLongOrNull(attribute);
                        if (seconds != null) {
                            if (seconds <= 0) {
                                remove = true;
                            } else {
                                expiresAt = now + seconds * 1000L;
                            }
                        }
                    }
                    case "expires" -> {
                        Long expires = parseCookieDate(attribute);
                        if (expires != null) {
                            if (expires <= now) {
                                remove = true;
                            } else {
                                expiresAt = expires;
                            }
                        }
                    }
                    case "secure" -> secure = true;
                    case "httponly" -> httpOnly = true;
                    default -> {
                        // SameSite / Partitioned / Priority 等未知属性直接忽略
                    }
                }
            }
            if (remove) {
                cookies.remove(name);
                logger.info("汽水 Set-Cookie 要求删除: {}", name);
                continue;
            }
            cookies.put(name, new StoredCookie(name, value, domain, path, secure, httpOnly, expiresAt));
            logger.info("收到汽水 Set-Cookie: {}（domain={}, {}{}）", name,
                    domain.isEmpty() ? "host" : domain,
                    expiresAt < 0 ? "会话 Cookie" : "过期时间 " + Instant.ofEpochMilli(expiresAt),
                    secure ? ", Secure" : "");
        }
    }

    /** 清空本地 Cookie（登出 / 换号）。 */
    public synchronized void clearCookies() {
        cookies.clear();
        saveCookies();
    }

    /** 从磁盘载入上次保存的 Cookie，失败时静默跳过（不影响启动）。 */
    public final synchronized void loadCookies() {
        if (!Files.isRegularFile(cookieFile)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(cookieFile, StandardCharsets.UTF_8));
            if (root == null || !root.isArray()) {
                return;
            }
            long now = System.currentTimeMillis();
            int loaded = 0;
            for (JsonNode node : root) {
                String name = node.path("name").asText("").trim();
                String value = node.path("value").asText("");
                if (name.isEmpty()) {
                    continue;
                }
                long expiresAt = node.has("expiresAt") ? node.path("expiresAt").asLong(-1)
                        : legacyMaxAgeExpiry(node.path("maxAge").asLong(-1), now);
                if (expiresAt > 0 && expiresAt <= now) {
                    continue;
                }
                cookies.put(name, new StoredCookie(name, value, node.path("domain").asText(""),
                        node.path("path").asText("/"), node.path("secure").asBoolean(false),
                        node.path("httpOnly").asBoolean(false), expiresAt));
                loaded++;
            }
            logger.info("已载入汽水音乐 Cookie {} 条：{}（session={}）", loaded, cookieNames(), hasSession());
        } catch (Exception e) {
            logger.warn("载入汽水音乐 Cookie 失败: {}", e.getMessage());
        }
    }

    /** 老版本落盘用的是 maxAge 字段（秒，-1 为会话），这里换算成绝对过期时间。 */
    private static long legacyMaxAgeExpiry(long maxAge, long nowMillis) {
        if (maxAge == 0) {
            return nowMillis - 1;
        }
        return maxAge < 0 ? -1 : nowMillis + maxAge * 1000L;
    }

    /** 把当前 Cookie 落盘，供重启后复用。 */
    public final synchronized void saveCookies() {
        ArrayNode array = objectMapper.createArrayNode();
        long now = System.currentTimeMillis();
        for (StoredCookie cookie : cookies.values()) {
            if (cookie.expired(now)) {
                continue;
            }
            ObjectNode node = array.addObject();
            node.put("name", cookie.name());
            node.put("value", cookie.value());
            node.put("domain", cookie.domain());
            node.put("path", cookie.path());
            node.put("secure", cookie.secure());
            node.put("httpOnly", cookie.httpOnly());
            node.put("expiresAt", cookie.expiresAt());
        }
        try {
            Path parent = cookieFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(cookieFile, array.toPrettyString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("保存汽水音乐 Cookie 失败: {}", e.getMessage());
        }
    }

    public Path getCookieFile() {
        return cookieFile;
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 Cookie 日期（{@code Expires=...}）。
     *
     * <p>{@code java.net.HttpCookie} 遇到「星期与日期对不上」的写法会整条丢弃，浏览器却照收；
     * 这里把星期前缀去掉再试，仍然解析不出来就当会话 Cookie（返回 {@code null}，不删 cookie）。</p>
     */
    static Long parseCookieDate(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        for (String candidate : cookieDateCandidates(value)) {
            for (DateTimeFormatter format : COOKIE_DATE_FORMATS) {
                Long parsed = parseDateWith(format, candidate);
                if (parsed != null) {
                    return parsed;
                }
            }
            try {
                return OffsetDateTime.parse(candidate).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // 继续试 ISO 本地时间
            }
            try {
                return LocalDateTime.parse(candidate.replace(' ', 'T'))
                        .toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // 都不匹配：当会话 Cookie 处理（返回 null，不删 cookie）
            }
        }
        return null;
    }

    /** Cookie 日期常见形态：RFC 1123、Netscape/RFC 850、asctime（可能有/没有星期）。 */
    private static final List<DateTimeFormatter> COOKIE_DATE_FORMATS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy HH:mm:ss z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d HH:mm:ss yyyy", Locale.ENGLISH));

    /** 原文 + 去掉星期前缀的两个变体，空白统一成单空格。 */
    private static List<String> cookieDateCandidates(String value) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(value);
        int comma = value.indexOf(',');
        if (comma > 0 && comma <= 4) {
            candidates.add(value.substring(comma + 1).trim());
        }
        int space = value.indexOf(' ');
        if (space > 0 && space <= 4) {
            candidates.add(value.substring(space + 1).trim());
        }
        List<String> normalized = new ArrayList<>();
        for (String candidate : candidates) {
            normalized.add(candidate.trim().replaceAll("\\s+", " "));
        }
        return normalized;
    }

    private static Long parseDateWith(DateTimeFormatter format, String candidate) {
        try {
            return ZonedDateTime.parse(candidate, format).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // 无时区信息时按 UTC 解析
        }
        try {
            return LocalDateTime.parse(candidate, format).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 参数与 MFA 解析

    /** 账号 SDK 的 encrypt：UTF-8 字节逐字节 XOR 0x05 后转十六进制（不补零）。 */
    public static String encrypt(String value) {
        StringBuilder result = new StringBuilder();
        for (byte b : String.valueOf(value).getBytes(StandardCharsets.UTF_8)) {
            result.append(Integer.toHexString((b ^ 0x05) & 0xFF));
        }
        return result.toString();
    }

    /** 归一化 status：把 1/2/3/4/5 之类的数字码映射成字符串状态。 */
    public static String normalizeStatus(JsonNode data) {
        if (data == null || data.isMissingNode()) {
            return "";
        }
        if (!data.hasNonNull("status")) {
            return "";
        }
        String raw = data.path("status").asText("").trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "1", "new" -> STATUS_NEW;
            case "2", "scanned" -> STATUS_SCANNED;
            case "3", "confirmed" -> STATUS_CONFIRMED;
            case "4", "5", "expired", "refused" -> STATUS_EXPIRED;
            default -> raw;
        };
    }

    /**
     * 从 check_qrconnect 响应里抠二次验证参数。
     *
     * <p>{@code encrypt_uid} / {@code std_verify_*} 在响应里的层级和键名大小写都不固定，
     * 这里按 SugarPlayer 的做法把整棵 JSON 的键名归一化后递归搜索。</p>
     */
    public Optional<MfaChallenge> extractMfa(JsonNode response) {
        Map<String, String> found = new LinkedHashMap<>();
        Set<String> verifyWays = new LinkedHashSet<>();
        Map<String, String> bizParams = new LinkedHashMap<>();
        walkMfa(response, found, verifyWays, bizParams, false);

        Map<String, String> verifyParams = new LinkedHashMap<>();
        for (String key : VERIFY_PARAM_KEYS) {
            String value = found.get(key);
            if (value != null && !value.isEmpty()) {
                verifyParams.put(key, value);
            }
        }
        // 官方验证组件的入口：biz_params.url（顶层 url 也认）
        String verifyUrl = firstNonEmpty(bizParams.get("url"), found.get("verify_url"), "");
        String encryptUid = firstNonEmpty(found.get("encrypt_uid"), bizParams.get("encrypt_uid"), "");
        if (encryptUid.isEmpty() && bizParams.isEmpty() && verifyUrl.isEmpty()) {
            return Optional.empty();
        }
        String mobile = firstNonEmpty(found.get("mobile"), found.get("mobilemask"),
                bizParams.get("mobile"), bizParams.get("channel_mobile"), "");
        return Optional.of(new MfaChallenge(encryptUid, mobile, new ArrayList<>(verifyWays),
                verifyParams, bizParams, verifyUrl));
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private void walkMfa(JsonNode node, Map<String, String> found, Set<String> verifyWays,
                         Map<String, String> bizParams, boolean insideBizParams) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            // 是否位于「二次验证决策」节点上：官方组件把 url 直接放在决策响应里
            boolean verifyNode = insideBizParams
                    || node.has("account_flow")
                    || node.has("biz_params")
                    || node.has("verify_from")
                    || node.path("error_code").asInt(0) == 2046;
            node.properties().forEach(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                String normalized = normalizeKey(key);
                boolean bizHere = insideBizParams || "bizparams".equals(normalized);
                if ("bizparams".equals(normalized) && value.isObject()) {
                    // 官方验证组件把 encrypt_uid / std_verify_* / url 藏在 biz_params 里，
                    // 重放原请求时也要把这些字段原样带回去，所以整块拍平留一份
                    value.properties().forEach(child -> {
                        if (child.getValue().isValueNode()) {
                            bizParams.putIfAbsent(child.getKey(), child.getValue().asText("").trim());
                        }
                    });
                } else if (bizHere && value.isValueNode()) {
                    bizParams.putIfAbsent(key, value.asText("").trim());
                }
                if ("encryptuid".equals(normalized) && value.isValueNode()) {
                    found.putIfAbsent("encrypt_uid", value.asText("").trim());
                } else if (VERIFY_PARAM_KEYS.contains(key) && value.isValueNode()) {
                    String text = value.asText("").trim();
                    if (!text.isEmpty()) {
                        found.putIfAbsent(key, text);
                    }
                } else if ("mobile".equals(normalized) || "mobilemask".equals(normalized)) {
                    found.putIfAbsent(normalized, value.asText("").trim());
                } else if ("channelmobile".equals(normalized)) {
                    // 手机号可能只出现在 channel_mobile 里，归一化后当作 mobile 兜底
                    found.putIfAbsent("mobile", value.asText("").trim());
                } else if ("verifyway".equals(normalized) && value.isValueNode()) {
                    String text = value.asText("").trim();
                    if (!text.isEmpty()) {
                        verifyWays.add(text);
                    }
                } else if ("url".equals(normalized) && (bizHere || verifyNode) && value.isValueNode()) {
                    // 官方验证组件的脚本地址：决策响应里的 url，或 biz_params.url
                    found.putIfAbsent("verify_url", value.asText("").trim());
                }
                walkMfa(value, found, verifyWays, bizParams, bizHere);
            });
        } else if (node.isArray()) {
            node.forEach(child -> walkMfa(child, found, verifyWays, bizParams, insideBizParams));
        } else if (node.isTextual()) {
            String text = node.asText("").trim();
            if ((text.contains("std_verify_") || text.contains("passport_mfa_retry_tag"))
                    && !verifyWays.contains(text)) {
                int queryStart = text.indexOf('?');
                String query = queryStart >= 0 ? text.substring(queryStart + 1) : text;
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = pair.substring(0, eq);
                    if (VERIFY_PARAM_KEYS.contains(key)) {
                        found.putIfAbsent(key, urlDecode(pair.substring(eq + 1)));
                    }
                }
            }
            if (text.startsWith("{")) {
                try {
                    walkMfa(objectMapper.readTree(text), found, verifyWays, bizParams, insideBizParams);
                } catch (JsonProcessingException ignored) {
                    // 不是完整 JSON 串，跳过
                }
            }
        }
    }

    // ------------------------------------------------------------------ 内部实现

    private Map<String, String> commonQuery() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("aid", AID);
        query.put("device_id", deviceId);
        query.put("iid", installId);
        query.put("device_platform", "PC");
        query.put("version_code", VERSION_CODE);
        query.put("language", "zh");
        query.put("is_from_ttaccountsdk", "1");
        query.put("passport_jssdk_version", "1.6.10");
        query.put("passport_jssdk_type", "normal");
        return query;
    }

    /** MFA 三个接口用 lite 一套 query（和取二维码的 normal 不同）。 */
    private Map<String, String> liteQuery() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("passport_jssdk_version", "5.1.2");
        query.put("passport_jssdk_type", "lite");
        query.put("is_from_ttaccountsdk", "1");
        query.put("aid", AID);
        query.put("language", "zh");
        query.put("account_app_language", "en-US");
        query.put("new_authn_sdk_version", MFA_SDK_VERSION);
        query.put("is_new_login", "1");
        query.put("is_from_iesaccountsaas", "1");
        query.put("device_id", deviceId);
        query.put("install_id", installId);
        query.put("did", deviceId);
        query.put("iid", installId);
        query.put("device_platform", "PC");
        query.put("version_code", MFA_VERSION_CODE);
        query.put("biz_trace_id", String.format("%08x", RANDOM.nextInt()));
        return query;
    }

    private Map<String, String> mfaBaseForm(String encryptUid, Map<String, String> verifyParams) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mix_mode", "1");
        form.put("encrypt_uid", encryptUid == null ? "" : encryptUid);
        form.put("verify_ticket", "");
        form.put("copywriting_key", "qr_connect");
        form.put("ies_safety_diversion_tag", "mfa");
        form.put("new_verify_flow", "");
        form.put("aid", AID);
        form.put("new_authn_sdk_version", MFA_SDK_VERSION);
        if (verifyParams != null) {
            // 只透传服务端下发的二次验证参数，避免把页面传参直接拼进表单
            for (String key : VERIFY_PARAM_KEYS) {
                String value = verifyParams.get(key);
                if (value != null && !value.isEmpty()) {
                    form.put(key, value);
                }
            }
        }
        return form;
    }

    private JsonNode request(String method, String path, Map<String, String> query, Map<String, String> form)
            throws IOException {
        StringBuilder url = new StringBuilder(HOST).append(path);
        if (query != null && !query.isEmpty()) {
            url.append('?').append(encodeForm(query));
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .header("accept", "application/json, text/javascript")
                .header("user-agent", UA);
        String csrf = csrfToken();
        if (!csrf.isEmpty()) {
            builder.header("x-tt-passport-csrf-token", csrf);
        }
        String cookie = cookieHeader();
        if (!cookie.isEmpty()) {
            // 自己拼 Cookie 头：JDK 的 CookieHandler 会因为 Domain 判定规则把抖音的 cookie 丢掉
            builder.header("Cookie", cookie);
        }
        if (form == null) {
            builder.GET();
        } else {
            builder.header("content-type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form), StandardCharsets.UTF_8));
        }

        HttpResponse<String> response =
                HttpTransport.sendString(httpClient, builder.build(), "请求汽水音乐接口被中断: " + path);
        absorbCookies(response);
        saveCookies();

        String body = response.body();
        if (body == null || body.isBlank()) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("http_status", response.statusCode());
            return empty;
        }
        try {
            JsonNode parsed = objectMapper.readTree(body);
            if (parsed == null) {
                ObjectNode raw = objectMapper.createObjectNode();
                raw.put("http_status", response.statusCode());
                raw.put("raw", body);
                return raw;
            }
            return parsed;
        } catch (JsonProcessingException e) {
            logger.debug("汽水接口 {} 返回非 JSON（HTTP {}）", path, response.statusCode());
            ObjectNode raw = objectMapper.createObjectNode();
            raw.put("http_status", response.statusCode());
            raw.put("raw", body);
            return raw;
        }
    }

    private String csrfToken() {
        synchronized (this) {
            StoredCookie cookie = cookies.get("passport_csrf_token");
            if (cookie == null) {
                cookie = cookies.get("passport_csrf_token_default");
            }
            return cookie == null || cookie.expired(System.currentTimeMillis()) ? "" : cookie.value();
        }
    }

    private static String encodeForm(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!result.isEmpty()) {
                result.append('&');
            }
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                            StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static String resolve(String property, String env, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
