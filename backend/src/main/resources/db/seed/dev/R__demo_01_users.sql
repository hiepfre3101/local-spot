-- =====================================================================================
-- DỮ LIỆU DEMO — CHỈ profile dev (application-dev.yml thêm classpath:db/seed/dev vào Flyway).
-- Không bao giờ nằm trong db/migration → prod / test không có tài khoản mật khẩu công khai này.
--
-- Repeatable (R__): Flyway chạy lại khi nội dung file đổi, sau mọi migration V*. Vì vậy mọi lệnh
-- phải idempotent (INSERT IGNORE theo khóa UNIQUE) và KHÔNG xóa / ghi đè dữ liệu đang có.
-- Hàng được sinh bằng SQL (CTE đệ quy + công thức số học cố định, không RAND) → chạy ở máy nào
-- cũng ra cùng một bộ dữ liệu.
--
-- Mọi tài khoản: mật khẩu "LocalSpot2026", email @localspot.test (TLD dành riêng, không gửi được thư thật).
--   admin@          USER + ADMIN
--   mod1@, mod2@    USER + MODERATOR
--   owner1..6@      USER + OWNER (sở hữu một số địa điểm ở R__demo_02_places.sql)
--   member01..51@   USER; member09, 18, 27... chưa xác thực email; tuổi tài khoản 0–399 ngày
-- Chưa có review / vote nên trust mọi tài khoản < 30 — review demo seed sau khi có service tính
-- rating và trust (checklist E), để không chép công thức lõi ra SQL.
-- =====================================================================================

SET @demo_pw = '$2a$10$6KpZwSyBc2bx/dKnOuPqY.mKYnWcXmonz5C3b/l7TpvZholAMoEq2';
SET @t0 = TIMESTAMP('2026-09-30 00:00:00');

INSERT IGNORE INTO users (email, password_hash, display_name, bio, email_verified_at, created_at, updated_at)
VALUES
    ('admin@localspot.test',  @demo_pw, 'Quản trị LocalSpot',  'Tài khoản quản trị demo.',
        @t0 - INTERVAL 200 DAY, @t0 - INTERVAL 200 DAY, @t0 - INTERVAL 200 DAY),
    ('mod1@localspot.test',   @demo_pw, 'Nguyễn Ngọc Lan',     'Kiểm duyệt viên khu vực miền Bắc.',
        @t0 - INTERVAL 190 DAY, @t0 - INTERVAL 190 DAY, @t0 - INTERVAL 190 DAY),
    ('mod2@localspot.test',   @demo_pw, 'Trần Minh Quang',     'Kiểm duyệt viên khu vực miền Nam.',
        @t0 - INTERVAL 185 DAY, @t0 - INTERVAL 185 DAY, @t0 - INTERVAL 185 DAY),
    ('owner1@localspot.test', @demo_pw, 'Lê Văn Thìn',         'Chủ quán phở gia truyền.',
        @t0 - INTERVAL 160 DAY, @t0 - INTERVAL 160 DAY, @t0 - INTERVAL 160 DAY),
    ('owner2@localspot.test', @demo_pw, 'Phạm Thị Hạnh',       'Chủ quán cơm nhà làm.',
        @t0 - INTERVAL 150 DAY, @t0 - INTERVAL 150 DAY, @t0 - INTERVAL 150 DAY),
    ('owner3@localspot.test', @demo_pw, 'Hoàng Gia Bảo',       'Chủ chuỗi cà phê nhỏ.',
        @t0 - INTERVAL 140 DAY, @t0 - INTERVAL 140 DAY, @t0 - INTERVAL 140 DAY),
    ('owner4@localspot.test', @demo_pw, 'Võ Thị Mai',          'Chủ tiệm trà sữa.',
        @t0 - INTERVAL 120 DAY, @t0 - INTERVAL 120 DAY, @t0 - INTERVAL 120 DAY),
    ('owner5@localspot.test', @demo_pw, 'Đặng Quốc Huy',       'Chủ nhà hàng hải sản.',
        @t0 - INTERVAL 100 DAY, @t0 - INTERVAL 100 DAY, @t0 - INTERVAL 100 DAY),
    ('owner6@localspot.test', @demo_pw, 'Bùi Thanh Tâm',       'Chủ quán chay.',
        @t0 - INTERVAL 90 DAY,  @t0 - INTERVAL 90 DAY,  @t0 - INTERVAL 90 DAY);

INSERT IGNORE INTO users (email, password_hash, display_name, email_verified_at, created_at, updated_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 51
)
SELECT CONCAT('member', LPAD(n, 2, '0'), '@localspot.test'),
       @demo_pw,
       CONCAT_WS(' ',
           ELT(1 + MOD(n, 14), 'Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Võ', 'Đặng',
               'Bùi', 'Đỗ', 'Hồ', 'Ngô'),
           ELT(1 + MOD(n * 7, 9), 'Văn', 'Thị', 'Minh', 'Ngọc', 'Thanh', 'Quốc', 'Thu', 'Hoài', 'Gia'),
           ELT(1 + MOD(n * 11, 24), 'An', 'Bình', 'Châu', 'Dũng', 'Giang', 'Hà', 'Hải', 'Hiếu', 'Hương', 'Khánh',
               'Linh', 'Long', 'Mai', 'Nam', 'Nhung', 'Phúc', 'Quân', 'Sơn', 'Tâm', 'Thảo', 'Trang', 'Tuấn',
               'Vy', 'Yến')),
       CASE WHEN MOD(n, 9) = 0 THEN NULL ELSE @t0 - INTERVAL MOD(n * 37, 400) DAY + INTERVAL 1 HOUR END,
       @t0 - INTERVAL MOD(n * 37, 400) DAY,
       @t0 - INTERVAL MOD(n * 37, 400) DAY
FROM seq;

-- Mọi tài khoản demo là thành viên; nhân sự và chủ quán nhận thêm role tương ứng
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'USER'
WHERE u.email LIKE '%@localspot.test';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@localspot.test';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'MODERATOR'
WHERE u.email IN ('mod1@localspot.test', 'mod2@localspot.test');

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'OWNER'
WHERE u.email REGEXP '^owner[1-6]@localspot\\.test$';
