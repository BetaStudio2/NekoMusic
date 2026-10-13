package com.neko.music.service;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * 防重放挑战的「动态难度」策略：在批量项（{@link ReplayChallengeService#difficultyFor}）之上，
 * 按**服务端观测到的该来源行为**再叠加一段加压比特数。
 *
 * <p>为什么不能只看批量：{@code read} / {@code write} 由客户端自选，攻击者用 {@code read=1} 就能
 * 每次拿到最低档的题，然后循环领取——「难度随批量增长」对脚本形同不存在。真正无法被客户端参数
 * 拉低的输入，是服务端自己记下的**该来源的领取频率**：脚本必须持续换题换 nonce，压力就必然上来。</p>
 *
 * <p><b>加压没有上限</b>，由两条轴叠加而成：</p>
 * <ul>
 *   <li><b>速率档</b>：{@code floor(log2(窗口内计数 / }{@link #BASE_REQUESTS}{@code ))}——请求越频繁
 *       难度越高，每翻一倍加 1 比特，不封顶。</li>
 *   <li><b>持续档</b>：窗口内计数越过 {@link #SUSTAINED_REQUESTS} 就记一次「持续档」（每个窗口
 *       最多一档），每档 {@link #STEP_BITS} 比特，档位只升不降、安静 {@link #LEVEL_QUIET_SECONDS}
 *       后随 TTL 归零。速率档受请求闸门限制（窗口内速率天然有上限），持续档则**随持续时长无限累加**
 *       ——一直踩线的来源只会越来越贵，没有"停在某个最高档"这回事。</li>
 * </ul>
 *
 * <p>加压是**成本抬高**而非安全边界：难度每加 1 比特只让求解耗时翻倍，脚本终究解得出来。真正的门槛
 * 仍是签发限额与各入口的速率限制，这里只把「持续脚本」的单价往上抬，正常用户（一次领取后几分钟才
 * 回来一次）恒为 0。</p>
 *
 * <p><b>无上限不等于无限发题</b>：加压算出的难度可能远超任何客户端能解出的范围，此时继续发题只会
 * 把同出口的正常用户一起锁死（题目发出去就没人解得开）。因此调用方在难度超过
 * {@link ReplayChallengeService#MAX_ISSUABLE_DIFFICULTY_BITS} 时不再发题，改为落库标记该来源并
 * 明确拒绝（见 {@link ReplaySourceMarkService}）；阶梯本身依旧没有上限，落库标记是**升级处置**，
 * 不是给难度封顶。</p>
 *
 * <p>加压只按来源生效：全局加压会把难度推给所有正常用户，等于给攻击者一个自我 DoS 的杠杆。
 * 阈值与存储键一律是类常量，不新增配置项；计数落 Redis，多实例共享同一份额度。存储不可用时退回
 * 基准难度并放行——难度决策失败不应该把整条领取链路打死，与 {@link ReplayIssueLimiter} /
 * {@link AuthAbuseLimiter} 的失败策略一致。</p>
 *
 * <p>成本取舍：换题路径原本**完全不碰 Redis**（题目只存进程内），本策略给它加了一次计数写入，
 * 因此把「自增 + 记持续档」合并成一次 Lua 往返、且只在计数超过持续线时写档位键；否则这个入口会变成
 * 新的 Redis 写放大面。这也是本策略最脆的一环：计数一旦被打满，副作用只是 Redis 压力，而不是把正常
 * 用户挡在门外（加压只抬价、不拒绝）。</p>
 */
public final class ReplayDifficultyPolicy {

    private static final Logger logger = LoggerFactory.getLogger(ReplayDifficultyPolicy.class);

    /**
     * 计价窗口（秒）。
     *
     * <p>取 10 分钟而不是 1 分钟：窗口太短只能测到瞬时突发，脚本只要把节奏摊平就回到基准档；
     * 再长则会把共享出口一天的正常流量算进来（那样区分的是"人多"而不是"频"）。</p>
     */
    static final int PRESSURE_WINDOW_SECONDS = 600;

    /**
     * 窗口内计数不超过该值时完全不加压（相当于 0.85 次请求/秒的均值）。
     *
     * <p>取 512 而不是更小，是为了让共享出口（校园网 / 运营商 NAT）里一批正常用户叠起来也不会被
     * 误加压：正常客户端一次领满 16+16 后可以撑几分钟，一轮完整领取只贡献 2 次计数。</p>
     */
    static final int BASE_REQUESTS = 512;

    /**
     * 窗口内计数超过该值才算「持续滥用」，记一次持续档（≈6.8 次请求/秒的均值）。
     *
     * <p>这个速率已经远高于任何共享出口的正常叠加：换算下来是每 10 分钟 65536 个 nonce。
     * 只有脚本会长期踩在这条线以上。</p>
     */
    static final int SUSTAINED_REQUESTS = 4096;

    /** 每记一次持续档叠加的比特数。 */
    static final int STEP_BITS = 2;

    /** 持续档的安静期（秒）：期间没有再踩线，档位随 TTL 归零。 */
    static final int LEVEL_QUIET_SECONDS = 3600;

    /**
     * 速率档比特数的算术上限。
     *
     * <p>这**不是**策略上的难度封顶，只是防止 {@code extraBits} 溢出的保险丝：30 比特已经意味着
     * 2^30 次哈希起步，离可发题的线上限差着好几个数量级，实际永远先被调用方的可解线拦下。</p>
     */
    static final int MAX_RATE_BITS = 30;

    /** 持续档的算术上限（同样是防溢出，不是策略封顶）。 */
    static final int MAX_STRIKE_LEVEL = 30;

    private static final String PRESSURE_PREFIX = "replay_pressure:";
    private static final String LEVEL_PREFIX = "replay_level:";

    private static final Store REDIS_STORE = new RedisStore();

    private static volatile Store store = REDIS_STORE;

    private ReplayDifficultyPolicy() {
    }

    /**
     * 记录一次「领取侧请求」（换题或兑换 nonce）。
     *
     * @return 窗口内的累计次数；存储不可用返回 {@code -1}
     */
    public static int observe(String clientIp) {
        Pressure observed = measure(clientIp);
        if (observed.requests() < 0) {
            logger.debug("防重放难度计数不可用（Redis 无响应），本次按基准难度处理");
            return -1;
        }
        return (int) Math.min(observed.requests(), Integer.MAX_VALUE);
    }

    /** 本次签发应叠加的加压比特数：按速率档 + 持续档给出档位（无上限）。 */
    public static int extraBits(String clientIp) {
        return extraBits(measure(clientIp));
    }

    /**
     * 记账一次「领取侧请求」，并把这次观测原样返回（一次 Redis 往返）。
     *
     * <p>调用方需要在同一份观测上同时拿到"窗口请求数"和"加压档位"（例如越界后要落库留痕）时用它，
     * 避免为了取两个值而记两次账、或在两次往返之间被别的请求改动计数。</p>
     */
    public static Pressure measure(String clientIp) {
        String source = normalize(clientIp);
        return store.incrementAndRead(
                PRESSURE_PREFIX + source, LEVEL_PREFIX + source,
                PRESSURE_WINDOW_SECONDS, SUSTAINED_REQUESTS, LEVEL_QUIET_SECONDS);
    }

    /**
     * 速率档 + 持续档；两条轴都无策略上限，只有防溢出截断。
     *
     * <p>计数不可用时按基准档（{@code 0}）处理。</p>
     */
    public static int extraBits(Pressure observed) {
        if (observed.requests() < 0) {
            logger.debug("防重放难度计数不可用（Redis 无响应），本次按基准难度处理");
            return 0;
        }
        int bits = rateBits(observed.requests()) + strikeBits(observed.level());
        return Math.min(bits, MAX_RATE_BITS + MAX_STRIKE_LEVEL * STEP_BITS);
    }

    /**
     * 速率档：窗口内计数每翻一倍（相对 {@link #BASE_REQUESTS}）加 1 比特。
     *
     * <p>即 {@code floor(log2(count / BASE_REQUESTS))}；计数不可用（{@code < 0}）按 0 处理。</p>
     */
    public static int rateBits(long requestsInWindow) {
        if (requestsInWindow <= BASE_REQUESTS) {
            return 0;
        }
        long quotient = requestsInWindow / BASE_REQUESTS;
        int bits = 63 - Long.numberOfLeadingZeros(quotient);
        return Math.min(bits, MAX_RATE_BITS);
    }

    /** 持续档：每档 {@link #STEP_BITS} 比特，档位本身由存储侧累加。 */
    static int strikeBits(long level) {
        long safe = Math.min(Math.max(0, level), MAX_STRIKE_LEVEL);
        return (int) safe * STEP_BITS;
    }

    private static String normalize(String clientIp) {
        return clientIp == null || clientIp.isBlank()
                ? "unknown"
                : clientIp.trim().toLowerCase(Locale.ROOT);
    }

    /** 仅供单元测试替换存储实现；生产代码请勿调用（传 {@code null} 恢复 Redis 实现）。 */
    public static void useStore(Store replacement) {
        store = replacement == null ? REDIS_STORE : replacement;
    }

    /** 测试用：清空进程内引用（存储内容由实现自行决定）。 */
    public static void reset() {
        store = REDIS_STORE;
    }

    /**
     * 一次往返取回的两个数：窗口内累计的领取侧请求数，以及当前持续档位。
     *
     * <p>{@code requests < 0} 表示存储不可用，调用方按基准难度处理。</p>
     */
    public record Pressure(long requests, long level) {

        /** 存储不可用。 */
        static final Pressure UNAVAILABLE = new Pressure(-1, 0);
    }

    /**
     * 计数存储抽象：生产实现是 Redis（{@link RedisStore}），单元测试注入内存实现。
     */
    public interface Store {

        /**
         * 窗口内原子自增计数；计数**刚好**越过 {@code sustainedRequests} 时把持续档 +1 并在档位键上
         * 续期安静期——一个窗口最多记一档，否则窗口内超线后的每个请求都会再升一档，档位会瞬间炸穿。
         * 合并成一次往返是刻意的：换题入口没有全站闸门，多一次请求就多一份开销。
         *
         * @return 自增后的计数与当前档位；存储不可用返回 {@link Pressure#UNAVAILABLE}
         */
        Pressure incrementAndRead(String pressureKey, String levelKey, int windowSeconds,
                                  int sustainedRequests, int quietSeconds);
    }

    /**
     * 生产实现：自增 + 首次设过期 + 判持续档 + 读档位合并为一次 Lua。
     *
     * <p>自增与设过期必须原子（否则进程重启会留下永不过期的计数键）；档位与计数在同一脚本里判定，
     * 并发突发下不会出现「都读到未超线」的竞态，也不会被多实例重复升级。</p>
     */
    public static final class RedisStore implements Store {

        private static final String OBSERVE_SCRIPT =
                "local count = redis.call('INCR', KEYS[1]) "
                        + "if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end "
                        + "local level = tonumber(redis.call('GET', KEYS[2]) or '0') "
                        + "if count == tonumber(ARGV[2]) + 1 then "
                        + "  level = level + 1 "
                        + "  redis.call('SETEX', KEYS[2], tonumber(ARGV[3]), level) "
                        + "end "
                        + "return {count, level}";

        @Override
        public Pressure incrementAndRead(String pressureKey, String levelKey, int windowSeconds,
                                         int sustainedRequests, int quietSeconds) {
            var redisService = Main.getRedisService();
            if (redisService == null) {
                return Pressure.UNAVAILABLE;
            }
            List<Object> raw = redisService.evalMulti(OBSERVE_SCRIPT,
                    new String[]{pressureKey, levelKey},
                    new String[]{String.valueOf(windowSeconds), String.valueOf(sustainedRequests),
                            String.valueOf(quietSeconds)});
            if (raw == null || raw.size() < 2 || !(raw.get(0) instanceof Number count)) {
                return Pressure.UNAVAILABLE;
            }
            return new Pressure(count.longValue(), parseNumber(raw.get(1)));
        }

        /** Lua 数组里的数值经协议返回可能是整数或文本；读不到 / 值非法一律按 0 处理。 */
        private static long parseNumber(Object raw) {
            if (raw instanceof Number number) {
                return Math.max(0, number.longValue());
            }
            if (raw instanceof CharSequence text) {
                try {
                    return Math.max(0, Long.parseLong(text.toString().trim()));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            return 0;
        }
    }
}
