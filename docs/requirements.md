# Đặc tả yêu cầu — LocalSpot

> Trạng thái: **Đã chốt** (2026-09-27) — toàn bộ câu hỏi mở Q1–Q6 đã được supervisor xác nhận.
> Nguồn: [plan-v1.md](../plan-v1.md) mục 1, 4, 5, 6, 10.
> Tài liệu này là đầu vào trực tiếp cho Chương 1 (phạm vi) và Chương 3 (bảng FR/NFR) của báo cáo.

---

## 1. Phạm vi

### 1.1 Trong phạm vi
Giữ nguyên danh sách ở plan-v1.md mục 1. Phân tầng ưu tiên theo **MoSCoW** để có đường cắt rõ ràng khi trễ tiến độ (plan-v1.md mục 11, rủi ro "ôm quá nhiều tính năng"):

| Mức | Ý nghĩa | Nguyên tắc chọn |
|---|---|---|
| **M** — Must | MVP, bắt buộc có khi bảo vệ | Luồng chính + 4 điểm khác biệt cốt lõi (chống review ảo, Bayesian, tìm kiếm tiếng Việt, truy vấn không gian) |
| **S** — Should | Nên có, làm sau khi MVP chạy | Tính năng chủ quán, cộng đồng có giá trị demo cao |
| **C** — Could | Cắt đầu tiên nếu trễ | Gamification, tính năng phụ |

### 1.2 Ngoài phạm vi
Theo plan-v1.md mục 1:
- Thanh toán, đặt bàn, giao đồ ăn.
- Ứng dụng mobile native (chỉ web responsive).
- Nhắn tin riêng giữa người dùng.
- Đa ngôn ngữ (chỉ tiếng Việt; có sẵn cấu trúc i18n).

---

## 2. Tác nhân

| Tác nhân | Mô tả | Role RBAC |
|---|---|---|
| Khách vãng lai | Chưa đăng nhập | — |
| Thành viên | Đã đăng ký; kế thừa Khách | `user` |
| Chủ địa điểm | Thành viên đã được duyệt sở hữu ≥1 địa điểm; kế thừa Thành viên | `owner` |
| Kiểm duyệt viên | Duyệt nội dung, xử lý báo cáo | `moderator` |
| Quản trị viên | Toàn quyền hệ thống; kế thừa Kiểm duyệt viên | `admin` |
| Hệ thống | Tác vụ nền (tính rating, xử lý ảnh, gửi mail, đồng bộ index) | — |

Phân quyền `moderator` vs `admin` (đã chốt Q2): **moderator** duyệt địa điểm / review / yêu cầu sở hữu, xử lý báo cáo, ẩn nội dung; **admin** có toàn bộ quyền moderator, thêm khóa tài khoản, gán role, quản lý danh mục, tiện ích, dashboard.

---

## 3. Yêu cầu chức năng (FR)

Cột "Nguồn" trỏ về mục trong plan-v1.md để truy vết.

### 3.1 Tài khoản & xác thực
| Mã | Tác nhân | Chức năng | Mô tả / tiêu chí chấp nhận | Ưu tiên | Nguồn |
|---|---|---|---|---|---|
| FR-01 | Khách | Đăng ký | Email + mật khẩu + tên hiển thị; email duy nhất; gửi mail xác thực | M | §4, §10.D |
| FR-02 | Thành viên | Xác thực email | Link có hạn dùng; chưa xác thực thì không được viết review | M | §5 chống review ảo |
| FR-03 | Khách | Đăng nhập | Trả access token + refresh token (JWT) | M | §2, §10.D |
| FR-04 | Thành viên | Làm mới token, đăng xuất | Refresh token xoay vòng; đăng xuất thu hồi refresh token | M | §10.D |
| FR-05 | Khách | Quên / đặt lại mật khẩu | Gửi link đặt lại qua email, có hạn dùng, dùng một lần | M | §10.D |
| FR-06 | Thành viên | Đổi mật khẩu | Yêu cầu mật khẩu cũ; thu hồi các refresh token khác | M | §4, §10.D |
| FR-07 | Thành viên | Quản lý hồ sơ | Tên hiển thị, avatar, giới thiệu; xem review/ảnh/bộ sưu tập của mình | M | §4, §10.F |

### 3.2 Khám phá địa điểm
| Mã | Tác nhân | Chức năng | Mô tả / tiêu chí chấp nhận | Ưu tiên | Nguồn |
|---|---|---|---|---|---|
| FR-08 | Khách | Trang chủ | Địa điểm nổi bật (theo điểm Bayesian), danh mục, mới nhất; có cache | M | §4, §10.F |
| FR-09 | Khách | Tìm kiếm từ khóa | Qua Meilisearch; khớp có dấu / không dấu ("pho" → "phở"), chịu lỗi chính tả, synonym tiếng Việt | M | §1 khác biệt, §6 `/search` |
| FR-10 | Khách | Lọc & sắp xếp | Lọc theo danh mục, khoảng giá, tiện ích, khoảng cách, điểm sao; sắp xếp theo điểm, khoảng cách, mới nhất | M | §4 |
| FR-11 | Khách | Tìm quanh vị trí | `GET /places/nearby?lat&lng&radius`; truy vấn dùng spatial index trên `places.location` | M | §1 khác biệt, §6 |
| FR-12 | Khách | Bản đồ | Leaflet + OSM, marker cluster, nút "gần tôi" (Geolocation API) | M | §4, §10.F |
| FR-13 | Khách | Chi tiết địa điểm | Thông tin, gallery ảnh, giờ mở cửa, tiện ích, điểm Bayesian, phân bố sao; ghi nhận lượt xem | M | §4 |
| FR-14 | Khách | Danh sách review | Phân trang cursor; sắp xếp mới nhất / hữu ích nhất; chỉ hiện review đã duyệt | M | §6 |

### 3.3 Đóng góp địa điểm
| Mã | Tác nhân | Chức năng | Mô tả / tiêu chí chấp nhận | Ưu tiên | Nguồn |
|---|---|---|---|---|---|
| FR-15 | Thành viên | Đề xuất địa điểm mới | Chọn tọa độ trên bản đồ, danh mục, địa chỉ, giờ mở cửa, tiện ích; trạng thái `pending` đến khi được duyệt | M | §4, §6 |
| FR-16 | Thành viên | Upload ảnh địa điểm | Xử lý nền qua RabbitMQ: resize, nén, gỡ EXIF; lưu S3/MinIO | M | §2, §10.E |

### 3.4 Đánh giá & tương tác
| Mã | Tác nhân | Chức năng | Mô tả / tiêu chí chấp nhận | Ưu tiên | Nguồn |
|---|---|---|---|---|---|
| FR-17 | Thành viên | Viết review | Sao 1–5, nội dung, ngày đã đến; **mỗi người một review / địa điểm** (`UNIQUE(place_id, user_id)`) | M | §4, §5 |
| FR-18 | Thành viên | Sửa / xóa review của mình | Xóa mềm; sửa/xóa kích hoạt tính lại rating | M | §4, §6 |
| FR-19 | Thành viên | Ảnh đính kèm review | Nhiều ảnh, crop phía client, xử lý nền như FR-16 | M | §10.E, §10.F |
| FR-20 | Thành viên | Vote "hữu ích" | Mỗi người một vote / review (`UNIQUE(review_id, user_id)`), bấm lại để bỏ vote | M | §5, §6 |
| FR-21 | Thành viên | Bình luận review | Bình luận phân cấp tối đa 2 cấp (bình luận + trả lời); xóa mềm | M | §4, §10.E |
| FR-22 | Hệ thống | Tính điểm Bayesian | `(v/(v+m))·R + (m/(v+m))·C`; tính lại bất đồng bộ qua `ReviewCreatedEvent` khi review được tạo/sửa/xóa/duyệt | M | §5, §10.E |
| FR-23 | Hệ thống | Chống review ảo | Rate limit N review/ngày; yêu cầu email đã xác thực; review từ tài khoản trust score thấp vào hàng chờ duyệt; cảnh báo nhiều review cùng địa điểm từ cùng dải IP trong thời gian ngắn | M | §5 |
| FR-24 | Thành viên | Check-in | Tọa độ GPS phải nằm trong bán kính ~200 m của địa điểm | M | §4, §5 |
| FR-25 | Thành viên | Báo cáo vi phạm | Báo cáo địa điểm / review / bình luận / người dùng (quan hệ đa hình), kèm lý do | M | §4, §10.E |

### 3.5 Cộng đồng
| Mã | Tác nhân | Chức năng | Mô tả / tiêu chí chấp nhận | Ưu tiên | Nguồn |
|---|---|---|---|---|---|
| FR-26 | Thành viên | Bộ sưu tập | Tạo/sửa/xóa, thêm/bớt địa điểm; chế độ công khai có link chia sẻ | S | §4, §6 |
| FR-27 | Thành viên | Thông báo trong ứng dụng | Danh sách + đánh dấu đã đọc; đẩy realtime qua WebSocket/STOMP | S | §1, §10.E |
| FR-28 | Thành viên | Theo dõi người dùng | Follow/unfollow, xem danh sách theo dõi | C | §4, §5 |
| FR-29 | Thành viên | Huy hiệu & bảng xếp hạng đóng góp | Huy hiệu theo mốc đóng góp; bảng xếp hạng theo điểm đóng góp | C | §4, §5 |
| FR-30 | Thành viên | Thẻ tự do (tags) | Gắn thẻ như "view đẹp", "hợp gia đình" | C | §5 bảng `tags` |

### 3.6 Chủ địa điểm
| Mã | Tác nhân | Chức năng | Mô tả / tiêu chí chấp nhận | Ưu tiên | Nguồn |
|---|---|---|---|---|---|
| FR-31 | Thành viên | Yêu cầu xác nhận sở hữu | Gửi kèm minh chứng; một yêu cầu đang chờ / địa điểm / người | S | §4, §5 `place_claims` |
| FR-32 | Chủ địa điểm | Cập nhật thông tin địa điểm | Thông tin, ảnh, giờ mở cửa, tiện ích của địa điểm mình sở hữu | S | §4 |
| FR-33 | Chủ địa điểm | Phản hồi review | Phản hồi công khai; thông báo cho người viết review | S | §4, §5 `owner_replies` |
| FR-34 | Chủ địa điểm | Thống kê địa điểm | Lượt xem (gộp theo ngày), lượt check-in, phân bố điểm sao | C | §4, §5 `place_views` |

### 3.7 Quản trị & kiểm duyệt
| Mã | Tác nhân | Chức năng | Mô tả / tiêu chí chấp nhận | Ưu tiên | Nguồn |
|---|---|---|---|---|---|
| FR-35 | Kiểm duyệt viên | Duyệt địa điểm | Duyệt / từ chối (kèm lý do); duyệt thì đồng bộ index tìm kiếm | M | §4 |
| FR-36 | Kiểm duyệt viên | Duyệt review trong hàng chờ | Duyệt / từ chối review bị đưa vào hàng chờ bởi FR-23 | M | §5, §10.E |
| FR-37 | Kiểm duyệt viên | Xử lý báo cáo | Hàng đợi báo cáo; ẩn nội dung; bỏ qua báo cáo | M | §4 |
| FR-38 | Quản trị viên | Quản lý người dùng | Tìm kiếm, khóa/mở khóa tài khoản, gán role | M | §4 |
| FR-39 | Quản trị viên | Quản lý danh mục & tiện ích | Danh mục phân cấp, tiện ích | M | §4, §10.E |
| FR-40 | Kiểm duyệt viên | Duyệt yêu cầu sở hữu | Duyệt thì gán role `owner` cho người yêu cầu | S | §4 |
| FR-41 | Quản trị viên | Dashboard thống kê | Người dùng mới, review theo ngày, địa điểm hot | S | §4 |
| FR-42 | Hệ thống | Nhật ký thao tác quản trị | Ghi `activity_log` qua Spring AOP cho mọi thao tác admin/moderator | S | §5 `activity_log` |

**Tổng**: 42 FR — 30 Must, 8 Should, 4 Could.

---

## 4. Yêu cầu phi chức năng (NFR)

| Mã | Nhóm | Yêu cầu | Cách kiểm chứng |
|---|---|---|---|
| NFR-01 | Hiệu năng | API đọc (danh sách, chi tiết, nearby) p95 < 300 ms với dữ liệu seed (≥500 địa điểm, ≥2000 review) | Đo bằng k6/JMeter, đưa kết quả vào Chương 5 |
| NFR-02 | Hiệu năng | Tìm kiếm `/search` p95 < 200 ms | Như trên |
| NFR-03 | Hiệu năng | Truy vấn bán kính dùng spatial index, không full scan | `EXPLAIN` cho thấy dùng index `places.location` |
| NFR-04 | Hiệu năng | Không có N+1 query trên các API danh sách | Hibernate SQL log / p6spy (§10.G) |
| NFR-05 | Hiệu năng | Lighthouse Performance ≥ 80 (mobile) cho trang chủ và chi tiết | Lighthouse report |
| NFR-06 | Bảo mật | Mật khẩu băm BCrypt; access token 15 phút, refresh token 7 ngày, lưu dạng băm, xoay vòng | Unit/integration test |
| NFR-07 | Bảo mật | RBAC + `@PreAuthorize` / `PermissionEvaluator` trên mọi endpoint ghi | Test phân quyền cho từng role |
| NFR-08 | Bảo mật | Chống XSS, SQL injection (JPA tham số hóa), mass assignment (chỉ bind qua DTO), CSRF không áp dụng do dùng JWT header | Checklist §10.G |
| NFR-09 | Bảo mật | Upload: kiểm tra MIME thật + phần mở rộng, ≤ 5 MB/ảnh, ≤ 10 ảnh/review, gỡ EXIF | Test upload file sai định dạng / quá cỡ |
| NFR-10 | Bảo mật | Rate limit cho đăng nhập, đăng ký, quên mật khẩu, viết review | Test vượt ngưỡng trả 429 |
| NFR-11 | Quyền riêng tư | Không công khai email; gỡ tọa độ GPS trong EXIF ảnh; xóa mềm dữ liệu người dùng | Review code + test |
| NFR-12 | Toàn vẹn dữ liệu | Các ràng buộc ở plan-v1.md §5 "Ràng buộc quan trọng" được đảm bảo ở tầng CSDL, không chỉ ở tầng ứng dụng | Migration + test vi phạm ràng buộc |
| NFR-13 | Tin cậy | Tác vụ nền (ảnh, mail, rating) có retry và dead-letter queue; lỗi tác vụ nền không làm hỏng request chính | Test consumer lỗi |
| NFR-14 | Tin cậy | Sao lưu CSDL định kỳ (hằng ngày khi deploy, giữ 7 bản); health check qua Actuator | Script backup + `/actuator/health` |
| NFR-15 | Khả năng mở rộng | API stateless (JWT) để chạy nhiều instance sau Nginx; tác vụ nặng tách ra queue | Lập luận kiến trúc trong Chương 3 |
| NFR-16 | Khả dụng (UX) | Responsive từ màn hình 360 px; có skeleton/empty/error state | Kiểm tra thủ công + ảnh chụp |
| NFR-17 | Khả dụng (UX) | Accessibility cơ bản: tương phản WCAG AA, điều hướng bàn phím, `alt` cho ảnh | Lighthouse Accessibility ≥ 90 |
| NFR-18 | Tương thích | 2 phiên bản mới nhất của Chrome, Edge, Firefox, Safari | Kiểm tra thủ công |
| NFR-19 | Bảo trì | Phân lớp Controller → DTO → Service → Repository → Entity; Checkstyle/SpotBugs + ESLint/Prettier trong CI | CI xanh |
| NFR-20 | Kiểm thử | ≥ 30 feature test luồng chính; unit test cho `RatingCalculator`; E2E tìm kiếm → chi tiết → review | Báo cáo test trong CI |
| NFR-21 | API | REST prefix `/api/v1`, lỗi theo RFC 7807 `ProblemDetail`, phân trang cursor cho danh sách dài, tài liệu OpenAPI tự sinh | Swagger UI |
| NFR-22 | Quan sát | Sentry cho lỗi runtime; log có request id | Dashboard Sentry |

---

## 5. Tham số nghiệp vụ cần chốt

Các giá trị này sẽ nằm trong `application.yml` (cấu hình được), không hard-code, để có thể thử nghiệm và trình bày trong báo cáo.

| Tham số | Giá trị | Trạng thái |
|---|---|---|
| `m` — ngưỡng review trong Bayesian | 10 | Đã chốt |
| `C` — trung bình sao toàn hệ thống | Tính lại mỗi lần tính lại rating, lấy từ bảng tổng hợp | Đã chốt |
| N — số review tối đa / ngày / tài khoản | 5 | Đã chốt |
| Công thức trust score | Xem mục 5.1 | Đã chốt |
| Ngưỡng trust score để bỏ qua hàng chờ duyệt | 30 | Đã chốt |
| Cảnh báo IP | ≥ 3 review cùng địa điểm từ cùng /24 trong 24 h | Đã chốt |
| Bán kính check-in | 200 m | Theo plan (§5) |
| Độ sâu bình luận | 2 cấp (bình luận + trả lời) | Đã chốt |
| Mật khẩu | ≥ 8 ký tự, có chữ và số | Đã chốt (U1) |
| Hạn link xác thực email / đặt lại mật khẩu | 24 h / 30 phút | Đã chốt (U1) |
| Giới hạn đăng nhập sai | 5 lần / 15 phút theo (email, IP); refresh token bị dùng lại → thu hồi toàn bộ | Đã chốt (U2) |
| Review từ dải IP bị cảnh báo | Bắt buộc vào hàng chờ + gắn cờ "nghi ngờ IP" | Đã chốt (U3) |
| Độ dài tối thiểu review | 20 ký tự | Đã chốt (U4) |
| Check-in | Từ chối nếu GPS accuracy > 100 m; tối đa 1 check-in / địa điểm / ngày | Đã chốt (U5) |
| Số chủ mỗi địa điểm | 1 | Đã chốt (U6) |
| Cảnh báo trùng khi đề xuất địa điểm | Tên gần giống trong bán kính 50 m → cảnh báo, không chặn | Đã chốt (U7) |
| Tìm quanh vị trí | Mặc định 2 km, tối đa 20 km; tâm Hà Nội khi không có quyền vị trí | Đã chốt (U8) |
| Meilisearch lỗi | Fallback MySQL `LIKE` | Đã chốt (U9) |
| Báo cáo trùng | Mỗi người 1 báo cáo / đối tượng | Đã chốt (U10) |
| Lưu token phía client | Refresh token: cookie HttpOnly, Secure, SameSite=Strict, Path=/api/v1/auth; access token chỉ trong bộ nhớ (không localStorage) | Đã chốt (S1) |
| Upload ảnh review | Review + ảnh trong một request multipart qua API (không presigned URL) | Đã chốt (S2) |
| Cột điểm địa điểm | Lưu cả `avg_rating` (trung bình thô) và `bayesian_score` | Đã chốt (S3) |

---

### 5.1 Trust score

```
trust = clamp(w1 × age_score + w2 × helpful_score − w3 × penalty_score + base, 0, 100)
w1 = 1, w2 = 2, w3 = 3, base = 10, ngưỡng hàng chờ = 30   (đã chốt)
Review mới được đăng ngay nếu trust ≥ 30, ngược lại vào hàng chờ duyệt (FR-23, FR-36).
```

| Thành phần | Cách tính điểm thành phần | Đóng góp tối đa sau trọng số |
|---|---|---|
| `base` | Tài khoản đã xác thực email | +10 |
| `age_score` | +1 mỗi 7 ngày tuổi tài khoản, **trần 18** (~4 tháng) | +18 |
| `helpful_score` | +1 mỗi vote "hữu ích" **hợp lệ** nhận được, **trần 36** | +72 |
| `penalty_score` | +25 mỗi vi phạm (review bị moderator từ chối, hoặc báo cáo vi phạm được xác nhận), **không trần**; mỗi vi phạm hết hiệu lực sau **30 ngày** (đã chốt) | −75 **mỗi vi phạm** |

**Vote hợp lệ** (đã chốt — chống vote chéo): chỉ tính vote từ người có `trust ≥ 30` **tại thời điểm vote** (đã chốt: chụp giá trị lúc vote, lưu cờ `counted` trên `review_votes`) — tránh tính đệ quy trust của người vote phụ thuộc trust của người khác.

**Căn cứ chọn trần** (các ràng buộc thiết kế):
1. **Tổng dương tối đa = 100 đúng bằng trần thang điểm**: 10 + 18 + 2×36 = 100. Không có điểm dư để hấp thụ hình phạt.
2. **Tuổi tài khoản một mình không đủ qua ngưỡng**: 10 + 18 = 28 < 30 → tài khoản "nuôi" không đóng góp (sleeper) vẫn bị duyệt. Đây là lý do trần tuổi giảm từ 30 xuống 18 khi ngưỡng hạ từ 50 xuống 30.
3. **Một vi phạm đủ kéo người dùng tốt nhất xuống dưới ngưỡng**: điều kiện `100 − 3P < 30` → `P ≥ 24`; chọn `P = 25` → 100 − 75 = 25 < 30. (Với P = 20 cũ, người dùng 100 điểm vi phạm vẫn còn 40 ≥ 30 → hình phạt mất tác dụng.)
4. **Hai vi phạm trong 30 ngày = về 0** với mọi tài khoản (100 − 150 → 0).
5. **Hình phạt hết hạn sau 30 ngày** (đã chốt): có đường quay lại cho người dùng đã đạt trần, giữ hàng chờ moderator không phình theo thời gian.

Ví dụ (ngưỡng 30):

| Tài khoản | Tính | Trust | Kết quả |
|---|---|---|---|
| Mới tạo, vừa xác thực email | 10 + 0 + 0 | 10 | Vào hàng chờ |
| 4 tháng, không ai vote hữu ích (sleeper) | 10 + 18 + 0 | 28 | Vào hàng chờ |
| Mới tạo, 10 vote hữu ích hợp lệ | 10 + 0 + 2×10 | 30 | Đăng ngay |
| 2 tháng (9 tuần), 6 vote hữu ích hợp lệ | 10 + 9 + 2×6 | 31 | Đăng ngay |
| Người dùng tối đa (≥4 tháng, ≥36 vote) | 10 + 18 + 72 | 100 | Đăng ngay |
| Người dùng tối đa, 1 vi phạm trong 30 ngày | 100 − 3×25 | 25 | Vào hàng chờ |
| Người dùng tối đa, 2 vi phạm trong 30 ngày | 100 − 3×50 | −50 → 0 | Vào hàng chờ |
| Người dùng tối đa, vi phạm đã quá 30 ngày | 100 − 0 | 100 | Đăng ngay |

- Trọng số, trần, mức phạt, thời hạn, ngưỡng đặt trong `application.yml` để chạy thử nghiệm với dữ liệu seed và báo cáo tỉ lệ bắt review ảo ở Chương 5.

## 6. Câu hỏi mở / đã chốt

| # | Câu hỏi | Đề xuất của dev | Ảnh hưởng |
|---|---|---|---|
| Q1 ✅ | Phân tầng MoSCoW ở mục 3 có đúng ý không? Đặc biệt: check-in (FR-24) và báo cáo vi phạm (FR-25) để Must; bộ sưu tập, thông báo để Should | Như bảng | Lộ trình tuần 5–11 |
| Q2 ✅ | Phân quyền `moderator` vs `admin`? | Moderator: duyệt địa điểm/review/claim, xử lý báo cáo, ẩn nội dung. Admin: thêm khóa tài khoản, gán role, danh mục, tiện ích, dashboard | Bảng `permissions` seed, `@PreAuthorize` |
| Q3 ✅ | Tham số chống review ảo và trust score | Chốt toàn bộ như mục 5 và 5.1 (ngưỡng 30, trần 18/36, phạt 25/vi phạm hết hạn 30 ngày, chỉ tính vote từ người trust ≥ 30 chụp lúc vote) | Schema `users`, `review_votes`, FR-23, Chương 3 |
| Q4 ✅ | Banner có trong plan §4 nhưng không có bảng ở §5 | **Đã cắt** khỏi phạm vi, bỏ FR-43 và dòng banner trong plan §4 | ERD |
| Q5 ✅ | Độ sâu bình luận phân cấp? | 2 cấp — đủ cho hội thoại, tránh truy vấn đệ quy | Schema `comments` |
| Q6 ✅ | Các con số NFR | Như bảng | Chương 5 kiểm thử |
