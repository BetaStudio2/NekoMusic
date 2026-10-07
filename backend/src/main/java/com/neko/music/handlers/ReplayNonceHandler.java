package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neko.music.service.ReplayNonceService;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * 防重放 nonce 签发接口：{@code GET /api/replay/nonce}。
 *
 * <p>客户端批量领取一次性 nonce（读 / 写两个类别），随后在每个受保护动态请求上带
 * {@code X-Neko-Nonce}。nonce 绑定调用方 IP 哈希，一次性消费，默认 120 秒过期。</p>
 *
 * <p>查询参数：{@code read} / {@code write} 分别指定两类的数量（缺省 {@code 16}，上限
 * {@code 64}）；显式传 {@code 0} 表示该类不领。响应：</p>
 * <pre>{"success":true,"message":"","data":{"nonces":{"read":["…"],"write":["…"]},"expiresIn":120}}</pre>
 *
 * <p>本接口自身豁免 nonce 校验（否则无法自举），由 IP 限流与防爬过滤器兜底。</p>
 */
public class ReplayNonceHandler extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        int readCount = parseCount(request.getParameter("read"), ReplayNonceService.DEFAULT_BATCH);
        int writeCount = parseCount(request.getParameter("write"), ReplayNonceService.DEFAULT_BATCH);

        String clientIp = ClientIpResolver.clientIp(request);
        List<String> readNonces = ReplayNonceService.issue(ReplayNonceService.SCOPE_READ, readCount, clientIp);
        List<String> writeNonces = ReplayNonceService.issue(ReplayNonceService.SCOPE_WRITE, writeCount, clientIp);

        // 少发即视为 Redis 故障：明确报错，绝不静默返回不足的 nonce（否则客户端会拿到空池）
        if (readNonces.size() < readCount || writeNonces.size() < writeCount) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "服务繁忙，请稍后重试");
            return;
        }

        JsonObject nonces = new JsonObject();
        nonces.add("read", toJsonArray(readNonces));
        nonces.add("write", toJsonArray(writeNonces));

        JsonObject data = new JsonObject();
        data.add("nonces", nonces);
        data.addProperty("expiresIn", ReplayNonceService.NONCE_TTL_SECONDS);

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "");
        body.add("data", data);
        sendSuccessResponse(response, body);
    }

    /** 解析数量参数：缺省用 {@code defaultValue}，非法值兜底默认，越界夹取到 [0, MAX_BATCH]。 */
    private static int parseCount(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(0, Math.min(value, ReplayNonceService.MAX_BATCH));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
