package com.neko.music.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderCaptchaServiceTest {

    private static final String IP = "203.0.113.7";
    private static final String UA = "Mozilla/5.0 TestBrowser";

    @Test
    void finalOffsetAloneCannotIssuePass() throws Exception {
        SliderCaptchaService service = new SliderCaptchaService();
        Map<String, Object> payload = service.createChallengePayload(IP, UA);
        String token = (String) payload.get("captchaToken");
        int secretX = secretX(service, token);

        Optional<String> pass = service.verifySlideAndIssuePass(
                token, secretX, List.of(), IP, UA);

        assertTrue(pass.isEmpty());
    }

    @Test
    void validTraceIssuesPassAndBindsItToSource() throws Exception {
        SliderCaptchaService service = new SliderCaptchaService();
        Map<String, Object> payload = service.createChallengePayload(IP, UA);
        String token = (String) payload.get("captchaToken");
        int secretX = secretX(service, token);

        Optional<String> pass = service.verifySlideAndIssuePass(
                token, secretX, validTrace(secretX), IP, UA);

        assertTrue(pass.isPresent());
        assertTrue(service.consumePassToken(pass.get(), IP, UA));
    }

    @Test
    void passCannotBeConsumedFromAnotherSource() throws Exception {
        SliderCaptchaService service = new SliderCaptchaService();
        Map<String, Object> payload = service.createChallengePayload(IP, UA);
        String token = (String) payload.get("captchaToken");
        int secretX = secretX(service, token);

        String pass = service.verifySlideAndIssuePass(
                token, secretX, validTrace(secretX), IP, UA).orElseThrow();

        assertFalse(service.consumePassToken(pass, "198.51.100.9", UA));
    }

    private static List<SliderCaptchaService.DragSample> validTrace(int secretX) {
        List<SliderCaptchaService.DragSample> trace = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            trace.add(new SliderCaptchaService.DragSample(
                    i * 70L, Math.round(secretX * i / 8f)));
        }
        return trace;
    }

    private static int secretX(SliderCaptchaService service, String token) throws Exception {
        Field field = SliderCaptchaService.class.getDeclaredField("challenges");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> challenges = (ConcurrentHashMap<String, Object>) field.get(service);
        Object challenge = challenges.get(token);
        assertNotNull(challenge);
        Field secretField = challenge.getClass().getDeclaredField("secretX");
        secretField.setAccessible(true);
        return secretField.getInt(challenge);
    }
}
