package com.neko.music.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * 服务端二维码渲染：把文本编码为 PNG，并在正中心合成软件自身的图标。
 *
 * <p>中心图标会遮挡部分码点，因此固定使用 H 级纠错（约可恢复 30% 损毁区域）；
 * 图标资源缺失或合成失败时自动回退为纯二维码。</p>
 */
public final class QrCodeRenderer {

    private static final Logger logger = LoggerFactory.getLogger(QrCodeRenderer.class);

    /** 内置软件图标（classpath 资源） */
    private static final String LOGO_RESOURCE = "/brand/qr-logo.png";
    /** 二维码前景色（与前端登录码保持一致） */
    private static final int DARK_RGB = 0x0F1524;
    private static final int LIGHT_RGB = 0xFFFFFF;
    /** 图标边长占二维码边长的比例 */
    private static final double LOGO_RATIO = 0.24;

    private static volatile BufferedImage logoImage;
    private static volatile boolean logoLoaded;

    private QrCodeRenderer() {
    }

    /**
     * 生成带中心图标的二维码 PNG data URL。
     *
     * @param text 二维码内容
     * @param size 输出边长（像素），过小将回退到 128
     * @return {@code data:image/png;base64,...}
     */
    public static String toPngDataUrl(String text, int size) {
        BufferedImage image = render(text, size);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("二维码 PNG 编码失败", e);
        }
    }

    /** 渲染二维码位图并在中心合成图标。 */
    public static BufferedImage render(String text, int size) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("二维码内容不能为空");
        }
        int side = Math.max(size, 128);

        BitMatrix matrix = encode(text, side);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? DARK_RGB : LIGHT_RGB);
            }
        }

        BufferedImage logo = logo();
        if (logo != null) {
            drawLogo(image, logo);
        }
        return image;
    }

    private static BitMatrix encode(String text, int side) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, side, side, hints);
        } catch (WriterException e) {
            throw new IllegalArgumentException("二维码内容无法编码", e);
        }
    }

    /** 在二维码正中心绘制白色圆角底衬 + 软件图标（图标保持原始宽高比）。 */
    private static void drawLogo(BufferedImage image, BufferedImage logo) {
        int size = Math.min(image.getWidth(), image.getHeight());
        int box = (int) Math.round(size * LOGO_RATIO);
        int pad = Math.max(2, (int) Math.round(box * 0.14));
        int background = box + pad * 2;
        int origin = (size - background) / 2;

        double scale = Math.min((double) box / logo.getWidth(), (double) box / logo.getHeight());
        int logoWidth = Math.max(1, (int) Math.round(logo.getWidth() * scale));
        int logoHeight = Math.max(1, (int) Math.round(logo.getHeight() * scale));
        int logoX = (size - logoWidth) / 2;
        int logoY = (size - logoHeight) / 2;

        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            double arc = background * 0.44;
            g.fill(new RoundRectangle2D.Double(origin, origin, background, background, arc, arc));
            g.drawImage(logo, logoX, logoY, logoWidth, logoHeight, null);
        } finally {
            g.dispose();
        }
    }

    /** 惰性加载并缓存内置图标；失败返回 null（回退为纯二维码）。 */
    private static BufferedImage logo() {
        if (logoLoaded) {
            return logoImage;
        }
        synchronized (QrCodeRenderer.class) {
            if (!logoLoaded) {
                try (InputStream in = QrCodeRenderer.class.getResourceAsStream(LOGO_RESOURCE)) {
                    if (in != null) {
                        logoImage = ImageIO.read(in);
                    }
                    if (logoImage == null) {
                        logger.warn("未找到二维码中心图标资源 {}，将输出纯二维码", LOGO_RESOURCE);
                    }
                } catch (IOException e) {
                    logger.warn("读取二维码中心图标失败，将输出纯二维码", e);
                }
                logoLoaded = true;
            }
        }
        return logoImage;
    }
}
