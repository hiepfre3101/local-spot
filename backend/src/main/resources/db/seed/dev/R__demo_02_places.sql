-- =====================================================================================
-- DỮ LIỆU DEMO — CHỈ profile dev. Quy tắc như R__demo_01_users.sql: idempotent, không xóa / ghi đè,
-- sinh tất định bằng công thức số học (không RAND).
--
-- 300 địa điểm giả (tên, địa chỉ hư cấu — không phải quán có thật):
--   Hà Nội 110 · TP.HCM 100 · Đà Nẵng 40 · Huế 25 · Đà Lạt 25; tọa độ rải quanh trung tâm mỗi thành phố
--   để thử truy vấn bán kính và marker cluster.
--   Trạng thái: n % 20 = 0 → PENDING (hàng chờ duyệt), n % 37 = 0 → REJECTED, còn lại APPROVED.
--   owner1..6 sở hữu các địa điểm n % 25 = 1 (đã duyệt).
--   review_count / avg_rating / bayesian_score giữ mặc định 0 — chưa có review demo (xem file 01).
--   phone để trống: số điện thoại bịa có thể trùng số thật của người khác.
-- Khóa idempotent: slug = <tiền tố>-<tên>-<thành phố>-<n>.
-- =====================================================================================

SET @t0 = TIMESTAMP('2026-09-30 00:00:00');

INSERT IGNORE INTO places (category_id, created_by, owner_id, name, slug, description, address, city, location,
                           price_min, price_max, status, reject_reason, moderated_by, moderated_at,
                           created_at, updated_at)
WITH RECURSIVE
seq (n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 300
),
city (idx, name, slug, lat, lng, spread, streets) AS (
    SELECT 1, 'Hà Nội', 'ha-noi', 21.0285, 105.8542, 0.070,
           JSON_ARRAY('Hàng Bạc', 'Hàng Buồm', 'Tạ Hiện', 'Lý Quốc Sư', 'Phan Đình Phùng', 'Tràng Tiền', 'Bà Triệu',
                      'Tống Duy Tân', 'Kim Mã', 'Xuân Diệu', 'Láng Hạ', 'Trần Duy Hưng', 'Nguyễn Hữu Huân',
                      'Đường Thành', 'Hai Bà Trưng', 'Thụy Khuê')
    UNION ALL SELECT 2, 'TP. Hồ Chí Minh', 'tp-hcm', 10.7769, 106.7009, 0.080,
           JSON_ARRAY('Nguyễn Huệ', 'Lê Lợi', 'Pasteur', 'Nguyễn Thị Minh Khai', 'Võ Văn Tần', 'Lê Thánh Tôn',
                      'Bùi Viện', 'Phạm Ngũ Lão', 'Nguyễn Trãi', 'Cách Mạng Tháng Tám', 'Điện Biên Phủ',
                      'Trần Hưng Đạo', 'Nam Kỳ Khởi Nghĩa', 'Hồ Tùng Mậu')
    UNION ALL SELECT 3, 'Đà Nẵng', 'da-nang', 16.0544, 108.2022, 0.050,
           JSON_ARRAY('Bạch Đằng', 'Trần Phú', 'Lê Duẩn', 'Nguyễn Văn Linh', 'Võ Nguyên Giáp', 'Hoàng Sa',
                      'Phạm Văn Đồng', 'Hùng Vương')
    UNION ALL SELECT 4, 'Huế', 'hue', 16.4637, 107.5909, 0.030,
           JSON_ARRAY('Lê Lợi', 'Hùng Vương', 'Nguyễn Công Trứ', 'Chu Văn An', 'Đinh Tiên Hoàng', 'Phạm Ngũ Lão')
    UNION ALL SELECT 5, 'Đà Lạt', 'da-lat', 11.9404, 108.4583, 0.030,
           JSON_ARRAY('Trần Phú', 'Bùi Thị Xuân', 'Phan Đình Phùng', 'Nguyễn Chí Thanh', 'Trương Công Định',
                      'Ba Tháng Hai')
),
-- Mỗi danh mục con: tiền tố tên (kèm slug ASCII), khoảng giá (VND) và câu mô tả.
cat (idx, slug, kind, prefixes, prefix_slugs, price_min, price_max, blurb) AS (
    SELECT 1, 'pho-bun', 'FOOD', JSON_ARRAY('Phở', 'Bún chả', 'Bún bò', 'Bún riêu', 'Bún thang'),
           JSON_ARRAY('pho', 'bun-cha', 'bun-bo', 'bun-rieu', 'bun-thang'), 35000, 70000,
           'Nước dùng ninh xương nhiều giờ, bánh tươi mỗi sáng, phục vụ nhanh.'
    UNION ALL SELECT 2, 'com', 'FOOD', JSON_ARRAY('Cơm tấm', 'Cơm gà', 'Cơm niêu', 'Cơm nhà'),
           JSON_ARRAY('com-tam', 'com-ga', 'com-nieu', 'com-nha'), 35000, 90000,
           'Cơm nóng, món mặn thay đổi theo ngày, hợp bữa trưa văn phòng.'
    UNION ALL SELECT 3, 'lau-nuong', 'FOOD', JSON_ARRAY('Lẩu', 'Nướng', 'Lẩu nấm', 'Lẩu dê'),
           JSON_ARRAY('lau', 'nuong', 'lau-nam', 'lau-de'), 150000, 350000,
           'Không gian rộng cho nhóm bạn và gia đình, nguyên liệu chọn trong ngày.'
    UNION ALL SELECT 4, 'hai-san', 'FOOD', JSON_ARRAY('Hải sản', 'Ốc', 'Cua ghẹ'),
           JSON_ARRAY('hai-san', 'oc', 'cua-ghe'), 120000, 400000,
           'Hải sản tươi sống chọn tại bể, chế biến theo yêu cầu.'
    UNION ALL SELECT 5, 'mon-chay', 'FOOD', JSON_ARRAY('Quán chay', 'Cơm chay', 'Buffet chay'),
           JSON_ARRAY('quan-chay', 'com-chay', 'buffet-chay'), 40000, 120000,
           'Món chay thanh đạm, không dùng bột ngọt, có món theo mùa.'
    UNION ALL SELECT 6, 'an-vat', 'FOOD', JSON_ARRAY('Bánh mì', 'Bánh cuốn', 'Bánh xèo', 'Chè', 'Xôi'),
           JSON_ARRAY('banh-mi', 'banh-cuon', 'banh-xeo', 'che', 'xoi'), 15000, 50000,
           'Món ăn vặt đường phố, giá mềm, mở đến khuya.'
    UNION ALL SELECT 7, 'ca-phe', 'DRINK', JSON_ARRAY('Cà phê', 'Cà phê trứng', 'Cà phê muối', 'Cafe'),
           JSON_ARRAY('ca-phe', 'ca-phe-trung', 'ca-phe-muoi', 'cafe'), 25000, 65000,
           'Cà phê rang xay tại chỗ, góc ngồi yên tĩnh để làm việc.'
    UNION ALL SELECT 8, 'tra-tra-sua', 'DRINK', JSON_ARRAY('Trà sữa', 'Trà chanh', 'Trà'),
           JSON_ARRAY('tra-sua', 'tra-chanh', 'tra'), 20000, 60000,
           'Trà pha mới theo ly, điều chỉnh độ ngọt và đá theo ý.'
    UNION ALL SELECT 9, 'kem-trang-mieng', 'DRINK', JSON_ARRAY('Kem', 'Sữa chua', 'Bánh ngọt'),
           JSON_ARRAY('kem', 'sua-chua', 'banh-ngot'), 20000, 70000,
           'Món tráng miệng làm thủ công, vị thay đổi theo mùa.'
    UNION ALL SELECT 10, 'cong-vien-ho', 'SPOT', JSON_ARRAY('Công viên', 'Hồ', 'Vườn hoa'),
           JSON_ARRAY('cong-vien', 'ho', 'vuon-hoa'), NULL, NULL,
           'Không gian xanh để đi bộ, chạy bộ và chụp ảnh lúc bình minh.'
    UNION ALL SELECT 11, 'di-tich-bao-tang', 'SPOT', JSON_ARRAY('Bảo tàng', 'Đình', 'Nhà cổ'),
           JSON_ARRAY('bao-tang', 'dinh', 'nha-co'), 20000, 50000,
           'Điểm tham quan văn hóa, có thuyết minh và khu trưng bày hiện vật.'
    UNION ALL SELECT 12, 'pho-di-bo-cho-dem', 'SPOT', JSON_ARRAY('Chợ đêm', 'Phố đi bộ'),
           JSON_ARRAY('cho-dem', 'pho-di-bo'), NULL, NULL,
           'Sôi động về đêm với hàng ăn, biểu diễn đường phố và gian hàng thủ công.'
    UNION ALL SELECT 13, 'ngam-canh', 'SPOT', JSON_ARRAY('Đồi', 'Bến', 'Góc ngắm'),
           JSON_ARRAY('doi', 'ben', 'goc-ngam'), NULL, NULL,
           'Điểm ngắm cảnh đẹp nhất lúc hoàng hôn, nên đến sớm để có chỗ.'
),
base AS (
    SELECT n,
           CASE WHEN n <= 110 THEN 1 WHEN n <= 210 THEN 2 WHEN n <= 250 THEN 3 WHEN n <= 275 THEN 4 ELSE 5 END
               AS city_idx,
           1 + MOD(n * 5, 13) AS cat_idx,
           CASE WHEN MOD(n, 20) = 0 THEN 'PENDING' WHEN MOD(n, 37) = 0 THEN 'REJECTED' ELSE 'APPROVED' END
               AS status,
           @t0 - INTERVAL MOD(n * 29, 300) DAY - INTERVAL MOD(n * 13, 24) HOUR AS created_at
    FROM seq
),
named AS (
    SELECT b.*, c.name AS city_name, c.slug AS city_slug, k.slug AS cat_slug, k.price_min, k.price_max, k.blurb,
           JSON_UNQUOTE(JSON_EXTRACT(k.prefixes, CONCAT('$[', MOD(b.n * 3, JSON_LENGTH(k.prefixes)), ']')))
               AS prefix,
           JSON_UNQUOTE(JSON_EXTRACT(k.prefix_slugs, CONCAT('$[', MOD(b.n * 3, JSON_LENGTH(k.prefixes)), ']')))
               AS prefix_slug,
           CASE WHEN k.kind = 'SPOT'
                THEN ELT(1 + MOD(b.n * 7, 12), 'Bình Minh', 'Hoa Sữa', 'Thu Vàng', 'Ngàn Thông', 'Sen Hồng',
                         'Gió Chiều', 'Lộc Vừng', 'Mây Trắng', 'Phượng Vĩ', 'Trăng Non', 'Hoàng Hôn', 'Sương Mai')
                ELSE ELT(1 + MOD(b.n * 7, 24), 'Cô Lan', 'Bà Hạnh', 'Ông Mập', 'Gia Truyền', 'Phố Cổ', 'Góc Phố',
                         'Nhà Làm', 'Chú Béo', 'Bếp Nhà', 'Vị Quê', 'Hương Xưa', 'Mộc', 'An Nhiên', 'Lá', 'Mây',
                         'Hẻm Nhỏ', 'Ngõ Nhỏ', 'Ba Miền', 'Thìn', 'Tám', 'Sông Hàn', 'Cây Đa', 'Ngọc', 'Hai Anh')
           END AS suffix,
           CASE WHEN k.kind = 'SPOT'
                THEN ELT(1 + MOD(b.n * 7, 12), 'binh-minh', 'hoa-sua', 'thu-vang', 'ngan-thong', 'sen-hong',
                         'gio-chieu', 'loc-vung', 'may-trang', 'phuong-vi', 'trang-non', 'hoang-hon', 'suong-mai')
                ELSE ELT(1 + MOD(b.n * 7, 24), 'co-lan', 'ba-hanh', 'ong-map', 'gia-truyen', 'pho-co', 'goc-pho',
                         'nha-lam', 'chu-beo', 'bep-nha', 'vi-que', 'huong-xua', 'moc', 'an-nhien', 'la', 'may',
                         'hem-nho', 'ngo-nho', 'ba-mien', 'thin', 'tam', 'song-han', 'cay-da', 'ngoc', 'hai-anh')
           END AS suffix_slug,
           JSON_UNQUOTE(JSON_EXTRACT(c.streets, CONCAT('$[', MOD(b.n * 11, JSON_LENGTH(c.streets)), ']')))
               AS street,
           ROUND(c.lat + (MOD(b.n * 7919, 1000) / 1000 - 0.5) * c.spread, 6) AS lat,
           ROUND(c.lng + (MOD(b.n * 104729, 997) / 997 - 0.5) * c.spread, 6) AS lng
    FROM base b
    JOIN city c ON c.idx = b.city_idx
    JOIN cat k ON k.idx = b.cat_idx
)
SELECT (SELECT id FROM categories WHERE slug = x.cat_slug),
       (SELECT id FROM users WHERE email = CONCAT('member', LPAD(1 + MOD(x.n * 13, 51), 2, '0'), '@localspot.test')),
       CASE WHEN MOD(x.n, 25) = 1 AND x.status = 'APPROVED'
            THEN (SELECT id FROM users WHERE email = CONCAT('owner', 1 + MOD(x.n DIV 25, 6), '@localspot.test'))
       END,
       CONCAT(x.prefix, ' ', x.suffix),
       CONCAT(x.prefix_slug, '-', x.suffix_slug, '-', x.city_slug, '-', x.n),
       CONCAT(x.prefix, ' ', x.suffix, ' — ', x.blurb),
       CONCAT(1 + MOD(x.n * 17, 180), ' ', x.street),
       x.city_name,
       ST_PointFromText(CONCAT('POINT(', x.lat, ' ', x.lng, ')'), 4326),
       CASE WHEN x.price_min IS NULL THEN NULL ELSE x.price_min + MOD(x.n, 3) * 5000 END,
       CASE WHEN x.price_max IS NULL THEN NULL ELSE x.price_max + MOD(x.n, 3) * 10000 END,
       x.status,
       CASE WHEN x.status = 'REJECTED' THEN 'Trùng với một địa điểm đã có trên hệ thống.' END,
       CASE WHEN x.status <> 'PENDING'
            THEN (SELECT id FROM users WHERE email = IF(x.city_idx = 2, 'mod2@localspot.test', 'mod1@localspot.test'))
       END,
       CASE WHEN x.status <> 'PENDING' THEN x.created_at + INTERVAL 1 DAY END,
       x.created_at,
       x.created_at
FROM named x;

-- Giờ mở cửa: một khung / ngày theo loại hình; phở-bún chia ca sáng + tối (thử nhiều khung / ngày);
-- chợ đêm mở qua đêm (close_time < open_time); bảo tàng / di tích nghỉ thứ Hai.
-- Chỉ thêm cho địa điểm demo chưa có giờ mở cửa → chạy lại không nhân đôi.
INSERT INTO opening_hours (place_id, day_of_week, open_time, close_time)
WITH RECURSIVE
d (dow) AS (SELECT 1 UNION ALL SELECT dow + 1 FROM d WHERE dow < 7),
demo AS (
    SELECT p.id, c.slug AS cat_slug
    FROM places p
    JOIN categories c ON c.id = p.category_id
    JOIN users u ON u.id = p.created_by
    WHERE u.email LIKE '%@localspot.test'
      AND NOT EXISTS (SELECT 1 FROM opening_hours oh WHERE oh.place_id = p.id)
),
shifts (cat_slug, open_time, close_time) AS (
    SELECT 'pho-bun', TIME '06:00', TIME '10:30'
    UNION ALL SELECT 'pho-bun', TIME '17:00', TIME '21:30'
    UNION ALL SELECT 'com', TIME '10:00', TIME '21:00'
    UNION ALL SELECT 'lau-nuong', TIME '16:00', TIME '23:00'
    UNION ALL SELECT 'hai-san', TIME '11:00', TIME '22:30'
    UNION ALL SELECT 'mon-chay', TIME '07:00', TIME '21:00'
    UNION ALL SELECT 'an-vat', TIME '14:00', TIME '23:30'
    UNION ALL SELECT 'ca-phe', TIME '07:00', TIME '23:00'
    UNION ALL SELECT 'tra-tra-sua', TIME '09:00', TIME '22:30'
    UNION ALL SELECT 'kem-trang-mieng', TIME '10:00', TIME '22:00'
    UNION ALL SELECT 'cong-vien-ho', TIME '05:00', TIME '22:00'
    UNION ALL SELECT 'di-tich-bao-tang', TIME '08:00', TIME '17:00'
    UNION ALL SELECT 'pho-di-bo-cho-dem', TIME '18:00', TIME '01:00'
    UNION ALL SELECT 'ngam-canh', TIME '05:30', TIME '19:00'
)
SELECT demo.id, d.dow, s.open_time, s.close_time
FROM demo
JOIN shifts s ON s.cat_slug = demo.cat_slug
CROSS JOIN d
WHERE NOT (demo.cat_slug = 'di-tich-bao-tang' AND d.dow = 1)
ORDER BY demo.id, d.dow, s.open_time;

-- Tiện ích: ~40% tổ hợp theo CRC32(slug địa điểm + slug tiện ích) — tất định theo slug, không theo id.
-- "Vé vào cửa" chỉ cho bảo tàng / di tích; "Giao hàng", "Mang đi" không cho điểm check-in.
INSERT IGNORE INTO place_amenity (place_id, amenity_id)
SELECT p.id, a.id
FROM places p
JOIN categories c ON c.id = p.category_id
JOIN categories root ON root.id = c.parent_id
JOIN users u ON u.id = p.created_by
JOIN amenities a
WHERE u.email LIKE '%@localspot.test'
  AND (
        (a.slug = 've-vao-cua' AND c.slug = 'di-tich-bao-tang')
     OR (a.slug <> 've-vao-cua'
         AND NOT (root.slug = 'diem-check-in' AND a.slug IN ('giao-hang', 'mang-di', 'thanh-toan-the', 'wifi'))
         AND MOD(CRC32(CONCAT(p.slug, '|', a.slug)), 10) < 4)
  );
