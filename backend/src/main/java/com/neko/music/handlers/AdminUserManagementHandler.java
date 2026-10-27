package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.model.Admin;
import com.neko.music.model.AdminAccount;
import com.neko.music.service.UserManagementException;
import com.neko.music.service.UserManagementService;
import com.neko.music.util.OperationAudit;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 管理员账号管理：列表、重置密码、删除。路由保持在 {@code /api/admin/users/*}。
 */
public final class AdminUserManagementHandler extends AdminAccountApiServlet {

    private static final Pattern EDIT_PATH = Pattern.compile("^/(\\d+)/edit/?$");
    private static final Pattern DELETE_PATH = Pattern.compile("^/(\\d+)/?$");

    public AdminUserManagementHandler() {
        this(Main.getUserManagementService(), PermissionHelper::getAdminFromRequest);
    }

    AdminUserManagementHandler(
            UserManagementService service,
            Function<HttpServletRequest, Admin> adminResolver) {
        super(service, adminResolver);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        Admin actor = requireAdmin(request, response);
        if (actor == null) {
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo != null && !pathInfo.isBlank() && !"/".equals(pathInfo)) {
            sendErrorResponse(response, HttpStatus.NOT_FOUND_404, "user.not_found");
            return;
        }

        try {
            List<AdminAccount> accounts = service.listAdmins(actor);
            response.setStatus(HttpStatus.OK_200);
            sendResponse(response, true, "admin.user.list_ok", accounts);
        } catch (UserManagementException e) {
            sendFailure(response, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        Admin actor = requireAdmin(request, response);
        if (actor == null) {
            return;
        }

        Integer adminId = targetId(
                request.getPathInfo(),
                EDIT_PATH,
                "admin.account.id_required",
                "admin.account.id_invalid",
                response);
        if (adminId == null) {
            return;
        }

        AdminEditRequest editRequest;
        try {
            editRequest = Main.getObjectMapper().readValue(readBody(request), AdminEditRequest.class);
        } catch (Exception e) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }

        try {
            service.updateAdminPassword(actor, adminId, editRequest == null ? null : editRequest.password());
            OperationAudit.add(request, "密码", "（未公开）", "（已重置）");
            response.setStatus(HttpStatus.OK_200);
            sendSuccessResponse(response, "admin.user.update_ok");
        } catch (UserManagementException e) {
            sendFailure(response, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        Admin actor = requireAdmin(request, response);
        if (actor == null) {
            return;
        }

        Integer adminId = targetId(
                request.getPathInfo(),
                DELETE_PATH,
                "admin.account.id_required",
                "admin.account.id_invalid",
                response);
        if (adminId == null) {
            return;
        }

        try {
            service.deleteAdmin(actor, adminId);
            response.setStatus(HttpStatus.OK_200);
            sendSuccessResponse(response, "admin.user.delete_ok");
        } catch (UserManagementException e) {
            sendFailure(response, e);
        }
    }

    private record AdminEditRequest(String password) {
    }
}
