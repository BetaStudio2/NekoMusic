package com.neko.music.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NekoAuthOAuthServiceTest {

    @Test
    @DisplayName("return_to 只接受站内相对路径")
    void returnToAcceptsOnlyLocalPaths() throws Exception {
        assertEquals("/", NekoAuthOAuthService.normalizeReturnTo(null));
        assertEquals("/", NekoAuthOAuthService.normalizeReturnTo("  "));
        assertEquals("/", NekoAuthOAuthService.normalizeReturnTo("/"));
        assertEquals("/favorites?tab=music", NekoAuthOAuthService.normalizeReturnTo("/favorites?tab=music"));
        assertEquals("/playlist/12#tracks", NekoAuthOAuthService.normalizeReturnTo("/playlist/12#tracks"));
    }

    @Test
    @DisplayName("return_to 拒绝绝对地址、协议相对地址、反斜杠与控制字符")
    void returnToRejectsOpenRedirectAndInjection() {
        assertInvalid("https://evil.example/");
        assertInvalid("//evil.example/");
        assertInvalid("/\\evil.example/");
        assertInvalid("/ok\nLocation: https://evil.example/");
        assertInvalid("/" + "a".repeat(513));
    }

    private static void assertInvalid(String value) {
        NekoAuthOAuthService.OAuthLoginException exception = assertThrows(
                NekoAuthOAuthService.OAuthLoginException.class,
                () -> NekoAuthOAuthService.normalizeReturnTo(value));
        assertEquals("invalid_return_to", exception.code());
    }
}
