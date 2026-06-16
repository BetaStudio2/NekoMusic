package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.database.VipPayOrderDatabaseManager;
import com.neko.music.model.VipPriceItem;
import com.neko.music.payment.zpay.ZpayMapiClient;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 登录用户发起 VIP 套餐 ZPay 支付：写入本地订单并调用 {@code mapi.php} 返回收银台 URL / 二维码等。
 */
public class VipPayCreateHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(VipPayCreateHandler.class);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        ConfigManager cfg = Main.getConfigManager();
        if (!cfg.isZpayEnabled()) {
            sendJson(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, false, "支付功能未启用");
            return;
        }
        if (cfg.getZpayPid().isEmpty() || cfg.getZpayKey().isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, false, "支付未配置完整（pid/key）");
            return;
        }
        String notifyUrl = cfg.getZpayNotifyUrl();
        if (notifyUrl.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, false, "未配置 zpay.public_base_url");
            return;
        }
        String ret = cfg.getZpayFrontendReturnUrl();
        if (ret.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, false, "未配置 zpay.public_base_url 或 zpay.frontend_return_url（支付完成回跳）");
            return;
        }

        String auth = req.getHeader("Authorization");
        Optional<Integer> userIdOpt = Main.getUserAuthService().validateToken(auth == null ? "" : auth);
        if (userIdOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_UNAUTHORIZED, false, "请先登录");
            return;
        }
        int userId = userIdOpt.get();

        StringBuilder body = new StringBuilder();
        try (BufferedReader r = req.getReader()) {
            String line;
            while ((line = r.readLine()) != null) {
                body.append(line);
            }
        }
        JsonObject root;
        try {
            root = Main.getGson().fromJson(body.toString(), JsonObject.class);
        } catch (Exception e) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "请求体不是合法 JSON");
            return;
        }
        if (root == null || !root.has("pricingId") || !root.get("pricingId").isJsonPrimitive()) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "缺少 pricingId");
            return;
        }
        int pricingId = root.get("pricingId").getAsInt();
        String payType = "alipay";
        if (root.has("payType") && root.get("payType").isJsonPrimitive()) {
            payType = root.get("payType").getAsString().trim().toLowerCase();
        }
        if (!"alipay".equals(payType) && !"wxpay".equals(payType)) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "payType 仅支持 alipay 或 wxpay");
            return;
        }

        Optional<VipPriceItem> itemOpt = Main.getVipPricingDatabaseManager().getById(pricingId);
        if (itemOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "套餐不存在");
            return;
        }
        VipPriceItem item = itemOpt.get();
        if (item.getMonths() <= 0 && item.getDays() <= 0) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "套餐时长无效");
            return;
        }
        if (item.getPriceYuan() <= 0) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "套餐价格无效");
            return;
        }

        BigDecimal moneyBd = BigDecimal.valueOf(item.getPriceYuan()).setScale(2, RoundingMode.HALF_UP);
        String moneyStr = moneyBd.toPlainString();
        String outTradeNo = VipPayOrderDatabaseManager.newOutTradeNo();
        String productName = buildProductName(item);

        VipPayOrderDatabaseManager orders = Main.getVipPayOrderDatabaseManager();
        try {
            orders.insertPending(outTradeNo, userId, pricingId, item.getMonths(), item.getDays(), moneyBd, payType);
        } catch (Exception e) {
            logger.error("写入支付订单失败", e);
            sendJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "创建订单失败");
            return;
        }

        Map<String, String> mapi = new HashMap<>();
        mapi.put("pid", cfg.getZpayPid());
        mapi.put("type", payType);
        mapi.put("out_trade_no", outTradeNo);
        mapi.put("notify_url", notifyUrl);
        mapi.put("name", productName);
        mapi.put("money", moneyStr);
        mapi.put("clientip", clientIp(req));
        mapi.put("device", guessDevice(req));
        mapi.put("param", "");
        mapi.put("return_url", ret);

        try {
            JsonObject zr = ZpayMapiClient.requestMapi(cfg, mapi);
            if (!ZpayMapiClient.isApiSuccess(zr)) {
                String msg = zr.has("msg") ? zr.get("msg").getAsString() : "ZPay 下单失败";
                orders.deletePending(outTradeNo);
                sendJson(resp, HttpServletResponse.SC_BAD_GATEWAY, false, msg);
                return;
            }
            JsonObject data = new JsonObject();
            data.addProperty("outTradeNo", outTradeNo);
            copyIfPresent(zr, data, "O_id");
            copyIfPresent(zr, data, "trade_no");
            copyIfPresent(zr, data, "payurl");
            copyIfPresent(zr, data, "payurl2");
            copyIfPresent(zr, data, "qrcode");
            copyIfPresent(zr, data, "img");
            JsonObject ok = new JsonObject();
            ok.addProperty("success", true);
            ok.add("data", data);
            sendRawJson(resp, HttpServletResponse.SC_OK, ok);
        } catch (Exception e) {
            logger.error("调用 ZPay mapi 失败", e);
            orders.deletePending(outTradeNo);
            sendJson(resp, HttpServletResponse.SC_BAD_GATEWAY, false, "支付网关请求失败: " + e.getMessage());
        }
    }

    private static void copyIfPresent(JsonObject from, JsonObject to, String key) {
        if (from.has(key) && !from.get(key).isJsonNull()) {
            to.add(key, from.get(key));
        }
    }

    private static String buildProductName(VipPriceItem item) {
        StringBuilder sb = new StringBuilder("Neko歌姬计划 VIP");
        if (item.getMonths() > 0) {
            sb.append(item.getMonths()).append("个月");
        }
        if (item.getDays() > 0) {
            sb.append(item.getDays()).append("天");
        }
        String s = sb.toString();
        if (s.length() > 100) {
            return s.substring(0, 100);
        }
        return s;
    }

    private static String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "127.0.0.1" : ip;
    }

    private static String guessDevice(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua == null) {
            return "pc";
        }
        String u = ua.toLowerCase();
        if (u.contains("mobile") || u.contains("android") || u.contains("iphone")) {
            return "mobile";
        }
        return "pc";
    }

    private static void sendJson(HttpServletResponse resp, int code, boolean success, String message) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("success", success);
        o.addProperty("message", message);
        sendRawJson(resp, code, o);
    }

    private static void sendRawJson(HttpServletResponse resp, int code, JsonObject o) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setStatus(code);
        try (PrintWriter w = resp.getWriter()) {
            w.print(o.toString());
        }
    }
}
