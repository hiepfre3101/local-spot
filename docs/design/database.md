# Thiết kế cơ sở dữ liệu — LocalSpot

> Trạng thái: **Đã chốt** (2026-09-28) — D1–D7 đã được supervisor xác nhận; là đầu vào cho Flyway `V1__init.sql`.
> Nguồn: [plan-v1.md](../../plan-v1.md) §5, [requirements.md](../requirements.md) §5–5.1, [use-cases.md](../analysis/use-cases.md).
> ERD: [erd-*.puml](../diagrams/) (3 sơ đồ theo miền). Tài liệu này là "bảng mô tả chi tiết từng bảng CSDL" cho Chương 3 và Phụ lục.

---

## 1. Quy ước chung

| Quy ước | Giá trị | Lý do |
|---|---|---|
| DBMS | MySQL 8.4, InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci` | Tiếng Việt + emoji; collation `ai_ci` so sánh không phân biệt dấu/hoa thường cho các truy vấn `LIKE` dự phòng (U9) |
| Khóa chính | `id BIGINT UNSIGNED AUTO_INCREMENT` | Đơn giản, index nhỏ; bảng nối dùng khóa tổ hợp |
| Tên | `snake_case`, bảng số nhiều | Khớp plan §5 |
| Thời gian | `DATETIME(6)` lưu **UTC**; hiển thị theo `Asia/Ho_Chi_Minh` | Tránh lệch múi giờ giữa app / DB / container |
| Audit | `created_at`, `updated_at` trên bảng nghiệp vụ | JPA Auditing |
| Xóa mềm | `deleted_at DATETIME(6) NULL` trên `users`, `places`, `reviews`, `comments` | Plan §5; Hibernate `@SQLRestriction("deleted_at IS NULL")` |
| Trạng thái | `VARCHAR(20)` + `CHECK`, map `@Enumerated(STRING)` | Thêm trạng thái mới không cần `ALTER ... ENUM` |
| Khóa lạc quan | `version INT` trên `places`, `reviews`, `place_claims`, `reports` | Hai kiểm duyệt viên xử lý cùng lúc → 409 (UC28) |
| Tọa độ | `POINT SRID 4326`, thứ tự trục **(lat, lng)** theo chuẩn EPSG của MySQL 8 | Spatial index + `ST_Distance_Sphere` |

---

## 2. Danh sách bảng theo miền

| Miền | Bảng | Số bảng |
|---|---|---|
| Tài khoản & phân quyền | `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `refresh_tokens`, `user_tokens`, `user_violations` | 8 |
| Địa điểm | `categories`, `places`, `place_photos`, `opening_hours`, `amenities`, `place_amenity`, `tags`, `taggables`, `place_views`, `place_claims` | 10 |
| Đánh giá & tương tác | `reviews`, `review_photos`, `review_votes`, `comments`, `owner_replies`, `check_ins` | 6 |
| Cộng đồng & vận hành | `collections`, `collection_place`, `follows`, `badges`, `user_badges`, `reports`, `notifications`, `activity_log` | 8 |
| **Tổng** | | **32** |

So với plan §5: thêm `refresh_tokens` (S1), `user_tokens` (FR-02, FR-05), `user_violations` (trust score §5.1); bỏ `banners` (Q4).

---

## 3. Mô tả chi tiết

Ký hiệu: **PK** khóa chính · **FK** khóa ngoại · **UQ** unique · **IX** index · **NN** not null.

### 3.1 Tài khoản & phân quyền

#### `users`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| email | VARCHAR(191) | NN, UQ | Đăng nhập |
| password_hash | VARCHAR(100) | NN | BCrypt |
| display_name | VARCHAR(100) | NN | |
| avatar_url | VARCHAR(500) | | |
| bio | VARCHAR(500) | | |
| email_verified_at | DATETIME(6) | | NULL = chưa xác thực → không được viết review (FR-02) |
| helpful_votes_count | INT UNSIGNED | NN, default 0 | Số vote hữu ích **hợp lệ** nhận được (`review_votes.counted = 1`) — thành phần `helpful_score` của trust |
| contribution_points | INT UNSIGNED | NN, default 0 | Điểm đóng góp cho bảng xếp hạng (FR-29) |
| locked_until | DATETIME(6) | | Khóa tài khoản đến thời điểm này (UC29) |
| lock_reason | VARCHAR(500) | | |
| created_at, updated_at, deleted_at | DATETIME(6) | | `created_at` là thành phần `age_score` của trust |

**Trust score không lưu thành cột** (D1): tính khi cần từ `created_at`, `helpful_votes_count` và `SUM(user_violations.points)` còn hiệu lực.

**Xóa tài khoản** (D7): đặt `deleted_at`, đồng thời ẩn danh hóa `email = deleted_{id}@localspot.invalid`, `display_name = "Người dùng đã xóa"`, xóa `avatar_url`, `bio`, thu hồi mọi refresh token → email được giải phóng để đăng ký lại (NFR-11).

#### `roles`, `permissions`, `role_permissions`, `user_roles`
| Bảng | Cột | Ràng buộc |
|---|---|---|
| roles | id, name VARCHAR(50) | name UQ — `USER`, `OWNER`, `MODERATOR`, `ADMIN` |
| permissions | id, name VARCHAR(100), description | name UQ — dạng `resource:action`, ví dụ `place:approve`, `review:moderate`, `user:lock` |
| role_permissions | role_id FK, permission_id FK | PK(role_id, permission_id) |
| user_roles | user_id FK, role_id FK | PK(user_id, role_id) |

Quyền seed theo Q2: `MODERATOR` = duyệt địa điểm / review / claim, xử lý báo cáo, ẩn nội dung; `ADMIN` = toàn bộ quyền `MODERATOR` + `user:lock`, `user:assign-role`, `category:manage`, `amenity:manage`, `dashboard:view`. Kế thừa role thực hiện bằng cách seed đủ permission cho mỗi role (không dùng cây role) — đơn giản, dễ truy vấn.

#### `refresh_tokens` (S1)
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| user_id | BIGINT UNSIGNED | FK users, NN, IX | |
| token_hash | CHAR(64) | NN, UQ | SHA-256 của token, không lưu token gốc |
| family_id | CHAR(36) | NN, IX | Chuỗi xoay vòng; token cũ bị dùng lại → thu hồi cả family / mọi token của user (U2) |
| expires_at | DATETIME(6) | NN | +7 ngày |
| used_at, revoked_at | DATETIME(6) | | |
| user_agent | VARCHAR(255) | | |
| ip_address | VARCHAR(45) | | |
| created_at | DATETIME(6) | NN | |

#### `user_tokens`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| user_id | BIGINT UNSIGNED | FK users, NN | |
| type | VARCHAR(20) | NN, CHECK IN (`EMAIL_VERIFY`, `PASSWORD_RESET`) | |
| token_hash | CHAR(64) | NN, UQ | |
| expires_at | DATETIME(6) | NN | 24 h / 30 phút (U1) |
| used_at | DATETIME(6) | | Dùng một lần |
| created_at | DATETIME(6) | NN | |

#### `user_violations` (trust score §5.1)
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| user_id | BIGINT UNSIGNED | FK users, NN | Người vi phạm |
| source_type | VARCHAR(30) | NN, CHECK IN (`REVIEW_REJECTED`, `REPORT_CONFIRMED`) | |
| source_id | BIGINT UNSIGNED | NN | id review / report tương ứng |
| points | TINYINT UNSIGNED | NN, default 25 | Lưu lại mức phạt tại thời điểm ghi, để đổi cấu hình không làm sai lịch sử |
| expires_at | DATETIME(6) | NN | `created_at + 30 ngày` |
| created_by | BIGINT UNSIGNED | FK users | Kiểm duyệt viên |
| created_at | DATETIME(6) | NN | |
| | | IX(user_id, expires_at) | `SUM(points) WHERE expires_at > now()` |

### 3.2 Địa điểm

#### `categories`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| parent_id | BIGINT UNSIGNED | FK categories | Danh mục phân cấp; NULL = gốc |
| name | VARCHAR(100) | NN | |
| slug | VARCHAR(120) | NN, UQ | |
| icon | VARCHAR(100) | | |
| sort_order | INT | NN, default 0 | |

#### `places`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| category_id | BIGINT UNSIGNED | FK categories, NN | |
| created_by | BIGINT UNSIGNED | FK users, NN | Người đề xuất (UC11) |
| owner_id | BIGINT UNSIGNED | FK users | Chủ địa điểm, tối đa 1 (U6) |
| name | VARCHAR(200) | NN | |
| slug | VARCHAR(220) | NN, UQ | |
| description | TEXT | | |
| address | VARCHAR(300) | NN | |
| city | VARCHAR(100) | NN | |
| location | POINT SRID 4326 | NN, **SPATIAL IX** | |
| price_min, price_max | INT UNSIGNED | CHECK price_min ≤ price_max | Khoảng giá (VND) cho bộ lọc FR-10 |
| phone | VARCHAR(20) | | |
| website | VARCHAR(300) | | |
| status | VARCHAR(20) | NN, CHECK IN (`PENDING`, `APPROVED`, `REJECTED`, `HIDDEN`) | |
| reject_reason | VARCHAR(500) | | |
| moderated_by | BIGINT UNSIGNED | FK users | |
| moderated_at | DATETIME(6) | | |
| review_count | INT UNSIGNED | NN, default 0 | `v` — review `PUBLISHED` |
| avg_rating | DECIMAL(3,2) | NN, default 0 | `R` — trung bình thô (S3) |
| bayesian_score | DECIMAL(4,3) | NN, default 0 | Điểm xếp hạng hiển thị (S3) |
| checkin_count | INT UNSIGNED | NN, default 0 | |
| version | INT | NN | Optimistic lock |
| created_at, updated_at, deleted_at | DATETIME(6) | | |
| | | IX(category_id, status, bayesian_score) | Xếp hạng theo danh mục (D3, plan §5 đã cập nhật) |
| | | IX(status, created_at) | Trang chủ "mới nhất", hàng chờ duyệt |
| | | IX(owner_id) | |

#### `place_photos`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| place_id | BIGINT UNSIGNED | FK places, NN | |
| uploaded_by | BIGINT UNSIGNED | FK users, NN | |
| storage_key | VARCHAR(300) | NN | Khóa gốc trên S3/MinIO; URL các kích thước suy ra từ khóa |
| status | VARCHAR(20) | NN, CHECK IN (`PROCESSING`, `READY`, `FAILED`) | |
| is_cover | BOOLEAN | NN, default false | Ảnh đại diện |
| sort_order | INT | NN, default 0 | |
| width, height | INT UNSIGNED | | Điền sau khi xử lý |
| created_at | DATETIME(6) | NN | |

#### `opening_hours`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| place_id | BIGINT UNSIGNED | FK places, NN | |
| day_of_week | TINYINT | NN, CHECK 1–7 | ISO: 1 = Thứ Hai |
| open_time, close_time | TIME | NN | `close_time < open_time` = mở qua đêm |
| | | IX(place_id, day_of_week) | Nhiều khung giờ / ngày được phép |

#### `amenities`, `place_amenity`
| Bảng | Cột | Ràng buộc |
|---|---|---|
| amenities | id, name VARCHAR(100), slug VARCHAR(120), icon | slug UQ |
| place_amenity | place_id FK, amenity_id FK | PK(place_id, amenity_id) |

#### `tags`, `taggables` (FR-30, Could)
| Bảng | Cột | Ràng buộc |
|---|---|---|
| tags | id, name VARCHAR(50), slug VARCHAR(60) | slug UQ |
| taggables | tag_id FK, taggable_type VARCHAR(20), taggable_id, created_by FK users | PK(tag_id, taggable_type, taggable_id) — giữ đa hình như plan (D5); hiện chỉ dùng `taggable_type = PLACE` |

#### `place_views`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| place_id | BIGINT UNSIGNED | FK places | |
| view_date | DATE | | Ngày theo giờ Việt Nam |
| view_count | INT UNSIGNED | NN | `INSERT ... ON DUPLICATE KEY UPDATE view_count = view_count + 1` |
| | | PK(place_id, view_date) | Gộp theo ngày để không phình bảng (plan §5) |

#### `place_claims` (UC23, UC30)
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| place_id | BIGINT UNSIGNED | FK places, NN | |
| user_id | BIGINT UNSIGNED | FK users, NN | |
| contact_phone | VARCHAR(20) | NN | |
| evidence_note | VARCHAR(1000) | | |
| evidence_keys | JSON | NN | Danh sách khóa file minh chứng trong bucket **riêng tư** |
| status | VARCHAR(20) | NN, CHECK IN (`PENDING`, `APPROVED`, `REJECTED`) | |
| reject_reason | VARCHAR(500) | | |
| reviewed_by | BIGINT UNSIGNED | FK users | |
| reviewed_at | DATETIME(6) | | |
| version | INT | NN | |
| created_at | DATETIME(6) | NN | |
| | | IX(place_id, status) | "Một yêu cầu đang chờ / địa điểm / người" kiểm tra ở service |

### 3.3 Đánh giá & tương tác

#### `reviews`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| place_id | BIGINT UNSIGNED | FK places, NN | |
| user_id | BIGINT UNSIGNED | FK users, NN | |
| rating | TINYINT UNSIGNED | NN, CHECK 1–5 | |
| content | TEXT | NN | ≥ 20 ký tự (U4, kiểm tra ở DTO) |
| visited_at | DATE | NN | |
| status | VARCHAR(20) | NN, CHECK IN (`PENDING`, `PUBLISHED`, `REJECTED`, `HIDDEN`) | |
| ip_address | VARCHAR(45) | NN | |
| ip_prefix | VARCHAR(45) | NN | IPv4 /24 (ví dụ `113.22.5.0`), IPv6 /64 — dùng cho luật cảnh báo IP (U3) |
| ip_flagged | BOOLEAN | NN, default false | |
| helpful_count | INT UNSIGNED | NN, default 0 | Tổng vote (hiển thị), khác `users.helpful_votes_count` chỉ đếm vote hợp lệ |
| reject_reason | VARCHAR(500) | | |
| moderated_by | BIGINT UNSIGNED | FK users | |
| moderated_at | DATETIME(6) | | |
| version | INT | NN | |
| created_at, updated_at, deleted_at | DATETIME(6) | | |
| | | **UQ(place_id, user_id)** | Mỗi người một review / địa điểm (plan §5). Tính cả review đã xóa mềm (D2 = A): xóa rồi không viết lại được, muốn đổi ý thì sửa |
| | | IX(place_id, status, created_at) | Plan §5 — danh sách review của địa điểm |
| | | IX(user_id, created_at) | Rate limit 5 / 24 h |
| | | IX(place_id, ip_prefix, created_at) | Luật cảnh báo IP |
| | | IX(status, created_at) | Hàng chờ duyệt (UC28) |

#### `review_photos`
Giống `place_photos` nhưng `review_id` (FK reviews) thay `place_id`, không có `is_cover`.

#### `review_votes`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| review_id | BIGINT UNSIGNED | FK reviews | |
| user_id | BIGINT UNSIGNED | FK users | Người vote |
| counted | BOOLEAN | NN | Trust người vote ≥ 30 **tại thời điểm vote** (đã chốt) |
| created_at | DATETIME(6) | NN | |
| | | **PK(review_id, user_id)** | Đảm bảo `UNIQUE(review_id, user_id)` của plan §5 |

Không cho vote review của chính mình (kiểm tra ở service).

#### `comments`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| review_id | BIGINT UNSIGNED | FK reviews, NN | |
| user_id | BIGINT UNSIGNED | FK users, NN | |
| parent_id | BIGINT UNSIGNED | FK comments | NULL = bình luận gốc; chỉ được trỏ tới bình luận gốc (2 cấp, Q5 — kiểm tra ở service) |
| content | VARCHAR(2000) | NN | |
| status | VARCHAR(20) | NN, CHECK IN (`VISIBLE`, `HIDDEN`) | |
| created_at, updated_at, deleted_at | DATETIME(6) | | |
| | | IX(review_id, created_at) | |

#### `owner_replies`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| review_id | BIGINT UNSIGNED | FK reviews, NN, **UQ** | Một phản hồi / review, sửa được (D6) |
| user_id | BIGINT UNSIGNED | FK users, NN | Chủ địa điểm tại thời điểm phản hồi |
| content | VARCHAR(2000) | NN | |
| created_at, updated_at | DATETIME(6) | | |

#### `check_ins`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| user_id | BIGINT UNSIGNED | FK users, NN | |
| place_id | BIGINT UNSIGNED | FK places, NN | |
| location | POINT SRID 4326 | NN | Vị trí người dùng gửi lên |
| accuracy_m | SMALLINT UNSIGNED | NN | ≤ 100 (U5) |
| distance_m | SMALLINT UNSIGNED | NN | ≤ 200 — lưu lại để thống kê / giải trình |
| checkin_date | DATE | NN | Ngày theo giờ Việt Nam |
| created_at | DATETIME(6) | NN | |
| | | **UQ(user_id, place_id, checkin_date)** | 1 check-in / địa điểm / ngày (U5) ở tầng CSDL |

### 3.4 Cộng đồng & vận hành

#### `collections`, `collection_place`
| Bảng | Cột | Ràng buộc |
|---|---|---|
| collections | id, user_id FK, name VARCHAR(150), description VARCHAR(1000), is_public BOOLEAN, share_slug VARCHAR(40), created_at, updated_at | share_slug UQ (NULL khi riêng tư) |
| collection_place | collection_id FK, place_id FK, note VARCHAR(500), added_at | **PK(collection_id, place_id)** (plan §5) |

#### `follows`
| Cột | Ràng buộc |
|---|---|
| follower_id FK users, following_id FK users, created_at | PK(follower_id, following_id); CHECK(follower_id <> following_id); IX(following_id) |

#### `badges`, `user_badges`
| Bảng | Cột | Ràng buộc |
|---|---|---|
| badges | id, code VARCHAR(50), name, description, icon | code UQ — ví dụ `REVIEWER_1`…`REVIEWER_5`, `EXPLORER` |
| user_badges | user_id FK, badge_id FK, awarded_at | PK(user_id, badge_id) |

Tiêu chí trao huy hiệu viết trong code (không lưu JSON luật) — FR-29 là Could.

#### `reports` (UC17, UC29)
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| reporter_id | BIGINT UNSIGNED | FK users, NN | |
| target_type | VARCHAR(20) | NN, CHECK IN (`PLACE`, `REVIEW`, `COMMENT`, `USER`) | Quan hệ đa hình |
| target_id | BIGINT UNSIGNED | NN | **Không có FK** (đa hình) — toàn vẹn kiểm tra ở service |
| reason | VARCHAR(30) | NN, CHECK IN (`SPAM`, `FAKE`, `OFFENSIVE`, `WRONG_INFO`, `OTHER`) | |
| detail | VARCHAR(1000) | | |
| status | VARCHAR(20) | NN, CHECK IN (`OPEN`, `RESOLVED`, `DISMISSED`) | |
| handled_by | BIGINT UNSIGNED | FK users | |
| handled_at | DATETIME(6) | | |
| version | INT | NN | |
| created_at | DATETIME(6) | NN | |
| | | **UQ(reporter_id, target_type, target_id)** | 1 báo cáo / người / đối tượng (U10) |
| | | IX(status, target_type, target_id) | Gom nhóm hàng đợi (UC29) |

#### `notifications`
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| user_id | BIGINT UNSIGNED | FK users, NN | Người nhận |
| type | VARCHAR(50) | NN | `REVIEW_PUBLISHED`, `PLACE_APPROVED`, `OWNER_REPLIED`... |
| data | JSON | NN | Dữ liệu hiển thị (plan §5) |
| read_at | DATETIME(6) | | |
| created_at | DATETIME(6) | NN | |
| | | IX(user_id, read_at, created_at) | Đếm chưa đọc, danh sách mới nhất |

#### `activity_log` (FR-42)
| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| actor_id | BIGINT UNSIGNED | FK users, NN | |
| action | VARCHAR(50) | NN | `PLACE_APPROVE`, `REVIEW_REJECT`, `USER_LOCK`... |
| target_type | VARCHAR(20) | NN | |
| target_id | BIGINT UNSIGNED | NN | |
| metadata | JSON | | Lý do, giá trị trước / sau |
| ip_address | VARCHAR(45) | | |
| created_at | DATETIME(6) | NN | |
| | | IX(actor_id, created_at), IX(target_type, target_id) | |

Ghi qua Spring AOP `@Aspect` quanh các phương thức service có annotation `@AuditedAction` (plan §5 chọn AOP thay Hibernate Envers).

---

## 4. Tính toán dẫn xuất (denormalized)

| Cột | Cập nhật khi | Bởi |
|---|---|---|
| `places.review_count`, `avg_rating`, `bayesian_score` | Review đổi sang / rời khỏi `PUBLISHED` | `RecalculateRatingListener` (AFTER_COMMIT, @Async) — tính lại `C` và địa điểm liên quan |
| `places.bayesian_score` (mọi địa điểm) | Hằng đêm 03:00 (D4) | `RatingRecalculationJob` (`@Scheduled`) — cập nhật theo lô với `C` mới, vì `C` đổi làm điểm của mọi địa điểm khác lỗi thời |
| `places.checkin_count` | Check-in thành công | `CheckInService` (cùng transaction) |
| `reviews.helpful_count` | Vote / bỏ vote | `ReviewVoteService` (`UPDATE ... SET helpful_count = helpful_count ± 1`, nguyên tử) |
| `users.helpful_votes_count` | Vote có `counted = 1` / bỏ vote đó | `ReviewVoteService` |
| `users.contribution_points` | Review được công khai, địa điểm được duyệt... | Listener |

Lý do denormalize (trả lời câu hỏi bảo vệ "vì sao denormalize `avg_rating`?"): trang danh sách / tìm kiếm sắp xếp theo điểm trên hàng trăm địa điểm; tính `AVG` mỗi request là `O(số review)` và không đánh index được, còn cột lưu sẵn cho phép index `(category_id, status, bayesian_score)` và đọc `O(1)`. Đánh đổi: phải giữ đồng bộ khi ghi — giải quyết bằng event sau commit và có thể chạy lại toàn bộ bằng job đối soát.

---

## 5. Câu hỏi mở / đã chốt

| # | Câu hỏi | Đề xuất | Đánh đổi |
|---|---|---|---|
| D1 ✅ | Trust score lưu cột hay tính khi cần? | **Tính khi cần** từ `created_at`, `helpful_votes_count`, `SUM(user_violations.points)` còn hiệu lực — 1 truy vấn có index | Trust thay đổi theo thời gian (tuổi tăng, vi phạm hết hạn) → cột lưu sẵn sẽ lỗi thời nếu không có job định kỳ |
| D2 ✅ (A) | Soft delete vs `UNIQUE(place_id, user_id)` trên `reviews`: review đã xóa mềm vẫn chiếm chỗ → người dùng **không viết lại được** review cho địa điểm đó | **Giữ nguyên ràng buộc như plan** — muốn đổi ý thì sửa review (UC13), không xóa rồi viết lại. Chặn luôn chiêu xóa-viết lại để "làm mới" review | Phương án khác: cột sinh `active_key = IF(deleted_at IS NULL, 1, NULL)` + `UQ(place_id, user_id, active_key)` cho phép viết lại, nhưng lệch với ràng buộc ghi trong plan |
| D3 ✅ | Plan §5 ghi index `places(category_id, status, avg_rating)`. Sau S3, sắp xếp dùng `bayesian_score` | Đổi thành `(category_id, status, bayesian_score)` và **sửa plan §5** cho khớp | Là ràng buộc cốt lõi (CLAUDE.md §3) nên cần xác nhận trước khi sửa plan |
| D4 ✅ | `C` (trung bình toàn hệ thống) thay đổi → `bayesian_score` của **mọi** địa điểm khác đều lỗi thời, không chỉ địa điểm vừa có review | Mỗi event: tính lại `C` và điểm **địa điểm liên quan**; thêm **job đêm** (`@Scheduled`, 03:00) tính lại toàn bộ `bayesian_score` với `C` mới | `C` biến động rất chậm khi đã có nhiều review → sai lệch trong ngày không đáng kể; tránh cập nhật cả bảng `places` sau mỗi review |
| D5 ✅ | `tags` / `taggables` đa hình như plan, trong khi FR-30 chỉ gắn thẻ cho địa điểm | Giữ `taggables` như plan (cho phép mở rộng gắn thẻ review sau này) | Bảng `place_tags` đơn giản hơn và có FK thật, nhưng lệch tên với plan |
| D6 ✅ | Chủ địa điểm được phản hồi mấy lần / review? | 1 phản hồi, sửa được (`UQ(review_id)`) | |
| D7 ✅ | Soft delete vs `users.email` UNIQUE: tài khoản đã xóa vẫn giữ email | Khi xóa tài khoản, **ẩn danh hóa** email (`deleted_{id}@localspot.invalid`) và tên hiển thị → email dùng đăng ký lại được, phù hợp NFR-11 | Mất khả năng khôi phục tài khoản đã xóa |
