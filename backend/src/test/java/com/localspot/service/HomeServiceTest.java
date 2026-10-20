package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Đoạn trích của đánh giá nổi bật (ReviewCard): ≤ 180 ký tự, cắt ở ranh giới từ, không tách đôi ký tự. */
class HomeServiceTest {

    @Test
    void shortContentIsKeptWithWhitespaceCollapsed() {
        assertThat(HomeService.excerpt("  Phở ngon,\n\n  nước   trong.  ")).isEqualTo("Phở ngon, nước trong.");
    }

    @Test
    void exactlyMaxLengthIsNotCut() {
        String text = "a".repeat(HomeService.EXCERPT_MAX);
        assertThat(HomeService.excerpt(text)).isEqualTo(text);
    }

    @Test
    void longContentIsCutAtWordBoundaryWithEllipsis() {
        String content =
                "Bánh cuốn tráng mỏng, nhân thịt mộc nhĩ đậm vừa. ".repeat(10).strip();
        String excerpt = HomeService.excerpt(content);
        assertThat(excerpt.codePointCount(0, excerpt.length())).isLessThanOrEqualTo(HomeService.EXCERPT_MAX);
        assertThat(excerpt)
                .endsWith("…")
                .doesNotContain(" …")
                .doesNotContain(",…")
                .doesNotContain(".…");
        // Từ cuối còn nguyên: phần trước "…" là đầu của nội dung gốc, ngay sau đó không phải chữ cái
        String kept = excerpt.substring(0, excerpt.length() - 1);
        assertThat(content).startsWith(kept);
        assertThat(Character.isLetter(content.charAt(kept.length()))).isFalse();
    }

    @Test
    void oneHugeWordIsHardCut() {
        String excerpt = HomeService.excerpt("https://example.vn/" + "x".repeat(400));
        assertThat(excerpt.codePointCount(0, excerpt.length())).isEqualTo(HomeService.EXCERPT_MAX);
        assertThat(excerpt).endsWith("…");
    }

    @Test
    void surrogatePairsAreNeverSplit() {
        // Ký tự ngoài BMP (2 char UTF-16) — cắt theo code point nên không còn nửa cặp surrogate trước "…"
        assertThat(HomeService.excerpt("𝓟".repeat(300))).isEqualTo("𝓟".repeat(HomeService.EXCERPT_MAX - 1) + "…");
    }
}
