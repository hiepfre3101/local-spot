-- =====================================================================================
-- Huy hiệu đóng góp (FR-29, O3 — chốt 2026-10-12). Cần ở mọi môi trường: tiêu chí trao viết trong code
-- (com.localspot.service.BadgeRule), bảng chỉ giữ danh mục để hiển thị — thêm huy hiệu = migration mới + rule mới.
-- icon là khóa trung tính; frontend tự ánh xạ sang bộ icon của design system.
-- Trao một lần, không thu hồi (user_badges.awarded_at là lịch sử) — kể cả khi nội dung sau đó bị xóa / ẩn.
-- =====================================================================================

INSERT INTO badges (code, name, description, icon) VALUES
    ('REVIEWER_1',   'Người đánh giá cấp 1', 'Có 1 đánh giá đang hiển thị.',                         'reviewer'),
    ('REVIEWER_2',   'Người đánh giá cấp 2', 'Có 10 đánh giá đang hiển thị.',                        'reviewer'),
    ('REVIEWER_3',   'Người đánh giá cấp 3', 'Có 25 đánh giá đang hiển thị.',                        'reviewer'),
    ('REVIEWER_4',   'Người đánh giá cấp 4', 'Có 50 đánh giá đang hiển thị.',                        'reviewer'),
    ('REVIEWER_5',   'Người đánh giá cấp 5', 'Có 100 đánh giá đang hiển thị.',                       'reviewer'),
    ('EXPLORER',     'Nhà thám hiểm',        'Đã check-in tại 10 địa điểm khác nhau.',               'explorer'),
    ('PHOTOGRAPHER', 'Nhiếp ảnh gia',        'Có 20 ảnh trong các đánh giá đang hiển thị.',          'photographer'),
    ('HELPFUL',      'Người chia sẻ hữu ích', 'Nhận 50 lượt "hữu ích" hợp lệ cho các đánh giá của mình.', 'helpful');
