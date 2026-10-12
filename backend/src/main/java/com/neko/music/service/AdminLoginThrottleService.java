package com.neko.music.service;

/**
 * 管理端登录失败限流与锁定（阈值与键前缀的管理端配置）。
 *
 * <p>背景：{@code POST /api/admin/login} 原本没有任何失败限速与锁定，可以无成本地慢速爆破；而
 * 反自动化门禁可被脚本化通过、来源 IP 也可能被伪造头影响，因此不能只靠「IP 维度」兜底
 * （CWE-307）。</p>
 *
 * <p>计数、锁定、成功回退与存储脚本等通用机制见 {@link LoginThrottleService}；本类只负责给出
 * 管理端的键前缀与阈值。用户名的「每账号」口径由 {@link LoginThrottleService#accountBucket} 用
 * 解析出的管理员 ID 保证，不再依赖应用层复刻数据库排序规则的等价类。</p>
 */
public class AdminLoginThrottleService extends LoginThrottleService {

    /** 失败计数窗口（秒）。 */
    static final int FAILURE_WINDOW_SECONDS = 900;

    /** 同一用户名在窗口内允许的失败次数；达到即锁定。 */
    public static final int MAX_FAILURES_PER_USERNAME = 10;

    /** 同一来源 IP 在窗口内允许的失败次数；达到即锁定（比用户名阈值宽，兼顾 NAT 后的正常误输）。 */
    public static final int MAX_FAILURES_PER_SOURCE = 20;

    /** 计数取不到 TTL 时兜底的建议等待秒数。 */
    static final int LOCK_SECONDS = 900;

    private static final String KEY_PREFIX = "admin_login_fail:";

    public AdminLoginThrottleService(Store store) {
        super(store, KEY_PREFIX, MAX_FAILURES_PER_USERNAME, MAX_FAILURES_PER_SOURCE,
                FAILURE_WINDOW_SECONDS, LOCK_SECONDS);
    }
}
