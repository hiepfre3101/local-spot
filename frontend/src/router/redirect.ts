/**
 * Đích quay lại sau đăng nhập (`?redirect=`) — chỉ chấp nhận đường dẫn nội bộ. Chặn open redirect: `//evil.com` hay
 * `https://evil.com` trong link đăng nhập sẽ đưa người dùng sang trang khác sau khi họ đã nhập mật khẩu.
 */
export function safeRedirect(value: unknown, fallback = '/'): string {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\')
  ) {
    return fallback
  }
  return value
}
