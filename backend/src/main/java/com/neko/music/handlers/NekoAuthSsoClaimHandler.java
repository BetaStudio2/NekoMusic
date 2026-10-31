package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.model.User;
import com.neko.music.service.AuthAbuseLimiter;
import com.neko.music.service.NekoAuthOAuthService;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.VipUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** NekoMusic 浏览器 SSO 完成页领取一次性登录交接。 */
public final class NekoAuthSsoClaimHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(NekoAuthSsoClaimHandler.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        if (enforceAuthAbuseLimit(request, response, AuthAbuseLimiter.Scope.LOGIN)) {
            return;
        }
        NekoAuthOAuthService oauthService = Main.getNekoAuthOAuthService();
        UserAuthService authService = Main.getUserAuthService();
        if (oauthService == null || authService == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "登录服务暂时不可用");
            return;
        }

        JsonNode body;
        try {
            body = MAPPER.readTree(readBody(request));
        } catch (IOException exception) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "请求格式不正确");
            return;
        }
        String handoff = body == null ? null : body.path("handoff").asText("");
        try {
            NekoAuthOAuthService.ClaimResult claim = oauthService.claimHandoff(handoff);
            Integer userId = authService.validateToken(claim.accessToken()).orElse(null);
            User user = userId == null ? null : authService.findUserById(userId).orElse(null);
            if (user == null) {
                sendErrorResponse(response, HttpServletResponse.SC_GONE, "登录交接已过期，请重新登录");
                return;
            }
            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.id());
            userData.put("nickname", user.nickname());
            userData.put("email", user.email());
            userData.put("createdAt", user.createdAt());
            userData.put("isVip", VipUtil.isVipActiveNow(user.vipExpiresAt()));
            userData.put("vipExpiresAt",
                    user.vipExpiresAt() != null ? user.vipExpiresAt().toInstant().toString() : null);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("token", claim.accessToken());
            data.put("returnTo", claim.returnTo());
            data.put("user", userData);
            sendResponse(response, true, "登录成功", data);
        } catch (NekoAuthOAuthService.OAuthLoginException exception) {
            logger.info("SSO handoff 领取失败: code={}", exception.code());
            sendErrorResponse(response, HttpServletResponse.SC_GONE, exception.getMessage());
        } catch (RuntimeException exception) {
            logger.warn("SSO handoff 领取异常", exception);
            sendErrorResponse(response, HttpServletResponse.SC_BAD_GATEWAY, "登录服务暂时不可用");
        }
    }

}
