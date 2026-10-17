/**
 * Bản nháp form trong trình duyệt (localStorage) — tiện ích cho người dùng, không phải dữ liệu bắt buộc phải còn: chế
 * độ riêng tư, bộ nhớ đầy hay trình duyệt chặn thì đọc ra `null` / ghi bỏ qua, form vẫn chạy bình thường. Không lưu tệp
 * (ảnh) — chỉ dữ liệu JSON. Khóa gồm id người dùng để hai tài khoản chung máy không thấy nháp của nhau.
 */
const PREFIX = 'localspot:draft:'

export function draftKey(form: string, userId: number): string {
  return `${PREFIX}${form}:${userId}`
}

export function loadDraft<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

export function saveDraft(key: string, value: unknown): boolean {
  try {
    localStorage.setItem(key, JSON.stringify(value))
    return true
  } catch {
    return false
  }
}

export function clearDraft(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // không xoá được thì thôi — lần sau khôi phục, người dùng tự xoá
  }
}
