import type { BannerSlot } from '@/types/api'
import type { CropPreset } from '@/utils/images'

/**
 * Ba ô ảnh trang chủ (UC34) — tên, vị trí trên HomePage và tỷ lệ khung hiển thị (gợi ý khi cắt ảnh; trang chủ vẫn
 * `object-fit: cover` nên ảnh lệch tỷ lệ chỉ bị cắt mép, không méo).
 */
export const BANNER_SLOTS: readonly {
  slot: BannerSlot
  title: string
  description: string
  crop: CropPreset
}[] = [
  {
    slot: 'HERO',
    title: 'Nền hero',
    description:
      'Ảnh nền khối đầu trang. Tiêu đề và ô tìm kiếm nằm đè lên — trang tự phủ một lớp tối đều để chữ luôn đọc được.',
    crop: { label: 'Khung hero', aspect: 15 / 7 },
  },
  {
    slot: 'SIGNUP_BAND',
    title: 'Băng mời đăng ký',
    description: 'Ảnh bên phải băng màu "Đi cùng cộng đồng…".',
    crop: { label: 'Khung băng', aspect: 4 / 3 },
  },
  {
    slot: 'COMMUNITY',
    title: 'Khối thành viên',
    description: 'Ảnh dưới tiêu đề "Không chỉ là trang đánh giá…".',
    crop: { label: 'Khung khối', aspect: 5 / 2 },
  },
]
