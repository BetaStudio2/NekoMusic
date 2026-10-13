package com.neko.music.service;

import com.neko.music.database.ReplaySourceMarkDatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 加压越界来源的**落库标记**：加压阶梯没有上限（{@link ReplayDifficultyPolicy}），持续踩线的来源
 * 最终会走到「题目对任何客户端都已不可解」的档位。此时不再发题，而是把来源写进主库
 * {@code replay_source_marks} 并明确拒绝领取。
 *
 * <p>标记与加压的分工：加压是**价格**（每次领取按频率收费，Redis 内结算），标记是**处置**
 * （越界即升级，落 SQL 留痕、可复核、可人工放行）。两者都不封顶，越界线只看"题目还能不能解出来"。</p>
 *
 * <p>成本控制：越界判定发生在来源已经明显异常之后，但校验仍不能变成新的数据库热点，因此</p>
 * <ul>
 *   <li>只在来源已经有加压档位（{@code extraBits > 0}）时才查标记——正常用户永不触达 SQL；</li>
 *   <li>查询结果（含"未标记"）在本进程内缓存一小段时间，避免同一来源在加压窗口内反复回源；</li>
 *   <li>写标记按来源冷却，同一来源冷却期内不重复写库（次数由表内 {@code marks} 累计）。</li>
 * </ul>
 *
 * <p>失败策略：存储不可用时**放行**（只记日志），与各限流一致——标记层故障不应该把整条领取链路打死。
 * 阈值、冷却与缓存时长都是类常量，不新增配置项。</p>
 */
public final class ReplaySourceMarkService {

    private static final Logger logger = LoggerFactory.getLogger(ReplaySourceMarkService.class);

    /** 标记有效期（秒）：到期自动恢复可领取，不需要人工清理。 */
    static final int MARK_TTL_SECONDS = 3600;

    /** 同一来源写标记的最小间隔（秒）：避免把 SQL 变成新的写放大面。 */
    static final int MARK_WRITE_COOLDOWN_SECONDS = 300;

    /** 「已标记」结果的进程内缓存时长（秒）：比标记本身短，便于人工放行后尽快生效。 */
    static final int POSITIVE_CACHE_SECONDS = 60;

    /** 「未标记」结果的缓存时长（秒）：挡住同一来源在加压窗口内的重复回源。 */
    static final int NEGATIVE_CACHE_SECONDS = 30;

    /** 缓存条目上限：超过即整体清空，防止被海量来源撑爆内存。 */
    static final int MAX_CACHE_ENTRIES = 50_000;

    /** 越界来源的处置标签（落库便于分类查看）。 */
    public static final String REASON_UNSOLVABLE = "unsolvable-difficulty";

    private static volatile ReplaySourceMarkService current;

    private final Store store;
    private final LongSupplier clockMillis;

    /** 来源 → 缓存到期时刻（毫秒），正值表示"已标记"，负值表示"未标记"。 */
    private final Map<String, Long> verdictCache = new ConcurrentHashMap<>();

    /** 来源 → 上次写标记时刻（毫秒）。 */
    private final Map<String, Long> lastWriteAt = new ConcurrentHashMap<>();

    public ReplaySourceMarkService(Store store) {
        this(store, null);
    }

    ReplaySourceMarkService(Store store, LongSupplier clockMillis) {
        this.store = store;
        this.clockMillis = clockMillis != null ? clockMillis : System::currentTimeMillis;
    }

    /** 生产环境在启动时注册；未注册时调用方跳过标记判定（不阻断领取）。 */
    public static void setCurrent(ReplaySourceMarkService service) {
        current = service;
    }

    /** 当前注册的标记服务；未注册返回 {@code null}。 */
    public static ReplaySourceMarkService current() {
        return current;
    }

    /**
     * 标记一个越界来源。
     *
     * @param windowRequests 触发时的窗口内请求数（留痕用）
     * @param difficultyBits 触发时的目标难度（留痕用）
     */
    public void mark(String clientIp, int windowRequests, int difficultyBits, String reason) {
        if (store == null) {
            return;
        }
        String ip = normalize(clientIp);
        long now = clockMillis.getAsLong();
        Long previous = lastWriteAt.get(ip);
        if (previous != null && now - previous < MARK_WRITE_COOLDOWN_SECONDS * 1000L) {
            return;
        }
        if (lastWriteAt.size() > MAX_CACHE_ENTRIES) {
            lastWriteAt.clear();
        }
        lastWriteAt.put(ip, now);
        if (!store.upsert(ip, windowRequests, difficultyBits, reason, MARK_TTL_SECONDS)) {
            // 存储不可用：既不落库也不在本地"假装"标记（否则内存与库不一致，人工放行也清不掉），
            // 保持冷却以免对故障中的库重试成灾。越界请求本身仍会被拒绝。
            logger.warn("防重放加压越界但标记未能落库: ip={} 窗口请求={} 目标难度={}",
                    ip, windowRequests, difficultyBits);
            return;
        }
        // 立刻写进判定缓存：同一来源后续的领取直接拒绝，不必等下一次回源
        verdictCache.put(ip, now + MARK_TTL_SECONDS * 1000L);
        logger.warn("防重放加压越界，已标记来源: ip={} 窗口请求={} 目标难度={}", ip, windowRequests, difficultyBits);
    }

    /** 该来源当前是否被标记（带进程内缓存；存储故障按未标记处理）。 */
    public boolean isMarked(String clientIp) {
        if (store == null) {
            return false;
        }
        String ip = normalize(clientIp);
        long now = clockMillis.getAsLong();
        Long cached = verdictCache.get(ip);
        if (cached != null && Math.abs(cached) > now) {
            return cached > 0;
        }
        if (verdictCache.size() > MAX_CACHE_ENTRIES) {
            verdictCache.clear();
        }
        boolean marked = store.isMarked(ip);
        long ttl = (marked ? POSITIVE_CACHE_SECONDS : NEGATIVE_CACHE_SECONDS) * 1000L;
        verdictCache.put(ip, marked ? now + ttl : -(now + ttl));
        return marked;
    }

    /** 人工放行：清除标记并让本进程缓存立即失效。 */
    public boolean clear(String clientIp) {
        String ip = normalize(clientIp);
        verdictCache.remove(ip);
        lastWriteAt.remove(ip);
        return store != null && store.clear(ip);
    }

    /** 当前有效标记，供后台查看。 */
    public List<ReplaySourceMarkDatabaseManager.Mark> listActive(int limit) {
        return store == null ? List.of() : store.listActive(limit);
    }

    /** 来源标识归一化：去空白 + 转小写（IPv6 十六进制大小写无关）。 */
    private static String normalize(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 标记存储抽象：生产实现直连主库表 {@code replay_source_marks}（{@link SqlStore}），
     * 单元测试注入内存实现。
     */
    public interface Store {

        /** 落一条标记（已存在则刷新并累计次数）；写入失败返回 {@code false}。 */
        boolean upsert(String ip, int windowRequests, int difficultyBits, String reason, long ttlSeconds);

        /** 该来源当前是否处于标记有效期内；存储不可用按未标记处理。 */
        boolean isMarked(String ip);

        /** 当前有效标记（按最近标记时间倒序）。 */
        List<ReplaySourceMarkDatabaseManager.Mark> listActive(int limit);

        /** 按来源删除标记。 */
        boolean clear(String ip);
    }

    /** 生产实现：直接落到主库表，随业务库一并备份。 */
    public static final class SqlStore implements Store {

        private final ReplaySourceMarkDatabaseManager databaseManager;

        public SqlStore(ReplaySourceMarkDatabaseManager databaseManager) {
            this.databaseManager = databaseManager;
        }

        @Override
        public boolean upsert(String ip, int windowRequests, int difficultyBits, String reason, long ttlSeconds) {
            return databaseManager != null
                    && databaseManager.upsert(ip, windowRequests, difficultyBits, reason, ttlSeconds);
        }

        @Override
        public boolean isMarked(String ip) {
            return databaseManager != null && databaseManager.isMarked(ip);
        }

        @Override
        public List<ReplaySourceMarkDatabaseManager.Mark> listActive(int limit) {
            return databaseManager == null
                    ? List.of()
                    : databaseManager.listActive(limit);
        }

        @Override
        public boolean clear(String ip) {
            return databaseManager != null && databaseManager.clear(ip);
        }
    }
}
