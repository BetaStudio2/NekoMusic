package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QishuiMusicClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void encryptMatchesAccountSdk() {
        // 账号 SDK：UTF-8 字节逐字节 XOR 0x05 后转十六进制（不补零）
        assertEquals("34363d353534363d353535", QishuiMusicClient.encrypt("13800138000"));
    }

    @Test
    void normalizesStatusCodes() {
        assertEquals("new", QishuiMusicClient.normalizeStatus(json("{\"status\":\"1\"}")));
        assertEquals("scanned", QishuiMusicClient.normalizeStatus(json("{\"status\":\"scanned\"}")));
        assertEquals("confirmed", QishuiMusicClient.normalizeStatus(json("{\"status\":\"3\"}")));
        assertEquals("expired", QishuiMusicClient.normalizeStatus(json("{\"status\":\"refused\"}")));
        assertEquals("", QishuiMusicClient.normalizeStatus(json("{\"account_flow\":\"verify\"}")));
    }

    @Test
    void extractsMfaFromNestedResponse() {
        String payload = """
                {"data":{"account_flow":"verify","error_code":2046,
                  "verify_info":{"encrypt_uid":"uid_123","channel_mobile":"138****8000",
                    "verify_way":"mobile_sms_verify",
                    "extra":"std_verify_token=tk1&std_verify_scene=qr_connect&passport_mfa_retry_tag=rt1"},
                  "std_verify_flow_id":"flow1"}}
                """;
        Optional<QishuiMusicClient.MfaChallenge> result = client().extractMfa(json(payload));
        assertTrue(result.isPresent());
        QishuiMusicClient.MfaChallenge mfa = result.get();
        assertEquals("uid_123", mfa.encryptUid());
        assertEquals("138****8000", mfa.mobile());
        assertTrue(mfa.verifyWays().contains("mobile_sms_verify"));
        assertEquals("tk1", mfa.verifyParams().get("std_verify_token"));
        assertEquals("qr_connect", mfa.verifyParams().get("std_verify_scene"));
        assertEquals("rt1", mfa.verifyParams().get("passport_mfa_retry_tag"));
        assertEquals("flow1", mfa.verifyParams().get("std_verify_flow_id"));
    }

    @Test
    void extractsOfficialWebVerifyChallenge() {
        // 客户端真实协议：2046 + url（官方验证组件）+ biz_params（重放时要原样带回）
        String payload = """
                {"data":{"error_code":2046,"account_flow":"verify",
                  "url":"https://lf-verify.example/uc-verify.js",
                  "biz_params":{"encrypt_uid":"uid_9","std_verify_token":"tk9","verify_reason":"login"}}}
                """;
        QishuiMusicClient.MfaChallenge mfa = client().extractMfa(json(payload)).orElseThrow();
        assertTrue(mfa.hasVerifyUrl());
        assertEquals("https://lf-verify.example/uc-verify.js", mfa.verifyUrl());
        assertEquals("uid_9", mfa.encryptUid());
        assertEquals("tk9", mfa.verifyParams().get("std_verify_token"));
        assertEquals("login", mfa.bizParams().get("verify_reason"));
    }

    @Test
    void ignoresResponseWithoutVerificationSignal() {
        assertTrue(client().extractMfa(json("{\"data\":{\"status\":\"new\"}}")).isEmpty());
    }

    @Test
    void persistsAndReloadsCookies(@TempDir Path dir) throws Exception {
        Path cookieFile = dir.resolve("qishui_cookies.json");
        Files.writeString(cookieFile, """
                [{"name":"sessionid","value":"sess-abc","domain":".qishui.com","path":"/",
                  "maxAge":-1,"secure":false,"httpOnly":false,"version":0},
                 {"name":"ttwid","value":"tw-1","domain":".qishui.com","path":"/",
                  "maxAge":-1,"secure":false,"httpOnly":false,"version":0}]
                """, StandardCharsets.UTF_8);

        QishuiMusicClient client = new QishuiMusicClient(objectMapper, cookieFile.toString());
        assertTrue(client.hasSession());
        assertTrue(client.cookieHeader().contains("sessionid=sess-abc"));
        assertTrue(client.cookieHeader().contains("ttwid=tw-1"));

        client.clearCookies();
        assertFalse(client.hasSession());
        assertTrue(client.cookieHeader().isEmpty());

        QishuiMusicClient reloaded = new QishuiMusicClient(objectMapper, cookieFile.toString());
        assertFalse(reloaded.hasSession());
    }

    private QishuiMusicClient client() {
        return new QishuiMusicClient(objectMapper, "/tmp/qishui-test-cookies-unused.json");
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
