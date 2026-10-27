package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.model.Admin;
import com.neko.music.service.UserManagementException;
import com.neko.music.service.UserManagementService;
import com.neko.music.util.OperationAudit;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;
import java.util.function.Function;

/** 超级管理员创建普通用户账号：{@code POST /api/admin/create-user}。 */
public final class AdminCreateUserHandler extends AdminAccountApiServlet {

    public AdminCreateUserHandler() {
        this(Main.getUserManagementService(), PermissionHelper::getAdminFromRequest);
    }

    AdminCreateUserHandler(
            UserManagementService service,
            Function<HttpServletRequest, Admin> adminResolver) {
        super(service, adminResolver);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        Admin actor = requireAdmin(request, response);
        if (actor == null) {
            return;
        }

        CreateUserRequest createRequest;
        try {
            createRequest = Main.getObjectMapper().readValue(readBody(request), CreateUserRequest.class);
        } catch (Exception e) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }
        if (createRequest == null) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }

        try {
            service.createUser(
                    actor,
                    createRequest.username(),
                    createRequest.email(),
                    createRequest.password());
            OperationAudit.add(request, "普通用户", "（无）", createRequest.username());
            response.setStatus(HttpStatus.OK_200);
            sendSuccessResponse(response, "admin.user.create_ok");
        } catch (UserManagementException e) {
            sendFailure(response, e);
        }
    }

    private record CreateUserRequest(String username, String email, String password) {
    }
}
