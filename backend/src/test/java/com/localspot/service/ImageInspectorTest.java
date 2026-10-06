package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localspot.config.PhotoProperties;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.service.ImageInspector.Format;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/** NFR-09: định dạng theo chữ ký file (không theo Content-Type), đuôi tệp khớp định dạng, dung lượng, megapixel. */
class ImageInspectorTest {

    private final ImageInspector inspector = new ImageInspector(TestImages.PROPERTIES);

    @Test
    void acceptsJpegAndPngByContent() {
        assertThat(inspector
                        .inspect(file("a.JPG", TestImages.rotatedWithGps()), "photos[0]")
                        .format())
                .isEqualTo(Format.JPEG);
        assertThat(inspector
                        .inspect(file("a.jpeg", TestImages.jpeg(10, 10)), "photos[0]")
                        .format())
                .isEqualTo(Format.JPEG);
        assertThat(inspector
                        .inspect(file("b.png", TestImages.transparentPng()), "photos[1]")
                        .format())
                .isEqualTo(Format.PNG);
    }

    @Test
    void ignoresClaimedContentTypeAndChecksRealBytes() {
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
        expectInvalid(file("x.jpg", gif), "photos[3]", "JPEG hoặc PNG");
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        expectInvalid(file("x.webp", webp), "photos[0]", "JPEG hoặc PNG");
    }

    @Test
    void extensionMustMatchDetectedFormat() {
        expectInvalid(file("anh.jpg", TestImages.transparentPng()), "photos[0]", "Phần mở rộng");
        expectInvalid(file("anh.png.exe", TestImages.transparentPng()), "photos[0]", "Phần mở rộng");
        expectInvalid(file("anh", TestImages.jpeg(10, 10)), "photos[0]", "Phần mở rộng");
    }

    @Test
    void rejectsEmptyOversizedAndCorruptFiles() {
        expectInvalid(file("a.jpg", new byte[0]), "photos[0]", "rỗng");

        PhotoProperties tiny = new PhotoProperties(10, 30, DataSize.ofBytes(100), 40_000_000L, 0.8f, 320, 960, 1920);
        assertThatThrownBy(() -> new ImageInspector(tiny).inspect(file("a.jpg", TestImages.rotatedWithGps()), "f"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("vượt quá");

        byte[] jpegMagicThenGarbage = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0};
        expectInvalid(file("a.jpg", jpegMagicThenGarbage), "photos[0]", "hỏng");
    }

    @Test
    void rejectsDecompressionBombFromHeaderAlone() {
        expectInvalid(file("bom.png", TestImages.pngHeaderOnly(20_000, 20_000)), "photos[0]", "megapixel");
        // Vừa ngưỡng 40 MP vẫn nhận (chỉ đọc header — không giải mã)
        assertThat(inspector
                        .inspect(file("ok.png", TestImages.pngHeaderOnly(8000, 5000)), "photos[0]")
                        .format())
                .isEqualTo(Format.PNG);
    }

    private void expectInvalid(MockMultipartFile file, String field, String messagePart) {
        assertThatThrownBy(() -> inspector.inspect(file, field)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_IMAGE);
            assertThat(e.getErrors()).singleElement().satisfies(err -> {
                assertThat(err.field()).isEqualTo(field);
                assertThat(err.message()).contains(messagePart);
            });
        });
    }

    /** Content-Type luôn khai "image/jpeg" — kiểm tra phải bỏ qua nó. */
    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("photos", name, "image/jpeg", content);
    }
}
