package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.database.ClientRegistryDatabaseManager;
import com.neko.music.model.ClientApp;
import com.neko.music.model.ClientInstance;
import com.neko.music.service.ClientIdentityService;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.ClientIdentityProtocol;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;

/**
 * 后台第三方客户端凭据管理。属于管理端接口，不写入公开 API 文档。
 *
 * <ul>
 *   <li>{@code GET /api/admin/client-apps}</li>
 *   <li>{@code POST /api/admin/client-apps}</li>
 *   <li>{@code PUT /api/admin/client-apps/{clientId}}</li>
 *   <li>{@code DELETE /api/admin/client-apps/{clientId}}</li>
 * </ul>
 */
public class AdminClientAppsHandler extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.CLIENT_VIEW)) {
            return;
        }
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        ArrayNode items = MAPPER.createArrayNode();
        for (ClientApp app : registry().listApps()) {
            ObjectNode node = items.addObject();
            node.put("clientId", app.clientId());
            node.put("name", app.name());
            node.put("ownerLabel", app.ownerLabel());
            node.put("platform", app.platform());
            node.put("confidential", app.confidential());
            node.put("status", app.status());
            node.put("official", app.official());
            node.put("attestationRequired", app.attestationRequired());
            node.put("allowedOrigins", app.allowedOrigins());
            node.put("packageName", app.packageName());
            node.put("signingDigest", app.signingDigest());
            node.put("createdAt", app.createdAt());
            node.put("expiresAt", app.expiresAt());
        }
        ObjectNode data = MAPPER.createObjectNode();
        data.set("items", items);
        sendJson(response, 200, true, "ok", data);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.CLIENT_EDIT)) {
            return;
        }
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        JsonNode root;
        try {
            root = MAPPER.readTree(readBody(request));
        } catch (Exception e) {
            sendErrorResponse(response, 400, "net.invalid_json");
            return;
        }
        String name = text(root, "name");
        String platform = text(root, "platform").toLowerCase(Locale.ROOT);
        boolean confidential = root.path("confidential").asBoolean(false);
        boolean attestationRequired = root.path("attestationRequired").asBoolean(false);
        String ownerLabel = text(root, "ownerLabel");
        String origins = text(root, "allowedOrigins");
        String packageName = text(root, "packageName");
        String signingDigest = text(root, "signingDigest").toLowerCase(Locale.ROOT);
        long expiresDays = root.path("expiresDays").asLong(0L);
        if (name.isEmpty() || name.length() > 100 || !ClientIdentityProtocol.isPlatform(platform)
                || ownerLabel.length() > 100 || origins.length() > 4096
                || packageName.length() > 255 || signingDigest.length() > 64
                || expiresDays < 0 || expiresDays > 3650) {
            sendErrorResponse(response, 400, "admin.settings.invalid");
            return;
        }
        if (attestationRequired && (packageName.isEmpty() || !isSha256(signingDigest))) {
            sendErrorResponse(response, 400, "admin.settings.invalid");
            return;
        }
        String clientId = ClientIdentityService.newClientId(confidential);
        String plaintextSecret = confidential ? ClientIdentityService.newClientSecret() : null;
        Long expiresAt = expiresDays == 0 ? null
                : System.currentTimeMillis() + expiresDays * 24L * 60 * 60 * 1000;
        ClientApp app = new ClientApp(0L, clientId, name, ownerLabel, platform, confidential,
                plaintextSecret == null ? null : ClientRegistryDatabaseManager.hashSecret(plaintextSecret),
                "active", false, attestationRequired,
                origins.isEmpty() ? null : origins,
                packageName.isEmpty() ? null : packageName,
                signingDigest.isEmpty() ? null : signingDigest,
                System.currentTimeMillis(), System.currentTimeMillis(), expiresAt);
        try {
            registry().createApp(app);
        } catch (Exception e) {
            sendErrorResponse(response, 409, "admin.client.create_failed");
            return;
        }
        ObjectNode data = MAPPER.createObjectNode();
        data.put("clientId", clientId);
        if (plaintextSecret != null) {
            data.put("clientSecret", plaintextSecret);
        }
        data.put("platform", platform);
        data.put("confidential", confidential);
        sendJson(response, 200, true, "ok", data);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.CLIENT_EDIT)) {
            return;
        }
        String clientId = pathValue(request.getPathInfo());
        JsonNode root;
        try {
            root = MAPPER.readTree(readBody(request));
        } catch (Exception e) {
            sendErrorResponse(response, 400, "net.invalid_json");
            return;
        }
        String status = text(root, "status").toLowerCase(Locale.ROOT);
        if (clientId.isEmpty() || (!"active".equals(status) && !"disabled".equals(status))) {
            sendErrorResponse(response, 400, "admin.settings.invalid");
            return;
        }
        if (!registry().updateStatus(clientId, status)) {
            sendErrorResponse(response, 404, "admin.client.not_found");
            return;
        }
        identityService().invalidate(clientId, null);
        sendJson(response, 200, true, "ok", MAPPER.createObjectNode().put("clientId", clientId)
                .put("status", status));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.CLIENT_EDIT)) {
            return;
        }
        String clientId = pathValue(request.getPathInfo());
        if (clientId.isEmpty() || !registry().updateStatus(clientId, "disabled")) {
            sendErrorResponse(response, 404, "admin.client.not_found");
            return;
        }
        identityService().invalidate(clientId, null);
        sendJson(response, 200, true, "ok", MAPPER.createObjectNode().put("clientId", clientId));
    }

    private static ClientRegistryDatabaseManager registry() {
        return Main.getClientRegistryDatabaseManager();
    }

    private static ClientIdentityService identityService() {
        return Main.getClientIdentityService();
    }

    private static String text(JsonNode root, String field) {
        if (root == null) {
            return "";
        }
        JsonNode node = root.get(field);
        return node == null || !node.isTextual() ? "" : node.asText().trim();
    }

    private static String pathValue(String pathInfo) {
        if (pathInfo == null) {
            return "";
        }
        String value = pathInfo;
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.trim();
    }

    private static boolean isSha256(String value) {
        return value.matches("^[0-9a-f]{64}$");
    }
}
