package com.neko.music.util;

import jakarta.servlet.http.Part;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageUploadValidatorTest {

    @Test
    void acceptsRealPngImage() throws Exception {
        Part part = new BytesPart("cover.png", "image/png", pngBytes());

        ImageUploadValidator.ValidationResult result =
                ImageUploadValidator.validatePart(part, ImageUploadValidator.DEFAULT_MAX_IMAGE_BYTES);

        assertTrue(result.isValid());
    }

    @Test
    void rejectsNonImageWithImageExtension() {
        Part part = new BytesPart("cover.png", "image/png", "not an image".getBytes());

        ImageUploadValidator.ValidationResult result =
                ImageUploadValidator.validatePart(part, ImageUploadValidator.DEFAULT_MAX_IMAGE_BYTES);

        assertFalse(result.isValid());
    }

    @Test
    void rejectsMismatchedExtensionAndActualFormat() throws Exception {
        Part part = new BytesPart("cover.jpg", "image/jpeg", pngBytes());

        ImageUploadValidator.ValidationResult result =
                ImageUploadValidator.validatePart(part, ImageUploadValidator.DEFAULT_MAX_IMAGE_BYTES);

        assertFalse(result.isValid());
    }

    @Test
    void acceptsStructurallyValidWebpHeader() {
        Part part = new BytesPart("cover.webp", "image/webp", minimalVp8xWebp());

        ImageUploadValidator.ValidationResult result =
                ImageUploadValidator.validatePart(part, ImageUploadValidator.DEFAULT_MAX_IMAGE_BYTES);

        assertTrue(result.isValid());
    }

    @Test
    void rejectsWebpWithMismatchedExtension() {
        Part part = new BytesPart("cover.png", "image/png", minimalVp8xWebp());

        ImageUploadValidator.ValidationResult result =
                ImageUploadValidator.validatePart(part, ImageUploadValidator.DEFAULT_MAX_IMAGE_BYTES);

        assertFalse(result.isValid());
    }

    @Test
    void rejectsInvalidWebpHeader() {
        byte[] bytes = minimalVp8xWebp();
        bytes[16] = 9;
        Part part = new BytesPart("cover.webp", "image/webp", bytes);

        ImageUploadValidator.ValidationResult result =
                ImageUploadValidator.validatePart(part, ImageUploadValidator.DEFAULT_MAX_IMAGE_BYTES);

        assertFalse(result.isValid());
    }

    @Test
    void rejectsOversizedWebpCanvas() {
        byte[] bytes = minimalVp8xWebp();
        int widthMinusOne = 30_000 - 1;
        bytes[24] = (byte) (widthMinusOne & 0xff);
        bytes[25] = (byte) ((widthMinusOne >> 8) & 0xff);
        bytes[26] = (byte) ((widthMinusOne >> 16) & 0xff);
        Part part = new BytesPart("cover.webp", "image/webp", bytes);

        ImageUploadValidator.ValidationResult result =
                ImageUploadValidator.validatePart(part, ImageUploadValidator.DEFAULT_MAX_IMAGE_BYTES);

        assertFalse(result.isValid());
    }

    private static byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] minimalVp8xWebp() {
        return new byte[]{
                'R', 'I', 'F', 'F',
                22, 0, 0, 0,
                'W', 'E', 'B', 'P',
                'V', 'P', '8', 'X',
                10, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0,
                0, 0, 0
        };
    }

    private static final class BytesPart implements Part {
        private final String fileName;
        private final String contentType;
        private final byte[] bytes;

        private BytesPart(String fileName, String contentType, byte[] bytes) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getSubmittedFileName() {
            return fileName;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public void write(String fileName) {
        }

        @Override
        public void delete() {
        }

        @Override
        public String getHeader(String name) {
            return null;
        }

        @Override
        public Collection<String> getHeaders(String name) {
            return List.of();
        }

        @Override
        public Collection<String> getHeaderNames() {
            return List.of();
        }
    }
}
