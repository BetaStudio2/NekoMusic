package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.database.VipPayOrderDatabaseManager;
import com.neko.music.payment.zpay.ZpaySignUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ZPay 异步通知：验签、校验金额与订单，幂等延长会员；须返回纯文本 {@code success}。
 *
 * @see <a href="https://z-pay.cn/doc.html">ZPay 开发文档</a>
 */
public class ZpayNotifyHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ZpayNotifyHandler.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        Map<String, String> params = toSingleValueMap(req);
        ConfigManager cfg = Main.getConfigManager();

        if (!cfg.isZpayEnabled() || cfg.getZpayKey().isEmpty()) {
            writePlain(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "fail");
            return;
        }

        String sign = params.get("sign");
        if (!ZpaySignUtil.verify(params, cfg.getZpayKey(), sign)) {
            logger.warn("ZPay 异步通知验签失败 out={}", params.get("out_trade_no"));
            writePlain(resp, HttpServletResponse.SC_BAD_REQUEST, "fail");
            return;
        }

        String pid = params.getOrDefault("pid", "");
        if (!cfg.getZpayPid().equals(pid)) {
            logger.warn("ZPay 异步通知 pid 不匹配");
            writePlain(resp, HttpServletResponse.SC_BAD_REQUEST, "fail");
            return;
        }

        if (!"TRADE_SUCCESS".equals(params.get("trade_status"))) {
            writePlain(resp, HttpServletResponse.SC_OK, "success");
            return;
        }

        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String money = params.get("money");
        if (outTradeNo == null || outTradeNo.isEmpty() || money == null || money.isEmpty()) {
            writePlain(resp, HttpServletResponse.SC_BAD_REQUEST, "fail");
            return;
        }

        VipPayOrderDatabaseManager.CompleteResult r =
                Main.getVipPayOrderDatabaseManager().tryCompletePaidOrder(outTradeNo, tradeNo, money);
        switch (r) {
            case COMPLETED, ALREADY_DONE -> writePlain(resp, HttpServletResponse.SC_OK, "success");
            case INVALID -> writePlain(resp, HttpServletResponse.SC_BAD_REQUEST, "fail");
        }
    }

    private static Map<String, String> toSingleValueMap(HttpServletRequest req) {
        Map<String, String> m = new HashMap<>();
        for (String name : Collections.list(req.getParameterNames())) {
            String v = req.getParameter(name);
            if (v != null) {
                m.put(name, v);
            }
        }
        return m;
    }

    private static void writePlain(HttpServletResponse resp, int status, String body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/plain;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.print(body);
        }
    }
}
