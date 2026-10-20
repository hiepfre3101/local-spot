package com.localspot.config;

import com.meilisearch.sdk.model.MatchingStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.search.*} — Meilisearch (FR-09). Tham số so khớp chốt 2026-10-08 sau khi đo trên dữ liệu tiếng Việt.
 *
 * @param host URL Meilisearch ({@code http://localhost:7700} khi dev)
 * @param apiKey khóa API (dev: master key trong {@code ../.env})
 * @param index tên index địa điểm
 * @param matchingStrategy truy vấn nhiều từ không khớp đủ thì bỏ từ nào trước. {@code last} (mặc định Meilisearch): bỏ
 *     từ cuối trước. Đã thử {@code frequency} (bỏ từ phổ biến trước): trả rỗng cho truy vấn thường như "pho bat dan ngon"
 *     — loại. Điểm yếu của {@code last} là từ chung chung đầu truy vấn ("quán phở thìn") — xử lý ở {@code SearchQueries}
 * @param oneTypoMinWordSize từ dài từ chừng này ký tự mới chấp nhận 1 lỗi chính tả; 4 thay vì mặc định 5 vì âm tiết
 *     tiếng Việt ngắn ("bunn cha", "phoo thin" với 5 thì rỗng). Không hạ xuống 3: bỏ dấu rồi "pho" / "cho" / "bo" quá gần
 * @param twoTypoMinWordSize từ dài từ chừng này ký tự mới chấp nhận 2 lỗi
 * @param retryAfterFailure Meilisearch lỗi → dùng fallback MySQL (U9) suốt khoảng này rồi mới thử lại, để mỗi request
 *     không phải chờ hết timeout của một máy chủ đang treo
 * @param reindexOnStartup khởi động thì đẩy toàn bộ địa điểm đã duyệt vào hàng đợi đồng bộ — tự lành khi message đồng
 *     bộ bị mất (không có outbox) hoặc index mới tạo
 */
@Validated
@ConfigurationProperties("localspot.search")
public record SearchProperties(
        @NotBlank String host,
        String apiKey,
        @NotBlank String index,
        @NotNull MatchingStrategy matchingStrategy,
        @Positive int oneTypoMinWordSize,
        @Positive int twoTypoMinWordSize,
        @NotNull Duration retryAfterFailure,
        boolean reindexOnStartup) {}
