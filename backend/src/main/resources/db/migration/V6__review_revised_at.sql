-- =====================================================================================
-- Đánh giá cập nhật theo lần ghé mới (supervisor chốt 2026-10-19 — phương án A): vẫn 1 review / người / địa điểm
-- (UNIQUE(place_id, user_id) giữ nguyên), nhưng tác giả quay lại có thể sửa nội dung theo lần ghé mới.
--
--   revised_at   lần cuối tác giả sửa số sao / nội dung / ngày đến; NULL = chưa sửa lần nào.
--
-- Nhãn "đã check-in" so ngày check-in với ngày của COALESCE(revised_at, created_at) thay vì ngày viết đầu tiên: review
-- được làm mới sau khi đã có mặt ở quán (check-in kiểm GPS) mới được nhãn. Không cập nhật dữ liệu cũ — NULL đúng nghĩa
-- "chưa sửa", nhãn của review hiện có giữ nguyên.
-- =====================================================================================
ALTER TABLE reviews
    ADD COLUMN revised_at DATETIME(6) NULL AFTER visited_at;
