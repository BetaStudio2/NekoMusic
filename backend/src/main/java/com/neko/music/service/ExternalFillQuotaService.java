package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * 外站补全来源配额：公开搜索未命中站内曲库时会触发服务端外站下载入库，这里按来源限制单个窗口内
 * 可触发的外站补全次数，避免被构造大量不同歌名刷带宽 / 磁盘，并降低对网易云 / QQ 的风控暴露面。
 *
 * <p>与「拒绝整个搜索请求」不同，超出配额只是<b>跳过外站补全</b>（仍正常返回站内结果），
 * 因此不会把正常搜索打死。计数落在 Redis 固定窗口，多实例部署共享同一份额度——只用进程内计数时
 * 请求被分到 N 个实例会被放大到 N 倍。Redis 不可用时放行（只记 WARN），与
 * {@link AuthAbuseLimiter} / {@link IPRateLimitService} 一致：限流不可用不应把整条链路打死。</p>
 *
 * <p>阈值是类常量，不新增配置项（属安全策略）。</p>
 */
public class ExternalFillQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalFillQuotaService.class);

    private static final String KEY_PREFIX = "external_fill:";
    /** 单个来源在一个窗口内可触发的外站补全次数。 */
    static final int MAX_FILLS_PER_WINDOW = 20;
    /** 固定窗口长度（秒）。 */
    static final int WINDOW_SECONDS = 600;

    private final AuthAbuseLimiter.Store store;

    public ExternalFillQuotaService(AuthAbuseLimiter.Store store) {
        this.store = store;
    }

    /**
     * 消耗一个外站补全额度。
     *
     * @return {@code true} 允许本次补全；{@code false} 超出配额（计数存储不可用时放行）
     */
    public boolean tryAcquire(String clientIp) {
        String key = KEY_PREFIX + normalizeIp(clientIp);
        long count = store.incrementInWindow(key, WINDOW_SECONDS);
        if (count < 0) {
            logger.warn("外站补全配额计数不可用（Redis 无响应），本次放行: key={}", key);
            return true;
        }
        if (count <= MAX_FILLS_PER_WINDOW) {
            return true;
        }
        logger.warn("来源外站补全请求超过配额，跳过本次补全: 窗口内已 {} 次", count);
        return false;
    }

    private static String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank()
                ? "unknown"
                : clientIp.trim().toLowerCase(Locale.ROOT);
    }
}
