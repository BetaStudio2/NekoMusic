package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.model.VipPriceItem;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * 公开读取 VIP 价目表（独立 SQLite 库）。
 */
public class VipPricingPublicHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(VipPricingPublicHandler.class);

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        applyCors(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        applyCors(response);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        try {
            List<VipPriceItem> list = Main.getVipPricingDatabaseManager().listAll();
            ObjectMapper mapper = Main.getObjectMapper();
            ArrayNode arr = mapper.createArrayNode();
            for (VipPriceItem it : list) {
                arr.add(itemNode(mapper, it));
            }
            ObjectNode root = mapper.createObjectNode();
            root.put("success", true);
            root.set("data", arr);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(mapper.writeValueAsString(root));
        } catch (Exception e) {
            logger.error("获取 VIP 价目失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"message\":\"服务器内部错误\"}");
        }
    }

    private static ObjectNode itemNode(ObjectMapper mapper, VipPriceItem it) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", it.getId());
        n.put("months", it.getMonths());
        n.put("days", it.getDays());
        n.put("priceYuan", it.getPriceYuan());
        n.put("sortOrder", it.getSortOrder());
        n.put("updatedAt", it.getUpdatedAt() != null ? it.getUpdatedAt() : "");
        return n;
    }

    private static void applyCors(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
