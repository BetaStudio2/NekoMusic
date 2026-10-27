package com.neko.music.handlers;

import com.neko.music.model.Admin;
import com.neko.music.service.UserManagementException;
import com.neko.music.service.UserManagementService;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 用户管理接口共用的鉴权、缓存头、路径解析与错误映射。 */
abstract class AdminAccountApiServlet extends ApiServlet {

    protected final UserManagementService service;
    private final Function<HttpServletRequest, Admin> adminResolver;

    protected AdminAccountApiServlet(
            UserManagementService service,
            Function<HttpServletRequest, Admin> adminResolver) {
        this.service = service;
        this.adminResolver = adminResolver;
    }

    protected final void prepare(HttpServletResponse response) {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
    }

    /** 返回当前管理员；未登录时已写出 401，调用方直接 return。 */
    protected final Admin requireAdmin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Admin admin = adminResolver.apply(request);
        if (admin == null || !admin.active()) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED_401, "auth.unauthorized");
            return null;
        }
        return admin;
    }

    protected final void sendFailure(HttpServletResponse response, UserManagementException error)
            throws IOException {
        int status = switch (error.kind()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST_400;
            case NOT_FOUND -> HttpStatus.NOT_FOUND_404;
            case CONFLICT -> HttpStatus.CONFLICT_409;
            case FORBIDDEN -> HttpStatus.FORBIDDEN_403;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR_500;
        };
        sendErrorResponse(response, status, error.messageKey());
    }

    /** 从 {@code /12/edit} 这样的路径中取出唯一的目标 ID。 */
    protected final Integer targetId(
            String pathInfo,
            Pattern pattern,
            String missingKey,
            String invalidKey,
            HttpServletResponse response) throws IOException {
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, missingKey);
            return null;
        }
        Matcher matcher = pattern.matcher(pathInfo);
        if (!matcher.matches()) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, invalidKey);
            return null;
        }
        try {
            int id = Integer.parseInt(matcher.group(1));
            if (id <= 0) {
                throw new NumberFormatException("id must be positive");
            }
            return id;
        } catch (NumberFormatException e) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, invalidKey);
            return null;
        }
    }
}
