package com.neko.music.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeRendererTest {

    @Test
    void rendersDecodableQrWithCenterLogo() throws Exception {
        String content = "nekomusic://qrlogin?sid=AbCdEf1234567890AbCdEf1234567890";

        BufferedImage image = QrCodeRenderer.render(content, 320);

        assertEquals(320, image.getWidth());
        assertEquals(320, image.getHeight());
        assertEquals(content, decode(image), "中心合成图标后二维码应仍可被解码");
    }

    @Test
    void producesPngDataUrl() {
        String dataUrl = QrCodeRenderer.toPngDataUrl("nekomusic://qrlogin?sid=test", 256);
        assertTrue(dataUrl.startsWith("data:image/png;base64,"), "应返回 PNG data URL");
        assertTrue(dataUrl.length() > "data:image/png;base64,".length());
    }

    private static String decode(BufferedImage image) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Result result = new MultiFormatReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, true));
        return result.getText();
    }
}
