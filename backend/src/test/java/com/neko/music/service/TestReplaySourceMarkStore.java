package com.neko.music.service;

import com.neko.music.database.ReplaySourceMarkDatabaseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用来源标记存储：内存实现 {@link ReplaySourceMarkService.Store}，语义与 SQL 实现一致
 * （upsert 累计、按来源查询、按来源删除），并记录读写次数以便断言"没有回源查库"。
 */
public class TestReplaySourceMarkStore implements ReplaySourceMarkService.Store {

    private final Map<String, Integer> rows = new HashMap<>();
    private final List<String> upserts = new ArrayList<>();
    private int reads;
    private boolean down;

    /** 模拟存储整体不可用。 */
    public void setDown(boolean value) {
        this.down = value;
    }

    /** 直接摆一条已存在的标记。 */
    public void seedMarked(String ip) {
        rows.put(ip, 1);
    }

    /** 已落库的标记条目（保留在库中的部分，不受 TTL 影响）。 */
    public Map<String, Integer> rows() {
        return rows;
    }

    /** 写库次数与内容：形如 {@code ip:窗口请求:难度:原因}。 */
    public List<String> upserts() {
        return upserts;
    }

    /** 回源查库次数。 */
    public int reads() {
        return reads;
    }

    @Override
    public boolean upsert(String ip, int windowRequests, int difficultyBits, String reason, long ttlSeconds) {
        if (down) {
            return false;
        }
        rows.merge(ip, 1, Integer::sum);
        upserts.add(ip + ":" + windowRequests + ":" + difficultyBits + ":" + reason);
        return true;
    }

    @Override
    public boolean isMarked(String ip) {
        reads++;
        return !down && rows.containsKey(ip);
    }

    @Override
    public List<ReplaySourceMarkDatabaseManager.Mark> listActive(int limit) {
        return rows.keySet().stream()
                .limit(Math.max(0, limit))
                .map(ip -> new ReplaySourceMarkDatabaseManager.Mark(ip, rows.get(ip), 0, 0, "", null, null, null))
                .toList();
    }

    @Override
    public boolean clear(String ip) {
        return rows.remove(ip) != null;
    }
}
