package com.neko.music.handlers;

import com.neko.music.service.SourceBanService;
import com.neko.music.service.TestSourceBanStore;
import com.neko.music.service.NekoAuthAccountService;
import com.neko.music.service.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册接口的来源失败计数：注册验证码是短的、可猜的秘密，反复失败必须计入递增退避封禁，
 * 否则攻击者可以「换邮箱猜码」把注册验证码的尝试限制绕开。
 */
class UserRegisterHandlerTest {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String BODY = "{\"nickname\":\"tester\",\"password\":\"pass123456\","
            + "\"email\":\"user@example.com\",\"verificationCode\":\"000000\"}";

    /** 认证桩：白名单恒通过、验证码恒判错，聚焦「验证码失败是否计入来源封禁」。 */
    private static final class StubUserAuthService extends UserAuthService {
        StubUserAuthService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public boolean isWhitelistedEmail(String email) {
            return true;
        }

        @Override
        public boolean registerUserWithCode(String nickname, String password, String email,
                                            String verificationCode) {
            throw new NekoAuthAccountService.AccountBridgeException(
                    400, "EMAIL_VERIFICATION_CODE_INVALID", "验证码错误", 0);
        }
    }

    @Test
    void repeatedWrongRegisterCodeBansTheSource() throws Exception {
        TestSourceBanStore store = new TestSourceBanStore();
        SourceBanService ban = new SourceBanService(store);

        for (int i = 0; i < SourceBanService.FAILURES_BEFORE_BAN; i++) {
            register(ban);
        }

        assertTrue(ban.isBanned(CLIENT_IP), "反复验证码失败应触发来源封禁");
    }

    @Test
    void belowThresholdDoesNotBan() throws Exception {
        TestSourceBanStore store = new TestSourceBanStore();
        SourceBanService ban = new SourceBanService(store);

        register(ban);

        assertFalse(ban.isBanned(CLIENT_IP), "未达阈值不应封禁");
    }

    private void register(SourceBanService ban) throws Exception {
        UserRegisterHandler handler = new UserRegisterHandler();
        inject(handler, "userAuthService", new StubUserAuthService());
        inject(handler, "sourceBanService", ban);

        HttpServletResponse response = proxy(HttpServletResponse.class, (p, m, a) -> {
            if ("getWriter".equals(m.getName())) {
                return new PrintWriter(new StringWriter());
            }
            return defaultValue(m);
        });
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(BODY.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getReader" -> reader;
            case "getRemoteAddr" -> CLIENT_IP;
            case "getMethod" -> "POST";
            case "getRequestURI" -> "/api/user/register";
            case "getContextPath" -> "";
            case "getHeader" -> null;
            default -> defaultValue(m);
        });

        handler.doPost(request, response);
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        var declared = UserRegisterHandler.class.getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }
}
