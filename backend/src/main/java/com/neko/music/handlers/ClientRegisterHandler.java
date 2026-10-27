package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.service.ClientIdentityService;
import com.neko.music.util.ClientIdentityProtocol;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code POST /api/client/register}：证明持有新实例私钥并登记实例，返回短期令牌。
 *
 * <p>要求平台证明的 Android 包可附带 Google Key Attestation 证书链；证明不可用时记录降级
 * 可信等级，但仍以挑战签名验证实例私钥。机密第三方客户端在此接口额外校验创建时下发的 secret。</p>
 */
public class ClientRegisterHandler extends ApiServlet {

    private static final int MAX_BODY_CHARS = 96 * 1024;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        String raw = readBody(request);
        if (raw.length() > MAX_BODY_CHARS) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
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
        String publicKey = text(root, "public_key");
        String challenge = text(root, "challenge");
        String signature = text(root, "signature");
        String bodyOrigin = text(root, "origin");
        long timestamp = root.path("timestamp").asLong(0L);
        ClientIdentityProtocol.ReportedClient reported = ClientIdentityProtocol.parseReportedClient(
                request.getHeader("X-Neko-Client"));
        if (reported == null || !reported.platform().equals(platform)) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        String headerOrigin = request.getHeader("Origin");
        if (headerOrigin != null && !headerOrigin.isBlank()
                && !bodyOrigin.isBlank() && !headerOrigin.equals(bodyOrigin)) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        String origin = bodyOrigin.isBlank() ? headerOrigin : bodyOrigin;
        List<String> chain = new ArrayList<>();
        JsonNode chainNode = root.path("attestation").path("chain");
        if (chainNode.isArray()) {
            for (JsonNode item : chainNode) {
                if (item.isTextual()) {
                    chain.add(item.asText());
                }
            }
        }
        ClientIdentityService service = Main.getClientIdentityService();
        if (service == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "security.client.busy");
            return;
        }
        var result = service.register(new ClientIdentityService.RegistrationInput(
                        clientId, platform, instanceId, publicKey, challenge, timestamp,
                        signature, chain, origin),
                ClientIpResolver.clientIp(request), request.getHeader("User-Agent"),
                request.getHeader(ClientIdentityProtocol.HEADER_SECRET));
        if (result.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        ClientIdentityService.Registration registration = result.get();
        JsonObject data = new JsonObject();
        data.addProperty("instance_id", registration.instance().instanceId());
        data.addProperty("token", registration.token().token());
        data.addProperty("expires_at", registration.token().expiresAtSeconds());
        data.addProperty("attestation", registration.attestationLevel());
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
