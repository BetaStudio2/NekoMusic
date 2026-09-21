package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.model.User;
import com.neko.music.util.RequestAuthUtil;
import com.neko.music.util.VipUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 获取当前登录用户信息（{@code GET /api/user/info}）。
 *
 * <p>客户端只持久化 Token，昵称等资料启动时用本接口拉取，避免本地缓存过期。</p>
 */
@WebServlet("/api/user/info")
public class UserInfoHandler extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");

        Integer userId = RequestAuthUtil.authenticate(request);
        if (userId == null) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }

        Optional<User> userOpt = Main.getUserAuthService().findUserById(userId);
        if (userOpt.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "用户不存在");
            return;
        }

        User user = userOpt.get();
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.id());
        userData.put("nickname", user.nickname());
        userData.put("email", user.email());
        userData.put("createdAt", user.createdAt());
        userData.put("isVip", VipUtil.isVipActiveNow(user.vipExpiresAt()));
        userData.put("vipExpiresAt",
                user.vipExpiresAt() != null ? user.vipExpiresAt().toInstant().toString() : null);

        Map<String, Object> data = new HashMap<>();
        data.put("user", userData);
        sendResponse(response, true, "获取用户信息成功", data);
    }
}
