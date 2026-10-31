package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.neko.music.service.ReplayChallengeService;
import com.neko.music.service.ReplayDifficultyPolicy;
import com.neko.music.service.ReplaySourceMarkService;
import com.neko.music.service.ReplayIssueLimiter;
import com.neko.music.service.ReplayNonceService;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.PowAudit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 防重放挑战签发接口：{@code GET /api/replay/challenge}。
 *
 * <p>领取 nonce 的第一步：客户端按需要的数量换一道题（{@code seed} + {@code difficulty}），
 * 解题后带 {@code challenge} / {@code proof} 调 {@code GET /api/replay/nonce} 兑换 nonce。
 * 题目一次性、短时有效，并绑定领取方的来源，因此无法转卖或换端复用；批量越大题目越难，
 * 用于抵消「一次请求签发一大批 nonce」的放大效应；此外还会按**该来源的领取压力**动态加压
 * （见 {@link ReplayDifficultyPolicy}），让持续脚本的单价随频率上升。</p>
 *
 * <p>本接口与 nonce 接口同样是自举接口，豁免 nonce 校验，由签发限额与防爬过滤器兜底。</p>
 *
 * <p>加压档位随来源频率无限累加；当目标难度越过
 * {@link ReplayChallengeService#MAX_ISSUABLE_DIFFICULTY_BITS}（任何客户端都已解不开）时，本接口不再
 * 发题，改为把来源落库标记（{@link ReplaySourceMarkService}）并回 {@code 429}。已被标记的来源在标记
 * 有效期内不再签发新题。</p>
 */
public class ReplayChallengeHandler extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        int readCount = ReplayNonceService.clampBatch(request.getParameter("read"), ReplayNonceService.DEFAULT_BATCH);
        int writeCount = ReplayNonceService.clampBatch(request.getParameter("write"), ReplayNonceService.DEFAULT_BATCH);
        if (readCount + writeCount <= 0) {
            // 不解释参数该怎么写：请求本身有误时统一回一句，别把领取规则指给探测者
            PowAudit.mark(request, null, PowAudit.INVALID);
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "security.replay.invalid");
            return;
        }

        String clientIp = ClientIpResolver.clientIp(request);
        if (!ReplayIssueLimiter.tryAcquireRequest(clientIp)) {
            PowAudit.mark(request, null, PowAudit.RATE_LIMITED);
            sendTooManyRequests(response, "security.rate_limit.too_frequent");
            return;
        }

        // 按该来源的领取压力给出加压档位：批量由客户端自选、可以压低，压力计数不能。
        // 阶梯无上限，因此费用会一直往上涨，直到越过「题目还发得出去」的界线。
        ReplayDifficultyPolicy.Pressure pressure = ReplayDifficultyPolicy.measure(clientIp);
        int extraBits = ReplayDifficultyPolicy.extraBits(pressure);

        // 已经落库标记的来源：在标记有效期内不再发题。只在来源已有加压档位时才回源查库，
        // 正常用户（恒为基准档）永不触达标记表
        ReplaySourceMarkService marks = ReplaySourceMarkService.current();
        if (extraBits > 0 && marks != null && marks.isMarked(clientIp)) {
            PowAudit.mark(request, null, PowAudit.RATE_LIMITED);
            sendTooManyRequests(response, "security.rate_limit.too_frequent");
            return;
        }

        int target = ReplayChallengeService.targetDifficulty(readCount, writeCount, extraBits);
        if (target > ReplayChallengeService.MAX_ISSUABLE_DIFFICULTY_BITS) {
            // 再大的题任何客户端都解不开，发出去只会锁死同出口的正常用户：
            // 改为落库标记 + 拒绝，把"无限加价"升级成可见、可复核的处置
            if (marks != null) {
                marks.mark(clientIp, (int) Math.min(Integer.MAX_VALUE, Math.max(0, pressure.requests())),
                        target, ReplaySourceMarkService.REASON_UNSOLVABLE);
            }
            // 难度就是这次算出来、已经越过可解线的那个值：落库留痕，便于复核加压是否合理
            PowAudit.mark(request, target, PowAudit.MARKED);
            sendTooManyRequests(response, "security.rate_limit.too_frequent");
            return;
        }

        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(
                readCount, writeCount, request.getHeader("User-Agent"),
                request.getHeader("X-Neko-Client"), extraBits);
        if (challenge == null) {
            PowAudit.mark(request, target, PowAudit.ERROR);
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "security.replay.busy");
            return;
        }
        PowAudit.mark(request, challenge.difficulty(), PowAudit.ISSUED);

        JsonObject data = new JsonObject();
        data.addProperty("challenge", challenge.id());
        data.addProperty("algorithm", ReplayChallengeService.ALGORITHM);
        data.addProperty("seed", challenge.seed());
        data.addProperty("difficulty", challenge.difficulty());
        data.addProperty("read", challenge.read());
        data.addProperty("write", challenge.write());
        data.addProperty("expiresIn", challenge.ttlSeconds());

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "");
        body.add("data", data);
        sendSuccessResponse(response, body);
    }
}
