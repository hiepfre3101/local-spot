import type { ReportTargetType } from '@/types/api'

/** Đối tượng mở hộp thoại báo cáo (UC17). `label` dùng cho tiêu đề: "đánh giá của Minh Anh". */
export interface ReportTarget {
  type: ReportTargetType
  id: number
  label: string
}
