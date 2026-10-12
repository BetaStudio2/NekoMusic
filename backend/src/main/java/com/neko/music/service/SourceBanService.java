package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 认证失败来源的<b>递增退避临时封禁</b>：在既有的「账号锁定 + 来源失败计数」之上，对反复失败的
 * 来源 IP 给出一段会随复发逐级加长的封禁期（CWE-307 的处置纵深）。
 *
 * <p>与 {@link IPRateLimitService} 的区别：后者按 <b>/24 网段</b>对全站请求做粗粒度兜底，代价是
 * 共享出口容易被整段误伤；本类只对<b>精确来源 IP</b>、且只在<b>真实认证失败</b>时触发——正常用户
 * 不产生失败，就不会被牵连。它也不替代账号维度的锁定：账号维度才是「单账号可被猜多少次」的边界
 * （且不受代理轮换影响），本类只是把「一个来源持续爆破」的成本继续抬高、并让处置变得可见。</p>
 *
 * <p><b>为什么必需</b>：{@link AuthAbuseLimiter} 只做「请求速率」闸门——超限当前请求被拒，窗口一过
 * 又能继续；{@link LoginThrottleService} 的来源维度锁定窗口只有 15 分钟。二者对「长时间、跨窗口
 * 的持续爆破」都只是短暂降温。递增退避让同一来源的复发代价越来越大，直至单次封禁 24 小时；
 * 每次触发封禁还会经可注入的告警出口异步投递通知（生产复用通知服务的 Webhook），让处置可见。</p>
 *
 * <p>阈值、窗口、退避阶梯与封禁时长都是<b>类常量</b>，不新增配置项；判定在 Redis 中以单条 Lua
 * 原子完成，多实例部署共享同一份额度。存储不可用时放行（只记 WARN），与既有各限流一致——
 * 限流不可用不应把认证链路打死。</p>
 */
public class SourceBanService {

    private static final Logger logger = LoggerFactory.getLogger(SourceBanService.class);

    private static final String STRIKES_PREFIX = "src_ban:strikes:";
    private static final String LEVEL_PREFIX = "src_ban:level:";
    private static final String BANNED_PREFIX = "src_ban:banned:";

    /**
     * 同一窗口内累计多少次认证失败才触发一次封禁。
     *
     * <p>取值低于来源维度失败锁定（管理端 20/900s、用户端 30/900s），因此会在来源锁定之外更早
     * 一步给出「跨窗口仍然持续」的处置；同时又高于正常用户偶发输错，避免共享出口被误封。</p>
     */
    public static final int FAILURES_BEFORE_BAN = 12;

    /** 触发封禁所需的失败计数窗口（秒）。 */
    static final int STRIKE_WINDOW_SECONDS = 600;

    /** 距上次触发封禁多久没有新的触发，就把退避档位回退到起点（秒）。 */
    static final int LEVEL_QUIET_SECONDS = 86400;

    /** 递增退避封禁时长（秒）：5min → 15min → 1h → 6h → 24h（封顶）。 */
    static final int[] BAN_LADDER_SECONDS = {300, 900, 3600, 21600, 86400};

    /** 同一来源两次告警之间的最小间隔（秒）：避免攻击者用大量来源把告警通道刷爆。 */
    static final int ALERT_COOLDOWN_SECONDS = 300;

    /** 告警去重表的上限：超过即整体清空，防止被大量不同来源撑爆内存。 */
    static final int ALERT_TRACK_MAX_ENTRIES = 10_000;

    private final Store store;

    /**
     * 告警出口（可为 {@code null}）：生产接 {@link NotificationService} 的 Webhook，
     * 测试接计数 lambda。用函数式接口而非直接依赖 {@code NotificationService}，既解耦也便于断言。
     */
    private final AlertSink alertSink;

    /** 来源 → 上次告警时刻（秒）。仅在启用告警时使用。 */
    private final Map<String, Long> lastAlertAt;

    /** 秒级时钟：默认取系统时间，测试可注入可控时钟。 */
    private final LongSupplier clockSeconds;

    public SourceBanService(Store store) {
        this(store, null, null);
    }

    public SourceBanService(Store store, AlertSink alertSink) {
        this(store, alertSink, null);
    }

    SourceBanService(Store store, AlertSink alertSink, LongSupplier clockSeconds) {
        this.store = store;
        this.alertSink = alertSink;
        this.lastAlertAt = alertSink == null ? null : new ConcurrentHashMap<>();
        this.clockSeconds = clockSeconds != null ? clockSeconds : () -> System.currentTimeMillis() / 1000L;
    }

    /** 认证失败来源被封禁时的告警出口。 */
    @FunctionalInterface
    public interface AlertSink {
        void authSourceBanned(String clientIp, long durationSeconds, String scope);
    }

    /**
     * 记录一次认证失败；若本次触发封禁则返回封禁秒数，否则返回 {@code 0}。
     *
     * <p>必须在确认凭据失败之后调用（而非尝试之前）：本类只统计真实失败，正常用户的成功登录不参与。</p>
     */
    public long recordAuthFailure(String clientIp) {
        return recordAuthFailure(clientIp, "auth");
    }

    /**
     * 记录一次认证失败，并带上触发入口标签。
     *
     * @param scope 触发入口的短标签（如 {@code admin-login} / {@code register}），仅用于日志与告警
     * @return 本次触发的封禁秒数；未触发或存储不可用返回 {@code 0}
     */
    public long recordAuthFailure(String clientIp, String scope) {
        if (store == null) {
            return 0;
        }
        String ip = normalizeIp(clientIp);
        long duration = store.recordFailure(
                STRIKES_PREFIX + ip, LEVEL_PREFIX + ip, BANNED_PREFIX + ip,
                STRIKE_WINDOW_SECONDS, FAILURES_BEFORE_BAN, LEVEL_QUIET_SECONDS, BAN_LADDER_SECONDS);
        if (duration > 0) {
            logger.warn("来源认证失败反复超阈值，已临时封禁: ip={} 入口={} 时长={}秒", ip, scope, duration);
            alertBan(ip, duration, scope);
        }
        return Math.max(0, duration);
    }

    /**
     * 按来源冷却后投递告警：同一来源在 {@link #ALERT_COOLDOWN_SECONDS} 内只告警一次，
     * 避免攻击者用海量来源把告警通道刷爆；告警本身异常不得影响封禁结果。
     */
    private void alertBan(String ip, long duration, String scope) {
        if (alertSink == null) {
            return;
        }
        long now = clockSeconds.getAsLong();
        if (lastAlertAt.size() > ALERT_TRACK_MAX_ENTRIES) {
            lastAlertAt.clear();
        }
        Long previous = lastAlertAt.get(ip);
        if (previous != null && now - previous < ALERT_COOLDOWN_SECONDS) {
            return;
        }
        lastAlertAt.put(ip, now);
        try {
            alertSink.authSourceBanned(ip, duration, scope);
        } catch (Exception e) {
            logger.warn("发送来源封禁告警失败 ip={}: {}", ip, e.getMessage());
        }
    }

    /**
     * 认证成功：清掉该来源窗口内的失败计数（不降退避档位）。
     *
     * <p>只退回到「这一轮窗口还没失败过」，避免正常用户偶发输错被累计成封禁；但既有档位保留，
     * 所以「先爆破、插一次成功登录洗白、再爆破」并不能把递增退避清零。</p>
     */
    public void recordAuthSuccess(String clientIp) {
        if (store != null) {
            store.clearStrikes(STRIKES_PREFIX + normalizeIp(clientIp));
        }
    }

    /** 该来源当前是否处于封禁期。 */
    public boolean isBanned(String clientIp) {
        return store != null && store.isBanned(BANNED_PREFIX + normalizeIp(clientIp));
    }

    /** 封禁剩余秒数；未封禁 / 已到期返回 {@code 0}（供 {@code Retry-After} 使用）。 */
    public long remainingBanSeconds(String clientIp) {
        if (store == null) {
            return 0;
        }
        return Math.max(0, store.ttlSeconds(BANNED_PREFIX + normalizeIp(clientIp)));
    }

    /** 来源标识归一化：去空白 + 转小写（IPv6 十六进制大小写无关），空值归为 {@code unknown}。 */
    private static String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 计数与封禁的存储抽象：生产实现是 Redis（{@link RedisStore}），测试注入内存实现，
     * 单元测试因此不依赖真实 Redis——与 {@link LoginThrottleService}/{@link AuthAbuseLimiter} 同构。
     */
    public interface Store {

        /**
         * 记录一次失败：窗口内原子自增；达到阈值时按递增退避档位落一条封禁。
         *
         * @return 本次触发的封禁秒数；未触发或存储不可用返回 {@code 0}
         */
        long recordFailure(String strikesKey, String levelKey, String bannedKey,
                           int windowSeconds, int threshold, int levelQuietSeconds, int[] ladderSeconds);

        /** 该封禁键当前是否存在。 */
        boolean isBanned(String bannedKey);

        /** 读封禁剩余 TTL（秒）；无键 / 已过期 / 不可用返回 {@code 0}。 */
        long ttlSeconds(String bannedKey);

        /** 清掉失败计数（认证成功时调用），不影响退避档位与既有封禁。 */
        void clearStrikes(String strikesKey);
    }

    /**
     * 生产实现：单条 Lua 原子完成「失败自增 → 达阈值则升档 → 落封禁」。
     *
     * <p>Lua 内先自增再判定，保证并发突发下每个请求拿到互不相同的计数，不会出现「都读到未超阈值」
     * 的竞态（CWE-362）；升档与封禁写入也在同一脚本内，避免多实例并发重复升级。</p>
     */
    public static final class RedisStore implements Store {

        private static final String RECORD_SCRIPT =
                "local strikes = redis.call('INCR', KEYS[1]) "
                        + "if tonumber(strikes) == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end "
                        + "if tonumber(strikes) < tonumber(ARGV[2]) then return 0 end "
                        + "redis.call('DEL', KEYS[1]) "
                        + "local level = redis.call('INCR', KEYS[2]) "
                        + "if tonumber(level) == 1 then redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3])) end "
                        + "local maxLevel = tonumber(ARGV[4]) "
                        + "if tonumber(level) > maxLevel then level = maxLevel end "
                        + "local duration = tonumber(ARGV[4 + tonumber(level)]) "
                        + "redis.call('SETEX', KEYS[3], duration, level) "
                        + "return duration";

        private final RedisService redisService;

        public RedisStore(RedisService redisService) {
            this.redisService = redisService;
        }

        @Override
        public long recordFailure(String strikesKey, String levelKey, String bannedKey,
                                  int windowSeconds, int threshold, int levelQuietSeconds, int[] ladderSeconds) {
            if (redisService == null) {
                return 0;
            }
            String[] args = new String[4 + ladderSeconds.length];
            args[0] = String.valueOf(windowSeconds);
            args[1] = String.valueOf(threshold);
            args[2] = String.valueOf(levelQuietSeconds);
            args[3] = String.valueOf(ladderSeconds.length);
            for (int i = 0; i < ladderSeconds.length; i++) {
                args[4 + i] = String.valueOf(ladderSeconds[i]);
            }
            Object result = redisService.eval(RECORD_SCRIPT,
                    new String[]{strikesKey, levelKey, bannedKey}, args);
            if (result instanceof Number number) {
                return Math.max(0, number.longValue());
            }
            logger.warn("来源封禁计数不可用（Redis 无响应），本次放行: key={}", strikesKey);
            return 0;
        }

        @Override
        public boolean isBanned(String bannedKey) {
            return redisService != null && redisService.exists(bannedKey);
        }

        @Override
        public long ttlSeconds(String bannedKey) {
            if (redisService == null) {
                return 0;
            }
            long ttl = redisService.ttl(bannedKey);
            return ttl > 0 ? ttl : 0;
        }

        @Override
        public void clearStrikes(String strikesKey) {
            if (redisService != null) {
                redisService.del(strikesKey);
            }
        }
    }
}
