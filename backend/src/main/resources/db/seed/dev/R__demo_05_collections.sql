-- =====================================================================================
-- DỮ LIỆU DEMO — CHỈ profile dev. Quy tắc như R__demo_01_users.sql: idempotent, không xóa dữ liệu, sinh tất định.
--
-- Bộ sưu tập (UC18): 1–3 bộ cho mỗi thành viên đã xác thực có số thứ tự lẻ, đặt tên theo chủ đề + thành phố; ~nửa
-- công khai (hiện ở tab bộ sưu tập trên trang cá nhân và mở được qua /c/<shareSlug>). Mỗi bộ 3–12 địa điểm đã duyệt cùng
-- thành phố, thêm lần lượt theo thời gian. share_slug theo cùng dạng CollectionService sinh (tên + 6 ký tự) nhưng hậu
-- tố tính tất định từ khóa của bộ thay vì ngẫu nhiên.
-- Khóa idempotent: (user_id, name) qua NOT EXISTS (bảng không có UNIQUE tự nhiên); PK(collection_id, place_id).
-- =====================================================================================

SET @t0 = TIMESTAMP('2026-09-30 00:00:00');

INSERT INTO collections (user_id, name, description, is_public, share_slug, created_at, updated_at)
WITH RECURSIVE
k (j) AS (SELECT 0 UNION ALL SELECT j + 1 FROM k WHERE j < 2),
owner AS (
    SELECT u.id, u.created_at, CAST(REGEXP_SUBSTR(u.email, '[0-9]+') AS UNSIGNED) AS n
    FROM users u
    WHERE u.email REGEXP '^member[0-9]{2}@localspot\\.test$' AND u.email_verified_at IS NOT NULL
),
theme (t, title, slug, blurb) AS (
    SELECT 0, 'Quán ăn nên thử ở', 'quan-an-nen-thu-o', 'Những quán mình đã ăn và muốn quay lại.'
    UNION ALL SELECT 1, 'Cà phê làm việc ở', 'ca-phe-lam-viec-o', 'Chỗ yên tĩnh, wifi ổn, ngồi lâu được.'
    UNION ALL SELECT 2, 'Cuối tuần dạo chơi ở', 'cuoi-tuan-dao-choi-o', 'Điểm đi dạo, chụp ảnh khi có khách tới chơi.'
),
city (c, name, slug) AS (
    SELECT 0, 'Hà Nội', 'ha-noi' UNION ALL SELECT 1, 'TP. Hồ Chí Minh', 'tp-hcm' UNION ALL SELECT 2, 'Đà Nẵng', 'da-nang'
),
planned AS (
    SELECT o.id AS user_id, o.created_at, o.n, k.j,
           MOD(o.n + k.j, 3) AS t,
           MOD(o.n * 7 + k.j, 3) AS c,
           MOD(o.n * 3 + k.j * 5, 2) = 0 AS is_public
    FROM owner o
    JOIN k ON k.j <= MOD(o.n, 3)
    WHERE MOD(o.n, 2) = 1
)
SELECT p.user_id,
       CONCAT(th.title, ' ', ci.name),
       th.blurb,
       p.is_public,
       CASE WHEN p.is_public
            THEN CONCAT(th.slug, '-', ci.slug, '-',
                        LOWER(LPAD(CONV(MOD(p.n * 7919 + p.j * 104729, 2176782335), 10, 36), 6, '0')))
       END,
       GREATEST(p.created_at, @t0 - INTERVAL 60 DAY) + INTERVAL p.j DAY,
       GREATEST(p.created_at, @t0 - INTERVAL 60 DAY) + INTERVAL p.j DAY
FROM planned p
JOIN theme th ON th.t = p.t
JOIN city ci ON ci.c = p.c
WHERE NOT EXISTS (SELECT 1 FROM collections x WHERE x.user_id = p.user_id AND x.name = CONCAT(th.title, ' ', ci.name));

-- Địa điểm: cùng thành phố, đúng nhóm danh mục của chủ đề; số lượng 3–12 theo id bộ
INSERT IGNORE INTO collection_place (collection_id, place_id, note, added_at)
WITH demo AS (
    SELECT c.id, c.created_at,
           CASE WHEN c.name LIKE 'Quán ăn%' THEN 'quan-an' WHEN c.name LIKE 'Cà phê%' THEN 'ca-phe-do-uong'
                ELSE 'diem-check-in' END AS root_slug,
           TRIM(SUBSTRING_INDEX(c.name, ' ở ', -1)) AS city,
           3 + MOD(c.id * 7, 10) AS wanted
    FROM collections c
    JOIN users u ON u.id = c.user_id
    WHERE u.email LIKE '%@localspot.test'
),
ranked AS (
    SELECT d.id AS collection_id, d.created_at, p.id AS place_id, d.wanted,
           ROW_NUMBER() OVER (PARTITION BY d.id ORDER BY MOD(p.id * 7919 + d.id * 31, 997), p.id) AS rn
    FROM demo d
    JOIN places p ON p.city = d.city AND p.status = 'APPROVED'
    JOIN categories cat ON cat.id = p.category_id
    JOIN categories root ON root.id = COALESCE(cat.parent_id, cat.id) AND root.slug = d.root_slug
)
SELECT r.collection_id,
       r.place_id,
       CASE WHEN MOD(r.place_id + r.collection_id, 4) = 0
            THEN ELT(1 + MOD(r.place_id, 4), 'Nên đi buổi sáng.', 'Gọi món đặc trưng.', 'Cuối tuần hơi đông.',
                     'Có chỗ để xe.')
       END,
       r.created_at + INTERVAL r.rn HOUR
FROM ranked r
WHERE r.rn <= r.wanted;
