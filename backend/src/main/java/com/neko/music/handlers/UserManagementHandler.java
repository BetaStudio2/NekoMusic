package com.neko.music.handlers;

import com.neko.music.util.PermissionHelper;

import com.neko.music.model.SuccessResponse;
import com.neko.music.model.ErrorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.util.HandlerResponses;
import com.neko.music.util.VipUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class UserManagementHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
            return;
        }
        
        // 检查是否有用户查看权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.USER_VIEW)) {
            logger.warn("权限不足，无用户查看权限");
            return;
        }

        String pathInfo = request.getPathInfo();
        
        // 如果 pathInfo 为 null 或空，说明是访问 /api/users，返回所有用户
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("")) {
            // 获取所有普通用户
            List<RegularUser> regularUsers = getAllRegularUsers();

            HandlerResponses.writeJson(response, HttpStatus.OK_200, new RegularUsersResponse(true, "获取用户列表成功", regularUsers));
            return;
        }

        // 其他路径处理（如果需要）
        HandlerResponses.writeJson(response, HttpStatus.NOT_FOUND_404, new ErrorResponse("未找到请求的资源"));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
            return;
        }
        
        // 检查是否有用户编辑权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.USER_EDIT)) {
            logger.warn("权限不足，无用户编辑权限");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.endsWith("/edit")) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的请求路径"));
            return;
        }

        // 解析用户ID
        String pathWithoutEdit = pathInfo.substring(0, pathInfo.indexOf("/edit"));
        String idStr = pathWithoutEdit.replace("/", "");
        int userId;
        try {
            userId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的用户ID"));
            return;
        }

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            requestBody.append(line);
        }

        JsonNode root;
        try {
            root = Main.getObjectMapper().readTree(requestBody.toString());
        } catch (Exception e) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的请求格式"));
            return;
        }

        String pwdField = root.has("password") && root.get("password").isTextual() ? root.get("password").asText("") : "";
        boolean hasPassword = root.has("password") && !pwdField.isBlank();
        boolean hasVip = root.has("vipExpiresAt");
        if (!hasPassword && !hasVip) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("请提供非空 password 和/或 vipExpiresAt"));
            return;
        }

        try {
            boolean success = updateUserFields(userId, root);
            if (!success) {
                HandlerResponses.writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, new ErrorResponse("修改用户信息失败"));
                return;
            }
        } catch (IllegalArgumentException e) {
            logger.warn("修改用户 VIP 参数无效: {}", e.getMessage());
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse(e.getMessage()));
            return;
        }

        HandlerResponses.writeJson(response, HttpStatus.OK_200, new SuccessResponse(true, "修改用户信息成功"));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查管理员权限
        if (HandlerResponses.rejectIfUnauthorized(request, response)) {
            return;
        }
        
        // 检查是否有用户删除权限
        if (!com.neko.music.util.PermissionHelper.checkPermission(request, response, com.neko.music.util.AdminPermissionUtil.Permission.USER_DELETE)) {
            logger.warn("权限不足，无用户删除权限");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("用户ID不能为空"));
            return;
        }

        // 解析用户ID
        String idStr = pathInfo.replace("/", "");
        int userId;
        try {
            userId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            HandlerResponses.writeJson(response, HttpStatus.BAD_REQUEST_400, new ErrorResponse("无效的用户ID"));
            return;
        }

        // 删除用户
        boolean success = deleteUser(userId);
        
        if (!success) {
            HandlerResponses.writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, new ErrorResponse("删除用户失败"));
            return;
        }

        HandlerResponses.writeJson(response, HttpStatus.OK_200, new SuccessResponse(true, "删除用户成功"));
    }

    // 检查管理员权限

    // 获取所有普通用户
    private List<RegularUser> getAllRegularUsers() {
        List<RegularUser> regularUsers = new ArrayList<>();
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, username, email, created_at, vip_expires_at FROM users ORDER BY created_at DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Timestamp vip = rs.getTimestamp("vip_expires_at");
                    boolean hasVip = !rs.wasNull();
                    regularUsers.add(new RegularUser(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getTimestamp("created_at").toString(),
                            hasVip && VipUtil.isVipActiveNow(vip),
                            hasVip ? vip.toInstant().toString() : null));
                }
            }
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
        }
        
        return regularUsers;
    }

    // 删除用户
    private boolean deleteUser(int userId) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "DELETE FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (Exception e) {
            logger.error("删除用户失败", e);
            return false;
        }
    }

    /**
     * 更新密码与/或 VIP 到期时间。请求 JSON 字段：
     * <ul>
     *   <li>{@code password}（可选）：非空则更新密码</li>
     *   <li>{@code vipExpiresAt}（可选）：ISO-8601 字符串、或 {@code null}、或空字符串表示清除会员</li>
     * </ul>
     */
    private boolean updateUserFields(int userId, JsonNode root) throws IllegalArgumentException {
        String password = null;
        if (root.has("password") && root.get("password").isTextual()) {
            String p = root.get("password").asText("");
            if (!p.isBlank()) {
                password = p;
            }
        }

        boolean hasVip = root.has("vipExpiresAt");
        boolean clearVip = false;
        Timestamp vipTs = null;
        if (hasVip) {
            JsonNode n = root.get("vipExpiresAt");
            if (n.isNull()) {
                clearVip = true;
            } else if (n.isTextual()) {
                String raw = n.asText("").trim();
                if (raw.isEmpty()) {
                    clearVip = true;
                } else {
                    vipTs = parseVipExpiresAt(raw);
                }
            } else {
                throw new IllegalArgumentException("vipExpiresAt 必须为字符串或 null");
            }
        }

        if (password == null && !hasVip) {
            return false;
        }

        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (password != null) {
                    de.mkammerer.argon2.Argon2 argon2 = de.mkammerer.argon2.Argon2Factory.create();
                    String passwordHash = argon2.hash(10, 65536, 1, password.toCharArray());
                    String sql = "UPDATE users SET password = ? WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, passwordHash);
                        stmt.setInt(2, userId);
                        if (stmt.executeUpdate() == 0) {
                            conn.rollback();
                            return false;
                        }
                    }
                }
                if (hasVip) {
                    String sql = "UPDATE users SET vip_expires_at = ? WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        if (clearVip) {
                            stmt.setNull(1, Types.TIMESTAMP);
                        } else {
                            stmt.setTimestamp(1, vipTs);
                        }
                        stmt.setInt(2, userId);
                        if (stmt.executeUpdate() == 0) {
                            conn.rollback();
                            return false;
                        }
                    }
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                logger.error("修改用户信息失败", e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("修改用户信息失败", e);
            return false;
        }
    }

    private static Timestamp parseVipExpiresAt(String raw) {
        try {
            if (raw.endsWith("Z") || raw.contains("+") || raw.matches(".+-\\d{2}:\\d{2}$")) {
                return Timestamp.from(Instant.parse(raw));
            }
            LocalDateTime ldt = LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return Timestamp.from(ldt.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("vipExpiresAt 格式无效，请使用 ISO-8601 日期时间");
        }
    }

    // 内部类：普通用户
    public record RegularUser(int id, String username, String email, String registerTime, boolean vip, String vipExpiresAt) {
    }
    // 内部类：普通用户列表响应
    private record RegularUsersResponse(boolean success, String message, List<RegularUser> data) {
    }
    // 内部类：成功响应

    // 内部类：错误响应
}
