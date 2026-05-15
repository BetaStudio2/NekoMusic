package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.model.VipPriceItem;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理员全量更新 VIP 价目表（独立 SQLite 库）。需管理员 Bearer Token + 用户编辑权限。
 * <p>请求体 JSON：{@code { "items": [ { "months": 1, "days": 0, "priceYuan": 9.99 }, ... ] }}</p>
 */
public class VipPricingAdminHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(VipPricingAdminHandler.class);
    private static final int MAX_ITEMS = 64;

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        applyCors(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        applyCors(response);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        if (!isAdminTokenPresent(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"message\":\"需要管理员权限\"}");
            return;
        }
        if (!PermissionHelper.checkPermission(request, response, AdminPermissionUtil.Permission.USER_EDIT)) {
            return;
        }

        StringBuilder body = new StringBuilder();
        String line;
        try {
            while ((line = request.getReader().readLine()) != null) {
                body.append(line);
            }
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"无法读取请求体\"}");
            return;
        }

        ObjectMapper mapper = Main.getObjectMapper();
        JsonNode root;
        try {
            root = mapper.readTree(body.toString());
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"JSON 格式无效\"}");
            return;
        }

        if (!root.has("items") || !root.get("items").isArray()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"缺少 items 数组\"}");
            return;
        }

        JsonNode itemsNode = root.get("items");
        if (itemsNode.size() == 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"items 不能为空\"}");
            return;
        }
        if (itemsNode.size() > MAX_ITEMS) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"items 数量超过上限\"}");
            return;
        }

        List<VipPriceItem> parsed = new ArrayList<>();
        for (int i = 0; i < itemsNode.size(); i++) {
            JsonNode row = itemsNode.get(i);
            if (!row.isObject()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\":false,\"message\":\"items 元素必须为对象\"}");
                return;
            }
            int months = row.has("months") && row.get("months").isIntegralNumber() ? row.get("months").asInt() : 0;
            int days = row.has("days") && row.get("days").isIntegralNumber() ? row.get("days").asInt() : 0;
            if (!row.has("priceYuan") || !row.get("priceYuan").isNumber()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\":false,\"message\":\"每项必须包含数字 priceYuan\"}");
                return;
            }
            double price = row.get("priceYuan").asDouble();
            if (months < 0 || days < 0 || months + days <= 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\":false,\"message\":\"months、days 须为非负整数且至少一项大于 0\"}");
                return;
            }
            if (price < 0 || Double.isNaN(price) || Double.isInfinite(price)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\":false,\"message\":\"priceYuan 须为非负有限数\"}");
                return;
            }
            VipPriceItem it = new VipPriceItem();
            it.setMonths(months);
            it.setDays(days);
            it.setPriceYuan(price);
            parsed.add(it);
        }

        try {
            List<VipPriceItem> saved = Main.getVipPricingDatabaseManager().replaceAll(parsed);
            ArrayNode arr = mapper.createArrayNode();
            for (VipPriceItem it : saved) {
                ObjectNode n = mapper.createObjectNode();
                n.put("id", it.getId());
                n.put("months", it.getMonths());
                n.put("days", it.getDays());
                n.put("priceYuan", it.getPriceYuan());
                n.put("sortOrder", it.getSortOrder());
                n.put("updatedAt", it.getUpdatedAt() != null ? it.getUpdatedAt() : "");
                arr.add(n);
            }
            ObjectNode out = mapper.createObjectNode();
            out.put("success", true);
            out.put("message", "价目已更新");
            out.set("data", arr);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(mapper.writeValueAsString(out));
        } catch (Exception e) {
            logger.error("更新 VIP 价目失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"更新价目失败\"}");
        }
    }

    private static boolean isAdminTokenPresent(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) {
            return false;
        }
        String token = h.substring(7);
        return Main.getAdminAuthService().validateAdminToken(token);
    }

    private static void applyCors(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "PUT, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
