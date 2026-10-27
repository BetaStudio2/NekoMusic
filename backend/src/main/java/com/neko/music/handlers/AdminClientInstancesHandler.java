package com.neko.music.handlers;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.database.ClientRegistryDatabaseManager;
import com.neko.music.model.ClientInstance;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** 后台查看 / 吊销客户端安装实例。管理端接口，不写入公开 API 文档。 */
public class AdminClientInstancesHandler extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.CLIENT_VIEW)) {
            return;
        }
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        ArrayNode items = MAPPER.createArrayNode();
        for (ClientInstance instance : registry().listInstances(request.getParameter("client_id"))) {
            ObjectNode node = items.addObject();
            node.put("instanceId", instance.instanceId());
            node.put("clientId", instance.clientId());
            node.put("platform", instance.platform());
            node.put("attestationLevel", instance.attestationLevel());
            node.put("appId", instance.appId());
            node.put("signingDigest", instance.signingDigest());
            node.put("status", instance.status());
            node.put("createdAt", instance.createdAt());
            node.put("lastSeenAt", instance.lastSeenAt());
            node.put("expiresAt", instance.expiresAt());
        }
        ObjectNode data = MAPPER.createObjectNode();
        data.set("items", items);
        sendJson(response, 200, true, "ok", data);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.CLIENT_EDIT)) {
            return;
        }
        String instanceId = pathValue(request.getPathInfo());
        if (instanceId.isEmpty() || !registry().revokeInstance(instanceId)) {
            sendErrorResponse(response, 404, "admin.client.not_found");
            return;
        }
        Main.getClientIdentityService().invalidate(null, instanceId);
        sendJson(response, 200, true, "ok",
                MAPPER.createObjectNode().put("instanceId", instanceId).put("status", "revoked"));
    }

    private static ClientRegistryDatabaseManager registry() {
        return Main.getClientRegistryDatabaseManager();
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
}
