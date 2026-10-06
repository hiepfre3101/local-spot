package com.localspot.service;

import com.localspot.config.PhotoProperties;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Kiểm tra ảnh upload ngay trong request (NFR-09), trước khi nhận vào hàng đợi: định dạng thật theo chữ ký đầu file
 * (không tin {@code Content-Type} trình duyệt gửi), phần mở rộng khớp định dạng, dung lượng, số megapixel. Chỉ đọc phần
 * header — không giải mã cả ảnh trong request; giải mã thật ở consumer.
 *
 * <p>Chỉ nhận JPEG / PNG (chốt 2026-10-06): ImageIO không đọc WebP / HEIC; frontend crop bằng canvas rồi xuất JPEG nên
 * ảnh WebP / HEIC từ điện thoại vẫn upload được qua giao diện.
 */
@Component
public class ImageInspector {

    /** Định dạng ảnh nhận vào và phần mở rộng hợp lệ của nó. */
    public enum Format {
        JPEG("image/jpeg", Set.of("jpg", "jpeg")),
        PNG("image/png", Set.of("png"));

        private final String contentType;
        private final Set<String> extensions;

        Format(String contentType, Set<String> extensions) {
            this.contentType = contentType;
            this.extensions = extensions;
        }

        public String contentType() {
            return contentType;
        }
    }

    /** Ảnh đã qua kiểm tra. */
    public record Inspected(byte[] content, Format format) {}

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final PhotoProperties properties;

    public ImageInspector(PhotoProperties properties) {
        this.properties = properties;
    }

    /**
     * @param field tên trường báo lỗi 422, vd. {@code photos[2]} — frontend chỉ ra đúng ảnh hỏng
     */
    public Inspected inspect(MultipartFile file, String field) {
        if (file.isEmpty()) {
            throw invalid(field, "Ảnh rỗng.");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw invalid(field, "Ảnh vượt quá " + properties.maxFileSize().toMegabytes() + " MB.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw invalid(field, "Không đọc được ảnh.");
        }
        Format format = detect(content);
        if (format == null) {
            throw invalid(field, "Chỉ nhận ảnh JPEG hoặc PNG.");
        }
        if (!format.extensions.contains(extensionOf(file.getOriginalFilename()))) {
            throw invalid(field, "Phần mở rộng tệp không khớp định dạng ảnh " + format.name() + ".");
        }
        long pixels = pixelCount(content);
        if (pixels <= 0) {
            throw invalid(field, "Ảnh hỏng hoặc không đọc được.");
        }
        if (pixels > properties.maxPixels()) {
            throw invalid(field, "Ảnh quá lớn (tối đa " + properties.maxPixels() / 1_000_000 + " megapixel).");
        }
        return new Inspected(content, format);
    }

    static Format detect(byte[] content) {
        if (startsWith(content, JPEG_MAGIC)) {
            return Format.JPEG;
        }
        if (startsWith(content, PNG_MAGIC)) {
            return Format.PNG;
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Rộng × cao đọc từ header; 0 nếu không đọc được. */
    private static long pixelCount(byte[] content) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return 0;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return (long) reader.getWidth(0) * reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static ApiException invalid(String field, String message) {
        return ApiException.fieldError(ErrorCode.INVALID_IMAGE, field, message);
    }
}
