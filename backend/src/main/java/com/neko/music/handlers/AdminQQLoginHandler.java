package com.neko.music.handlers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.model.Admin;
import com.neko.music.service.QQMusicClient;
import com.neko.music.service.QQMusicLoginService;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * 后台「外站补全」的 QQ 音乐扫码登录入口，挂载在 {@code /api/admin/qq/*}，仅管理员及以上可用。
 *
 * <p>与网易云一样改为官方扫码流程：后端申请二维码并返回图片，前端轮询扫码状态；确认后后端把下发的
 * 登录凭据写入 {@code qq_search_fill.cookie}（复用现有补全配置），随即对 QQ 补全客户端生效。</p>
 *
 * <ul>
 *   <li>{@code GET  /api/admin/qq/status} —— 当前登录状态与账号资料</li>
 *   <li>{@code POST /api/admin/qq/qr/key} —— 申请二维码 key 与内容</li>
 *   <li>{@code GET  /api/admin/qq/qr/check?key=...} —— 轮询扫码状态，确认时落库</li>
 *   <li>{@code POST /api/admin/qq/logout} —— 清除登录 Cookie</li>
 * </ul>
 *
 * <p>属后台管理接口，按约定不写入对外 API 文档。</p>
 */
public class AdminQQLoginHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(AdminQQLoginHandler.class);

    /** 承载 QQ 音乐登录凭据的系统设置键（与补全客户端共用）。 */
    private static final String COOKIE_KEY = "qq_search_fill.cookie";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        switch (path(request)) {
            case "/status" -> handleStatus(request, response);
            case "/qr/check" -> handleQrCheck(request, response);
            default -> sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "common.unsupported_endpoint");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        switch (path(request)) {
            case "/qr/key" -> handleQrKey(request, response);
            case "/logout" -> handleLogout(request, response);
            default -> sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "common.unsupported_endpoint");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /** 当前登录状态：Cookie 是否已配置 + 实际登录账号资料。 */
    private void handleStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_VIEW)) {
            return;
        }
        noStore(response);
        ObjectNode data = MAPPER.createObjectNode();
        fillStatus(data);
        sendResponse(response, true, "ok", data);
    }

    /** 申请二维码：返回轮询 key 与二维码图像（服务端直接透出 QQ 下发的 PNG）。 */
    private void handleQrKey(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_EDIT)) {
            return;
        }
        noStore(response);
        QQMusicLoginService loginService = Main.getQQMusicLoginService();
        if (loginService == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "source.qq.login_not_ready");
            return;
        }
        try {
            QQMusicLoginService.QrKey key = loginService.fetchQrKey();
            ObjectNode data = MAPPER.createObjectNode();
            data.put("key", key.key());
            data.put("qrImage", key.imageDataUrl());
            sendResponse(response, true, "ok", data);
        } catch (IOException e) {
            logger.warn("申请 QQ 扫码登录二维码失败: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, "source.qq.login_unreachable");
        }
    }

    /** 轮询扫码状态；确认时把登录凭据写入系统设置并立即对补全客户端生效。 */
    private void handleQrCheck(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_VIEW)) {
            return;
        }
        noStore(response);
        String key = request.getParameter("key");
        if (key == null || key.isBlank()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "source.qq.qr_key_required");
            return;
        }
        QQMusicLoginService loginService = Main.getQQMusicLoginService();
        if (loginService == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "source.qq.login_not_ready");
            return;
        }
        try {
            QQMusicLoginService.QrCheck check = loginService.checkQr(key.trim());
            ObjectNode data = MAPPER.createObjectNode();
            data.put("code", check.status());
            data.put("status", statusName(check.status()));
            if (check.status() != 4) {
                data.put("loggedIn", false);
                if (!check.nickname().isBlank()) {
                    data.put("nickname", check.nickname());
                }
                sendResponse(response, true, "ok", data);
                return;
            }
            if (check.cookie().isBlank()) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, "source.qq.no_credential");
                return;
            }
            Admin admin = PermissionHelper.getAdminFromRequest(request);
            if (!persistCookie(check.cookie(), admin == null ? null : admin.id())) {
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "source.qq.save_credential_failed");
                return;
            }
            QQMusicClient.AccountProfile profile = fetchProfileQuietly();
            data.put("loggedIn", true);
            data.put("nickname", profile == null || profile.nickname().isBlank()
                    ? check.nickname() : profile.nickname());
            data.put("avatarUrl", profile == null ? check.avatarUrl() : profile.avatarUrl());
            data.put("userId", profile == null ? 0L : profile.userId());
            logger.info("管理员 {} 通过扫码完成 QQ 音乐登录，账号={}",
                    admin == null ? "?" : admin.username(),
                    profile == null ? check.nickname() : profile.nickname());
            sendResponse(response, true, "ok", data);
        } catch (IOException e) {
            logger.warn("轮询 QQ 扫码状态失败: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, "source.qq.login_unreachable");
        }
    }

    /** 退出登录：清空 QQ 音乐 Cookie（不影响其它设置）。 */
    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_EDIT)) {
            return;
        }
        noStore(response);
        Admin admin = PermissionHelper.getAdminFromRequest(request);
        if (!persistCookie("", admin == null ? null : admin.id())) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "source.qq.logout_failed");
            return;
        }
        ObjectNode data = MAPPER.createObjectNode();
        data.put("loggedIn", false);
        sendResponse(response, true, "source.qq.logged_out", data);
    }

    /** 组装当前登录状态；Cookie 未配置时直接判定未登录，不再请求 QQ 音乐。 */
    private void fillStatus(ObjectNode data) {
        boolean configured = Main.getConfigManager() != null
                && !Main.getConfigManager().getQqCookie().isEmpty();
        QQMusicClient.AccountProfile profile = configured ? fetchProfileQuietly() : null;
        data.put("configured", configured);
        data.put("loggedIn", profile != null);
        data.put("nickname", profile == null ? "" : profile.nickname());
        data.put("avatarUrl", profile == null ? "" : profile.avatarUrl());
        data.put("userId", profile == null ? 0L : profile.userId());
    }

    private QQMusicClient.AccountProfile fetchProfileQuietly() {
        QQMusicClient client = Main.getQQMusicClient();
        if (client == null) {
            return null;
        }
        try {
            return client.fetchAccountProfile();
        } catch (IOException e) {
            logger.warn("查询 QQ 音乐账号资料失败: {}", e.getMessage());
            return null;
        }
    }

    /** 把登录凭据写入系统设置并立即对补全客户端生效；返回是否写入成功。 */
    private boolean persistCookie(String cookie, Integer adminId) {
        try {
            Main.getSystemSettingsDatabaseManager()
                    .upsertAll(Map.of(COOKIE_KEY, cookie == null ? "" : cookie), adminId);
            Main.getConfigManager().applyOverrides(Main.getSystemSettingsDatabaseManager().loadAll());
            return true;
        } catch (SQLException e) {
            logger.error("保存 QQ 音乐登录凭据失败", e);
            return false;
        }
    }

    private static String statusName(int status) {
        return switch (status) {
            case 0 -> "expired";
            case 2 -> "scanned";
            case 4 -> "confirmed";
            default -> "waiting";
        };
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
    }

    private static String path(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null ? "" : path;
    }
}
