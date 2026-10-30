package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.service.NekoAuthOAuthService;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 浏览器跳转端点：{@code /oauth/nekocore/start} 与 {@code /oauth/nekocore/callback}。
 *
 * <p>这是 OAuth 浏览器导航，不是 JSON API：不能要求 nonce / 客户端签名。防重放由一次性 state
 * 保证。回调只把结果写入 JSON 响应体，不跳转到新增页面、不修改现有客户端，也不把 Token 放进 URL。</p>
 */
public final class NekoAuthOAuthHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(NekoAuthOAuthHandler.class);
    private NekoAuthOAuthService oauthService;

    @Override
    public void init() {
        oauthService = Main.getNekoAuthOAuthService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");

        String path = request.getServletPath();
        if (path.endsWith("/start")) {
            handleStart(request, response);
        } else if (path.endsWith("/callback")) {
            handleCallback(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleStart(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (oauthService == null) {
            sendError(response, "oauth_unavailable", "NekoAuth 登录暂时不可用");
            return;
        }
        try {
            response.sendRedirect(oauthService.authorizationUrl(request.getParameter("return_to")));
        } catch (NekoAuthOAuthService.OAuthLoginException exception) {
            logger.warn("NekoAuth 登录发起失败: code={} message={}", exception.code(), exception.getMessage());
            sendError(response, exception.code(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            logger.warn("NekoAuth 登录发起失败: 配置中的地址格式不正确");
            sendError(response, "oauth_not_configured", "NekoAuth 登录配置不正确");
        }
    }

    private void handleCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String oauthError = request.getParameter("error");
        if (oauthError != null && !oauthError.isBlank()) {
            String code = "access_denied".equals(oauthError) ? "access_denied" : "authorization_failed";
            logger.info("NekoAuth 授权未完成: error={}", oauthError);
            sendError(response, code, "NekoAuth 授权未完成");
            return;
        }
        if (oauthService == null) {
            sendError(response, "oauth_unavailable", "NekoAuth 登录暂时不可用");
            return;
        }
        try {
            NekoAuthOAuthService.LoginResult result = oauthService.complete(
                    request.getParameter("code"), request.getParameter("state"));
            JsonObject data = new JsonObject();
            data.addProperty("token", result.token());
            data.addProperty("return_to", result.returnTo());
            JsonObject body = new JsonObject();
            body.addProperty("success", true);
            body.addProperty("message", "登录成功");
            body.add("data", data);
            sendSuccessResponse(response, body);
        } catch (NekoAuthOAuthService.OAuthLoginException exception) {
            logger.warn("NekoAuth 登录回调失败: code={} message={}", exception.code(), exception.getMessage());
            sendError(response, exception.code(), exception.getMessage());
        }
    }

    private void sendError(HttpServletResponse response, String code, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("success", false);
        body.addProperty("error", code);
        body.addProperty("message", message == null || message.isBlank() ? "NekoAuth 登录失败" : message);
        writeJson(response, statusFor(code), GSON.toJson(body));
    }

    private static int statusFor(String code) {
        return switch (code) {
            case "oauth_disabled", "oauth_not_configured", "oauth_unavailable" -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            case "access_denied", "account_not_linked", "email_mismatch", "email_unverified" -> HttpServletResponse.SC_FORBIDDEN;
            case "token_exchange_failed", "userinfo_failed" -> HttpServletResponse.SC_BAD_GATEWAY;
            case "token_issue_failed" -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            default -> HttpServletResponse.SC_BAD_REQUEST;
        };
    }
}
