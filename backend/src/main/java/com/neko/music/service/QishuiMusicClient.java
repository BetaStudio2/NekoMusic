package com.neko.music.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * <p>登录成功后服务器下发 {@code sessionid} / {@code sessionid_ss}（域 {@code .qishui.com}），
 * 本类用 {@link CookieManager} 统一收拢，并持久化到 {@code qishui_cookies.json}，
 * 供重启后或其它汽水接口复用。</p>
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

    private final ObjectMapper objectMapper;
    private final CookieManager cookieManager = new CookieManager();
    private final HttpClient httpClient;
    private final Path cookieFile;
    private final String deviceId;
    private final String installId;

    public QishuiMusicClient(ObjectMapper objectMapper) {
        this(objectMapper, resolve("qishui.cookie_file", "QISHUI_COOKIE_FILE", DEFAULT_COOKIE_FILE));
    }

    public QishuiMusicClient(ObjectMapper objectMapper, String cookieFilePath) {
        this.objectMapper = objectMapper;
        this.deviceId = resolve("qishui.device_id", "QISHUI_DEVICE_ID", DEFAULT_DEVICE_ID);
        this.installId = resolve("qishui.install_id", "QISHUI_INSTALL_ID", DEFAULT_INSTALL_ID);
        this.cookieFile = Path.of(cookieFilePath).toAbsolutePath();
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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

    // ------------------------------------------------------------------ 状态与 Cookie

    /** 是否已经持有 sessionid（登录态的判据）。 */
    public boolean hasSession() {
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            String name = cookie.getName();
            if (("sessionid".equals(name) || "sessionid_ss".equals(name))
                    && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 供其它汽水接口复用的 Cookie 串（只取 qishui / douyin 域）。 */
    public String cookieHeader() {
        StringBuilder result = new StringBuilder();
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            String domain = cookie.getDomain() == null ? "" : cookie.getDomain().toLowerCase(Locale.ROOT);
            if (!domain.contains("qishui") && !domain.contains("douyin")) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append("; ");
            }
            result.append(cookie.getName()).append('=').append(cookie.getValue());
        }
        return result.toString();
    }

    /** 清空本地 Cookie（登出 / 换号）。 */
    public synchronized void clearCookies() {
        cookieManager.getCookieStore().removeAll();
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
            int loaded = 0;
            for (JsonNode node : root) {
                String name = node.path("name").asText("");
                String value = node.path("value").asText("");
                if (name.isEmpty()) {
                    continue;
                }
                HttpCookie cookie = new HttpCookie(name, value);
                String domain = node.path("domain").asText("");
                if (!domain.isEmpty()) {
                    cookie.setDomain(domain);
                }
                cookie.setPath(node.path("path").asText("/"));
                cookie.setMaxAge(node.path("maxAge").asLong(-1));
                cookie.setSecure(node.path("secure").asBoolean(false));
                cookie.setHttpOnly(node.path("httpOnly").asBoolean(false));
                cookie.setVersion(node.path("version").asInt(0));
                cookieManager.getCookieStore().add(URI.create("https://api.qishui.com/"), cookie);
                loaded++;
            }
            logger.info("已载入汽水音乐 Cookie {} 条（session={}）", loaded, hasSession());
        } catch (Exception e) {
            logger.warn("载入汽水音乐 Cookie 失败: {}", e.getMessage());
        }
    }

    /** 把当前 Cookie 落盘，供重启后复用。 */
    public final synchronized void saveCookies() {
        ArrayNode array = objectMapper.createArrayNode();
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            ObjectNode node = array.addObject();
            node.put("name", cookie.getName());
            node.put("value", cookie.getValue() == null ? "" : cookie.getValue());
            if (cookie.getDomain() != null) {
                node.put("domain", cookie.getDomain());
            }
            node.put("path", cookie.getPath() == null ? "/" : cookie.getPath());
            node.put("maxAge", cookie.getMaxAge());
            node.put("secure", cookie.getSecure());
            node.put("httpOnly", cookie.isHttpOnly());
            node.put("version", cookie.getVersion());
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
        if (form == null) {
            builder.GET();
        } else {
            builder.header("content-type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form), StandardCharsets.UTF_8));
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求汽水音乐接口被中断: " + path, e);
        } finally {
            saveCookies();
        }

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
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            String name = cookie.getName();
            if ("passport_csrf_token".equals(name) || "passport_csrf_token_default".equals(name)) {
                return cookie.getValue() == null ? "" : cookie.getValue();
            }
        }
        return "";
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
