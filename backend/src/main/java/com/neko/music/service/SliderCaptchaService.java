package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.security.SecureRandom;

/**
 * 服务端滑块拼图：生成图、在内存中保存正确 X；{@link #verifyAndConsume} 一次性消费 token。
 * 与 {@code POST /api/user/register} 配合：注册接口内必须校验通过才允许继续。
 */
public class SliderCaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(SliderCaptchaService.class);

    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 180;
    private static final int PUZZLE_W = 52;
    private static final int PUZZLE_H = 52;
    private static final int PUZZLE_ARC = 10;
    private static final int TOLERANCE_PX = 5;
    private static final long TTL_MS = 3 * 60 * 1000L;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, StoredChallenge> store = new ConcurrentHashMap<>();

    private static final class StoredChallenge {
        final int secretX;
        final long expiresAtMs;

        StoredChallenge(int secretX, long expiresAtMs) {
            this.secretX = secretX;
            this.expiresAtMs = expiresAtMs;
        }
    }

    public Map<String, Object> createChallengePayload() {
        pruneExpired();
        int puzzleY = 12 + random.nextInt(Math.max(1, BG_HEIGHT - PUZZLE_H - 24));
        int margin = 64;
        int minX = margin;
        int maxX = BG_WIDTH - PUZZLE_W - margin;
        int secretX = minX + random.nextInt(Math.max(1, maxX - minX));

        BufferedImage bg = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawNoiseBackground(g, BG_WIDTH, BG_HEIGHT, random);

        RoundRectangle2D puzzleShapeWorld = new RoundRectangle2D.Double(
                secretX, puzzleY, PUZZLE_W, PUZZLE_H, PUZZLE_ARC, PUZZLE_ARC);

        BufferedImage slider = extractPuzzlePiece(bg, puzzleShapeWorld, secretX, puzzleY);

        g.setColor(new Color(0, 0, 0, 115));
        g.fill(puzzleShapeWorld);
        g.setColor(new Color(255, 255, 255, 230));
        g.setStroke(new BasicStroke(2f));
        g.draw(puzzleShapeWorld);
        g.dispose();

        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        store.put(token, new StoredChallenge(secretX, now + TTL_MS));

        return Map.of(
                "captchaToken", token,
                "bgImage", toPngDataUrl(bg),
                "sliderImage", toPngDataUrl(slider),
                "puzzleY", puzzleY,
                "bgWidth", BG_WIDTH,
                "bgHeight", BG_HEIGHT,
                "sliderWidth", PUZZLE_W,
                "sliderHeight", PUZZLE_H
        );
    }

    /** 校验成功则删除 token，失败或过期返回 false */
    public boolean verifyAndConsume(String token, int clientOffsetX) {
        if (token == null || token.isBlank()) {
            return false;
        }
        pruneExpired();
        StoredChallenge ch = store.remove(token.trim());
        if (ch == null) {
            return false;
        }
        if (System.currentTimeMillis() > ch.expiresAtMs) {
            return false;
        }
        boolean ok = Math.abs(clientOffsetX - ch.secretX) <= TOLERANCE_PX;
        if (!ok) {
            logger.debug("slider captcha fail clientX={} expected={}", clientOffsetX, ch.secretX);
        }
        return ok;
    }

    private void pruneExpired() {
        if (store.size() < 512) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, StoredChallenge>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAtMs < now) {
                it.remove();
            }
        }
    }

    private static void drawNoiseBackground(Graphics2D g, int w, int h, Random rnd) {
        g.setColor(new Color(240, 244, 250));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 520; i++) {
            int x = rnd.nextInt(w);
            int yy = rnd.nextInt(h);
            int rr = 1 + rnd.nextInt(3);
            g.setColor(new Color(rnd.nextInt(90) + 80, rnd.nextInt(90) + 90, rnd.nextInt(80) + 120, 180));
            g.fillOval(x, yy, rr, rr);
        }
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(120 + rnd.nextInt(80), 120 + rnd.nextInt(80), 160 + rnd.nextInt(60), 90));
            g.drawLine(rnd.nextInt(w), rnd.nextInt(h), rnd.nextInt(w), rnd.nextInt(h));
        }
    }

    private static BufferedImage extractPuzzlePiece(BufferedImage bg, RoundRectangle2D puzzleShapeWorld,
                                                    int secretX, int puzzleY) {
        BufferedImage piece = new BufferedImage(PUZZLE_W, PUZZLE_H, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < PUZZLE_H; py++) {
            for (int px = 0; px < PUZZLE_W; px++) {
                int wx = secretX + px;
                int wy = puzzleY + py;
                if (puzzleShapeWorld.contains(wx, wy)) {
                    piece.setRGB(px, py, bg.getRGB(wx, wy) | 0xFF000000);
                }
            }
        }
        Graphics2D pg = piece.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D local = new RoundRectangle2D.Double(0, 0, PUZZLE_W, PUZZLE_H, PUZZLE_ARC, PUZZLE_ARC);
        pg.setColor(new Color(255, 255, 255, 230));
        pg.setStroke(new BasicStroke(2f));
        pg.draw(local);
        pg.dispose();
        return piece;
    }

    private static String toPngDataUrl(BufferedImage img) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("png encode", e);
        }
    }
}
