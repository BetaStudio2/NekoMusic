package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.model.User;
import com.neko.music.service.QrLoginService;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.RequestAuthUtil;
import com.neko.music.util.VipUtil;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫码登录（PC 显示二维码，手机端 NekoMusic App 扫码确认）。
 *
 * <ul>
 *   <li>{@code POST /api/user/qrlogin/create} — 新建会话，返回 sessionId 与二维码内容（无需登录）</li>
 *   <li>{@code GET  /api/user/qrlogin/status?sessionId=} — PC 的 SSE 长连接，状态变化即时推；confirmed 时一次性推走 token（无需登录）</li>
 *   <li>{@code POST /api/user/qrlogin/scan} — 手机扫码后标记已扫描（需登录）</li>
 *   <li>{@code POST /api/user/qrlogin/confirm} — 手机确认/拒绝登录（需登录）</li>
 * </ul>
 */
public class UserQrLoginHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserQrLoginHandler.class);

    /** 状态变更轮询间隔 */
    private static final long POLL_INTERVAL_MS = 700L;
    /** 心跳间隔，防止反向代理掐掉闲置长连接 */
    private static final long PING_INTERVAL_MS = 15_000L;

    private final ExecutorService statusExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("qr-login-sse-", 0).factory());

    private QrLoginService qrLoginService;
    private UserAuthService userAuthService;

    @Override
    public void init() {
        qrLoginService = Main.getQrLoginService();
        userAuthService = Main.getUserAuthService();
    }

    @Override
    public void destroy() {
        statusExecutor.shutdownNow();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/status".equals(path(request))) {
            sendJson(response, HttpServletResponse.SC_NOT_FOUND, false, "接口不存在", null);
            return;
        }
        handleStatusStream(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        switch (path(request)) {
            case "/create" -> handleCreate(response);
            case "/scan" -> handleScan(request, response);
            case "/confirm" -> handleConfirm(request, response);
            default -> sendJson(response, HttpServletResponse.SC_NOT_FOUND, false, "接口不存在", null);
        }
    }

    // ──────────────────────────── 接口实现 ────────────────────────────

    /** PC 新建扫码会话 */
    private void handleCreate(HttpServletResponse response) throws IOException {
        String sessionId = qrLoginService.createSession();
        if (sessionId == null) {
            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "服务暂时不可用，请稍后重试", null);
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("qrContent", QrLoginService.qrContentFor(sessionId));
        data.put("expiresIn", QrLoginService.sessionTtlSeconds());
        sendJson(response, HttpServletResponse.SC_OK, true, "ok", data);
    }

    /** PC 建立 SSE 长连接；状态一变就推，confirmed 时推走 token 并关闭连接。 */
    private void handleStatusStream(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = request.getParameter("sessionId");
        if (!QrLoginService.isValidSessionId(sessionId)) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST, false, "sessionId 无效", null);
            return;
        }

        AsyncContext asyncContext;
        try {
            asyncContext = request.startAsync();
        } catch (IllegalStateException e) {
            logger.warn("扫码登录 SSE 异步上下文创建失败", e);
            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "服务不支持流式响应", null);
            return;
        }
        asyncContext.setTimeout(0);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer;
        try {
            writer = response.getWriter();
            writer.print(": connected\n\n");
            writer.flush();
        } catch (IOException | IllegalStateException e) {
            logger.warn("扫码登录 SSE 初始化失败: {}", e.getMessage());
            completeQuietly(asyncContext);
            return;
        }

        statusExecutor.execute(() -> streamSessionStatus(sessionId, asyncContext, writer));
    }

    /**
     * 连接存续期间读 Redis，状态一变就推。
     *
     * <p>会话只在 Redis 里，扫码/确认可能落在另一台实例上，所以这里轮询共享状态而不是听本地事件；
     * 客户端因此只需一条连接，不再自己定时轮询。</p>
     */
    private void streamSessionStatus(String sessionId, AsyncContext asyncContext, PrintWriter writer) {
        long deadline = System.currentTimeMillis() + (QrLoginService.sessionTtlSeconds() + 10L) * 1000L;
        long lastPing = System.currentTimeMillis();
        String lastStatus = null;
        try {
            while (true) {
                Optional<QrLoginService.Session> sessionOpt = qrLoginService.load(sessionId);
                if (sessionOpt.isEmpty()) {
                    if (!QrLoginService.STATUS_EXPIRED.equals(lastStatus)) {
                        writeStatusEvent(writer, statusData(QrLoginService.STATUS_EXPIRED));
                    }
                    break;
                }

                QrLoginService.Session session = sessionOpt.get();
                if (!session.status.equals(lastStatus)) {
                    if (QrLoginService.STATUS_CONFIRMED.equals(session.status)) {
                        // token 只能被取走一次，取走后会话整体失效
                        qrLoginService.remove(sessionId);
                        writeStatusEvent(writer, confirmedData(session));
                        break;
                    }
                    writeStatusEvent(writer, statusData(session.status));
                    lastStatus = session.status;
                    if (QrLoginService.STATUS_CANCELED.equals(session.status)) {
                        break;
                    }
                }

                long now = System.currentTimeMillis();
                if (now >= deadline) {
                    writeStatusEvent(writer, statusData(QrLoginService.STATUS_EXPIRED));
                    break;
                }
                if (now - lastPing >= PING_INTERVAL_MS) {
                    writer.print(": ping\n\n");
                    writer.flush();
                    lastPing = now;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
        } catch (IOException e) {
            logger.debug("扫码登录 SSE 连接已断开: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            completeQuietly(asyncContext);
        }
    }

    /** confirmed 帧：带一次性 token 与用户信息；数据不完整时退化为 expired，避免 PC 卡在已确认态。 */
    private Map<String, Object> confirmedData(QrLoginService.Session session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", QrLoginService.STATUS_CONFIRMED);
        Map<String, Object> userData = session.userId == null
                ? null
                : userAuthService.findUserById(session.userId).map(this::toUserMap).orElse(null);
        if (session.token == null || userData == null) {
            data.put("status", QrLoginService.STATUS_EXPIRED);
        } else {
            data.put("token", session.token);
            data.put("user", userData);
        }
        return data;
    }

    /** 写一帧 {@code status} 事件并立即 flush。 */
    private static void writeStatusEvent(PrintWriter writer, Object data) throws IOException {
        writer.print("event: status\n");
        writer.print("data: " + Main.getObjectMapper().writeValueAsString(data) + "\n\n");
        writer.flush();
    }

    private static void completeQuietly(AsyncContext asyncContext) {
        try {
            asyncContext.complete();
        } catch (IllegalStateException ignored) {
            // 已结束
        }
    }

    /** 手机端扫码后标记（用于让 PC 提示“已扫码，请在手机上确认”） */
    private void handleScan(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = RequestAuthUtil.authenticate(request);
        if (userId == null) {
            sendJson(response, HttpServletResponse.SC_UNAUTHORIZED, false, "请先登录", null);
            return;
        }

        String sessionId = readSessionId(request);
        Optional<QrLoginService.Session> sessionOpt = qrLoginService.load(sessionId);
        if (sessionOpt.isEmpty()) {
            sendJson(response, HttpServletResponse.SC_GONE, false, "二维码已过期，请刷新后重试", null);
            return;
        }

        QrLoginService.Session session = sessionOpt.get();
        if (QrLoginService.STATUS_CONFIRMED.equals(session.status)) {
            sendJson(response, HttpServletResponse.SC_OK, true, "已确认", statusData(session.status));
            return;
        }
        if (session.userId != null && !session.userId.equals(userId)) {
            sendJson(response, HttpServletResponse.SC_CONFLICT, false, "该二维码已被其他账号扫描", null);
            return;
        }

        session.userId = userId;
        session.status = QrLoginService.STATUS_SCANNED;
        qrLoginService.save(sessionId, session);
        logger.info("扫码登录：用户 {} 已扫码，等待确认", userId);
        sendJson(response, HttpServletResponse.SC_OK, true, "ok", statusData(session.status));
    }

    /** 手机端确认或拒绝 */
    private void handleConfirm(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = RequestAuthUtil.authenticate(request);
        if (userId == null) {
            sendJson(response, HttpServletResponse.SC_UNAUTHORIZED, false, "请先登录", null);
            return;
        }

        JsonNode body = readJsonBody(request);
        String sessionId = body == null ? null : text(body, "sessionId");
        boolean approve = body == null || !body.has("approve") || body.get("approve").asBoolean(true);

        Optional<QrLoginService.Session> sessionOpt = qrLoginService.load(sessionId);
        if (sessionOpt.isEmpty()) {
            sendJson(response, HttpServletResponse.SC_GONE, false, "二维码已过期，请刷新后重试", null);
            return;
        }

        QrLoginService.Session session = sessionOpt.get();
        if (session.userId != null && !session.userId.equals(userId)) {
            sendJson(response, HttpServletResponse.SC_CONFLICT, false, "该二维码已被其他账号扫描", null);
            return;
        }
        if (QrLoginService.STATUS_CONFIRMED.equals(session.status)) {
            sendJson(response, HttpServletResponse.SC_OK, true, "已确认", statusData(session.status));
            return;
        }

        session.userId = userId;
        if (!approve) {
            session.status = QrLoginService.STATUS_CANCELED;
            session.token = null;
            qrLoginService.save(sessionId, session);
            logger.info("扫码登录：用户 {} 拒绝了登录请求", userId);
            sendJson(response, HttpServletResponse.SC_OK, true, "已取消", statusData(session.status));
            return;
        }

        String token = userAuthService.createTokenForUser(userId);
        if (token == null) {
            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "登录失败，请稍后重试", null);
            return;
        }

        session.status = QrLoginService.STATUS_CONFIRMED;
        session.token = token;
        qrLoginService.save(sessionId, session);
        logger.info("扫码登录：用户 {} 已确认登录", userId);
        sendJson(response, HttpServletResponse.SC_OK, true, "ok", statusData(session.status));
    }

    // ──────────────────────────── 工具方法 ────────────────────────────

    private static String path(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        return pathInfo == null ? "" : pathInfo;
    }

    private static Map<String, Object> statusData(String status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        return data;
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.id());
        userData.put("nickname", user.nickname());
        userData.put("email", user.email());
        userData.put("createdAt", user.createdAt());
        userData.put("isVip", VipUtil.isVipActiveNow(user.vipExpiresAt()));
        userData.put("vipExpiresAt",
                user.vipExpiresAt() != null ? user.vipExpiresAt().toInstant().toString() : null);
        return userData;
    }

    private static JsonNode readJsonBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            return null;
        }
        if (sb.length() == 0) {
            return null;
        }
        try {
            return Main.getObjectMapper().readTree(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String readSessionId(HttpServletRequest request) {
        JsonNode body = readJsonBody(request);
        if (body != null) {
            String fromBody = text(body, "sessionId");
            if (fromBody != null && !fromBody.isBlank()) {
                return fromBody;
            }
        }
        return request.getParameter("sessionId");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

}
