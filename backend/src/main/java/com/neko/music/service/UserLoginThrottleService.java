package com.neko.music.service;

/**
 * 用户端登录失败限流与锁定（阈值与键前缀的用户端配置）。
 *
 * <p>背景：{@code POST /api/user/login} 原本没有任何失败限速与账号锁定，可以无成本地高速字典爆破；
 * 全站 IP 限流（{@code IPRateLimitFilter}）按来源网段聚合、阈值偏宽，只做粗粒度兜底，挡不住
 * 「同一账号 + 少量来源」的定向爆破（CWE-307）。</p>
 *
 * <p>通用机制见 {@link LoginThrottleService}。阈值口径：</p>
 * <ul>
 *   <li><b>账号维度</b>取与管理端相同的 10 次/15 分钟——单账号的尝试总量由此封顶，无论攻击者换多少
 *       来源 IP；</li>
 *   <li><b>来源维度</b>放宽到 30 次/15 分钟，因为用户端来源远多于管理后台（公共 NAT、校园网出口），
 *       过窄会误伤正常用户，定向爆破由账号维度兜底。</li>
 * </ul>
 *
 * <p>账号维度按登录查询解析出的用户 ID 分桶（见 {@link LoginThrottleService#accountBucket}）：邮箱比较
 * 大小写不敏感、带 Unicode 兼容折叠、且忽略重音，用提交字符串直接分桶会被重音 / 兼容变体拆出多个
 * 空桶；改用库内唯一的用户 ID 后，同一邮箱的任何变形都落到同一桶。</p>
 */
public class UserLoginThrottleService extends LoginThrottleService {

    /** 失败计数窗口（秒）。 */
    static final int FAILURE_WINDOW_SECONDS = 900;

    /** 同一邮箱在窗口内允许的失败次数；达到即锁定。 */
    public static final int MAX_FAILURES_PER_EMAIL = 10;

    /** 同一来源 IP 在窗口内允许的失败次数；比邮箱阈值宽，兼顾 NAT 后的正常用户。 */
    public static final int MAX_FAILURES_PER_SOURCE = 30;

    /** 计数取不到 TTL 时兜底的建议等待秒数。 */
    static final int LOCK_SECONDS = 900;

    private static final String KEY_PREFIX = "user_login_fail:";

    public UserLoginThrottleService(Store store) {
        super(store, KEY_PREFIX, MAX_FAILURES_PER_EMAIL, MAX_FAILURES_PER_SOURCE,
                FAILURE_WINDOW_SECONDS, LOCK_SECONDS);
    }
}
