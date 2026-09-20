package com.neko.music.util;

import com.neko.music.Main;
import com.neko.music.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;

/**
 * Handler 层共用的 HTTP JSON 响应写出逻辑。
 *
 * <p>此前多个处理器各自内联「设置状态码 + content-type + Jackson 序列化写出」，
 * 这里收敛为一份，输出契约不变（content-type 与响应体逐字节一致）。
 */
public final class HandlerResponses {

    private HandlerResponses() {
    }

    /** 统一的 JSON 输出：状态码 + content-type + Jackson 序列化。 */
    public static void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(body));
    }

    /** 未通过管理员令牌校验时写出 401，返回 true 表示调用方应立即 return。 */
    public static boolean rejectIfUnauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (PermissionHelper.isAdminAuthorized(request)) {
            return false;
        }
        writeJson(response, HttpStatus.UNAUTHORIZED_401, new ErrorResponse("未授权访问"));
        return true;
    }
}
