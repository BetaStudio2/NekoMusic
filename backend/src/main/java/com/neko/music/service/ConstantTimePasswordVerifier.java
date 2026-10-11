package com.neko.music.service;

import de.mkammerer.argon2.Argon2;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 口令校验的等时封装：无论账号是否存在，口令校验都要付出同样的 Argon2 成本。
 *
 * <p>背景：登录逻辑若「先查账号、查不到就直接返回」，那么不存在的账号几乎瞬时返回，而存在的账号
 * 要跑一次 Argon2（本仓库参数下约 0.3～1.5 秒）。这个数量级差异可被远程稳定观测，构成用户名枚举
 * 时间侧信道（CWE-204）。</p>
 *
 * <p>做法：拿不到真实哈希时，对一份**进程内随机生成**的哈希（口令随机、没有任何账号使用，无法猜中）
 * 跑一次同参数校验再返回失败。调用方只负责交出「查到的哈希」或 {@code null}，存在性判断不再产生
 * 可观测差异。</p>
 *
 * <p>注意：本类只解决时间侧信道。陪跑 Argon2 会让不存在的账号也消耗 CPU 与内存，因此**调用方必须
 * 同时对该入口做频率限制**，否则会放大资源耗尽面。</p>
 */
public final class ConstantTimePasswordVerifier {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Argon2 argon2;
    private final String dummyHash;

    /**
     * @param iterations  迭代次数，必须与真实口令哈希一致
     * @param memoryKib   内存开销（KiB），必须与真实口令哈希一致
     * @param parallelism 并行度，必须与真实口令哈希一致
     */
    public ConstantTimePasswordVerifier(Argon2 argon2, int iterations, int memoryKib, int parallelism) {
        this.argon2 = argon2;
        byte[] randomSecret = new byte[32];
        RANDOM.nextBytes(randomSecret);
        this.dummyHash = argon2.hash(iterations, memoryKib, parallelism,
                HexFormat.of().formatHex(randomSecret).toCharArray());
    }

    /**
     * 校验口令；{@code storedHash} 为 {@code null}（账号不存在或没有可用哈希）时改用陪跑哈希跑一次，
     * 使两条路径耗时一致。
     *
     * @return 仅当 {@code storedHash} 非空且口令匹配时为 {@code true}
     */
    public boolean verify(String storedHash, char[] password) {
        boolean known = storedHash != null && !storedHash.isEmpty();
        boolean matches = argon2.verify(known ? storedHash : dummyHash, password);
        return known && matches;
    }
}
