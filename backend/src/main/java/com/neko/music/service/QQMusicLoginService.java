package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.util.HttpTransport;
import com.neko.music.util.OutboundUrlGuard;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ 音乐扫码登录：QQ 互联手机扫码 → 换取音乐凭据（musickey）。
 *
 * <p>流程与用户端一致：{@code ptqrshow} 出码 → {@code ptqrlogin} 轮询 → {@code check_sig}
 * 换会话 Cookie（含 {@code p_skey}）→ {@code graph.qq.com/oauth2.0/authorize} 换 code →
 * {@code QQConnectLogin.LoginServer / QQLogin} 换 musickey。成功后由上层把凭据写入
 * {@code qq_search_fill.cookie}，随即对补全客户端生效。</p>
 *
 * <p>登录期间需要在多步请求间保持 Cookie，故按二维码 key（qrsig）在内存里暂存会话，
 * 超时（{@link #SESSION_TTL_MS}）自动清理。</p>
 */
public class QQMusicLoginService {

    private static final Logger logger = LoggerFactory.getLogger(QQMusicLoginService.class);

    private static final String PTLOGIN = "https://ssl.ptlogin2.qq.com";
    private static final String GRAPH = "https://graph.qq.com";
    private static final String MUSICU = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String PTLOGIN_REFERER = "https://xui.ptlogin2.qq.com/";
    private static final String WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";
    private static final String QQ_OAUTH_REDIRECT =
            "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long SESSION_TTL_MS = 5 * 60 * 1000L;
    /** 只允许访问 qq.com 及其子域（ssl.ptlogin2.qq.com / graph.qq.com / u.y.qq.com）。 */
    private static final Set<String> ALLOWED_HOSTS = Set.of("qq.com");
    private static final Pattern PTUI_CALL = Pattern.compile("ptuiCB\\((.*?)\\)");
    private static final Pattern PTUI_ARG = Pattern.compile("'((?:\\\\.|[^'])*)'");
    private static final Pattern CODE_IN_LOCATION = Pattern.compile("(?:code=)(.+?)(?:&|$)");
    private static final Pattern UIN_IN_JUMP = Pattern.compile("(?:\\?|&)uin=(.+?)&");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpTransport.create(TIMEOUT);
    private final HttpClient noRedirectClient = HttpTransport.createWithoutRedirects(TIMEOUT);
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    /** 同一 key 串行化轮询：避免前端并发/重试把一次扫码的登录流程重复驱动（重复换票会失败）。 */
    private final Map<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    public QQMusicLoginService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 二维码：{@code key} 为 qrsig（轮询用），{@code imageDataUrl} 为 PNG data URL。 */
    public record QrKey(String key, String imageDataUrl) {
    }

    /**
     * 扫码轮询结果：{@code status} 0=过期 / 1=等待 / 2=已扫码 / 4=成功。
     * 成功时 {@code cookie} 为可持久化的登录 Cookie，其余字段为账号资料。
     */
    public record QrCheck(int status, String cookie, String nickname, String avatarUrl) {
    }

    private static final class Session {
        final Map<String, String> cookies = new ConcurrentHashMap<>();
        final long createdAt = System.currentTimeMillis();
    }

    /** 申请二维码：ptqrshow 出码并暂存登录会话 Cookie（含 qrsig）。 */
    public QrKey fetchQrKey() throws IOException {
        purgeExpired();
        String qrsig = null;
        Session session = new Session();
        long timestamp = System.currentTimeMillis();
        String url = PTLOGIN + "/ptqrshow?appid=716027609&e=2&l=M&s=3&d=72&v=4&t=" + timestamp
                + "&daid=383&pt_3rd_aid=100497308";
        Response response = request(httpClient, "GET", url, null,
                Map.of("Referer", PTLOGIN_REFERER), session);
        if (response.status() != 200) {
            throw new IOException("获取 QQ 登录二维码失败: HTTP " + response.status());
        }
        qrsig = session.cookies.get("qrsig");
        if (qrsig == null || qrsig.isBlank()) {
            throw new IOException("未能获取到 QQ 登录 qrsig");
        }
        sessions.put(qrsig, session);
        String imageDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(response.body());
        return new QrKey(qrsig, imageDataUrl);
    }

    /** 轮询扫码状态；status=4 时返回可持久化的登录 Cookie。 */
    public QrCheck checkQr(String key) throws IOException {
        ReentrantLock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            return checkQrUnderLock(key);
        } finally {
            lock.unlock();
        }
    }

    private QrCheck checkQrUnderLock(String key) throws IOException {
        purgeExpired();
        Session session = sessions.computeIfAbsent(key, k -> {
            Session created = new Session();
            created.cookies.put("qrsig", k);
            return created;
        });

        long now = System.currentTimeMillis();
        long ptqrtoken = QQMusicClient.hash33(key, 0);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("u1", "https://graph.qq.com/oauth2.0/login_jump");
        query.put("ptqrtoken", Long.toString(ptqrtoken));
        query.put("ptredirect", "0");
        query.put("h", "1");
        query.put("t", "1");
        query.put("g", "1");
        query.put("from_ui", "1");
        query.put("ptlang", "2052");
        query.put("action", "0-0-" + now);
        query.put("js_ver", "20102616");
        query.put("js_type", "1");
        query.put("pt_uistyle", "40");
        query.put("aid", "716027609");
        query.put("daid", "383");
        query.put("pt_3rd_aid", "100497308");
        query.put("has_onekey", "1");

        Response response = request(httpClient, "GET", PTLOGIN + "/ptqrlogin?" + joinQuery(query),
                null, Map.of("Referer", PTLOGIN_REFERER), session);
        List<String> args = parsePtuiArgs(response.text());
        if (args.isEmpty()) {
            return new QrCheck(1, "", "", "");
        }
        String statusCode = args.get(0);
        String nickname = args.size() > 5 ? args.get(5) : "";
        if ("65".equals(statusCode)) {
            sessions.remove(key);
            return new QrCheck(0, "", "", "");
        }
        if ("67".equals(statusCode)) {
            return new QrCheck(2, "", nickname, "");
        }
        if (!"0".equals(statusCode)) {
            return new QrCheck(1, "", "", "");
        }

        String jumpUrl = args.size() > 2 ? args.get(2) : "";
        if (jumpUrl.isEmpty() || !jumpUrl.startsWith("http")) {
            throw new IOException("QQ 登录跳转地址无效");
        }
        // check_sig：跟一次跳转以建立会话 Cookie（内含 p_skey）
        request(noRedirectClient, "GET", jumpUrl, null, Map.of("Referer", PTLOGIN_REFERER), session);
        String pSkey = firstNonEmpty(session.cookies.get("p_skey"), session.cookies.get("p_sKey"),
                session.cookies.get("skey"), session.cookies.get("pskey"));
        if (pSkey.isEmpty()) {
            throw new IOException("获取 QQ p_skey 失败");
        }

        Map<String, String> authForm = new LinkedHashMap<>();
        authForm.put("response_type", "code");
        authForm.put("client_id", "100497308");
        authForm.put("redirect_uri", QQ_OAUTH_REDIRECT);
        authForm.put("scope", "get_user_info,get_app_friends");
        authForm.put("state", "state");
        authForm.put("switch", "");
        authForm.put("from_ptlogin", "1");
        authForm.put("src", "1");
        authForm.put("update_auth", "1");
        authForm.put("openapi", "1010_1030");
        // g_tk 由会话 p_skey 派生，缺少它 authorize 会退化成授权确认页而不是回跳 code
        authForm.put("g_tk", Long.toString(QQMusicClient.hash33(pSkey, 5381)));
        authForm.put("auth_time", Long.toString(now));
        authForm.put("ui", Long.toHexString(now));
        Response authResponse = request(noRedirectClient, "POST", GRAPH + "/oauth2.0/authorize",
                joinQuery(authForm), Map.of("Referer", PTLOGIN_REFERER,
                        "Content-Type", "application/x-www-form-urlencoded"), session);
        String code = extractAuthCode(authResponse);
        if (code.isEmpty()) {
            logger.warn("QQ 授权换 code 失败: status={} location={} body={}",
                    authResponse.status(), authResponse.location(), head(authResponse.text(), 400));
            throw new IOException("获取 QQ 授权 code 失败");
        }

        Matcher uinMatcher = UIN_IN_JUMP.matcher(jumpUrl);
        String fallbackUin = uinMatcher.find() ? uinMatcher.group(1) : "";
        JsonNode loginData = qqLogin(code);
        sessions.remove(key);
        return finalizeLogin(loginData, fallbackUin);
    }

    private JsonNode qqLogin(String code) throws IOException {
        ObjectNode comm = objectMapper.createObjectNode();
        comm.put("ct", 11);
        comm.put("cv", "14090008");
        comm.put("v", "14090008");
        comm.put("tmeAppID", "qqmusic");
        comm.put("tmeLoginType", 2);
        comm.put("uin", "0");

        ObjectNode param = objectMapper.createObjectNode();
        param.put("code", code);
        ObjectNode module = objectMapper.createObjectNode();
        module.put("module", "QQConnectLogin.LoginServer");
        module.put("method", "QQLogin");
        module.set("param", param);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("comm", comm);
        body.set("req_0", module);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(MUSICU))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", WEB_UA)
                .header("Referer", "https://y.qq.com/")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpTransport.requireAllowedTarget(request, ALLOWED_HOSTS);
        HttpResponse<String> response = HttpTransport.sendString(httpClient, request, "QQ 登录请求被中断");
        if (!HttpTransport.isSuccess(response.statusCode())) {
            throw new IOException("QQ 登录接口 HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return root == null ? null : root.path("req_0").path("data");
    }

    private QrCheck finalizeLogin(JsonNode loginData, String fallbackUin) throws IOException {
        String musicId = stripLeadingO(text(loginData, "str_musicid"));
        if (musicId.isEmpty()) {
            musicId = stripLeadingO(text(loginData, "musicid"));
        }
        if (musicId.isEmpty()) {
            musicId = stripLeadingO(fallbackUin);
        }
        String musickey = text(loginData, "musickey");
        if (musicId.isEmpty() || musickey.isEmpty()) {
            throw new IOException("QQ 登录响应缺少有效凭据");
        }
        String cookie = "uin=" + musicId
                + "; qm_str_musicid=" + musicId
                + "; qm_keyst=" + musickey
                + "; qqmusic_key=" + musickey
                + "; tmeLoginType=2";
        String nickname = text(loginData, "nick");
        if (nickname.isEmpty()) {
            nickname = text(loginData, "nickname");
        }
        String avatar = QQMusicClient.normalizeCover(text(loginData, "logo"));
        if (avatar.isEmpty()) {
            avatar = "https://q.qlogo.cn/headimg_dl?dst_uin=" + musicId + "&spec=100";
        }
        logger.info("管理员通过扫码完成 QQ 音乐登录，账号={}", nickname.isEmpty() ? musicId : nickname);
        return new QrCheck(4, cookie, nickname, avatar);
    }

    private Response request(HttpClient client, String method, String url, String formBody,
                             Map<String, String> headers, Session session) throws IOException {
        URI uri = OutboundUrlGuard.requireAllowedHttpUrl(url, ALLOWED_HOSTS);
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri).timeout(TIMEOUT)
                .header("User-Agent", WEB_UA);
        headers.forEach(builder::header);
        String cookieHeader = joinCookies(session.cookies);
        if (!cookieHeader.isEmpty()) {
            builder.header("Cookie", cookieHeader);
        }
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(formBody == null ? "" : formBody,
                    StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        HttpRequest request = builder.build();
        HttpTransport.requireAllowedTarget(request, ALLOWED_HOSTS);
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            mergeCookies(session, response.headers().allValues("Set-Cookie"));
            String location = response.headers().firstValue("Location").orElse("");
            return new Response(response.statusCode(), response.body(), location);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("QQ 登录请求被中断", e);
        }
    }

    private static void mergeCookies(Session session, List<String> setCookies) {
        for (String raw : setCookies) {
            String first = raw.split(";")[0];
            int eq = first.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = first.substring(0, eq).trim();
            String value = first.substring(eq + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) {
                session.cookies.put(name, value);
            }
        }
    }

    private static String joinCookies(Map<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String joinQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                            StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static List<String> parsePtuiArgs(String text) {
        Matcher call = PTUI_CALL.matcher(text);
        if (!call.find()) {
            return List.of();
        }
        List<String> args = new java.util.ArrayList<>();
        Matcher arg = PTUI_ARG.matcher(call.group(1));
        while (arg.find()) {
            args.add(arg.group(1).replace("\\'", "'"));
        }
        return args;
    }

    /** 从 authorize 响应里取 code：优先 Location 头，回退到响应体（授权页可能把回跳地址写在 HTML 里）。 */
    private static String extractAuthCode(Response response) {
        Matcher inLocation = CODE_IN_LOCATION.matcher(response.location());
        if (inLocation.find()) {
            return inLocation.group(1);
        }
        Matcher inBody = CODE_IN_LOCATION.matcher(response.text());
        return inBody.find() ? inBody.group(1) : "";
    }

    /** 日志用：压平并截断响应体，便于排查 authorize 为何没回跳 code。 */
    private static String head(String text, int max) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        return node.path(field).asText("").trim();
    }

    private static String stripLeadingO(String value) {
        return value.startsWith("o") ? value.substring(1) : value;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void purgeExpired() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MS;
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().createdAt < cutoff) {
                keyLocks.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private record Response(int status, byte[] body, String location) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
