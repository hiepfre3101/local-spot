-- Đổi tài khoản quản trị truy cập qua mạng 'root'@'%' → 'localspot_root'@'%' (chốt 2026-10-19).
-- Lý do: tên "root" trùng tài khoản MySQL cài sẵn trên máy dev (dễ nhầm máy chủ khi kết nối từ host).
-- Image mysql không có biến môi trường để đổi tên root, nên đổi bằng SQL lúc khởi tạo volume mới.
-- Giữ nguyên 'root'@'localhost' (chỉ dùng qua socket trong container): entrypoint của image cần nó để tắt máy chủ
-- tạm sau bước init — đổi cả tài khoản đó thì lần khởi tạo đầu tiên thất bại.
-- RENAME USER giữ nguyên mật khẩu (MYSQL_ROOT_PASSWORD) và quyền.
RENAME USER 'root'@'%' TO 'localspot_root'@'%';
