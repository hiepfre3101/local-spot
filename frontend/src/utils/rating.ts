/**
 * Rating (README thành phần Rating): 5 hình tròn, mỗi hình tô `sun` theo tỷ lệ điểm — 4.7 → 4 hình đầy + 70% hình thứ
 * năm. Phần lẻ làm tròn tới 10% gần nhất.
 */
export function discFills(score: number): number[] {
  const rounded = Math.round(clamp(score, 0, 5) * 10) / 10
  return Array.from({ length: 5 }, (_, i) => Math.round(clamp(rounded - i, 0, 1) * 100))
}

/** Số điểm hiển thị cạnh hình tròn: "4.7" (dấu chấm — đúng mẫu thiết kế). */
export function scoreText(score: number): string {
  return (Math.round(clamp(score, 0, 5) * 10) / 10).toFixed(1)
}

/** Nhãn cho trình đọc màn hình: "4,7 trên 5 điểm, 2.314 đánh giá" (số kiểu Việt Nam). */
export function ratingLabel(score: number, count?: number): string {
  const base = `${scoreText(score).replace('.', ',')} trên 5 điểm`
  return count === undefined
    ? base
    : `${base}, ${new Intl.NumberFormat('vi-VN').format(count)} đánh giá`
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}
