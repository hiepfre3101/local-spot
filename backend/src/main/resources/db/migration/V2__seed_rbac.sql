-- =====================================================================================
-- Dữ liệu tham chiếu RBAC — mọi môi trường đều cần (khác dữ liệu demo chỉ có ở dev).
-- Tên permission khớp `x-permission` trong docs/api/openapi.yaml; phân chia theo Q2 (requirements.md).
--
-- Kế thừa role = seed đủ permission cho mỗi role (database.md §3.1), không dùng cây role:
--   OWNER ⊇ USER (plan §4: chủ địa điểm kế thừa thành viên), ADMIN ⊇ MODERATOR (Q2).
-- Tài khoản nhân sự vẫn được gán thêm USER để viết review / bình luận như thành viên.
-- Tra cứu bằng name (không hard-code id) để thứ tự AUTO_INCREMENT không ảnh hưởng.
-- =====================================================================================

INSERT INTO roles (name) VALUES ('USER'), ('OWNER'), ('MODERATOR'), ('ADMIN');

INSERT INTO permissions (name, description) VALUES
    -- Thành viên
    ('review:create',           'Viết đánh giá'),
    ('review:update-own',       'Sửa đánh giá của mình'),
    ('review:delete-own',       'Xóa đánh giá của mình'),
    ('comment:delete-own',      'Xóa bình luận của mình'),
    -- Chủ địa điểm
    ('place:update-own',        'Cập nhật thông tin, ảnh địa điểm mình sở hữu'),
    ('place:stats-own',         'Xem thống kê địa điểm mình sở hữu'),
    ('review:reply-own-place',  'Phản hồi đánh giá trên địa điểm mình sở hữu'),
    -- Kiểm duyệt viên
    ('place:approve',           'Duyệt / từ chối / ẩn địa điểm'),
    ('review:moderate',         'Duyệt / từ chối / ẩn đánh giá'),
    ('report:handle',           'Xử lý báo cáo vi phạm, ẩn nội dung bị báo cáo'),
    ('claim:approve',           'Duyệt yêu cầu xác nhận sở hữu'),
    -- Quản trị viên
    ('user:view',               'Xem danh sách người dùng'),
    ('user:lock',               'Khóa / mở khóa tài khoản'),
    ('user:assign-role',        'Gán / gỡ vai trò'),
    ('category:manage',         'Quản lý danh mục'),
    ('amenity:manage',          'Quản lý tiện ích'),
    ('dashboard:view',          'Xem dashboard thống kê');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('review:create', 'review:update-own', 'review:delete-own', 'comment:delete-own')
WHERE r.name = 'USER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('review:create', 'review:update-own', 'review:delete-own', 'comment:delete-own',
                                 'place:update-own', 'place:stats-own', 'review:reply-own-place')
WHERE r.name = 'OWNER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('place:approve', 'review:moderate', 'report:handle', 'claim:approve')
WHERE r.name = 'MODERATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('place:approve', 'review:moderate', 'report:handle', 'claim:approve',
                                 'user:view', 'user:lock', 'user:assign-role',
                                 'category:manage', 'amenity:manage', 'dashboard:view')
WHERE r.name = 'ADMIN';
