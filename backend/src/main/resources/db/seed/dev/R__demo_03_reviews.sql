-- =====================================================================================
-- DỮ LIỆU DEMO — CHỈ profile dev. Quy tắc như R__demo_01_users.sql: idempotent, không xóa dữ liệu, sinh tất định bằng
-- công thức số học (không RAND). Chạy sau 01 (tài khoản) và 02 (địa điểm) — Flyway chạy R__ theo thứ tự tên.
--
-- ~2000 review trên các địa điểm demo đã duyệt + vote hữu ích + bình luận 2 cấp + vi phạm (đã hết hạn) của review bị
-- từ chối. Người viết / vote / bình luận: 46 thành viên đã xác thực email + 6 chủ quán (chủ không đánh giá quán mình).
--
-- KHÔNG chép công thức lõi ra SQL (chốt 2026-10-12):
--   - Ba cột điểm của địa điểm giữ nguyên ở đây; DemoDataBootstrap (profile dev) gọi RatingService.recalculateAll()
--     khi khởi động → công thức Bayesian chỉ có ở RatingCalculator.
--   - Trust không được tính ở đây. Vòng lặp "chưa ai đạt 30 vì chưa có vote hợp lệ, vote chỉ hợp lệ khi người vote
--     đạt 30" được phá bằng NHÓM KỲ CỰU: thành viên tạo tài khoản ≥ 250 ngày trước mốc t0 (~1/3 thành viên) —
--     coi như người dùng đầu tiên của nền tảng. Chỉ vote của nhóm này có counted = TRUE; vote của người khác luôn
--     FALSE kể cả khi người đó về sau đạt 30 → seed chỉ có thể tính THIẾU trust, không bao giờ tính dư.
--   - Bộ đếm (reviews.helpful_count = mọi vote, users.helpful_votes_count = vote counted nhận được, kể cả trên review
--     đã xóa — như ReviewVoteService) được ĐẾM LẠI TỪ ĐẦU từ review_votes, không cộng dồn → chạy lại vẫn đúng.
--   - Ảnh review cần object trong kho → DemoDataBootstrap tải ảnh qua pipeline thật (photo.process), không ở đây.
--
-- Phân bố:
--   Độ phổ biến theo địa điểm: ~10% chưa có review (hiện "Chưa có đánh giá"), ~30% 1–3, ~35% 4–9, ~15% 10–20,
--   ~10% 25–40 → có cả cặp "ít review 5★" và "nhiều review ~4.5★" để minh họa Bayesian (plan §5).
--   Chất lượng mỗi địa điểm một mức (2–5), sao từng review dao động −2…+1 quanh mức đó.
--   NGƯỜI MỚI (thành viên tạo tài khoản < 60 ngày trước t0, 8 người) chỉ viết vài review và mọi review đều PENDING —
--   đúng theo §5.1: tài khoản mới chưa có vote hợp lệ tối đa 10 + 8 = 18 < 30. Review chờ không nhận vote → họ vẫn
--   dưới ngưỡng, đăng nhập bằng tài khoản này để demo nhánh "vào hàng chờ" của FR-23.
--   Trạng thái: ~7% PENDING (người mới + ~5% ngẫu nhiên; tạo trong 10 ngày gần t0 — hàng chờ UC28), ~3% REJECTED (chỉ người ngoài nhóm kỳ cựu,
--   cũ hơn 45 ngày → vi phạm đã hết hạn sau 30 ngày, không kéo trust hiện tại), còn lại PUBLISHED. PUBLISHED của nhóm
--   kỳ cựu đăng thẳng (moderated_by NULL); của người khác ghi là đã qua kiểm duyệt viên duyệt.
--   IP: 10.20.<người viết>.x — dải riêng, mỗi người một /24, không kích hoạt luật cảnh báo IP.
-- Khóa idempotent: UNIQUE(place_id, user_id) cho review, PK(review_id, user_id) cho vote; bình luận / vi phạm không có
-- khóa tự nhiên → NOT EXISTS theo (review, người, gốc) / (loại nguồn, id nguồn).
-- =====================================================================================

SET @t0 = TIMESTAMP('2026-09-30 00:00:00');

INSERT IGNORE INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                            reject_reason, moderated_by, moderated_at, created_at, updated_at)
WITH RECURSIVE
seq (i) AS (
    SELECT 0 UNION ALL SELECT i + 1 FROM seq WHERE i < 51
),
reviewer AS (
    SELECT u.id, u.created_at,
           ROW_NUMBER() OVER (ORDER BY u.email) - 1 AS ri,
           (u.email LIKE 'member%' AND u.created_at <= @t0 - INTERVAL 250 DAY) AS veteran,
           (u.email LIKE 'member%' AND u.created_at > @t0 - INTERVAL 60 DAY) AS newcomer
    FROM users u
    WHERE u.email REGEXP '^(member[0-9]+|owner[1-6])@localspot\\.test$'
      AND u.email_verified_at IS NOT NULL
),
place AS (
    SELECT p.id, p.owner_id, p.moderated_at,
           CAST(SUBSTRING_INDEX(p.slug, '-', -1) AS UNSIGNED) AS n,
           CASE root.slug WHEN 'quan-an' THEN 1 WHEN 'ca-phe-do-uong' THEN 2 ELSE 3 END AS kind
    FROM places p
    JOIN users creator ON creator.id = p.created_by
    JOIN categories c ON c.id = p.category_id
    JOIN categories root ON root.id = COALESCE(c.parent_id, c.id)
    WHERE creator.email LIKE '%@localspot.test' AND p.status = 'APPROVED'
),
sized AS (
    SELECT pl.*,
           CASE
               WHEN MOD(pl.n * 7, 20) < 2 THEN 0
               WHEN MOD(pl.n * 7, 20) < 8 THEN 1 + MOD(pl.n, 3)
               WHEN MOD(pl.n * 7, 20) < 15 THEN 4 + MOD(pl.n, 6)
               WHEN MOD(pl.n * 7, 20) < 18 THEN 10 + MOD(pl.n, 11)
               ELSE 25 + MOD(pl.n, 16)
           END AS review_total,
           ELT(1 + MOD(pl.n * 3, 10), 5, 4, 4, 3, 4, 5, 4, 2, 4, 3) AS tier
    FROM place pl
),
pair AS (
    -- Bước 7 nguyên tố cùng nhau với 52 người → i < 52 luôn ra người khác nhau trên cùng địa điểm
    SELECT s.id AS place_id, s.n, s.kind, s.tier, s.moderated_at AS place_approved_at,
           r.id AS user_id, r.ri, r.veteran, r.newcomer, r.created_at AS user_created_at, q.i,
           MOD(s.n * 7919 + q.i * 104729 + r.ri * 31, 10007) AS h
    FROM sized s
    JOIN seq q ON q.i < s.review_total
    JOIN reviewer r ON r.ri = MOD(s.n * 17 + q.i * 7, (SELECT COUNT(*) FROM reviewer))
    WHERE (s.owner_id IS NULL OR s.owner_id <> r.id)
      AND (NOT r.newcomer OR MOD(s.n + q.i, 8) = 0)
),
timed AS (
    SELECT p.*,
           GREATEST(p.user_created_at, p.place_approved_at) + INTERVAL 1 HOUR AS window_start
    FROM pair p
),
spread AS (
    SELECT t.*,
           t.window_start + INTERVAL FLOOR(TIMESTAMPDIFF(MINUTE, t.window_start, @t0) * MOD(t.h * 13, 1000) / 1000)
               MINUTE AS spread_at,
           LEAST(5, GREATEST(1, t.tier + ELT(1 + MOD(t.h, 10), -2, -1, -1, 0, 0, 0, 0, 0, 1, 1))) AS rating
    FROM timed t
    WHERE t.window_start < @t0 - INTERVAL 1 DAY
),
statused AS (
    SELECT sp.*,
           CASE
               WHEN sp.newcomer OR MOD(sp.h, 100) < 5 THEN 'PENDING'
               WHEN MOD(sp.h, 100) < 8 AND NOT sp.veteran AND sp.spread_at < @t0 - INTERVAL 45 DAY THEN 'REJECTED'
               ELSE 'PUBLISHED'
           END AS status
    FROM spread sp
),
final AS (
    SELECT st.*,
           CASE WHEN st.status = 'PENDING'
                THEN GREATEST(st.window_start,
                              @t0 - INTERVAL MOD(st.h, 10) DAY - INTERVAL MOD(st.h * 7, 23) HOUR)
                ELSE st.spread_at
           END AS created_at,
           CASE WHEN st.rating >= 4 THEN 1 WHEN st.rating = 3 THEN 2 ELSE 3 END AS mood
    FROM statused st
)
SELECT f.place_id,
       f.user_id,
       f.rating,
       CONCAT_WS(' ',
           CASE f.mood
               WHEN 1 THEN ELT(1 + MOD(f.h, 5), 'Rất hài lòng với lần ghé này.', 'Quay lại lần thứ ba vẫn thấy ổn định.',
                               'Đi cùng bạn bè, ai cũng khen.', 'Đáng đồng tiền bát gạo.',
                               'Một trong những chỗ mình thích nhất khu này.')
               WHEN 2 THEN ELT(1 + MOD(f.h, 4), 'Tạm ổn, chưa có gì quá đặc biệt.', 'Được cái này thì mất cái kia.',
                               'Không tệ nhưng cũng không quá ấn tượng.', 'Ghé thử một lần cho biết.')
               ELSE ELT(1 + MOD(f.h, 4), 'Khá thất vọng so với kỳ vọng.', 'Lần này trải nghiệm không tốt.',
                        'Không giống như những gì được giới thiệu.', 'Đọc review khen nhiều nên ghé thử, nhưng chưa ưng.')
           END,
           CASE f.kind
               WHEN 1 THEN ELT(1 + MOD(f.h * 3, 5), 'Gọi món đặc trưng của quán và một phần ăn kèm.',
                               'Đi vào buổi trưa, quán khá đông khách.', 'Đi nhóm bốn người, gọi khá nhiều món.',
                               'Ngồi trong nhà, có quạt và điều hòa.', 'Ghé ăn tối sau giờ làm.')
               WHEN 2 THEN ELT(1 + MOD(f.h * 3, 5), 'Gọi một ly đặc trưng và ngồi khoảng hai tiếng.',
                               'Ghé vào buổi chiều cuối tuần.', 'Quán có hai tầng, tầng trên yên tĩnh hơn.',
                               'Mang laptop theo để làm việc.', 'Hẹn bạn cũ ngồi nói chuyện.')
               ELSE ELT(1 + MOD(f.h * 3, 5), 'Đi dạo vào sáng sớm cuối tuần.', 'Ghé lúc hoàng hôn để chụp ảnh.',
                        'Đi cùng gia đình có trẻ nhỏ.', 'Gửi xe ngay cổng rồi đi bộ vào khoảng năm phút.',
                        'Dành khoảng một tiếng để tham quan.')
           END,
           CASE f.kind * 10 + f.mood
               WHEN 11 THEN ELT(1 + MOD(f.h, 2), 'Món ăn đậm đà, phần ăn đầy đặn, lên món nhanh.',
                                'Nguyên liệu tươi, giá hợp lý so với chất lượng.')
               WHEN 12 THEN ELT(1 + MOD(f.h, 2), 'Vị ổn nhưng hơi mặn so với khẩu vị của mình.',
                                'Phần ăn hơi ít so với giá.')
               WHEN 13 THEN ELT(1 + MOD(f.h, 2), 'Món nguội, phải chờ gần nửa tiếng mới có.',
                                'Bàn chưa được dọn sạch, nhân viên khá thờ ơ.')
               WHEN 21 THEN ELT(1 + MOD(f.h, 2), 'Đồ uống pha đúng vị, không quá ngọt.',
                                'Không gian yên tĩnh, nhân viên thân thiện.')
               WHEN 22 THEN ELT(1 + MOD(f.h, 2), 'Đồ uống bình thường, không gian hơi ồn.',
                                'Chỗ ngồi đẹp nhưng wifi chập chờn.')
               WHEN 23 THEN ELT(1 + MOD(f.h, 2), 'Đồ uống nhạt, đá nhiều hơn nước.',
                                'Quá đông và ồn, gần như không có chỗ ngồi.')
               WHEN 31 THEN ELT(1 + MOD(f.h, 2), 'Cảnh đẹp, lối đi sạch sẽ, có nhiều chỗ nghỉ chân.',
                                'Nhiều góc chụp ảnh đẹp, gửi xe giá rõ ràng.')
               WHEN 32 THEN ELT(1 + MOD(f.h, 2), 'Cảnh đẹp nhưng cuối tuần khá đông.',
                                'Đẹp nhưng thiếu chỗ ngồi và mái che.')
               ELSE ELT(1 + MOD(f.h, 2), 'Rác nhiều, nhà vệ sinh xuống cấp.',
                        'Gửi xe bị thu giá cao hơn niêm yết.')
           END,
           CASE f.mood
               WHEN 1 THEN ELT(1 + MOD(f.h * 7, 3), 'Chắc chắn sẽ quay lại.', 'Giới thiệu cho mọi người.',
                               'Lần sau sẽ dẫn gia đình tới.')
               WHEN 2 THEN ELT(1 + MOD(f.h * 7, 2), 'Có dịp sẽ thử lại.', 'Hy vọng lần sau tốt hơn.')
               ELSE ELT(1 + MOD(f.h * 7, 2), 'Mong chỗ này cải thiện.', 'Chắc sẽ không quay lại.')
           END),
       DATE(f.created_at) - INTERVAL MOD(f.h, 7) DAY,
       f.status,
       CONCAT('10.20.', f.ri, '.', 1 + MOD(f.h, 250)),
       CONCAT('10.20.', f.ri, '.0/24'),
       CASE WHEN f.status = 'REJECTED'
            THEN ELT(1 + MOD(f.h, 3), 'Nội dung mang tính quảng cáo cho địa điểm khác.',
                     'Nội dung không liên quan đến trải nghiệm tại địa điểm.',
                     'Ngôn từ xúc phạm người khác.')
       END,
       CASE WHEN f.status = 'REJECTED' OR (f.status = 'PUBLISHED' AND NOT f.veteran)
            THEN (SELECT id FROM users WHERE email = IF(MOD(f.n, 2) = 0, 'mod1@localspot.test', 'mod2@localspot.test'))
       END,
       CASE WHEN f.status = 'REJECTED' OR (f.status = 'PUBLISHED' AND NOT f.veteran)
            THEN f.created_at + INTERVAL 2 + MOD(f.h, 20) HOUR
       END,
       f.created_at,
       f.created_at
FROM final f;

-- Vi phạm REVIEW_REJECTED cho review bị từ chối (requirements §5.1: 25 điểm, hết hạn sau 30 ngày) — mọi review
-- REJECTED demo đều cũ hơn 45 ngày nên đã hết hạn, chỉ còn là lịch sử.
INSERT INTO user_violations (user_id, source_type, source_id, points, expires_at, created_by, created_at)
SELECT r.user_id, 'REVIEW_REJECTED', r.id, 25, r.moderated_at + INTERVAL 30 DAY, r.moderated_by, r.moderated_at
FROM reviews r
JOIN users u ON u.id = r.user_id
WHERE u.email LIKE '%@localspot.test'
  AND r.status = 'REJECTED'
  AND NOT EXISTS (SELECT 1 FROM user_violations v WHERE v.source_type = 'REVIEW_REJECTED' AND v.source_id = r.id);

-- Vote hữu ích: chỉ trên review đang hiện. Đa số 0–2 vote, một phần 3–6, ~20% 8–20 (sắp xếp HELPFUL có khác biệt).
-- counted = người vote thuộc nhóm kỳ cựu (xem đầu file). Bước 11 nguyên tố cùng nhau với 52 → người vote khác nhau.
INSERT IGNORE INTO review_votes (review_id, user_id, counted, created_at)
WITH RECURSIVE
seq (j) AS (
    SELECT 0 UNION ALL SELECT j + 1 FROM seq WHERE j < 19
),
voter AS (
    SELECT u.id, u.created_at,
           ROW_NUMBER() OVER (ORDER BY u.email) - 1 AS vi,
           (u.email LIKE 'member%' AND u.created_at <= @t0 - INTERVAL 250 DAY) AS veteran
    FROM users u
    WHERE u.email REGEXP '^(member[0-9]+|owner[1-6])@localspot\\.test$'
      AND u.email_verified_at IS NOT NULL
),
target AS (
    SELECT r.id, r.user_id, r.created_at,
           MOD(r.place_id * 7919 + r.user_id * 104729, 10007) AS h
    FROM reviews r
    JOIN users author ON author.id = r.user_id
    JOIN places p ON p.id = r.place_id
    WHERE author.email LIKE '%@localspot.test' AND r.status = 'PUBLISHED' AND p.status = 'APPROVED'
      AND r.deleted_at IS NULL
),
sized AS (
    SELECT t.*,
           CASE
               WHEN MOD(t.h, 10) < 5 THEN MOD(t.h, 3)
               WHEN MOD(t.h, 10) < 8 THEN 3 + MOD(t.h, 4)
               ELSE 8 + MOD(t.h, 13)
           END AS vote_total
    FROM target t
)
SELECT s.id,
       v.id,
       v.veteran,
       LEAST(@t0, GREATEST(s.created_at, v.created_at) + INTERVAL 1 + MOD(s.h * 3 + q.j * 17, 240) HOUR)
FROM sized s
JOIN seq q ON q.j < s.vote_total
JOIN voter v ON v.vi = MOD(s.h + q.j * 11, (SELECT COUNT(*) FROM voter))
WHERE v.id <> s.user_id;

-- Bộ đếm: đếm lại từ đầu (không cộng dồn) — đúng bất kể chạy lần mấy hay đã có vote thật qua API
UPDATE reviews r
JOIN users author ON author.id = r.user_id
SET r.helpful_count = (SELECT COUNT(*) FROM review_votes v WHERE v.review_id = r.id)
WHERE author.email LIKE '%@localspot.test';

UPDATE users u
SET u.helpful_votes_count = (
    SELECT COUNT(*) FROM review_votes v JOIN reviews r ON r.id = v.review_id
    WHERE r.user_id = u.id AND v.counted)
WHERE u.email LIKE '%@localspot.test';

-- Bình luận gốc: ~1/4 review đang hiện, 1–3 người (khác tác giả) mỗi review
INSERT INTO comments (review_id, user_id, parent_id, content, status, created_at, updated_at)
WITH RECURSIVE
seq (j) AS (
    SELECT 0 UNION ALL SELECT j + 1 FROM seq WHERE j < 2
),
commenter AS (
    SELECT u.id, u.created_at, ROW_NUMBER() OVER (ORDER BY u.email) - 1 AS ci
    FROM users u
    WHERE u.email REGEXP '^(member[0-9]+|owner[1-6])@localspot\\.test$'
      AND u.email_verified_at IS NOT NULL
),
target AS (
    SELECT r.id, r.user_id, r.rating, r.created_at,
           MOD(r.place_id * 104729 + r.user_id * 7919, 10007) AS h
    FROM reviews r
    JOIN users author ON author.id = r.user_id
    JOIN places p ON p.id = r.place_id
    WHERE author.email LIKE '%@localspot.test' AND r.status = 'PUBLISHED' AND p.status = 'APPROVED'
      AND r.deleted_at IS NULL
      AND MOD(r.place_id * 104729 + r.user_id * 7919, 10007) MOD 4 = 0
),
picked AS (
    SELECT t.*, c.id AS commenter_id, c.created_at AS commenter_created_at, q.j,
           GREATEST(t.created_at, c.created_at) + INTERVAL 3 + MOD(t.h + q.j * 29, 72) HOUR AS commented_at
    FROM target t
    JOIN seq q ON q.j <= MOD(t.h, 3)
    JOIN commenter c ON c.ci = MOD(t.h * 3 + q.j * 5 + 1, (SELECT COUNT(*) FROM commenter))
    WHERE c.id <> t.user_id
)
SELECT p.id,
       p.commenter_id,
       NULL,
       CASE WHEN p.rating >= 4
            THEN ELT(1 + MOD(p.h + p.j, 5), 'Cảm ơn bạn, cuối tuần này mình sẽ ghé thử.',
                     'Quán có chỗ để xe máy không bạn?', 'Đồng ý, mình cũng hay ghé chỗ này.',
                     'Bạn đi giờ nào mà vắng vậy?', 'Review chi tiết quá, lưu lại để đi.')
            ELSE ELT(1 + MOD(p.h + p.j, 4), 'Mình đi hôm trước thì thấy ổn, chắc hôm đó quán đông.',
                     'Cảm ơn bạn đã chia sẻ, mình sẽ cân nhắc.', 'Giá bây giờ còn như bạn viết không?',
                     'Mình cũng gặp tình trạng tương tự.')
       END,
       'VISIBLE',
       LEAST(@t0, p.commented_at),
       LEAST(@t0, p.commented_at)
FROM picked p
WHERE NOT EXISTS (SELECT 1 FROM comments c
                  WHERE c.review_id = p.id AND c.user_id = p.commenter_id AND c.parent_id IS NULL);

-- Trả lời (cấp 2): tác giả review trả lời khoảng một nửa số bình luận gốc của người khác trên review mình
INSERT INTO comments (review_id, user_id, parent_id, content, status, created_at, updated_at)
SELECT root.review_id,
       r.user_id,
       root.id,
       ELT(1 + MOD(root.id, 4), 'Có bạn nhé, ngay trước cửa luôn.', 'Mình đi buổi sáng, khoảng 9 giờ.',
           'Cảm ơn bạn đã đọc!', 'Lần gần nhất mình đi thì vẫn vậy.'),
       'VISIBLE',
       LEAST(@t0, root.created_at + INTERVAL 2 HOUR),
       LEAST(@t0, root.created_at + INTERVAL 2 HOUR)
FROM comments root
JOIN reviews r ON r.id = root.review_id
JOIN users author ON author.id = r.user_id
WHERE author.email LIKE '%@localspot.test'
  AND root.parent_id IS NULL AND root.deleted_at IS NULL AND root.user_id <> r.user_id
  AND MOD(root.review_id + root.user_id, 2) = 0
  AND NOT EXISTS (SELECT 1 FROM comments c WHERE c.parent_id = root.id AND c.user_id = r.user_id);
