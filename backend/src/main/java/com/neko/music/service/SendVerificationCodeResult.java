package com.neko.music.service;

/**
 * 发送邮箱验证码的结果
 */
public record SendVerificationCodeResult(
        boolean success,
        boolean rateLimited,
        long retryAfterSec
) {
    public static SendVerificationCodeResult ok() {
        return new SendVerificationCodeResult(true, false, 0);
    }

    public static SendVerificationCodeResult sendFailed() {
        return new SendVerificationCodeResult(false, false, 0);
    }

    public static SendVerificationCodeResult cooldown(long retryAfterSec) {
        return new SendVerificationCodeResult(false, true, retryAfterSec);
    }
}
