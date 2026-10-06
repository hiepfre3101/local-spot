package com.localspot.service;

import com.localspot.config.PhotoProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.util.EnumMap;
import java.util.Map;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * Ảnh gốc → 3 bản JPEG (FR-16): xoay đúng chiều theo EXIF Orientation (Thumbnailator tự áp khi đọc), nền trắng cho
 * PNG trong suốt, thu nhỏ theo cạnh dài (không phóng to ảnh nhỏ), nén JPEG. Ảnh đầu ra được ghi mới hoàn toàn nên không
 * mang theo EXIF nào của ảnh gốc — gỡ tọa độ GPS (NFR-11) mà không cần thư viện đọc / sửa metadata.
 *
 * <p>Xuất JPEG thay vì WebP: ImageIO / Thumbnailator không ghi được WebP (progress 2026-09-27).
 */
@Component
public class ImageResizer {

    /** Các bản đã nén + kích thước bản LARGE (lưu vào {@code width} / {@code height} để frontend giữ chỗ đúng tỉ lệ). */
    public record Resized(Map<PhotoSize, byte[]> variants, int width, int height) {

        public Resized {
            variants = Map.copyOf(variants);
        }
    }

    /** Ảnh không giải mã được — lỗi vĩnh viễn, thử lại cũng vô ích. */
    public static class UnreadableImageException extends Exception {

        @Serial
        private static final long serialVersionUID = 1L;

        UnreadableImageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final PhotoProperties properties;

    public ImageResizer(PhotoProperties properties) {
        this.properties = properties;
    }

    public Resized resize(byte[] original) throws UnreadableImageException {
        BufferedImage source;
        try {
            source =
                    Thumbnails.of(new ByteArrayInputStream(original)).scale(1.0).asBufferedImage();
        } catch (IOException | RuntimeException e) {
            throw new UnreadableImageException("Không giải mã được ảnh: " + e.getMessage(), e);
        }
        BufferedImage rgb = onWhite(source);
        Map<PhotoSize, byte[]> variants = new EnumMap<>(PhotoSize.class);
        BufferedImage large = null;
        try {
            for (PhotoSize size : PhotoSize.values()) {
                BufferedImage scaled = fit(rgb, maxEdge(size));
                variants.put(size, toJpeg(scaled));
                if (size == PhotoSize.LARGE) {
                    large = scaled;
                }
            }
        } catch (IOException e) {
            throw new UnreadableImageException("Không nén được ảnh: " + e.getMessage(), e);
        }
        return new Resized(variants, large.getWidth(), large.getHeight());
    }

    private int maxEdge(PhotoSize size) {
        return switch (size) {
            case THUMB -> properties.thumbSize();
            case MEDIUM -> properties.mediumSize();
            case LARGE -> properties.largeSize();
        };
    }

    /** Vẽ lên nền trắng RGB: JPEG không có kênh alpha, và gộp mọi kiểu màu (xám, bảng màu) về một kiểu. */
    private static BufferedImage onWhite(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static BufferedImage fit(BufferedImage image, int maxEdge) throws IOException {
        if (Math.max(image.getWidth(), image.getHeight()) <= maxEdge) {
            return image;
        }
        return Thumbnails.of(image).size(maxEdge, maxEdge).asBufferedImage();
    }

    private byte[] toJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(image)
                .scale(1.0)
                .outputFormat("jpg")
                .outputQuality(properties.jpegQuality())
                .toOutputStream(out);
        return out.toByteArray();
    }
}
