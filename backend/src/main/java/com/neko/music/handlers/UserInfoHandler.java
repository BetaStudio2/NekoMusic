package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.model.UserProfile;
import com.neko.music.service.UserManagementException;
import com.neko.music.service.UserManagementService;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.RequestAuthUtil;
import com.neko.music.util.VipUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** 获取当前登录用户信息：{@code GET /api/user/info}。 */
public final class UserInfoHandler extends ApiServlet {

    private final UserManagementService service;
    private final Function<HttpServletRequest, Integer> authResolver;

    public UserInfoHandler() {
        this(Main.getUserManagementService(), RequestAuthUtil::authenticate);
    }

    UserInfoHandler(
            UserManagementService service,
            Function<HttpServletRequest, Integer> authResolver) {
        this.service = service;
        this.authResolver = authResolver;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        Integer userId = authResolver.apply(request);
        if (userId == null) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "auth.login_required");
            return;
        }

        try {
            UserProfile user = service.getUserProfile(userId);
            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.id());
            userData.put("nickname", user.nickname());
            userData.put("email", user.email());
            userData.put("createdAt", user.createdAt());
            userData.put("isVip", VipUtil.isVipActiveNow(user.vipExpiresAt()));
            userData.put("vipExpiresAt",
                    user.vipExpiresAt() != null ? user.vipExpiresAt().toInstant().toString() : null);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", userData);
            sendResponse(response, true, "user.info_ok", data);
        } catch (UserManagementException e) {
            if (e.kind() == UserManagementException.Kind.NOT_FOUND) {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, e.messageKey());
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "common.internal_error");
            }
        }
    }
}
