package com.neko.music.model;

/**
 * 单个请求的威胁判定结果。
 *
 * <p>该结果只供服务端日志与后台安全分析使用：{@code reasons} 是可运维的命中原因摘要，
 * 不面向客户端，也不应写进对外 API 文档。</p>
 */
public record ThreatAssessment(String level, int score, String reasons, int engineVersion) {

    public static final String NORMAL = "normal";
    public static final String SUSPECTED = "suspected";
    public static final String ATTACK = "attack";

    /** 规则变化时递增；日志库会据此回填旧记录。 */
    public static final int CURRENT_ENGINE_VERSION = 6;

    public static ThreatAssessment normal() {
        return new ThreatAssessment(NORMAL, 0, null, CURRENT_ENGINE_VERSION);
    }

    public boolean isAttack() {
        return ATTACK.equals(level);
    }

    public boolean isSuspicious() {
        return SUSPECTED.equals(level);
    }
}
