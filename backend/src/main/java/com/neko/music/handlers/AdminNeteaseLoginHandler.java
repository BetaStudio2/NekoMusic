package com.neko.music.handlers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.model.Admin;
import com.neko.music.service.NeteaseCloudMusicClient;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.PermissionHelper;
import com.neko.music.util.QrCodeRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 后台「网易云补全」的扫码登录入口，挂载在 {@code /api/admin/netease/*}，仅管理员及以上可用。
 *
 * <p>此前管理员只能把整串网易云 Cookie 粘进系统设置，既难获取又会过期。这里改为官方扫码流程：
 * 后端申请 {@code unikey} 并返回二维码内容，前端轮询扫码状态；确认后后端把下发的登录 Cookie
 * 写入 {@code netease_search_fill.cookie}（复用现有补全配置），随即对补全客户端生效。</p>
 *
 * <ul>
 *   <li>{@code GET  /api/admin/netease/status} —— 当前登录状态与账号资料</li>
 *   <li>{@code POST /api/admin/netease/qr/key} —— 申请二维码 key 与内容</li>
 *   <li>{@code GET  /api/admin/netease/qr/check?key=...} —— 轮询扫码状态，确认时落库</li>
 *   <li>{@code POST /api/admin/netease/logout} —— 清除登录 Cookie</li>
 * </ul>
 *
 * <p>属后台管理接口，按约定不写入对外 API 文档。</p>
 */
public class AdminNeteaseLoginHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(AdminNeteaseLoginHandler.class);

    /** 承载网易云登录 Cookie 的系统设置键（与补全客户端共用）。 */
    private static final String COOKIE_KEY = "netease_search_fill.cookie";
    /** unikey 形态：UUID 风格（字母数字与连字符），限制长度避免异常输入透传。 */
    private static final Pattern KEY_PATTERN = Pattern.compile("[0-9A-Za-z-]{8,128}");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        switch (path(request)) {
            case "/status" -> handleStatus(request, response);
            case "/qr/check" -> handleQrCheck(request, response);
            default -> sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "不支持的接口");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        switch (path(request)) {
            case "/qr/key" -> handleQrKey(request, response);
            case "/logout" -> handleLogout(request, response);
            default -> sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "不支持的接口");
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

    /** 申请二维码：返回 unikey 与二维码图像（服务端渲染 PNG）。 */
    private void handleQrKey(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_EDIT)) {
            return;
        }
        noStore(response);
        NeteaseCloudMusicClient client = Main.getNeteaseCloudMusicClient();
        if (client == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "网易云客户端尚未就绪");
            return;
        }
        try {
            String key = client.fetchQrKey();
            String qrUrl = "https://music.163.com/login?codekey=" + key;
            ObjectNode data = MAPPER.createObjectNode();
            data.put("key", key);
            data.put("qrUrl", qrUrl);
            // 服务端渲染二维码，与用户端扫码登录保持一致（带中心图标）
            data.put("qrImage", QrCodeRenderer.toPngDataUrl(qrUrl, 480));
            sendResponse(response, true, "ok", data);
        } catch (IOException e) {
            logger.warn("申请网易云扫码登录二维码失败: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, "无法连接网易云，请稍后重试");
        }
    }

    /** 轮询扫码状态；803 确认时把登录 Cookie 写入系统设置并返回账号资料。 */
    private void handleQrCheck(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_EDIT)) {
            return;
        }
        noStore(response);
        String key = request.getParameter("key");
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "缺少有效的二维码 key");
            return;
        }
        NeteaseCloudMusicClient client = Main.getNeteaseCloudMusicClient();
        if (client == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "网易云客户端尚未就绪");
            return;
        }
        try {
            NeteaseCloudMusicClient.QrLoginCheck check = client.checkQrLogin(key);
            if (check.code() != 801) {
                logger.info("网易云扫码状态变化: code={}（800 过期/802 待确认/803 成功）", check.code());
            }
            ObjectNode data = MAPPER.createObjectNode();
            data.put("code", check.code());
            data.put("status", statusName(check.code()));
            if (check.code() != 803) {
                data.put("loggedIn", false);
                sendResponse(response, true, "ok", data);
                return;
            }
            if (check.cookie().isBlank()) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, "网易云未下发登录凭证，请重试");
                return;
            }
            Admin admin = PermissionHelper.getAdminFromRequest(request);
            if (!persistCookie(check.cookie(), admin == null ? null : admin.id())) {
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "保存登录凭证失败");
                return;
            }
            NeteaseCloudMusicClient.AccountProfile profile = client.fetchAccountProfile();
            data.put("loggedIn", profile != null);
            data.put("nickname", profile == null ? "" : profile.nickname());
            data.put("avatarUrl", profile == null ? "" : profile.avatarUrl());
            data.put("userId", profile == null ? 0L : profile.userId());
            logger.info("管理员 {} 通过扫码完成网易云登录，账号={}",
                    admin == null ? "?" : admin.username(), profile == null ? "未知" : profile.nickname());
            sendResponse(response, true, "ok", data);
        } catch (IOException e) {
            logger.warn("轮询网易云扫码状态失败: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, "无法连接网易云，请稍后重试");
        }
    }

    /** 退出登录：清空网易云 Cookie（不影响其它设置）。 */
    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_EDIT)) {
            return;
        }
        noStore(response);
        Admin admin = PermissionHelper.getAdminFromRequest(request);
        if (!persistCookie("", admin == null ? null : admin.id())) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "退出登录失败");
            return;
        }
        ObjectNode data = MAPPER.createObjectNode();
        data.put("loggedIn", false);
        sendResponse(response, true, "已退出网易云登录", data);
    }

    /** 组装当前登录状态；Cookie 未配置时直接判定未登录，不再请求网易云。 */
    private void fillStatus(ObjectNode data) {
        boolean configured = !Main.getConfigManager().getNeteaseCookie().isEmpty();
        NeteaseCloudMusicClient.AccountProfile profile = null;
        if (configured) {
            NeteaseCloudMusicClient client = Main.getNeteaseCloudMusicClient();
            if (client != null) {
                try {
                    profile = client.fetchAccountProfile();
                } catch (IOException e) {
                    logger.warn("查询网易云账号资料失败: {}", e.getMessage());
                }
            }
        }
        data.put("configured", configured);
        data.put("loggedIn", profile != null);
        data.put("nickname", profile == null ? "" : profile.nickname());
        data.put("avatarUrl", profile == null ? "" : profile.avatarUrl());
        data.put("userId", profile == null ? 0L : profile.userId());
    }

    /** 把登录 Cookie 写入系统设置并立即对补全客户端生效；返回是否写入成功。 */
    private boolean persistCookie(String cookie, Integer adminId) {
        try {
            Main.getSystemSettingsDatabaseManager()
                    .upsertAll(Map.of(COOKIE_KEY, cookie == null ? "" : cookie), adminId);
            Main.getConfigManager().applyOverrides(Main.getSystemSettingsDatabaseManager().loadAll());
            return true;
        } catch (SQLException e) {
            logger.error("保存网易云登录 Cookie 失败", e);
            return false;
        }
    }

    private static String statusName(int code) {
        return switch (code) {
            case 800 -> "expired";
            case 802 -> "scanned";
            case 803 -> "confirmed";
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
