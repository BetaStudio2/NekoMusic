package com.neko.music.util;

import java.sql.Timestamp;

/**
 * 用户 VIP 到期判断（与具体支付渠道无关，仅看 {@code vip_expires_at}）。
 */
public final class VipUtil {
    private VipUtil() {
    }

    /** 在指定时刻是否仍为有效会员（到期时间需严格晚于该时刻）。 */
    public static boolean isVipActiveAt(Timestamp vipExpiresAt, long atEpochMillis) {
        return vipExpiresAt != null && vipExpiresAt.getTime() > atEpochMillis;
    }

    public static boolean isVipActiveNow(Timestamp vipExpiresAt) {
        return isVipActiveAt(vipExpiresAt, System.currentTimeMillis());
    }
}
