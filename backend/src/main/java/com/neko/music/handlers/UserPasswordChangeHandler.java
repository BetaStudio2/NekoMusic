package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.service.UserManagementException;
import com.neko.music.service.UserManagementService;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.RequestAuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;
import java.util.function.Function;

/** 当前登录用户修改密码：{@code POST /api/user/password/change}。 */
public final class UserPasswordChangeHandler extends ApiServlet {

    private final UserManagementService service;
    private final Function<HttpServletRequest, Integer> authResolver;

    public UserPasswordChangeHandler() {
        this(Main.getUserManagementService(), RequestAuthUtil::authenticate);
    }

    UserPasswordChangeHandler(
            UserManagementService service,
            Function<HttpServletRequest, Integer> authResolver) {
        this.service = service;
        this.authResolver = authResolver;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        Integer userId = authResolver.apply(request);
        if (userId == null) {
            String token = RequestAuthUtil.extractToken(request);
            sendErrorObject(response, HttpStatus.UNAUTHORIZED_401,
                    token == null ? "auth.unauthorized" : "auth.invalid_token");
            return;
        }

        PasswordChangeRequest changeRequest;
        try {
            changeRequest = MAPPER.readValue(readBody(request), PasswordChangeRequest.class);
        } catch (Exception e) {
            sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }
        if (changeRequest == null) {
            sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }

        try {
            service.changeUserPassword(userId, changeRequest.oldPassword(), changeRequest.newPassword());
            sendSuccessResponse(response, "auth.password_change.ok");
        } catch (UserManagementException e) {
            sendErrorObject(response, statusFor(e), e.messageKey());
        }
    }

    private static int statusFor(UserManagementException error) {
        return switch (error.kind()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST_400;
            case NOT_FOUND -> HttpStatus.NOT_FOUND_404;
            case CONFLICT -> HttpStatus.CONFLICT_409;
            case FORBIDDEN -> HttpStatus.FORBIDDEN_403;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR_500;
        };
    }

    private record PasswordChangeRequest(String oldPassword, String newPassword) {
    }
}
