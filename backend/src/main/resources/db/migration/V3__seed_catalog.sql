-- =====================================================================================
-- Danh mục & tiện ích khởi đầu — mọi môi trường đều cần: đề xuất địa điểm (UC11) bắt buộc chọn
-- danh mục, nên hệ thống mới triển khai không dùng được nếu bảng rỗng. Quản trị viên sửa tiếp
-- qua UC32. `icon` để NULL: bộ icon chính thức chưa có (docs/design-system/README.md).
-- Huy hiệu (badges) CHƯA seed: bộ huy hiệu và công thức điểm đóng góp còn chờ chốt (O3).
-- =====================================================================================

INSERT INTO categories (parent_id, name, slug, sort_order) VALUES
    (NULL, 'Quán ăn',            'quan-an',        1),
    (NULL, 'Cà phê & đồ uống',   'ca-phe-do-uong', 2),
    (NULL, 'Điểm check-in',      'diem-check-in',  3);

INSERT INTO categories (parent_id, name, slug, sort_order)
SELECT c.id, v.name, v.slug, v.sort_order
FROM categories c
JOIN (
    SELECT 'quan-an' AS parent_slug, 'Phở & bún' AS name, 'pho-bun' AS slug, 1 AS sort_order
    UNION ALL SELECT 'quan-an',        'Cơm',                     'com',                2
    UNION ALL SELECT 'quan-an',        'Lẩu & nướng',             'lau-nuong',          3
    UNION ALL SELECT 'quan-an',        'Hải sản',                 'hai-san',            4
    UNION ALL SELECT 'quan-an',        'Món chay',                'mon-chay',           5
    UNION ALL SELECT 'quan-an',        'Ăn vặt',                  'an-vat',             6
    UNION ALL SELECT 'ca-phe-do-uong', 'Cà phê',                  'ca-phe',             1
    UNION ALL SELECT 'ca-phe-do-uong', 'Trà & trà sữa',           'tra-tra-sua',        2
    UNION ALL SELECT 'ca-phe-do-uong', 'Kem & tráng miệng',       'kem-trang-mieng',    3
    UNION ALL SELECT 'diem-check-in',  'Công viên & hồ',          'cong-vien-ho',       1
    UNION ALL SELECT 'diem-check-in',  'Di tích & bảo tàng',      'di-tich-bao-tang',   2
    UNION ALL SELECT 'diem-check-in',  'Phố đi bộ & chợ đêm',     'pho-di-bo-cho-dem',  3
    UNION ALL SELECT 'diem-check-in',  'Ngắm cảnh',               'ngam-canh',          4
) v ON v.parent_slug = c.slug;

INSERT INTO amenities (name, slug) VALUES
    ('Wi-Fi miễn phí',      'wifi'),
    ('Máy lạnh',            'may-lanh'),
    ('Chỗ đậu xe máy',      'do-xe-may'),
    ('Chỗ đậu ô tô',        'do-o-to'),
    ('Nhà vệ sinh',         'nha-ve-sinh'),
    ('Thanh toán thẻ',      'thanh-toan-the'),
    ('Chuyển khoản / QR',   'chuyen-khoan-qr'),
    ('Mang đi',             'mang-di'),
    ('Giao hàng',           'giao-hang'),
    ('Chỗ ngồi ngoài trời', 'ngoai-troi'),
    ('Phù hợp trẻ em',      'tre-em'),
    ('Cho phép thú cưng',   'thu-cung'),
    ('Lối đi xe lăn',       'xe-lan'),
    ('Vé vào cửa',          've-vao-cua');
