package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neko.music.service.ReplayChallengeService;
import com.neko.music.service.ReplayIssueLimiter;
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
 * {@code 64}）；显式传 {@code 0} 表示该类不领；{@code challenge} / {@code proof} 为
 * {@code GET /api/replay/challenge} 下发题目的编号与解答。响应：</p>
 * <pre>{"success":true,"message":"","data":{"nonces":{"read":["…"],"write":["…"]},"expiresIn":120}}</pre>
 *
 * <p>两条路径：携带 {@code challenge} + {@code proof} 时先验题（题目一次性、绑定来源，批量越大越难），
 * 通过后按题目绑定的数量签发。领取**必须**带挑战：{@link #REQUIRE_CHALLENGE} 打开时，没有
 * {@code challenge} 的请求直接 {@code 400}，否则「领取」又会退化成可被脚本批量刷取的免费接口。
 * 请求要过「单来源请求 + 全站 nonce 额度」两道闸门，超出返回 {@code 429}；单来源闸门在验题之前，
 * 避免限额被拒时把客户端已经解好的题目消耗掉。</p>
 *
 * <p>本接口自身豁免 nonce 校验（否则无法自举），由签发限额与防爬过滤器兜底。</p>
 */
public class ReplayNonceHandler extends ApiServlet {

    /**
     * 是否强制走「换题 → 解题 → 兑换」。
     *
     * <p>仅在需要紧急回退时改成 {@code false}：那会恢复过渡期的直接领取，任何客户端脚本都能
     * 免解题批量领取 nonce（仍受签发限额封顶），属于破窗行为，修好后应立刻改回。
     * 与豁免清单一样，开关写成类常量，不新增配置项。</p>
     */
    private static final boolean REQUIRE_CHALLENGE = true;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        String clientIp = ClientIpResolver.clientIp(request);
        int readCount = 0;
        int writeCount = 0;

        // 先过单来源闸门：限额被拒时不该消耗客户端已经解好的题目
        if (!ReplayIssueLimiter.tryAcquireRequest(clientIp)) {
            sendTooManyRequests(response, "请求过于频繁，请稍后再试");
            return;
        }

        String challengeId = request.getParameter("challenge");
        if (challengeId == null || challengeId.isBlank()) {
            if (REQUIRE_CHALLENGE) {
                // 领取必须先换题：没有 challenge 一律拒绝
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "缺少挑战，请先调用 GET /api/replay/challenge 换题");
                return;
            }
            // 紧急回退路径：直接领取（受下面的签发限额封顶）
            readCount = ReplayNonceService.clampBatch(request.getParameter("read"), ReplayNonceService.DEFAULT_BATCH);
            writeCount = ReplayNonceService.clampBatch(request.getParameter("write"), ReplayNonceService.DEFAULT_BATCH);
        } else {
            ReplayChallengeService.Outcome outcome = ReplayChallengeService.verify(
                    challengeId, request.getParameter("proof"), clientIp, request.getHeader("User-Agent"));
            switch (outcome.status()) {
                case OK -> {
                    // 以题目绑定的数量为准，避免「小批量领题、大批量兑换」
                    readCount = outcome.read();
                    writeCount = outcome.write();
                }
                case BAD_PROOF -> {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "挑战校验失败，请重新获取");
                    return;
                }
                default -> {
                    sendErrorResponse(response, HttpServletResponse.SC_CONFLICT, "挑战已失效，请重新获取");
                    return;
                }
            }
        }

        if (!ReplayIssueLimiter.tryAcquireNonces(readCount + writeCount)) {
            sendTooManyRequests(response, "请求过于频繁，请稍后再试");
            return;
        }

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

    private static JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
