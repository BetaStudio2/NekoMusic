package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.model.User;
import com.neko.music.service.AuthAbuseLimiter;
import com.neko.music.service.LoginThrottleService;
import com.neko.music.service.NekoAuthAccountService;
import com.neko.music.service.SourceBanService;
import com.neko.music.service.UserAuthService;
import com.neko.music.service.UserLoginThrottleService;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.VipUtil;
import jakarta.servlet.annotation.WebServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import com.neko.music.i18n.ApiMessages;

@WebServlet("/api/user/login")
public class UserLoginHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserLoginHandler.class);
    private UserAuthService userAuthService;
    private UserLoginThrottleService throttleService;
    private SourceBanService sourceBanService;

    @Override
    public void init() throws ServletException {
        userAuthService = Main.getUserAuthService();
        throttleService = Main.getUserLoginThrottleService();
        sourceBanService = Main.getSourceBanService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        if (enforceAuthAbuseLimit(request, response, AuthAbuseLimiter.Scope.LOGIN)) {
            return;
        }

        logger.info("收到用户登录请求");

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        try {
            JsonNode requestData = Main.getObjectMapper().readTree(requestBody.toString());

            String email = null;
            String password = null;

            if (requestData != null) {
                if (requestData.has("email")) {
                    email = requestData.get("email").asText();
                }
                if (requestData.has("password")) {
                    password = requestData.get("password").asText();
                }
            }

            // 验证请求参数
            if (email == null || password == null ||
                email.trim().isEmpty() || password.trim().isEmpty()) {

                sendResponse(response, false, "auth.login.fields_required", null);
                return;
            }

            // 验证密码长度
            if (password.length() < 6 || password.length() > 30) {
                sendResponse(response, false, "auth.login.bad_password_length", null);
                return;
            }

            String emailKey = email.trim();
            String clientIp = ClientIpResolver.clientIp(request);

            // 账号维度按与认证完全相同的路径解析出的用户 ID 分桶：邮箱比较走数据库排序规则
            // （大小写不敏感、Unicode 兼容折叠、且忽略重音），改用库内唯一的用户 ID 后，同一邮箱的
            // 任何变形都落到同一桶，不会被重音 / 兼容变体拆出多个空桶放大配额（CWE-178）。
            String accountBucket = LoginThrottleService.accountBucket(
                    userAuthService.findUserIdByEmail(emailKey), emailKey);

            // 失败限流必须早于口令校验：同一账号与来源 IP 的尝试各自原子计数，超阈值直接拒绝，
            // 避免「先校验、失败后再计数」在并发突发下被穿越的竞态（CWE-362）；被锁定后对
            // 「存在的邮箱」与「不存在的邮箱」返回完全一致，因此 429 不构成账号枚举信号。
            if (throttleService != null) {
                Optional<Long> locked = throttleService.beginAttempt(accountBucket, clientIp);
                if (locked.isPresent()) {
                    long retryAfterSec = locked.get();
                    logger.warn("用户登录已被限流: email={} ip={}", emailKey, clientIp);
                    response.setHeader("Retry-After", String.valueOf(retryAfterSec));
                    response.setStatus(429);
                    sendResponse(response, false,
                            ApiMessages.translate("auth.login.too_many_attempts", retryAfterSec), null);
                    return;
                }
            }

            // 用户认证
            Optional<UserAuthService.LoginSession> userOpt = userAuthService.authenticate(emailKey, password);

            if (userOpt.isPresent()) {
                UserAuthService.LoginSession session = userOpt.get();
                User user = session.user();
                logger.info("用户登录成功: {}", user.email());

                if (throttleService != null) {
                    throttleService.recordSuccess(accountBucket, clientIp);
                }
                if (sourceBanService != null) {
                    sourceBanService.recordAuthSuccess(clientIp);
                }

                // 令牌由 NekoAuth 签发；NekoMusic 只返回，不再落本地 token 存储。
                Map<String, Object> userData = new HashMap<>();
                userData.put("id", user.id());
                userData.put("nickname", user.nickname());
                userData.put("email", user.email());
                userData.put("createdAt", user.createdAt());
                userData.put("isVip", VipUtil.isVipActiveNow(user.vipExpiresAt()));
                userData.put("vipExpiresAt",
                        user.vipExpiresAt() != null ? user.vipExpiresAt().toInstant().toString() : null);

                Map<String, Object> responseData = new HashMap<>();
                responseData.put("user", userData);
                responseData.put("token", session.accessToken());

                sendResponse(response, true, "auth.login.ok", responseData);
            } else {
                logger.warn("用户登录失败: {}", email);
                // 来源维度失败计数：反复失败的来源会被递增退避临时封禁（只看请求，不看账号存在性）
                if (sourceBanService != null) {
                    sourceBanService.recordAuthFailure(clientIp, "user-login");
                }
                sendResponse(response, false, "auth.login.bad_credentials", null);
            }

        } catch (NekoAuthAccountService.AccountBridgeException e) {
            if (e.notConfigured() || e.unavailable()) {
                logger.error("NekoAuth 账号桥接不可用: {}", e.code());
                sendResponse(response, false, "auth.service_unavailable", null);
            } else if (e.status() == 403 && "ACCOUNT_DISABLED".equals(e.code())) {
                sendResponse(response, false, "auth.login.account_disabled", null);
            } else {
                logger.warn("NekoAuth 登录失败: status={} code={}", e.status(), e.code());
                sendResponse(response, false, "auth.login.bad_credentials", null);
            }
        } catch (Exception e) {
            logger.error("处理用户登录请求时发生错误: {}", e.getMessage(), e);
            sendResponse(response, false, "common.internal_error", null);
        }
    }

    /**
     * 发送JSON响应
     */
}
