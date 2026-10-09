package com.localspot.service;

import com.localspot.config.PhotoProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.springframework.util.unit.DataSize;

/** Ảnh mẫu cho test: tệp nhỏ trong {@code src/test/resources/images}, còn lại sinh trong bộ nhớ (không commit ảnh lớn). */
public final class TestImages {

    /** Cấu hình giống application.yml. */
    public static final PhotoProperties PROPERTIES =
            new PhotoProperties(10, 30, 10, DataSize.ofMegabytes(5), 40_000_000L, 0.8f, 320, 960, 1920);

    private TestImages() {}

    /**
     * JPEG 600 × 400 lưu nằm ngang, khối xanh ở góc trên-trái, EXIF Orientation = 6 (cần xoay 90° theo chiều kim đồng
     * hồ) và tọa độ GPS — hiển thị đúng là ảnh dọc 400 × 600, khối xanh ở góc trên-phải.
     */
    public static byte[] rotatedWithGps() {
        return resource("rotated-gps.jpg");
    }

    /** PNG 400 × 240 hoàn toàn trong suốt. */
    public static byte[] transparentPng() {
        return resource("transparent.png");
    }

    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(10, 120, 10));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * PNG chỉ có chữ ký + chunk IHDR khai báo {@code width × height} (CRC đúng) — đọc được kích thước từ header nhưng
     * không có dữ liệu ảnh. Dùng cho ảnh "bom giải nén" mà không cần tạo ảnh thật hàng trăm megapixel.
     */
    public static byte[] pngHeaderOnly(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII));
        ihdr.putInt(width).putInt(height);
        ihdr.put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0); // 8 bit RGB
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
        png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        png.putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
        return png.array();
    }

    public static BufferedImage decode(byte[] content) {
        try {
            return ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** JPEG có segment APP1 "Exif" (nơi chứa Orientation, GPS…). */
    public static boolean hasExif(byte[] jpeg) {
        byte[] marker = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i <= jpeg.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (jpeg[i + j] != marker[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] resource(String name) {
        try (InputStream in = TestImages.class.getResourceAsStream("/images/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu ảnh mẫu " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
