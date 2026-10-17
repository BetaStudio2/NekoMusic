package com.neko.music.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 成品响应体缓存：把「查询 + 序列化」的结果整份留在进程内，命中时直接写出。
 *
 * <p>给公开且本来就可被 CDN 缓存（{@code Cache-Control: public, max-age=...}）的接口用：
 * 这类响应内容对所有人一致、与请求头无关，因此缓存的是**最终 JSON 字符串**——命中路径上既没有
 * 数据库查询，也没有对象映射，只剩一次字符串写出。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>只能缓存「与请求无关」的响应。任何随 {@code Accept-Language}、令牌、查询参数变化的响应
 *       都不能放进来（否则会把 A 用户的响应发给 B）。</li>
 *   <li>TTL 必须不长于响应自己声明的 {@code Cache-Control}：客户端本来就允许持有这么久，
 *       服务端在同一窗口内复用同一份成品不算「更旧」。</li>
 *   <li>写入侧变更（上传、删除、发版）应调用 {@link #invalidate(String)}，让改动立刻可见；
 *       多实例部署下其余实例最多滞后一个 TTL。</li>
 *   <li>加载走条带锁串行：缓存刚过期时并发请求只有一个回源，其余等待复用同一份结果，
 *       避免过期瞬间把所有并发都打到数据库上（缓存击穿）。</li>
 * </ul>
 */
public final class JsonResponseCache {

    private record Entry(String body, long expiresAtMillis) {
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    /** 加载锁：条带数固定，与缓存条目数量无关。 */
    private static final StripedLock LOCKS = new StripedLock(16);

    private JsonResponseCache() {
    }

    /**
     * 取缓存的响应体；未命中（或已过期）时调用 {@code loader} 重新生成并记住。
     *
     * <p>{@code loader} 抛异常时不写入缓存，异常原样抛给调用方。</p>
     */
    public static String get(String key, long ttlMillis, Supplier<String> loader) {
        Entry entry = CACHE.get(key);
        if (entry != null && entry.expiresAtMillis() > System.currentTimeMillis()) {
            return entry.body();
        }
        ReentrantLock lock = LOCKS.forKey(key);
        lock.lock();
        try {
            entry = CACHE.get(key);
            if (entry != null && entry.expiresAtMillis() > System.currentTimeMillis()) {
                return entry.body();
            }
            String body = loader.get();
            CACHE.put(key, new Entry(body, System.currentTimeMillis() + Math.max(0L, ttlMillis)));
            return body;
        } finally {
            lock.unlock();
        }
    }

    /** 丢弃某个条目的缓存（数据变更后调用）。 */
    public static void invalidate(String key) {
        CACHE.remove(key);
    }

    /** 仅供测试：清空全部缓存。 */
    static void clear() {
        CACHE.clear();
    }
}
