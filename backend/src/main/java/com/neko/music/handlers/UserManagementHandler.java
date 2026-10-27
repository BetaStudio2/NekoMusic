package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.model.Admin;
import com.neko.music.model.UserAccount;
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
 * 普通用户管理：列表、修改密码/会员、删除。路由保持在 {@code /api/users/*}。
 */
public final class UserManagementHandler extends AdminAccountApiServlet {

    private static final Pattern EDIT_PATH = Pattern.compile("^/(\\d+)/edit/?$");
    private static final Pattern DELETE_PATH = Pattern.compile("^/(\\d+)/?$");

    public UserManagementHandler() {
        this(Main.getUserManagementService(), PermissionHelper::getAdminFromRequest);
    }

    UserManagementHandler(
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
            List<UserAccount> users = service.listUsers(actor);
            response.setStatus(HttpStatus.OK_200);
            sendResponse(response, true, "user.list_ok", users);
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

        Integer userId = targetId(
                request.getPathInfo(),
                EDIT_PATH,
                "admin.user.id_required",
                "admin.user.id_invalid",
                response);
        if (userId == null) {
            return;
        }

        JsonNode body;
        try {
            body = Main.getObjectMapper().readTree(readBody(request));
        } catch (Exception e) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }
        if (body == null || !body.isObject()) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "auth.bad_request");
            return;
        }

        String password = body.hasNonNull("password") && body.get("password").isTextual()
                ? body.get("password").asText()
                : "";
        boolean vipSpecified = body.has("vipExpiresAt");
        String vipRaw = vipSpecified && body.get("vipExpiresAt").isTextual()
                ? body.get("vipExpiresAt").asText()
                : null;
        if (vipSpecified && !body.get("vipExpiresAt").isNull() && !body.get("vipExpiresAt").isTextual()) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST_400, "admin.user.invalid_vip");
            return;
        }

        try {
            service.updateUser(actor, userId, password, vipSpecified, vipRaw);
            if (!password.isBlank()) {
                OperationAudit.add(request, "密码", "（未公开）", "（已重置）");
            }
            if (vipSpecified) {
                OperationAudit.add(request, "会员到期", "（未公开）", vipRaw == null || vipRaw.isBlank() ? "已清除" : vipRaw);
            }
            response.setStatus(HttpStatus.OK_200);
            sendSuccessResponse(response, "user.management.update_ok");
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

        Integer userId = targetId(
                request.getPathInfo(),
                DELETE_PATH,
                "admin.user.id_required",
                "admin.user.id_invalid",
                response);
        if (userId == null) {
            return;
        }

        try {
            service.deleteUser(actor, userId);
            response.setStatus(HttpStatus.OK_200);
            sendSuccessResponse(response, "user.management.delete_ok");
        } catch (UserManagementException e) {
            sendFailure(response, e);
        }
    }
}
