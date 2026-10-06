package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localspot.service.ImageResizer.Resized;
import com.localspot.service.ImageResizer.UnreadableImageException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** FR-16 / NFR-11: xoay theo EXIF, gỡ EXIF (GPS), thu nhỏ không phóng to, PNG trong suốt → nền trắng. */
class ImageResizerTest {

    private final ImageResizer resizer = new ImageResizer(TestImages.PROPERTIES);

    @Test
    void appliesExifOrientationAndStripsAllMetadata() throws Exception {
        byte[] original = TestImages.rotatedWithGps();
        assertThat(TestImages.hasExif(original)).isTrue();

        Resized resized = resizer.resize(original);

        // 600 × 400 + Orientation 6 → ảnh dọc 400 × 600; nhỏ hơn mọi giới hạn trừ thumb (320)
        assertThat(resized.width()).isEqualTo(400);
        assertThat(resized.height()).isEqualTo(600);
        BufferedImage large = TestImages.decode(resized.variants().get(PhotoSize.LARGE));
        assertThat(isBlue(large.getRGB(large.getWidth() - 5, 5)))
                .as("khối xanh sang góc trên-phải")
                .isTrue();
        assertThat(isBlue(large.getRGB(5, large.getHeight() - 5))).isFalse();
        for (PhotoSize size : PhotoSize.values()) {
            assertThat(TestImages.hasExif(resized.variants().get(size)))
                    .as("bản %s không còn EXIF / GPS", size)
                    .isFalse();
        }
    }

    @Test
    void shrinksByLongEdgeWithoutUpscaling() throws Exception {
        Resized big = resizer.resize(TestImages.jpeg(4000, 2500));
        assertThat(dimensions(big, PhotoSize.LARGE)).containsExactly(1920, 1200);
        assertThat(dimensions(big, PhotoSize.MEDIUM)).containsExactly(960, 600);
        assertThat(dimensions(big, PhotoSize.THUMB)).containsExactly(320, 200);
        assertThat(big.width()).isEqualTo(1920);

        Resized small = resizer.resize(TestImages.jpeg(300, 200));
        assertThat(dimensions(small, PhotoSize.LARGE)).containsExactly(300, 200);
        assertThat(dimensions(small, PhotoSize.THUMB)).containsExactly(300, 200);
    }

    @Test
    void flattensTransparentPngOntoWhite() throws Exception {
        Resized resized = resizer.resize(TestImages.transparentPng());

        BufferedImage large = TestImages.decode(resized.variants().get(PhotoSize.LARGE));
        Color corner = new Color(large.getRGB(3, 3));
        assertThat(corner.getRed()).isGreaterThan(245);
        assertThat(corner.getGreen()).isGreaterThan(245);
        assertThat(corner.getBlue()).isGreaterThan(245);
    }

    @Test
    void undecodableBytesAreAPermanentFailure() {
        byte[] garbage = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};
        assertThatThrownBy(() -> resizer.resize(garbage)).isInstanceOf(UnreadableImageException.class);
    }

    private static int[] dimensions(Resized resized, PhotoSize size) {
        BufferedImage image = TestImages.decode(resized.variants().get(size));
        return new int[] {image.getWidth(), image.getHeight()};
    }

    private static boolean isBlue(int rgb) {
        Color c = new Color(rgb);
        return c.getBlue() > 200 && c.getRed() < 60 && c.getGreen() < 60;
    }
}
