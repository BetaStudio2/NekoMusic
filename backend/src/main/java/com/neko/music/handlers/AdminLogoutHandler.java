package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.service.AdminAuthService;
import com.neko.music.service.RedisTokenStore;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 管理端登出：在服务端吊销当前 admin_token。
 *
 * <p>管理端会话只存活 24h 且此前没有任何服务端登出路径，一旦令牌泄露就只能等 TTL 到期；
 * 本接口使其可被立即吊销。属于后台管理接口，不写入对外 API 文档。</p>
 */
public class AdminLogoutHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminLogoutHandler.class);

    private AdminAuthService adminAuthService;

    @Override
    public void init() throws ServletException {
        adminAuthService = Main.getAdminAuthService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        AdminAuthService service = adminAuthService != null ? adminAuthService : Main.getAdminAuthService();
        String token = RedisTokenStore.normalizeBearerToken(request.getHeader("Authorization"));
        if (service == null || token.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "未授权访问");
            return;
        }

        try {
            service.logout(token);
        } catch (Exception e) {
            logger.error("管理端登出时吊销令牌失败", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "退出登录失败，请稍后重试");
            return;
        }

        sendSuccessResponse(response, "已退出登录");
    }
}
