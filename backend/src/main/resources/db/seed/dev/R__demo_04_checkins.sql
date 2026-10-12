-- =====================================================================================
-- DỮ LIỆU DEMO — CHỈ profile dev. Quy tắc như R__demo_01_users.sql: idempotent, không xóa dữ liệu, sinh tất định.
--
-- Check-in (UC16) cho ~40% review demo đang hiện: đúng ngày đến (visited_at ≤ ngày viết) → review có nhãn "đã check-in"
-- (sitemap P04, chốt 2026-10-12), cách địa điểm 20–180 m về phía bắc, sai số GPS 5–60 m — luôn qua được các luật U5.
-- Người viết review demo đã xác thực email và không phải chủ địa điểm (R__demo_03) → cũng qua cổng check-in.
-- 1° vĩ độ ≈ 111 195 m trên mặt cầu bán kính 6 370 986 m của ST_Distance_Sphere.
-- Khóa idempotent: UNIQUE(user_id, place_id, checkin_date). places.checkin_count đếm lại từ đầu (như bộ đếm vote).
-- =====================================================================================

INSERT IGNORE INTO check_ins (user_id, place_id, location, accuracy_m, distance_m, checkin_date, created_at)
WITH picked AS (
    SELECT r.user_id, r.place_id, r.visited_at, p.location,
           MOD(r.place_id * 7919 + r.user_id * 31, 10007) AS h
    FROM reviews r
    JOIN users u ON u.id = r.user_id
    JOIN places p ON p.id = r.place_id
    WHERE u.email LIKE '%@localspot.test'
      AND r.status = 'PUBLISHED' AND r.deleted_at IS NULL AND p.status = 'APPROVED'
      AND MOD(MOD(r.place_id * 7919 + r.user_id * 31, 10007), 10) < 4
),
placed AS (
    SELECT k.*, 20 + MOD(k.h * 13, 161) AS meters
    FROM picked k
)
SELECT x.user_id,
       x.place_id,
       ST_PointFromText(CONCAT('POINT(', ST_Latitude(x.location) + x.meters / 111195, ' ',
                               ST_Longitude(x.location), ')'), 4326),
       5 + MOD(x.h, 56),
       x.meters,
       x.visited_at,
       -- 10:00–18:59 giờ Việt Nam của ngày đến, lưu UTC
       TIMESTAMP(x.visited_at) + INTERVAL 3 + MOD(x.h, 9) HOUR + INTERVAL MOD(x.h * 7, 60) MINUTE
FROM placed x;

UPDATE places p
JOIN users creator ON creator.id = p.created_by
SET p.checkin_count = (SELECT COUNT(*) FROM check_ins c WHERE c.place_id = p.id)
WHERE creator.email LIKE '%@localspot.test';
