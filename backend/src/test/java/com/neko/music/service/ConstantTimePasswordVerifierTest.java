package com.neko.music.service;

import de.mkammerer.argon2.Argon2;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 等时口令校验测试：账号不存在时也必须付出一次同参数哈希校验，否则「秒回」本身就是
 * 用户名枚举时间侧信道。这里用 Argon2 动态代理记录调用次数与实参，不依赖真实 KDF 耗时
 * （不做任何时间断言，避免测试抖动）。
 */
class ConstantTimePasswordVerifierTest {

    private static final String HASHED_WITH_DUMMY = "$argon2id$v=19$m=65536,t=10,p=1$dummy";

    private int verifyCalls;
    private String lastVerifiedHash;
    private String lastHashedPassword;
    private boolean verifyResult;

    private ConstantTimePasswordVerifier verifier() {
        Argon2 argon2 = (Argon2) Proxy.newProxyInstance(Argon2.class.getClassLoader(),
                new Class<?>[]{Argon2.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hash" -> {
                        lastHashedPassword = String.valueOf(((char[]) args[3]));
                        yield HASHED_WITH_DUMMY;
                    }
                    case "verify" -> {
                        verifyCalls++;
                        lastVerifiedHash = (String) args[0];
                        yield verifyResult;
                    }
                    default -> defaultValue(method);
                });
        return new ConstantTimePasswordVerifier(argon2, 10, 65536, 1);
    }

    @Test
    void unknownAccountStillPaysExactlyOneHashVerification() {
        ConstantTimePasswordVerifier verifier = verifier();
        verifyResult = false;

        assertFalse(verifier.verify(null, "guess".toCharArray()));
        assertEquals(1, verifyCalls, "账号不存在时也必须跑一次口令校验");
        assertEquals(HASHED_WITH_DUMMY, lastVerifiedHash, "陪跑必须使用与真实校验同参数的哈希");

        assertFalse(verifier.verify("", "guess".toCharArray()));
        assertEquals(2, verifyCalls, "空哈希同样按「账号不存在」处理");
    }

    @Test
    void unknownAccountCannotBeLoggedIntoEvenIfTheKdfSaysMatch() {
        ConstantTimePasswordVerifier verifier = verifier();
        verifyResult = true;

        assertFalse(verifier.verify(null, "anything".toCharArray()),
                "没有真实哈希时即便校验返回匹配也必须拒绝，不能把陪跑哈希当成账号");
    }

    @Test
    void knownAccountUsesTheStoredHash() {
        ConstantTimePasswordVerifier verifier = verifier();
        verifyResult = true;

        assertTrue(verifier.verify("$argon2id$real", "pw".toCharArray()));
        assertEquals("$argon2id$real", lastVerifiedHash);
        assertEquals(1, verifyCalls);
    }

    @Test
    void dummyHashIsGeneratedFromARandomSecretNotNullPassword() {
        ConstantTimePasswordVerifier verifier = verifier();

        assertNotEquals("", lastHashedPassword);
        assertEquals(64, lastHashedPassword.length(), "陪跑口令应为随机盐的十六进制串，而非固定字面量");
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        return null;
    }
}
