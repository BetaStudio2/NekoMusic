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
import java.util.Map;
import java.util.function.Function;

/** 当前登录用户修改昵称：{@code POST /api/user/nickname/change}。 */
public final class UserNicknameChangeHandler extends ApiServlet {

    private final UserManagementService service;
    private final Function<HttpServletRequest, Integer> authResolver;

    public UserNicknameChangeHandler() {
        this(Main.getUserManagementService(), RequestAuthUtil::authenticate);
    }

    UserNicknameChangeHandler(
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
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED_401, "auth.unauthorized_please_login");
            return;
        }

        NicknameChangeRequest changeRequest;
        try {
            changeRequest = MAPPER.readValue(readBody(request), NicknameChangeRequest.class);
        } catch (Exception e) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }
        if (changeRequest == null) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }

        try {
            String nickname = service.changeUserNickname(userId, changeRequest.nickname());
            sendResponse(response, true, "user.nickname.change_ok", Map.of("nickname", nickname));
        } catch (UserManagementException e) {
            int status = switch (e.kind()) {
                case VALIDATION -> HttpStatus.BAD_REQUEST_400;
                case NOT_FOUND -> HttpStatus.NOT_FOUND_404;
                case CONFLICT -> HttpStatus.CONFLICT_409;
                case FORBIDDEN -> HttpStatus.FORBIDDEN_403;
                case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR_500;
            };
            String messageKey = e.kind() == UserManagementException.Kind.INTERNAL
                    ? "user.nickname.change_failed"
                    : e.messageKey();
            sendErrorResponse(response, status, messageKey);
        }
    }

    private record NicknameChangeRequest(String nickname) {
    }
}
