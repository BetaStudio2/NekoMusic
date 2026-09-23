package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.service.QishuiMusicClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 汽水音乐（抖音音乐）登录入口，挂载在 {@code /loser/qishui/*}。
 *
 * <ul>
 *   <li>{@code GET /loser/qishui/login} —— 扫码登录页（HTML，二维码 + 二次验证交互）</li>
 *   <li>{@code GET|POST /loser/qishui/login/qrcode} —— 取二维码（返回上游 qrcode/token）</li>
 *   <li>{@code GET|POST /loser/qishui/login/check} —— 轮询扫码状态；需二次验证时返回 MFA 信息</li>
 *   <li>{@code POST /loser/qishui/login/mfa/send|verify|upsms} —— 短信二次验证（需要 encrypt_uid）</li>
 *   <li>{@code POST /loser/qishui/login/mfa/resend} —— 官方验证组件完成后重放（isResend + biz_params）</li>
 *   <li>{@code POST /loser/qishui/login/expire} —— 作废当前二维码</li>
 *   <li>{@code GET /loser/qishui/login/state} —— 当前登录态与 Cookie 概况</li>
 *   <li>{@code POST /loser/qishui/login/logout} —— 删除后端保存的 Cookie</li>
 *   <li>{@code GET|POST /loser/qishui/getSongListDetail} —— 歌单详情（{@code playlist_id} 支持纯 ID / 分享链接）</li>
 * </ul>
 *
 * <p>登录成功后 Cookie 由 {@link QishuiMusicClient} 持久化，后续汽水接口可直接复用。
 * 歌单为只读元数据，公开歌单免登录；导入到站内歌单见 {@code /loser/qishui/pull}。</p>
 */
public class QishuiMusicHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(QishuiMusicHandler.class);

    /** 二维码 token 的合法形态（形如 32 位 hex + _lq） */
    private static final String TOKEN_PATTERN = "[A-Za-z0-9_-]{8,128}";
    /** 扫码后二次验证参数的服务端缓存时长（毫秒） */
    private static final long MFA_CACHE_TTL_MS = 10 * 60 * 1000L;

    private static final String PAGE_RESOURCE = "qishui_login.html";

    private final Map<String, CachedMfa> pendingMfa = new ConcurrentHashMap<>();
    private volatile String cachedPage;

    private record CachedMfa(QishuiMusicClient.MfaChallenge challenge, long createdAt) {
    }

    // ------------------------------------------------------------------ 入口

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch(request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void dispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String action = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

        try {
            switch (action) {
                case "/", "/login" -> sendLoginPage(response);
                case "/login/qrcode" -> fetchQrcode(request, response);
                case "/login/check" -> checkQrcode(request, response);
                case "/login/mfa/send" -> sendMfaCode(request, response);
                case "/login/mfa/verify" -> verifyMfaCode(request, response);
                case "/login/mfa/upsms" -> upsmsVerify(request, response);
                case "/login/mfa/resend" -> resendAfterVerify(request, response);
                case "/login/expire" -> expireQrcode(request, response);
                case "/getSongListDetail", "/playlist/detail" -> fetchPlaylistDetail(request, response);
                case "/login/state" -> sendState(response);
                case "/login/logout", "/login/clear" -> logout(response);
                default -> sendErrorData(response, HttpServletResponse.SC_NOT_FOUND, "接口不存在");
            }
        } catch (IllegalArgumentException e) {
            sendErrorData(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            logger.warn("汽水音乐接口请求失败 path={}: {}", action, e.getMessage());
            sendErrorData(response, HttpServletResponse.SC_BAD_GATEWAY,
                    e.getMessage() == null ? "请求汽水音乐接口失败" : e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 各动作

    private void fetchQrcode(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JsonNode upstream = client().fetchQrcode();
        JsonNode data = unwrap(upstream);
        int errorCode = data.path("error_code").asInt(0);
        if (errorCode != 0 || data.path("token").asText("").isEmpty()) {
            sendErrorData(response, HttpServletResponse.SC_BAD_GATEWAY, qishuiMessage(upstream, "取二维码失败"));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        putQrcodeFields(payload, data);
        sendSuccess(response, "ok", payload);
    }

    private void checkQrcode(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = requireToken(request);
        CachedMfa cached = takeFreshMfa(token);
        Map<String, String> extra = cached == null ? null : verificationBody(cached.challenge());
        // 官方验证组件走完后由 /login/mfa/resend 触发；普通轮询不带 isResend
        sendSuccess(response, "ok", checkResult(token, client().checkQrConnect(token, extra, false)));
    }

    /** 官方二次验证组件完成后重放原请求：客户端语义是 query 带 isResend，body 合并 biz_params。 */
    private void resendAfterVerify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = requireToken(request);
        CachedMfa cached = takeFreshMfa(token);
        if (cached == null) {
            throw new IllegalArgumentException("验证会话已过期，请重新获取二维码并扫码");
        }
        JsonNode upstream = client().checkQrConnect(token, verificationBody(cached.challenge()), true);
        sendSuccess(response, "ok", checkResult(token, upstream));
    }

    /** 组装重放时带回的二次验证字段：std_verify_* + biz_params（后者是官方验证组件的回执）。 */
    private static Map<String, String> verificationBody(QishuiMusicClient.MfaChallenge challenge) {
        Map<String, String> body = new LinkedHashMap<>(challenge.verifyParams());
        body.putAll(challenge.bizParams());
        return body;
    }

    /** 解释一次 check_qrconnect 响应，并同步服务端的 MFA 会话缓存。 */
    private Map<String, Object> checkResult(String token, JsonNode upstream) {
        JsonNode data = unwrap(upstream);
        Map<String, Object> payload = new LinkedHashMap<>();
        String status = QishuiMusicClient.normalizeStatus(data);
        if (status.isEmpty()) {
            // 没有 status：二次验证分支（error_code 2046 + account_flow=verify）
            Optional<QishuiMusicClient.MfaChallenge> challenge = client().extractMfa(data);
            if (challenge.isPresent()) {
                pendingMfa.put(token, new CachedMfa(challenge.get(), System.currentTimeMillis()));
                payload.put("status", QishuiMusicClient.STATUS_VERIFY);
                payload.put("mfa", mfaPayload(challenge.get()));
                payload.put("description", data.path("description").asText(""));
                payload.put("error_code", data.path("error_code").asInt(0));
                return payload;
            }
            payload.put("status", QishuiMusicClient.STATUS_NEW);
            payload.put("upstream", data);
            return payload;
        }

        payload.put("status", status);
        if (QishuiMusicClient.STATUS_EXPIRED.equals(status)) {
            // 上游失效时通常补发一张新码，直接透传给前端续用
            putQrcodeFields(payload, data);
        }
        if (QishuiMusicClient.STATUS_SCANNED.equals(status)) {
            payload.put("scan_user_avatar", data.path("scan_user_info").path("avatar_url").asText(""));
            payload.put("scan_device", data.path("scan_device_info").path("scan_device_display_name").asText(""));
        }
        if (QishuiMusicClient.STATUS_CONFIRMED.equals(status)) {
            pendingMfa.remove(token);
            // 本次 check_qrconnect 响应里的 Set-Cookie（sessionid / sessionid_ss）已由客户端收下
            client().saveCookies();
            payload.put("session", client().hasSession());
            payload.put("cookies", client().cookieNames());
            payload.put("login", loginState());
        }
        return payload;
    }

    private static Map<String, Object> mfaPayload(QishuiMusicClient.MfaChallenge challenge) {
        Map<String, Object> mfa = new LinkedHashMap<>();
        mfa.put("mode", challenge.hasVerifyUrl() ? "web" : "sms");
        mfa.put("encrypt_uid", challenge.encryptUid());
        mfa.put("mobile", challenge.mobile());
        mfa.put("verify_ways", challenge.verifyWays());
        mfa.put("url", challenge.verifyUrl());
        mfa.put("can_send_sms", challenge.canSendSms());
        return mfa;
    }

    private void sendMfaCode(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = requireToken(request);
        QishuiMusicClient.MfaChallenge challenge = requireSmsMfa(token);
        JsonNode upstream = client().sendMfaCode(challenge.encryptUid(), challenge.verifyParams());
        JsonNode data = unwrap(upstream);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error_code", data.path("error_code").asInt(-1));
        payload.put("description", data.path("description").asText(""));
        payload.put("mobile", data.path("mobile").asText(challenge.mobile()));
        sendSuccess(response, "ok", payload);
    }

    private void verifyMfaCode(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = requireToken(request);
        String code = request.getParameter("code");
        if (code == null || !code.trim().matches("[0-9]{4,8}")) {
            throw new IllegalArgumentException("缺少有效的短信验证码");
        }
        QishuiMusicClient.MfaChallenge challenge = requireSmsMfa(token);
        JsonNode upstream = client().validateMfaCode(challenge.encryptUid(), code.trim(),
                challenge.verifyParams());
        JsonNode data = unwrap(upstream);
        int errorCode = data.path("error_code").asInt(-1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error_code", errorCode);
        payload.put("description", data.path("description").asText(""));
        payload.put("ticket", data.path("ticket").asText(""));
        if (errorCode == 0) {
            // 校验通过后立刻重放一次，把 std_verify_* 带回去换 sessionid
            JsonNode check = client().checkQrConnect(token, verificationBody(challenge), true);
            payload.put("check", checkResult(token, check));
        }
        sendSuccess(response, "ok", payload);
    }

    private void upsmsVerify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = requireToken(request);
        QishuiMusicClient.MfaChallenge challenge = requireSmsMfa(token);
        JsonNode upstream = client().upsmsVerify(challenge.encryptUid(), challenge.verifyParams());
        JsonNode data = unwrap(upstream);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error_code", data.path("error_code").asInt(-1));
        payload.put("description", data.path("description").asText(""));
        sendSuccess(response, "ok", payload);
    }

    private void expireQrcode(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = requireToken(request);
        pendingMfa.remove(token);
        JsonNode upstream = client().expireQrcode(token);
        JsonNode data = unwrap(upstream);
        Map<String, Object> payload = new LinkedHashMap<>();
        putQrcodeFields(payload, data);
        sendSuccess(response, "ok", payload);
    }

    /** 歌单详情：ID 支持纯数字、{@code playlist/{id}}、{@code playlist_id=} 与汽水/抖音短链。 */
    private void fetchPlaylistDetail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String input = firstNonBlank(request.getParameter("playlist_id"),
                request.getParameter("url"), request.getParameter("id"));
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("缺少有效的 playlist_id（汽水歌单 ID 或分享链接）");
        }
        input = input.trim();
        if (input.length() > 2048) {
            throw new IllegalArgumentException("playlist_id 过长");
        }
        sendSuccess(response, "ok", client().fetchPlaylistDetailRaw(input));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void sendState(HttpServletResponse response) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("logged_in", client().hasSession());
        payload.put("cookie_file", client().getCookieFile().toString());
        payload.put("cookies", client().cookieNames());
        payload.put("login", loginState());
        sendSuccess(response, "ok", payload);
    }

    private void logout(HttpServletResponse response) throws IOException {
        client().clearCookies();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("logged_in", false);
        sendSuccess(response, "已清除汽水音乐 Cookie", payload);
    }

    // ------------------------------------------------------------------ 辅助

    /** 查一次登录态；失败不影响主流程，返回 {error_code, user_id, nickname} 概况。 */
    private Map<String, Object> loginState() {
        Map<String, Object> state = new LinkedHashMap<>();
        try {
            JsonNode data = unwrap(client().checkLogin());
            state.put("error_code", data.path("error_code").asInt(-1));
            JsonNode user = data.path("user");
            state.put("user_id", user.path("user_id").asText(data.path("user_id").asText("")));
            state.put("nickname", user.path("nickname").asText(data.path("nickname").asText("")));
        } catch (Exception e) {
            logger.warn("查询汽水音乐登录态失败: {}", e.getMessage());
            state.put("error_code", -1);
            state.put("description", "查询登录态失败");
        }
        return state;
    }

    /** 短信二次验证需要 encrypt_uid，缺失说明只能走官方验证组件流程。 */
    private QishuiMusicClient.MfaChallenge requireSmsMfa(String token) {
        CachedMfa cached = takeFreshMfa(token);
        if (cached == null) {
            throw new IllegalArgumentException("二次验证会话已过期，请重新获取二维码并扫码");
        }
        if (!cached.challenge().canSendSms()) {
            throw new IllegalArgumentException("当前二次验证需要打开验证页面完成，无法直接发短信");
        }
        return cached.challenge();
    }

    private CachedMfa takeFreshMfa(String token) {
        CachedMfa cached = pendingMfa.get(token);
        if (cached == null) {
            return null;
        }
        if (System.currentTimeMillis() - cached.createdAt() > MFA_CACHE_TTL_MS) {
            pendingMfa.remove(token);
            return null;
        }
        return cached;
    }

    private static String requireToken(HttpServletRequest request) {
        String token = request.getParameter("token");
        if (token == null || !token.trim().matches(TOKEN_PATTERN)) {
            throw new IllegalArgumentException("缺少有效的二维码 token");
        }
        return token.trim();
    }

    /** 取上游 data 节点；缺失时回退到整个响应，方便读取 error_code/description。 */
    private static JsonNode unwrap(JsonNode upstream) {
        if (upstream == null) {
            return Main.getObjectMapper().createObjectNode();
        }
        JsonNode data = upstream.path("data");
        return data.isObject() ? data : upstream;
    }

    /** 透传二维码字段（token/qrcode/qrcode_index_url/expire_time），字段顺序与原来一致。 */
    private static void putQrcodeFields(Map<String, Object> payload, JsonNode data) {
        payload.put("token", data.path("token").asText(""));
        payload.put("qrcode", data.path("qrcode").asText(""));
        payload.put("qrcode_index_url", data.path("qrcode_index_url").asText(""));
        payload.put("expire_time", data.path("expire_time").asLong(0));
    }

    private static String qishuiMessage(JsonNode upstream, String fallback) {
        JsonNode data = unwrap(upstream);
        String description = data.path("description").asText("");
        if (!description.isEmpty()) {
            int errorCode = data.path("error_code").asInt(0);
            return errorCode > 0 ? description + "（error_code " + errorCode + "）" : description;
        }
        String message = upstream.path("message").asText("");
        return message.isEmpty() ? fallback : message;
    }

    private void sendLoginPage(HttpServletResponse response) throws IOException {
        String page = cachedPage;
        if (page == null) {
            page = readPage();
            cachedPage = page;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/html;charset=utf-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(page);
    }

    private String readPage() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(PAGE_RESOURCE)) {
            if (stream == null) {
                throw new IOException("登录页模板缺失: " + PAGE_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static QishuiMusicClient client() {
        return Main.getQishuiMusicClient();
    }

    private static void sendSuccess(HttpServletResponse response, String message, Object data) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", data);
        writeJson(response, HttpServletResponse.SC_OK, body);
    }


    private static void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        ObjectNode node = (ObjectNode) Main.getObjectMapper().valueToTree(body);
        response.getWriter().write(node.toString());
    }
}
