import type { ReportReason, ReportTargetType } from '@/types/api'

/** Đối tượng mở hộp thoại báo cáo (UC17). `label` dùng cho tiêu đề: "đánh giá của Minh Anh". */
export interface ReportTarget {
  type: ReportTargetType
  id: number
  label: string
}

/** Nhãn lý do báo cáo — dùng chung cho hộp thoại báo cáo và thông báo kết quả xử lý. */
export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: 'Spam hoặc quảng cáo' },
  { value: 'FAKE', label: 'Giả mạo, không có thật' },
  { value: 'OFFENSIVE', label: 'Xúc phạm, thô tục' },
  { value: 'WRONG_INFO', label: 'Thông tin sai' },
  { value: 'OTHER', label: 'Lý do khác' },
]
