package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.service.ClientIdentityService;
import com.neko.music.util.ClientIdentityProtocol;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /api/client/token}：用实例私钥续签短期令牌。 */
public class ClientTokenHandler extends ApiServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        JsonNode root;
        try {
            root = MAPPER.readTree(readBody(request));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        if (root == null || !root.isObject()) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        String clientId = text(root, "client_id");
        String platform = text(root, "platform");
        String instanceId = text(root, "instance_id");
        String challenge = text(root, "challenge");
        String signature = text(root, "signature");
        long timestamp = root.path("timestamp").asLong(0L);
        ClientIdentityProtocol.ReportedClient reported = ClientIdentityProtocol.parseReportedClient(
                request.getHeader("X-Neko-Client"));
        ClientIdentityService service = Main.getClientIdentityService();
        if (reported == null || !reported.platform().equals(platform) || service == null) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        var issued = service.refresh(new ClientIdentityService.TokenInput(
                        clientId, platform, instanceId, challenge, timestamp, signature),
                request.getHeader("User-Agent"), request.getHeader("X-Neko-Client"));
        if (issued.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("token", issued.get().token());
        data.addProperty("expires_at", issued.get().expiresAtSeconds());
        data.addProperty("protocol", ClientIdentityProtocol.PROTOCOL_VERSION);
        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "");
        body.add("data", data);
        sendSuccessResponse(response, body);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || !node.isTextual() ? "" : node.asText().trim();
    }
}
