package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.neko.music.service.ReplayChallengeService;
import com.neko.music.service.ReplayIssueLimiter;
import com.neko.music.service.ReplayNonceService;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.HttpResourceCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 防重放挑战签发接口：{@code GET /api/replay/challenge}。
 *
 * <p>领取 nonce 的第一步：客户端按需要的数量换一道题（{@code seed} + {@code difficulty}），
 * 解题后带 {@code challenge} / {@code proof} 调 {@code GET /api/replay/nonce} 兑换 nonce。
 * 题目一次性、短时有效，并绑定领取方的来源，因此无法转卖或换端复用；批量越大题目越难，
 * 用于抵消「一次请求签发一大批 nonce」的放大效应。</p>
 *
 * <p>本接口与 nonce 接口同样是自举接口，豁免 nonce 校验，由签发限额与防爬过滤器兜底。</p>
 */
public class ReplayChallengeHandler extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        int readCount = ReplayNonceService.clampBatch(request.getParameter("read"), ReplayNonceService.DEFAULT_BATCH);
        int writeCount = ReplayNonceService.clampBatch(request.getParameter("write"), ReplayNonceService.DEFAULT_BATCH);
        if (readCount + writeCount <= 0) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "请至少领取一个 nonce");
            return;
        }

        String clientIp = ClientIpResolver.clientIp(request);
        if (!ReplayIssueLimiter.tryAcquireRequest(clientIp)) {
            sendTooManyRequests(response, "请求过于频繁，请稍后再试");
            return;
        }

        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(
                readCount, writeCount, clientIp, request.getHeader("User-Agent"));
        if (challenge == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "服务繁忙，请稍后重试");
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("challenge", challenge.id());
        data.addProperty("algorithm", ReplayChallengeService.ALGORITHM);
        data.addProperty("seed", challenge.seed());
        data.addProperty("difficulty", challenge.difficulty());
        data.addProperty("read", challenge.read());
        data.addProperty("write", challenge.write());
        data.addProperty("expiresIn", ReplayChallengeService.CHALLENGE_TTL_SECONDS);

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "");
        body.add("data", data);
        sendSuccessResponse(response, body);
    }
}
