package com.neko.music.security;

import com.neko.music.model.ThreatAssessment;
import com.neko.music.util.ClientFingerprint;
import com.neko.music.util.RequestCategory;

import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * 访问日志的威胁判定：正常 / 疑似攻击 / 攻击。
 *
 * <p>判定分三层：</p>
 * <ol>
 *   <li><b>请求特征</b>：扫描器 UA、敏感文件探测、注入载荷、路径穿越、异常方法、
 *       401/403/404/429，以及按来源密度累计的防重放 / PoW 异常。</li>
 *   <li><b>来源行为窗口</b>：单 IP 与粗粒度网段在短时间内的拒绝率、认证失败、404 数、路径扩散度。</li>
 *   <li><b>跨来源聚类</b>：同一 UA / 路径族在多网段、多 IP 上同步失败；以及全站在短窗口内的
 *       大面积拒绝。该层专门覆盖攻击者轮换 IPv4 /24、IPv6 /48 甚至不同地址段的情况。</li>
 * </ol>
 *
 * <p>状态保存在单实例内存中，窗口有上限并定期清理，不参与数据库写入关键路径。日志库不可用时
 * 仍能给出判定并写应用日志；服务重启后行为窗口从零开始，持久化日志里的历史判定不受影响。</p>
 */
public final class ThreatDetectionService {

    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final long BURST_WINDOW_MS = 60 * 1000L;
    private static final int WINDOW_MAX_EVENTS = 400;
    private static final int GLOBAL_WINDOW_MAX_EVENTS = 2000;
    private static final int MAX_WINDOWS = 20_000;
    private static final int CLEANUP_EVERY = 256;

    private static final int SUSPECTED_THRESHOLD = 30;
    private static final int ATTACK_THRESHOLD = 60;
    private static final double SUCCESS_PROBE_MIN_FAILURE_RATIO = 0.15;

    private static final Pattern SQL_INJECTION = Pattern.compile(
            "(?is)(\\bunion\\b.{0,80}\\bselect\\b|\\bselect\\b.{0,80}\\bfrom\\b|"
                    + "\\b(or|and)\\b\\s*['\"`]?\\s*\\d+\\s*=\\s*\\d+|\\bsleep\\s*\\(|"
                    + "\\bbenchmark\\s*\\(|\\binformation_schema\\b|\\bextractvalue\\s*\\(|"
                    + "\\bupdatexml\\s*\\(|\\bload_file\\s*\\(|\\binto\\s+outfile\\b|"
                    + "\\bwaitfor\\s+delay\\b)");
    private static final Pattern XSS = Pattern.compile(
            "(?is)(<\\s*script\\b|javascript\\s*:|on(?:error|load|click|mouseover)\\s*=|"
                    + "document\\.(?:cookie|domain)|<\\s*(?:iframe|svg|img)[^>]+on\\w+\\s*=)");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile(
            "(?is)(\\.\\./|\\.\\.\\\\|%2e%2e|/etc/(?:passwd|shadow)|/proc/self/|"
                    + "boot\\.ini|win\\.ini|web-inf/|meta-inf/|\\.git/(?:config|head)|"
                    + "\\.aws/credentials)");
    private static final Pattern COMMAND_INJECTION = Pattern.compile(
            "(?is)(?:;|\\||&&|\\$\\(|`)\\s*(?:cat|wget|curl|bash|sh|nc|ncat|whoami|id|"
                    + "uname|python|perl|php|powershell|cmd)\\b");
    private static final Pattern SCANNER_PATH = Pattern.compile(
            "(?is)(^|/)(?:\\.env|\\.git|\\.svn|\\.hg|\\.aws|\\.ssh|wp-admin|wp-login\\.php|"
                    + "xmlrpc\\.php|phpmyadmin|pma|adminer|actuator|server-status|manager/html|"
                    + "solr|console|shell|vendor/phpunit|docker-compose|\\.ds_store|backup|dump|"
                    + "credentials)(?:/|$|\\.)");
    private static final Pattern SUSPICIOUS_EXTENSION = Pattern.compile(
            "(?is).+\\.(?:php|asp|aspx|jsp|cgi|env|bak|sql|old|swp)(?:/.*)?$");

    private static final Set<String> UNUSUAL_METHODS = Set.of("TRACE", "TRACK", "CONNECT", "DEBUG");
    private static final Set<String> POW_HIGH_RISK = Set.of("marked");
    private static final Set<String> POW_REPLAY_FAILURE = Set.of(
            "invalid", "missing", "expired", "mismatch", "bad_proof");
    private static final Set<String> POW_REPLAY_CHALLENGE_FAILURE = Set.of(
            "invalid", "mismatch", "bad_proof");
    private static final Set<String> POW_RATE_LIMITED = Set.of("rate_limited");
    private static final int REPLAY_BURST_MIN_FAILURES = 80;
    private static final double REPLAY_BURST_MIN_RATIO = 0.80;
    private static final int REPLAY_BURST_SCORE = 50;
    private static final int SINGLE_PATH_FLOOD_MIN_REQUESTS = 300;
    private static final int SINGLE_PATH_FLOOD_SCORE = 60;

    private static final ThreatDetectionService SHARED =
            new ThreatDetectionService(System::currentTimeMillis);

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong seen = new AtomicLong();
    private final LongSupplier clock;

    ThreatDetectionService(LongSupplier clock) {
        this.clock = clock;
    }

    public static ThreatDetectionService shared() {
        return SHARED;
    }

    /**
     * 判定一个请求，并把事件写入来源 / 网段 / UA / 路径族的滑动窗口。
     *
     * <p>调用方应传入未脱敏的查询串用于特征匹配；本服务不会把它持久化或写日志。</p>
     */
    public ThreatAssessment assess(RequestFacts facts) {
        if (facts == null) {
            return ThreatAssessment.normal();
        }
        long now = facts.occurredAt() > 0 ? facts.occurredAt() : clock.getAsLong();
        List<String> reasons = new ArrayList<>();
        int score = 0;

        String path = safe(facts.path());
        String pathLower = path.toLowerCase(Locale.ROOT);
        String decoded = decode(path, facts.query());
        String decodedLower = decoded.toLowerCase(Locale.ROOT);
        String method = safe(facts.method()).toUpperCase(Locale.ROOT);
        String clientType = safe(facts.clientType());
        String clientIp = safe(facts.clientIp());

        int signature = signatureScore(pathLower, decodedLower, method, reasons);
        score += signature;

        boolean dynamic = RequestCategory.API.equals(facts.category())
                || RequestCategory.ADMIN.equals(facts.category());
        boolean denied = facts.status() == 401 || facts.status() == 403
                || facts.status() == 404 || facts.status() == 429;
        // 401 常见于令牌过期；把它与 403/404/429 的扫描失败拆开，避免一次全站掉签
        // 被误判成跨网段攻击。401 仍会进入单 IP 认证失败与低分认证聚类。
        boolean scanFailure = facts.status() == 403 || facts.status() == 404
                || facts.status() == 429;
        boolean notFound = facts.status() == 404;
        boolean authFailure = facts.status() == 401 || facts.status() == 403;

        if (denied && dynamic) {
            int points = notFound ? 12 : (facts.status() == 429 ? 15 : 10);
            score += points;
            if (notFound) {
                reasons.add("动态接口 404 探测");
            } else if (facts.status() == 429) {
                reasons.add("请求被限流");
            } else {
                reasons.add("动态接口认证/权限失败");
            }
        } else if (notFound && RequestCategory.PAGE.equals(facts.category())) {
            score += 4;
            reasons.add("页面路径探测");
        }

        String pow = safe(facts.powStatus());
        if (facts.status() == 409) {
            reasons.add(POW_REPLAY_FAILURE.contains(pow)
                    ? "请求冲突且防重放凭证失效" : "业务状态冲突");
        }

        if (ClientFingerprint.SCANNER.equals(clientType)) {
            score += 45;
            reasons.add("安全扫描器 User-Agent");
            if (denied) {
                score += 15;
            }
        } else if (ClientFingerprint.BOT.equals(clientType)) {
            score += 25;
            reasons.add("爬虫 User-Agent");
        } else if (ClientFingerprint.UNKNOWN.equals(clientType)
                && (safe(facts.userAgent()).isBlank() || dynamic)) {
            score += 15;
            reasons.add("缺少可识别客户端身份");
        }

        if (POW_HIGH_RISK.contains(pow)) {
            score += 55;
            reasons.add("来源已被防重放链路标记");
        } else if (POW_RATE_LIMITED.contains(pow)) {
            score += 20;
            reasons.add("防重放凭证签发被限流");
        } else if (POW_REPLAY_CHALLENGE_FAILURE.contains(pow)) {
            score += 15;
            reasons.add("防重放凭证失效（可能过期、已用或来源变化）");
        } else if ("missing".equals(pow) && dynamic) {
            score += 10;
            reasons.add("动态接口缺少防重放凭证");
        } else if ("expired".equals(pow)) {
            score += 10;
            reasons.add("防重放挑战已过期");
        }

        boolean highRisk = signature >= 60 || POW_HIGH_RISK.contains(pow)
                || ClientFingerprint.SCANNER.equals(clientType);
        boolean replayFailure = POW_REPLAY_FAILURE.contains(pow);
        Event event = new Event(now, highRisk, scanFailure, notFound, authFailure, replayFailure,
                facts.status() == 200, pathLower, networkScope(clientIp), clientIp,
                pathFamily(pathLower));
        String uaKey = uaFingerprint(facts.userAgent(), facts.nekoClient(), facts.clientId());
        remember("global", event, now, GLOBAL_WINDOW_MAX_EVENTS);
        if (!clientIp.isBlank()) {
            remember("ip:" + clientIp, event, now, WINDOW_MAX_EVENTS);
            remember("ip-path:" + clientIp + "|" + pathLower, event, now, WINDOW_MAX_EVENTS);
            String network = event.network();
            if (!network.isBlank()) {
                remember("net:" + network, event, now, WINDOW_MAX_EVENTS);
            }
        }
        if (!uaKey.isBlank()) {
            remember("ua:" + uaKey, event, now, WINDOW_MAX_EVENTS);
        }
        remember("path:" + event.pathFamily(), event, now, WINDOW_MAX_EVENTS);
        maybeCleanup(now);

        WindowStats ipStats = clientIp.isBlank()
                ? WindowStats.EMPTY : snapshot("ip:" + clientIp, now - WINDOW_MS);
        String network = event.network();
        WindowStats networkStats = network.isBlank()
                ? WindowStats.EMPTY : snapshot("net:" + network, now - WINDOW_MS);
        int exactPathHits = clientIp.isBlank() ? 0
                : windowCount("ip-path:" + clientIp + "|" + pathLower, now - WINDOW_MS);

        boolean success = facts.status() == 200;
        score += sourceBehaviorScore(ipStats, networkStats, isCredentialPath(pathLower), success,
                exactPathHits, reasons);
        // 单次 nonce / 挑战失效不参与全站聚类，否则同刻发生的其他扫描会把正常客户端的
        // 过期请求连带抬高。只有本地重放失败达到异常密度时，才进入跨来源关联。
        boolean strongAnomaly = signature > 0 || denied || highRisk;
        boolean replayBurst = qualifiesReplayBurst(ipStats);
        // 普通 200 不扫描跨来源窗口，避免把每个正常请求都拖进额外聚合；
        // 只有当前来源已经夹带失败探测时才进一步判断是否为成功遍历。
        if (strongAnomaly || replayBurst || (success && ipStats.scanFailures() > 0)) {
            WindowStats uaStats = uaKey.isBlank()
                    ? WindowStats.EMPTY : snapshot("ua:" + uaKey, now - WINDOW_MS);
            WindowStats pathStats = snapshot("path:" + event.pathFamily(), now - WINDOW_MS);
            WindowStats burstStats = snapshot("global", now - BURST_WINDOW_MS);
            boolean successProbe = success
                    && hasSuccessProbe(ipStats, uaStats, pathStats, burstStats);
            if (strongAnomaly || replayBurst || successProbe) {
                score += distributedCampaignScore(uaStats, pathStats, burstStats, success, reasons);
            }
        }
        score = Math.min(100, score);

        String level = score >= ATTACK_THRESHOLD ? ThreatAssessment.ATTACK
                : score >= SUSPECTED_THRESHOLD ? ThreatAssessment.SUSPECTED
                : ThreatAssessment.NORMAL;
        String reasonText = reasons.isEmpty() ? null : truncate(String.join("；", reasons), 500);
        return new ThreatAssessment(level, score, reasonText, ThreatAssessment.CURRENT_ENGINE_VERSION);
    }

    /** 单请求特征；返回高分特征的基础分。 */
    private static int signatureScore(String path, String decoded, String method, List<String> reasons) {
        int score = 0;
        if (SQL_INJECTION.matcher(decoded).find()) {
            score += 70;
            reasons.add("SQL 注入特征");
        }
        if (XSS.matcher(decoded).find()) {
            score += 65;
            reasons.add("跨站脚本特征");
        }
        if (PATH_TRAVERSAL.matcher(decoded).find()) {
            score += 70;
            reasons.add("路径穿越特征");
        }
        if (COMMAND_INJECTION.matcher(decoded).find()) {
            score += 65;
            reasons.add("命令注入特征");
        }
        if (SCANNER_PATH.matcher(path).find() || SUSPICIOUS_EXTENSION.matcher(path).matches()) {
            score += 65;
            reasons.add("敏感文件或扫描器路径");
        }
        if (decoded.indexOf('\0') >= 0 || decoded.contains("%00")) {
            score += 40;
            reasons.add("空字节编码");
        }
        if (UNUSUAL_METHODS.contains(method)) {
            score += 65;
            reasons.add("异常 HTTP 方法");
        }
        return score;
    }

    /** 单 IP / 粗粒度网段的行为分。 */
    private static int sourceBehaviorScore(WindowStats ip, WindowStats net, boolean credentialPath,
                                           boolean success, int exactPathHits,
                                           List<String> reasons) {
        int score = 0;
        if (ip.total() > 0) {
            if (ip.scanFailures() >= 8 && ip.total() >= 10 && ip.scanRatio() >= 0.70) {
                score += 30;
                reasons.add("同一 IP 短时高频 403/404/429");
            }
            if (ip.authFailures() >= 6) {
                score += 30;
                reasons.add("同一 IP 反复认证失败");
            }
            // 登录 / 注册等凭证接口 8 次即可确认爆破；普通接口可能只是客户端令牌失效，
            // 需要更高次数才升级为攻击。
            int authAttackCount = credentialPath ? 8 : 20;
            if (ip.authFailures() >= authAttackCount) {
                score += 20;
                reasons.add("同一 IP 持续认证失败");
            }
            if (qualifiesReplayBurst(ip)) {
                score += REPLAY_BURST_SCORE;
                reasons.add("同一来源短时大量防重放凭证失败");
            }
            if (qualifiesSinglePathFlood(exactPathHits)) {
                score += SINGLE_PATH_FLOOD_SCORE;
                reasons.add("同一来源短时高频重复请求同一路径");
            }
            if (ip.notFound() >= 8 && ip.distinctPaths() >= 6) {
                score += 20;
                reasons.add("同一 IP 连续探测不存在路径");
            }
            if (ip.highRisk() >= 2) {
                score += 20;
                reasons.add("同一 IP 多次命中高危特征");
            }
            if (success && qualifiesIpSuccessProbe(ip)) {
                score += 30;
                reasons.add("同一来源成功遍历资源并夹带失败探测");
            }

            if (net.total() > 0) {
                if (net.scanFailures() >= 10 && net.distinctIps() >= 5 && net.scanRatio() >= 0.45) {
                    score += 30;
                    reasons.add("同网段多 IP 同步失败");
                } else if (net.scanFailures() >= 20 && net.scanRatio() >= 0.65) {
                    score += 25;
                    reasons.add("同网段短时大量拒绝");
                }
                if (net.highRisk() >= 3) {
                    score += 20;
                    reasons.add("同网段多次高危探测");
                }
            }
        }
        return score;
    }

    /** 跨来源聚类：共享 UA、路径族与全站突发，覆盖攻击者轮换 IP 段的情况。 */
    private static int distributedCampaignScore(WindowStats ua, WindowStats path, WindowStats burst,
                                                boolean success, List<String> reasons) {
        int score = 0;
        int authScore = 0;
        int successScore = 0;
        if (ua.total() > 0) {
            if (ua.distinctNetworks() >= 4 && ua.scanFailures() >= 6 && ua.scanRatio() >= 0.5) {
                score += 35;
                reasons.add("多个网段共享同一客户端指纹");
            } else if (ua.total() >= 12 && ua.scanFailures() >= 8 && ua.scanRatio() >= 0.25) {
                score += 20;
                reasons.add("同一客户端指纹短时高频失败");
            }
            if (ua.authFailures() >= 6 && ua.distinctNetworks() >= 4) {
                authScore += 15;
                reasons.add("多个网段共享同一客户端指纹（认证失败）");
            }
        }

        if (path.distinctNetworks() >= 5 && path.scanFailures() >= 8 && path.scanRatio() >= 0.5) {
            score += 30;
            reasons.add("多个网段集中探测同一路径族");
        }
        if (path.authFailures() >= 8 && path.distinctNetworks() >= 5) {
            authScore += 10;
            reasons.add("多个网段集中出现认证失败");
        }

        if (burst.scanFailures() >= 60 && burst.distinctNetworks() >= 10) {
            score += 50;
            reasons.add("全站短时大规模失败");
        } else if (burst.scanFailures() >= 24 && burst.distinctNetworks() >= 6) {
            score += 35;
            reasons.add("全站多网段同步失败");
        }
        if (burst.highRisk() >= 5 && burst.distinctNetworks() >= 4) {
            score += 20;
            reasons.add("全站多网段高危特征活动");
        }
        if (burst.authFailures() >= 60 && burst.distinctNetworks() >= 10) {
            authScore += 20;
            reasons.add("全站短时大规模认证失败");
        } else if (burst.authFailures() >= 24 && burst.distinctNetworks() >= 6) {
            authScore += 15;
            reasons.add("全站多网段认证失败");
        }

        // 只有当前响应本身是 200 时才累计「成功遍历」分；否则正常的 200 流量会被
        // 失败簇的上下文连带抬高。成功遍历必须与足够密度的扫描失败同时出现，纯 200 不算攻击。
        if (success) {
            if (qualifiesUaSuccessProbe(ua)) {
                successScore += 30;
                reasons.add("多个网段共享同一客户端指纹成功遍历资源");
            }
            if (qualifiesPathSuccessProbe(path)) {
                successScore += 20;
                reasons.add("多个网段集中成功遍历同一路径族");
            }
            if (qualifiesBurstSuccessProbe(burst)) {
                successScore += 20;
                reasons.add("全站成功遍历与失败探测同时发生");
            }
        }

        // 纯 401 的跨来源聚类只给低分：它更可能是令牌过期 / 发布故障，而不是扫描。
        // 同一 IP 的高频认证失败与凭证接口爆破仍由来源行为层独立升级。
        score += Math.min(25, authScore);
        score += Math.min(40, successScore);
        return score;
    }

    /** 200 必须与同来源的失败探测同时成立；纯成功流量只走普通统计，不进入攻击聚类。 */
    private static boolean hasSuccessProbe(WindowStats ip, WindowStats ua,
                                           WindowStats path, WindowStats burst) {
        if (ip.scanFailures() == 0) {
            return false;
        }
        return qualifiesIpSuccessProbe(ip) || qualifiesUaSuccessProbe(ua)
                || qualifiesPathSuccessProbe(path) || qualifiesBurstSuccessProbe(burst);
    }

    /**
     * 成功遍历的公共门槛：大量成功、路径扩散，并夹带非低比例的扫描失败。
     *
     * <p>只看 200 数量会把正常客户端的大量资源请求误判为攻击；只看少量 404 又会被
     * 过期曲目、发布残留触发。要求失败在「成功 + 失败」中占到一定比例，才能把
     * 正常客户端偶发的缺失资源与主动枚举区分开。</p>
     */
    private static boolean qualifiesSuccessProbe(WindowStats stats, int minSuccesses,
                                                 int minSuccessPaths, int minFailures) {
        return stats.successes() >= minSuccesses
                && stats.distinctSuccessPaths() >= minSuccessPaths
                && stats.scanFailures() >= minFailures
                && stats.probeFailureRatio() >= SUCCESS_PROBE_MIN_FAILURE_RATIO;
    }

    private static boolean qualifiesIpSuccessProbe(WindowStats ip) {
        return qualifiesSuccessProbe(ip, 80, 40, 10);
    }

    private static boolean qualifiesUaSuccessProbe(WindowStats ua) {
        return ua.distinctNetworks() >= 4 && qualifiesSuccessProbe(ua, 100, 50, 12);
    }

    private static boolean qualifiesPathSuccessProbe(WindowStats path) {
        return path.distinctNetworks() >= 5 && qualifiesSuccessProbe(path, 100, 50, 12);
    }

    private static boolean qualifiesBurstSuccessProbe(WindowStats burst) {
        return burst.distinctNetworks() >= 6 && qualifiesSuccessProbe(burst, 300, 0, 24);
    }

    private static boolean qualifiesReplayBurst(WindowStats stats) {
        return stats.replayFailures() >= REPLAY_BURST_MIN_FAILURES
                && stats.replayRatio() >= REPLAY_BURST_MIN_RATIO;
    }

    private static boolean qualifiesSinglePathFlood(int exactPathHits) {
        return exactPathHits >= SINGLE_PATH_FLOOD_MIN_REQUESTS;
    }

    private static boolean isCredentialPath(String path) {
        return path.contains("/login") || path.contains("/register")
                || path.contains("/password") || path.contains("/reset")
                || path.contains("/verify") || path.contains("/qrlogin");
    }

    private void remember(String key, Event event, long now, int maxEvents) {
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        window.add(event, now - WINDOW_MS, maxEvents);
    }

    private WindowStats snapshot(String key, long cutoff) {
        Window window = windows.get(key);
        return window == null ? WindowStats.EMPTY : window.snapshot(cutoff);
    }

    private int windowCount(String key, long cutoff) {
        Window window = windows.get(key);
        return window == null ? 0 : window.count(cutoff);
    }

    private void maybeCleanup(long now) {
        if ((seen.incrementAndGet() & (CLEANUP_EVERY - 1)) != 0) {
            return;
        }
        long cutoff = now - WINDOW_MS;
        windows.entrySet().removeIf(entry -> entry.getValue().lastEventAt() < cutoff);
        if (windows.size() <= MAX_WINDOWS) {
            return;
        }
        int target = MAX_WINDOWS * 4 / 5;
        for (String key : windows.keySet()) {
            if (windows.size() <= target) {
                break;
            }
            windows.remove(key);
        }
    }

    private static String decode(String path, String query) {
        String raw = path + (query == null || query.isBlank() ? "" : "?" + query);
        if (raw.length() > 4096) {
            raw = raw.substring(0, 4096);
        }
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return raw;
        }
    }

    /**
     * 粗粒度来源网段：IPv4 取 /16、IPv6 取 /32。
     *
     * <p>日志展示仍保留 /24、/48 的细网段；这里只作为行为聚合键，故意更粗，避免攻击者轮换
     * 相邻 /24 或 /48 后每一段都被当成新来源。</p>
     */
    static String networkScope(String ip) {
        String value = safe(ip);
        if (value.isBlank()) {
            return "";
        }
        if (value.indexOf(':') >= 0) {
            try {
                byte[] bytes = InetAddress.getByName(value).getAddress();
                if (bytes.length == 16) {
                    int first = ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
                    int second = ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
                    return Integer.toHexString(first) + ":" + Integer.toHexString(second) + "::/32";
                }
            } catch (Exception ignored) {
                // 非法 IPv6 不参与网段聚合，请求本身仍会按 IP 与 UA 计入窗口
            }
            return "";
        }
        String[] octets = value.split("\\.");
        if (octets.length == 4) {
            return octets[0] + "." + octets[1] + ".0.0/16";
        }
        return "";
    }

    private static String uaFingerprint(String userAgent, String nekoClient, String clientId) {
        String ua = safe(userAgent).trim().toLowerCase(Locale.ROOT);
        String declared = safe(nekoClient).trim().toLowerCase(Locale.ROOT);
        String app = safe(clientId).trim().toLowerCase(Locale.ROOT);
        if (ua.isBlank() && declared.isBlank() && app.isBlank()) {
            return "";
        }
        return Integer.toHexString(ua.hashCode()) + ":" + ua.length() + ":"
                + Integer.toHexString(declared.hashCode()) + ":" + Integer.toHexString(app.hashCode());
    }

    private static String pathFamily(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String[] parts = path.split("/");
        if (parts.length >= 3 && !parts[1].isBlank()) {
            int second = parts[2].isBlank() ? 1 : 2;
            return "/" + parts[1] + "/" + parts[second] + "/*";
        }
        return "/" + (parts.length > 1 && !parts[1].isBlank() ? parts[1] : "*");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 一个请求事实快照。查询串仅用于规则匹配，调用方不要把它写入日志。 */
    public record RequestFacts(long occurredAt, String method, String path, String query,
                               String category, int status, String clientIp, String userAgent,
                               String clientType, String nekoClient, String clientId,
                               String clientInstanceId, String powStatus) {
    }

    private record Event(long at, boolean highRisk, boolean scanFailure, boolean notFound,
                         boolean authFailure, boolean replayFailure, boolean success, String path,
                         String network, String ip, String pathFamily) {
    }

    private record WindowStats(int total, int successes, int scanFailures, int notFound,
                               int authFailures, int replayFailures, int highRisk, int distinctIps,
                               int distinctNetworks, int distinctPaths,
                               int distinctSuccessPaths, double scanRatio) {
        private static final WindowStats EMPTY =
                new WindowStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0);

        static WindowStats of(List<Event> events) {
            if (events.isEmpty()) {
                return EMPTY;
            }
            int successes = 0;
            int scanFailures = 0;
            int notFound = 0;
            int authFailures = 0;
            int replayFailures = 0;
            int highRisk = 0;
            Set<String> ips = new HashSet<>();
            Set<String> networks = new HashSet<>();
            Set<String> paths = new HashSet<>();
            Set<String> successPaths = new HashSet<>();
            for (Event event : events) {
                if (event.success()) {
                    successes++;
                    successPaths.add(event.path());
                }
                if (event.scanFailure()) {
                    scanFailures++;
                }
                if (event.notFound()) {
                    notFound++;
                }
                if (event.authFailure()) {
                    authFailures++;
                }
                if (event.replayFailure()) {
                    replayFailures++;
                }
                if (event.highRisk()) {
                    highRisk++;
                }
                if (!event.ip().isBlank()) {
                    ips.add(event.ip());
                }
                if (!event.network().isBlank()) {
                    networks.add(event.network());
                }
                paths.add(event.pathFamily());
            }
            return new WindowStats(events.size(), successes, scanFailures, notFound, authFailures,
                    replayFailures, highRisk, ips.size(), networks.size(), paths.size(),
                    successPaths.size(), events.isEmpty() ? 0.0 : (double) scanFailures / events.size());
        }

        private double probeFailureRatio() {
            int mixed = successes + scanFailures;
            return mixed == 0 ? 0.0 : (double) scanFailures / mixed;
        }

        private double replayRatio() {
            return total == 0 ? 0.0 : (double) replayFailures / total;
        }
    }

    private static final class Window {
        private final Deque<Event> events = new ArrayDeque<>();
        private long lastEventAt;

        synchronized void add(Event event, long cutoff, int maxEvents) {
            prune(cutoff);
            events.addLast(event);
            while (events.size() > maxEvents) {
                events.removeFirst();
            }
            lastEventAt = event.at();
        }

        synchronized WindowStats snapshot(long cutoff) {
            prune(cutoff);
            return WindowStats.of(List.copyOf(events));
        }

        synchronized int count(long cutoff) {
            prune(cutoff);
            return events.size();
        }

        synchronized long lastEventAt() {
            return lastEventAt;
        }

        private void prune(long cutoff) {
            while (!events.isEmpty() && events.peekFirst().at() < cutoff) {
                events.removeFirst();
            }
        }
    }
}
