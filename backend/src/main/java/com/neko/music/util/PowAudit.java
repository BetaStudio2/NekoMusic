package com.neko.music.util;

import jakarta.servlet.ServletRequest;

/**
 * 把「本次请求与 PoW（防重放挑战）的关系」挂到请求属性上，供访问日志落库。
 *
 * <p>为什么走请求属性而不是各写各的：难度是在防重放链路上算出来的——受保护请求的难度来自它消费的
 * nonce（签发时按批量与该来源的加压档位计费），换题 / 兑换接口的难度则来自当场下发的挑战。访问日志
 * 由最外层的 {@link com.neko.music.filter.RequestAuditFilter} 统一落库，只有把结果挂回请求，才能让
 * 「哪个来源、在什么难度上、被放行还是被拒」在同一行日志里对齐。</p>
 *
 * <p>结果标签是**内部运维词表**，只出现在后台日志页，不属于客户端契约，可随防护策略调整；
 * 不要把这里的取值写进对外 API 文档。</p>
 */
public final class PowAudit {

    /** 请求属性：本次请求的有效 PoW 难度（比特）；不涉及 PoW 时为 {@code null}。 */
    public static final String ATTR_DIFFICULTY = "neko.pow.difficulty";

    /** 请求属性：本次请求的 PoW 结果标签，取值见下面常量。 */
    public static final String ATTR_STATUS = "neko.pow.status";

    /** 受保护请求消费了一枚按该难度签发的 nonce。 */
    public static final String VERIFIED = "verified";
    /** 受保护请求未带 nonce（或未先换题就想领 nonce）。 */
    public static final String MISSING = "missing";
    /** nonce 无效 / 重放，或换题参数非法。 */
    public static final String INVALID = "invalid";
    /** 挑战已过期。 */
    public static final String EXPIRED = "expired";
    /** 挑战与当前来源不匹配（换 IP / 换 UA）。 */
    public static final String MISMATCH = "mismatch";
    /** 解答不合格。 */
    public static final String BAD_PROOF = "bad_proof";
    /** 下发了挑战（含难度）。 */
    public static final String ISSUED = "issued";
    /** 挑战兑换成了 nonce。 */
    public static final String EXCHANGED = "exchanged";
    /** 被签发闸门挡住（来源或全站限额）。 */
    public static final String RATE_LIMITED = "rate_limited";
    /** 加压越界：题目已不可解，来源已落库标记并拒绝。 */
    public static final String MARKED = "marked";
    /** 存储或服务故障。 */
    public static final String ERROR = "error";

    private PowAudit() {
    }

    /** 记录本次请求的 PoW 结果；难度未知时传 {@code null}。 */
    public static void mark(ServletRequest request, Integer difficulty, String status) {
        if (request == null) {
            return;
        }
        request.setAttribute(ATTR_DIFFICULTY, difficulty);
        request.setAttribute(ATTR_STATUS, status);
    }

    /** 读取本次请求的 PoW 难度；未记录时为 {@code null}。 */
    public static Integer difficulty(ServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getAttribute(ATTR_DIFFICULTY) instanceof Integer value ? value : null;
    }

    /** 读取本次请求的 PoW 结果标签；未记录时为 {@code null}。 */
    public static String status(ServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getAttribute(ATTR_STATUS) instanceof String value ? value : null;
    }
}
