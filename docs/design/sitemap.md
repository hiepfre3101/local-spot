# Sitemap & wireframe — LocalSpot

Bản đồ route frontend → màn hình → use case. Nguồn màn hình (wireframe / prototype hi-fi): design system trên Claude Design, bản xuất cục bộ ở `docs/design-system/` (không commit — xem `.gitignore`). Sơ đồ: [diagrams/sitemap.puml](../diagrams/sitemap.puml) → `diagrams/png/sitemap.png`.

Cập nhật: 2026-09-29 — supervisor duyệt màn P01–P12 (xem mục 4).

## 1. Bố cục (layout)

| Layout | Dùng cho | Thành phần khung |
|---|---|---|
| `DefaultLayout` | Trang công khai (khách, thành viên, chủ địa điểm) | Header: logo · Khám phá · Bản đồ · Bảng xếp hạng · Đề xuất địa điểm; phải: Đăng nhập / Đăng ký **hoặc** chuông thông báo + ảnh đại diện. Chân trang `olive` |
| `AuthLayout` | Đăng nhập, đăng ký, khôi phục mật khẩu | Chia đôi: ảnh `radius-lg` trái, form 440px phải (P01) |
| `AdminLayout` | Kiểm duyệt viên, quản trị viên | Sidebar 224px theo vai trò + vùng chính; bo góc tối thiểu |

## 2. Route

Cột **Quyền**: `—` công khai; `auth` cần đăng nhập; `perm:x` cần permission `x` (seed theo Q2). Khách bấm thao tác ghi → `/login?redirect=<route hiện tại>` (không ẩn nút).

### 2.1 Trang công khai — `DefaultLayout`

| Route | Màn hình | UC | Quyền | Ghi chú |
|---|---|---|---|---|
| `/` | Trang chủ (HomePage) | UC07, UC08, UC09 | — | Ô tìm + danh mục → `/search`; "Tìm quanh tôi" → `/search?near=me` |
| `/search` | Tìm kiếm & bản đồ (P02) | UC08, UC09 | — | Query: `q`, `category`, `minRating`, `price`, `amenities`, `near`, `sort`. Danh sách + bản đồ cạnh nhau |
| `/places/:slug` | Chi tiết địa điểm (P03) | UC10, UC13–UC17, UC18, UC22, UC25 | — | Check-in mở hộp thoại P12 (không phải route). Chủ địa điểm thấy nút Phản hồi |
| `/places/:slug/review` | Viết / sửa đánh giá (P04) | UC12, UC13, UC22 | `auth` | Đã có đánh giá → chế độ sửa (1 đánh giá / người / địa điểm, D2) |
| `/propose` | Đề xuất địa điểm (P05) | UC11, UC22 | `auth` | Một trang có thanh tiến độ; cảnh báo trùng U7 |
| `/leaderboard` | Bảng xếp hạng (P08) | UC21, UC20 | — | Query: `period`, `region` |
| `/users/:id` | Trang cá nhân (P06) | UC18, UC20, UC21 | — | Tab qua query `?tab=reviews\|collections\|checkins\|badges`. Chính mình → nút "Chỉnh sửa hồ sơ" |
| `/collections/:id` | Bộ sưu tập của mình | UC18 | `auth` (chủ bộ) | Bật/tắt công khai từng bộ (`is_public`) |
| `/c/:shareSlug` | Bộ sưu tập chia sẻ | UC18 | — | Chỉ bộ công khai |
| `/settings/profile` | Cài đặt — Hồ sơ (P06) | UC06 | `auth` | |
| `/settings/password` | Cài đặt — Mật khẩu (P06) | UC06 | `auth` | |
| `/owner` | Địa điểm của tôi (P09) | UC23 | `auth` | Chưa sở hữu địa điểm nào → form yêu cầu xác nhận sở hữu; đã có → danh sách |
| `/owner/places/:id` | Quản lý địa điểm (P09) | UC24, UC25, UC26 | `perm:place:update-own` | Tab `?tab=stats\|info\|reviews` |
| — (thả xuống ở header) | Thông báo (P07) | UC19 | `auth` | Bảng thả xuống 400px, không có route |

### 2.2 Tài khoản — `AuthLayout`

| Route | Màn hình | UC | Quyền |
|---|---|---|---|
| `/login` | Đăng nhập (P01) | UC03 | chỉ khách |
| `/register` | Đăng ký (P01) | UC01 | chỉ khách |
| `/verify-email?token=` | Kiểm tra / xác thực email (P01) | UC02 | — |
| `/forgot-password` | Khôi phục mật khẩu — bước 1 (P01) | UC05 | chỉ khách |
| `/reset-password?token=` | Khôi phục mật khẩu — đặt mật khẩu mới (P01) | UC05 | — |

Đăng xuất (UC04) là mục trong menu ảnh đại diện, không có route.

### 2.3 Quản trị — `AdminLayout`

| Route | Màn hình | UC | Quyền |
|---|---|---|---|
| `/admin` | Chuyển hướng: quản trị viên → `/admin/dashboard`; kiểm duyệt viên → `/admin/queue` | — | có ít nhất một quyền kiểm duyệt |
| `/admin/dashboard` | Tổng quan (AdminDashboard) | UC33 | `perm:dashboard:view` |
| `/admin/queue` | Hàng chờ gộp FIFO (DataTable) — `GET /moderation/queue` | UC27–UC30 | bất kỳ quyền kiểm duyệt |
| `/admin/moderation/places/:id` | Duyệt địa điểm (AdminForm) | UC27 | `perm:place:approve` |
| `/admin/moderation/reviews/:id` | Duyệt đánh giá | UC28 | `perm:review:moderate` |
| `/admin/moderation/reports/:targetType/:targetId` | Xử lý báo cáo (nhóm theo đối tượng) | UC29 | `perm:report:handle` |
| `/admin/moderation/claims/:id` | Duyệt yêu cầu sở hữu | UC30 | `perm:claim:approve` |
| `/admin/users` | Người dùng & vai trò (P10) | UC31 | `perm:user:view` (nút gán vai trò / khoá theo `user:assign-role`, `user:lock`) |
| `/admin/catalog` | Danh mục & tiện ích (P11) | UC32 | `perm:category:manage`, `perm:amenity:manage` |

Danh sách riêng từng loại (lọc theo loại) là `/admin/queue?type=PLACE|REVIEW|REPORT|CLAIM` — không tách route.

### 2.4 Trang lỗi

`/403` (không đủ quyền), `/:pathMatch(.*)*` → 404.

## 3. Điều hướng chính

```
Khách ──► / ──► /search ──► /places/:slug ──(thao tác ghi)──► /login ──► quay lại route cũ
Thành viên ──► /places/:slug ──► /places/:slug/review ──► xác nhận "Đã đăng" | "Đang chờ duyệt"
Thành viên ──► /places/:slug ──(Check-in)──► hộp thoại P12
Thành viên ──► /owner ──(yêu cầu sở hữu, được duyệt UC30)──► /owner/places/:id
Kiểm duyệt viên ──► /admin/queue ──(Xử lý tiếp: mục cũ nhất)──► /admin/moderation/<loại>/:id ──► /admin/queue
```

## 4. Duyệt P01–P12 (2026-09-29)

Supervisor duyệt toàn bộ P01–P12 → chấp nhận **phương án đề xuất** của từng màn. Câu hỏi "Cần bạn quyết định" đã có câu trả lời trong tài liệu đã chốt thì **tài liệu đã chốt thắng**:

| Màn | Câu hỏi | Kết luận | Căn cứ |
|---|---|---|---|
| P01 | Đăng nhập trang riêng hay hộp thoại | Trang riêng | Phương án đề xuất |
| P01 | Google / Facebook | Không | Ngoài phạm vi (checklist A) |
| P01 | Quy tắc mật khẩu | ≥ 8 ký tự, có chữ và số | U1 |
| P02 | Bản đồ luôn cạnh danh sách | Có | Phương án đề xuất |
| P02 | Bộ lọc | Danh mục, **khoảng giá**, tiện ích, khoảng cách, điểm tối thiểu | FR-10 (P02 thiếu khoảng giá → bổ sung) |
| P02 | Ghim hiển thị | Điểm | Phương án đề xuất |
| P03 | Địa điểm tương tự | Lưới 4 cố định | Phương án đề xuất |
| P03 | Gắn thẻ | Thẻ tự do | FR-30 |
| P04 | Số đánh giá / địa điểm | 1 | D2, `UNIQUE(place_id, user_id)` |
| P04 | Bắt buộc check-in trước khi đánh giá | Không, chỉ hiện nhãn nếu đã check-in | Phương án đề xuất |
| P05 | Một trang hay nhiều bước | Một trang, thanh tiến độ | Phương án đề xuất |
| P05 | Kiểm tra trùng | Cảnh báo tên gần giống trong 50 m, không chặn | U7 |
| P05 | Theo dõi trạng thái đề xuất | Có — `GET /me/places` | openapi |
| P06 | Bộ sưu tập công khai / riêng tư | Bật/tắt từng bộ | FR-26, `collections.is_public` |
| P06 | Danh sách theo dõi công khai | Có | openapi `/users/{id}/followers`, `/following` |
| P09 | Giao diện công khai hay khung quản trị | Công khai | Phương án đề xuất |
| P09 | Một chủ nhiều địa điểm | Có (mỗi địa điểm 1 chủ) | U6, `GET /owner/places` trả danh sách |
| P10 | Nhật ký đổi vai trò | Ghi `activity_log`, không hiển thị trong P10 | FR-42 |
| P11 | Địa điểm một hay nhiều danh mục | Một | `places.category_id` NN |
| P11 | Danh mục con | Có | FR-39, `categories.parent_id` |
| P12 | Bán kính, GPS kém, tần suất | 200 m; từ chối khi accuracy > 100 m; 1 lần / địa điểm / ngày | U5 |

### Câu hỏi mở — không ảnh hưởng sitemap, cần chốt trước khi làm module tương ứng (✅ = đã chốt)

| # | Màn | Câu hỏi | Ảnh hưởng |
|---|---|---|---|
| O1 | P03 | Bình luận mở tại chỗ hay trang riêng của đánh giá | Nếu trang riêng → thêm route `/reviews/:id` |
| O2 ✅ | P04 | Sửa đánh giá đã đăng khi tác giả trust < 30: bản cũ vẫn hiện trong lúc bản sửa chờ duyệt? | **Chốt 2026-09-30**: không giữ bản cũ — đánh giá về `PENDING` cả bài tới khi duyệt lại; `reviews` giữ một trạng thái, không thêm bảng |
| O3 | P06, P08 | Bộ huy hiệu và công thức điểm đóng góp chính thức | Seed `badges`, listener `contribution_points` |
| O4 | P07 | Gửi email kèm thông báo? Gom thông báo cùng loại? Cần trang "Tất cả thông báo"? | FR-27 chỉ yêu cầu trong ứng dụng; trang riêng → thêm route `/notifications` |
| O5 ✅ | P09 | Chủ cập nhật thông tin (UC24) có phải duyệt lại? | **Chốt 2026-10-05**: không — áp dụng ngay (`PATCH /owner/places/{id}`, như E1). Chủ đã qua xác minh sở hữu (UC30); tên, vị trí, danh mục vốn không sửa được; thông tin sai → người dùng báo cáo WRONG_INFO |
| O6 ✅ | P10 | Gán trực tiếp vai trò "Chủ địa điểm"? Khoá có lý do / thời hạn? | **Chốt 2026-10-03**: không gán / gỡ OWNER tại P10 (chỉ qua duyệt yêu cầu sở hữu UC30); mọi tài khoản giữ USER; admin không tự khóa / tự gỡ ADMIN. Khoá bắt buộc thời hạn + lý do (openapi `POST /admin/users/{id}/lock`) |
| O7 ✅ | P11 | Tiện ích gắn theo danh mục hay dùng chung | **Chốt 2026-10-06**: dùng chung — giữ schema (`amenities` không có `category_id`), form / bộ lọc hiện đủ danh sách |
