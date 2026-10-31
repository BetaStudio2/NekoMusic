package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.model.ClientApp;
import com.neko.music.service.ClientChallengeService;
import com.neko.music.service.ClientIdentityService;
import com.neko.music.service.ReplayIssueLimiter;
import com.neko.music.util.ClientIdentityProtocol;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code GET /api/client/challenge}：实例注册 / 令牌续签的第一步。
 *
 * <p>挑战一次性、短时有效，并绑定 client_id、平台与稳定客户端指纹。接口本身豁免实例签名，
 * 但仍经过 IP 限流、防爬与签发限速。</p>
 */
public class ClientChallengeHandler extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        String clientId = trim(request.getParameter("client_id"));
        String platform = trim(request.getParameter("platform")).toLowerCase(Locale.ROOT);
        String purposeText = trim(request.getParameter("purpose"));
        ClientChallengeService.Purpose purpose = "token".equalsIgnoreCase(purposeText)
                ? ClientChallengeService.Purpose.TOKEN
                : ClientChallengeService.Purpose.REGISTER;
        ClientIdentityProtocol.ReportedClient reported = ClientIdentityProtocol.parseReportedClient(
                request.getHeader("X-Neko-Client"));
        ClientIdentityService service = Main.getClientIdentityService();
        if (clientId.isEmpty() || !ClientIdentityProtocol.isPlatform(platform)
                || reported == null || !platform.equals(reported.platform()) || service == null) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        Optional<ClientApp> app = service.findApp(clientId);
        if (app.isEmpty() || !app.get().active() || !platform.equals(app.get().platform())
                || !service.validateReportedPlatform(app.get(), request.getHeader("User-Agent"))) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "security.client.invalid");
            return;
        }
        String clientIp = ClientIpResolver.clientIp(request);
        if (!ReplayIssueLimiter.tryAcquireRequest(clientIp)) {
            sendTooManyRequests(response, "security.rate_limit.too_frequent");
            return;
        }
        String challenge = ClientChallengeService.issue(clientId, platform, purpose,
                request.getHeader("User-Agent"), request.getHeader("X-Neko-Client"));
        if (challenge == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "security.client.busy");
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("challenge", challenge);
        data.addProperty("expiresIn", ClientChallengeService.CHALLENGE_TTL_SECONDS);
        data.addProperty("purpose", purpose == ClientChallengeService.Purpose.TOKEN ? "token" : "register");
        data.addProperty("protocol", ClientIdentityProtocol.PROTOCOL_VERSION);
        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "");
        body.add("data", data);
        sendSuccessResponse(response, body);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
