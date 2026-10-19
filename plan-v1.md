# Kế hoạch dự án: Nền tảng đánh giá quán ăn & điểm check-in địa phương

> Tài liệu định hướng cho cả phần lập trình lẫn phần báo cáo tốt nghiệp.
> Tên tạm: **LocalSpot** (đổi tùy ý).

---

## 1. Tổng quan & phạm vi

**Bài toán.** Người dùng cần tìm quán ăn / cà phê / điểm check-in gần mình, đọc đánh giá thật từ cộng đồng thay vì quảng cáo. Chủ quán cần một kênh để cập nhật thông tin chính xác và phản hồi khách.

**Điểm khác biệt cần nhấn mạnh trong báo cáo** (đây là phần hội đồng hay hỏi — đừng làm bản sao Foody):
- Cơ chế **chống review ảo**: trust score người dùng, giới hạn tần suất, phát hiện bất thường.
- **Xếp hạng công bằng** bằng Bayesian average thay vì trung bình cộng đơn thuần.
- **Tìm kiếm tiếng Việt** hỗ trợ cả có dấu lẫn không dấu, chịu được lỗi chính tả.
- **Truy vấn không gian** (địa điểm trong bán kính X km) bằng spatial index.

**Trong phạm vi (in scope)**
- Đăng ký / đăng nhập, xác thực email, quên mật khẩu.
- Tìm kiếm, lọc, xem bản đồ, xem chi tiết địa điểm.
- Người dùng đóng góp địa điểm mới (chờ duyệt).
- Viết đánh giá kèm ảnh, chấm sao, bình luận, vote hữu ích.
- Check-in tại địa điểm.
- Bộ sưu tập cá nhân ("Quán ăn Hà Nội nên thử").
- Chủ quán xác nhận sở hữu, cập nhật thông tin, phản hồi review.
- Trang quản trị: duyệt địa điểm, xử lý báo cáo, quản lý người dùng, thống kê.
- Thông báo trong ứng dụng.

**Ngoài phạm vi (out of scope)** — nêu rõ trong báo cáo để giới hạn khối lượng
- Thanh toán, đặt bàn, giao đồ ăn.
- Ứng dụng mobile native.
- Nhắn tin riêng giữa người dùng.
- Đa ngôn ngữ (chỉ tiếng Việt; kiến trúc có sẵn i18n để mở rộng).

---

## 2. Công nghệ

### Frontend
| Hạng mục | Lựa chọn | Ghi chú |
|---|---|---|
| Framework | Vue 3 + Vite | Composition API + `<script setup>` |
| Ngôn ngữ | TypeScript | Nên dùng — điểm cộng lớn khi bảo vệ |
| State | Pinia | Store: auth, places, filters, notifications |
| Router | Vue Router 4 | Lazy load route, navigation guard |
| UI | TailwindCSS, theme lấy từ token của `docs/design-system/` | Component Vue tự viết theo design system (phẳng, không bóng, bo góc theo bề mặt) — **không dùng shadcn-vue/PrimeVue** *(chốt 2026-09-29)* |
| Form | VeeValidate + Zod | Validate đồng bộ với rule backend. *(2026-10-05: Zod 3 + adapter chính thức `@vee-validate/zod` — VeeValidate 4.15 chưa hỗ trợ Zod 4)* |
| HTTP | Axios + interceptor | Tự gắn token, xử lý 401 |
| Data fetching | TanStack Query (Vue Query) | Cache, infinite scroll cho danh sách review |
| Bản đồ | Leaflet + OpenStreetMap | Miễn phí, không cần thẻ tín dụng như Google Maps. *(2026-10-12: thêm `leaflet.markercluster` — plugin chính thức gom ghim, supervisor duyệt)* *(2026-10-13: lớp nền MapTiler Dataviz / Dataviz Dark — key miễn phí `VITE_MAPTILER_KEY`, thiếu key dùng OSM Đức; `tile.openstreetmap.org` bị nhà mạng chặn DNS, CARTO bắt buộc key)* |
| Ảnh | vue-advanced-cropper | Crop trước khi upload |
| Realtime | @stomp/stompjs | Client STOMP cho chuông thông báo (FR-27) — token ở khung CONNECT, tự nối lại lùi dần *(thêm 2026-10-19, supervisor duyệt)* |
| Khác | dayjs, vue-toastification | |

### Backend
| Hạng mục | Lựa chọn | Ghi chú |
|---|---|---|
| Framework | Spring Boot 4.1 + Java 21 (LTS) | API-only (REST), Maven Wrapper. *Đổi từ 3.3 (2026-09-29): dòng 3.x đã hết hỗ trợ OSS, Initializr không còn cung cấp* |
| Auth | Spring Security + JWT (OAuth2 Resource Server / Nimbus, HS256) | Access token + refresh token; giải thích rõ luồng trong báo cáo. *(2026-10-01: đổi từ jjwt + filter tự viết sang module chính thức của Spring Security — filter Bearer token có sẵn, ít code bảo mật tự viết; vẫn tự phát hành token, không dùng máy chủ OAuth2 / Keycloak)* |
| Phân quyền | RBAC tự thiết kế + `@PreAuthorize` | Bảng `roles`/`permissions` riêng (Spring không có sẵn như Spatie); Role: user, owner, moderator, admin |
| ORM | Spring Data JPA + Hibernate (+ hibernate-spatial) | Entity, Repository interface, tránh N+1 bằng `@EntityGraph`/fetch join; hibernate-spatial map cột `POINT SRID 4326` sang JTS `Point` *(thêm 2026-09-30)* |
| Spatial | `hibernate-spatial` (JTS `Point`) | Map cột `POINT SRID 4326` sang entity, viết truy vấn không gian trong JPQL — thêm 2026-09-28 (C1) |
| Migration | Flyway | Versioned SQL migration, chạy tự động khi start app |
| Media | Service tự viết + AWS S3 SDK v2 | Client tương thích MinIO khi dev, R2/S3 khi deploy |
| Ảnh | Thumbnailator | Resize, nén, xoay theo EXIF Orientation; ghi JPEG mới nên EXIF (GPS) bị gỡ — bỏ metadata-extractor vì không còn việc (2026-10-06) |
| Mapping DTO | MapStruct | Entity ↔ DTO, thay cho API Resource của Laravel |
| Boilerplate | Lombok | Giảm code getter/setter/constructor |
| Tìm kiếm | Meilisearch 1.54 (Java client chính thức 0.22) | Typo-tolerance, synonym tiếng Việt; tự viết service đồng bộ index thay cho Scout (queue `search.reindex`). Đổi từ v1.10 ngày 2026-10-08 — client hiện tại không tương thích v1.10 |
| Cache | Spring Cache + Redis (Lettuce) | Cache trang chủ, danh mục |
| Queue | RabbitMQ + Spring AMQP (hoặc Spring `@Async`) | Hàng đợi xử lý ảnh, mail, tính lại rating |
| Queue UI | RabbitMQ Management Plugin | Có ảnh chụp màn hình đưa vào báo cáo (thay cho Horizon) |
| Realtime | Spring WebSocket (STOMP, WebSocket thuần — bỏ SockJS 2026-10-16) | Thông báo trong ứng dụng (FR-27), gồm review mới cho chủ quán (thay cho Reverb) |
| API docs | springdoc-openapi + Swagger UI | Sinh OpenAPI tự động |
| Debug/Monitor | Spring Boot Actuator + Micrometer | Chỉ môi trường dev; không phải bản sao 1:1 của Telescope, ghi rõ hạn chế trong báo cáo |
| Test | JUnit 5 + Mockito + Testcontainers | Testcontainers cho MySQL/Redis khi integration test |

### Hạ tầng
- **CSDL**: MySQL 8 (cột `POINT` + `SPATIAL INDEX` cho tìm theo bán kính).
- **Lưu trữ ảnh**: MinIO khi dev, Cloudflare R2 / AWS S3 khi deploy.
- **Container**: Docker Compose (app, nginx, mysql, redis, rabbitmq, meilisearch, minio, mailpit).
- **Email**: Spring Mail (SMTP) gửi qua hàng đợi RabbitMQ (retry + dead-letter, NFR-13); dev dùng **Mailpit** bắt mọi thư (http://localhost:8025), deploy dùng SMTP thật qua biến môi trường. *(thêm 2026-10-01)*
- **CI/CD**: GitHub Actions — chạy JUnit + Vitest + Spotless/SpotBugs + ESLint/Prettier, build jar và image.
- **Deploy**: VPS Ubuntu + Nginx (reverse proxy) + systemd hoặc Docker chạy jar, hoặc Railway/Render nếu muốn nhanh.
- **Giám sát**: Sentry (bản free) + Spring Boot Actuator.

### Công cụ tài liệu
- Sơ đồ: draw.io hoặc PlantUML (nên PlantUML — sơ đồ ở dạng text, dễ sửa, dễ đưa vào git).
- Prototype giao diện: Figma.
- Quản lý task: GitHub Projects / Trello — chụp lại làm minh chứng quy trình.

> **Đổi stack backend (v2)**: Laravel/PHP đã được thay bằng Java Spring Boot ở bảng trên. Lý do và đánh đổi: Spring Boot đòi hỏi nhiều boilerplate hơn (DTO, mapper, migration) và không có sẵn các package "all-in-one" như Spatie, nhưng cho câu chuyện kiến trúc "doanh nghiệp" chặt chẽ hơn (DI, layered architecture rõ ràng, JPA/Hibernate, testing với Testcontainers) — phù hợp nếu hội đồng đánh giá cao kiến trúc backend hoặc nhóm/khoa quen Java hơn PHP.

---

## 3. Kiến trúc hệ thống

```
[Vue 3 SPA] ──HTTPS──> [Nginx] ──> [Spring Boot API]
                                        │
                        ┌───────────────┼───────────────┐
                        │               │               │
                    [MySQL 8]      [Redis]        [Meilisearch]
                   dữ liệu chính   cache            chỉ mục tìm kiếm
                                        │
                                  [RabbitMQ + Consumer]
                              xử lý ảnh, mail, tính rating
                                        │
                                   [S3 / MinIO]
```

**Phân lớp trong Spring Boot** (nêu rõ trong chương thiết kế):
```
Controller (@RestController)  →  DTO Request (Bean Validation)
                               →  Service (nghiệp vụ, @Transactional)
                               →  Repository (Spring Data JPA)
                               →  Entity
                               →  DTO Response (MapStruct mapper)
```
Kèm Application Event/Listener cho các tác vụ phụ: `ReviewCreatedEvent` (`ApplicationEventPublisher` + `@EventListener`/`@Async`) → cập nhật `avg_rating`, gửi thông báo, cộng điểm đóng góp. Lỗi được chuẩn hóa qua `@RestControllerAdvice` trả về `ProblemDetail` (RFC 7807, có sẵn từ Spring 6).

---

## 4. Danh sách chức năng theo tác nhân

### Khách vãng lai
- Xem trang chủ, địa điểm nổi bật, danh mục.
- Tìm kiếm theo từ khóa, lọc theo danh mục / khoảng giá / tiện ích / khoảng cách / điểm sao.
- Xem bản đồ và danh sách "gần tôi".
- Xem chi tiết địa điểm, ảnh, giờ mở cửa, đánh giá.
- Đăng ký / đăng nhập.

### Thành viên (kế thừa khách vãng lai)
- Viết đánh giá: chấm sao, nội dung, ảnh, ngày đã đến.
- Sửa / xóa đánh giá của chính mình.
- Bình luận vào đánh giá, vote "hữu ích".
- Check-in tại địa điểm (có kiểm tra khoảng cách GPS).
- Tạo và chia sẻ bộ sưu tập địa điểm.
- Đề xuất địa điểm mới.
- Theo dõi người dùng khác, xem bảng xếp hạng đóng góp.
- Báo cáo nội dung vi phạm.
- Quản lý hồ sơ, đổi mật khẩu, xem thông báo.

### Chủ địa điểm (kế thừa thành viên)
- Gửi yêu cầu xác nhận sở hữu (kèm minh chứng).
- Cập nhật thông tin, ảnh, giờ mở cửa, tiện ích.
- Phản hồi công khai vào đánh giá.
- Xem thống kê: lượt xem, lượt check-in, phân bố điểm sao.

### Quản trị viên / Kiểm duyệt viên
- Duyệt / từ chối địa điểm và yêu cầu sở hữu.
- Xử lý hàng đợi báo cáo, ẩn nội dung, khóa tài khoản.
- Quản lý danh mục, tiện ích.
- Quản lý ảnh trang chủ — 3 ô cố định *(cắt 2026-09-27, mở lại phạm vi tối thiểu 2026-10-17 — docs/requirements.md Q4, FR-43, UC34)*.
- Dashboard thống kê: người dùng mới, review theo ngày, địa điểm hot.

---

## 5. Thiết kế cơ sở dữ liệu

### Bảng lõi (đã có trong ERD)
`users`, `categories`, `places`, `place_photos`, `opening_hours`, `amenities`, `place_amenity`, `reviews`, `review_photos`, `review_votes`, `comments`, `check_ins`, `collections`, `collection_place`, `reports`

### Bảng mở rộng
| Bảng | Mục đích |
|---|---|
| `place_claims` | Yêu cầu xác nhận sở hữu: `place_id`, `user_id`, `evidence`, `status`, `reviewed_by` |
| `owner_replies` | Phản hồi của chủ quán: `review_id`, `user_id`, `content` |
| `follows` | `follower_id`, `following_id` |
| `badges` / `user_badges` | Huy hiệu đóng góp (Reviewer cấp 1–5, Nhà thám hiểm...) |
| `tags` / `taggables` | Thẻ tự do: "view đẹp", "hợp gia đình" |
| `place_views` | Log lượt xem (dạng gộp theo ngày để không phình bảng) |
| `notifications` | Bảng thông báo tự thiết kế: `user_id`, `type`, `data` (JSON), `read_at` |
| `activity_log` | Nhật ký thao tác admin — tự viết qua Spring AOP `@Aspect` ghi log quanh các thao tác admin (hoặc Hibernate Envers cho audit entity) |
| `roles`, `permissions`, `role_permissions`, `user_roles` | Bảng RBAC tự thiết kế (Spring không có sẵn như Spatie) |

### Ràng buộc quan trọng
- `reviews`: `UNIQUE(place_id, user_id)` — mỗi người chỉ đánh giá một địa điểm một lần.
- `review_votes`: `UNIQUE(review_id, user_id)`.
- `collection_place`: khóa chính tổ hợp `(collection_id, place_id)`.
- `places.location`: `POINT NOT NULL SRID 4326` + `SPATIAL INDEX`.
- `places.slug`, `users.email`, `categories.slug`: UNIQUE.
- Index tổ hợp: `places(category_id, status, bayesian_score)` *(đổi từ `avg_rating` — 2026-09-28, docs/design/database.md D3: xếp hạng dùng điểm Bayesian, `avg_rating` chỉ là trung bình thô để hiển thị)*, `reviews(place_id, status, created_at)`.
- Soft delete cho `places`, `reviews`, `comments`, `users`.

### Công thức xếp hạng (Bayesian average)
```
điểm_hiển_thị = (v / (v + m)) × R + (m / (v + m)) × C

v = số review của địa điểm
R = trung bình sao của địa điểm
m = số review tối thiểu để đáng tin (ví dụ 10)
C = trung bình sao toàn hệ thống
```
Quán 5 sao / 1 review sẽ không vượt mặt quán 4.6 sao / 300 review. Đây là điểm dễ ghi điểm khi bảo vệ — nhớ chuẩn bị ví dụ số cụ thể.

### Chống review ảo
- Rate limit: tối đa N review / ngày / tài khoản.
- Bắt buộc xác thực email trước khi viết review đầu tiên.
- Trust score tăng theo thời gian hoạt động và số vote "hữu ích" nhận được; review từ tài khoản trust thấp bị đưa vào hàng chờ duyệt.
- Cảnh báo khi nhiều review cùng địa điểm đến từ cùng dải IP trong thời gian ngắn.
- Check-in yêu cầu tọa độ GPS nằm trong bán kính ~200m của địa điểm.

---

## 6. Thiết kế API (REST, prefix `/api/v1`)

> Bảng dưới là tóm tắt. Đặc tả đầy đủ (83 operation, schema, mã lỗi, quyền): [docs/api/openapi.yaml](docs/api/openapi.yaml). Các chỗ đổi so với bản đầu được đánh dấu *(2026-09-29)*.

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/auth/register`, `/auth/login`, `/auth/logout`, `/auth/refresh`, `/auth/verify-email`, `/auth/forgot-password`, `/auth/reset-password` | Xác thực *(bổ sung các luồng FR-02/04/05)* |
| GET | `/places` | Danh sách + lọc + phân trang |
| GET | `/places/nearby?lat=&lng=&radius=` | Tìm theo bán kính |
| GET | `/places/{slug}` | Chi tiết |
| POST | `/places` | Đề xuất địa điểm mới |
| GET | `/places/{id}/reviews` | Đánh giá của địa điểm |
| POST | `/places/{id}/reviews` | Viết đánh giá |
| PATCH/DELETE | `/reviews/{id}` | Sửa / xóa |
| PUT/DELETE | `/reviews/{id}/vote` | Vote / bỏ vote hữu ích *(đổi từ POST: idempotent, bấm 2 lần không đảo trạng thái ngoài ý muốn)* |
| POST | `/reviews/{id}/comments` | Bình luận |
| POST | `/places/{id}/check-in` | Check-in |
| CRUD | `/collections`, `PUT/DELETE /collections/{id}/places/{placeId}` | Bộ sưu tập |
| POST | `/reports` | Báo cáo vi phạm |
| GET | `/search?q=` | Tìm kiếm qua Meilisearch (fallback MySQL `LIKE` — U9) |
| * | `/owner/*` | Chủ địa điểm: sửa thông tin, ảnh, thống kê *(tách riêng: tránh trùng đường dẫn với `/places/{slug}`)* |
| * | `/moderation/*` | Kiểm duyệt viên: hàng chờ địa điểm / review / báo cáo / yêu cầu sở hữu *(tách khỏi `/admin` theo phân quyền Q2)*; `GET /moderation/queue` gộp 4 loại, xếp FIFO *(2026-09-29)* |
| CRUD | `/admin/*` | Quản trị viên: người dùng, role, danh mục, tiện ích, dashboard |

**Quy ước chung**: phân trang cursor cho danh sách dài, chuẩn hóa lỗi theo RFC 7807, versioning bằng prefix URL, response là DTO map bằng MapStruct *(thay "API Resource" của Laravel)*.

---

## 7. Cấu trúc source code

### Backend
```
backend/
├── src/main/java/com/localspot/
│   ├── controller/{PlaceController,ReviewController,AuthController,AdminController}.java
│   ├── dto/
│   │   ├── request/{PlaceRequest,ReviewRequest}.java
│   │   └── response/{PlaceResponse,ReviewResponse}.java
│   ├── entity/{User,Place,Review,Category,...}.java
│   ├── mapper/{PlaceMapper,ReviewMapper}.java        # MapStruct
│   ├── service/{PlaceService,ReviewService,RatingCalculator}.java
│   ├── repository/{PlaceRepository,ReviewRepository}.java   # Spring Data JPA
│   ├── event/{ReviewCreatedEvent,PlaceApprovedEvent}.java
│   ├── listener/{RecalculateRatingListener,NotificationPushListener}.java
│   ├── amqp/{ImageProcessingConsumer,SearchReindexConsumer}.java
│   ├── security/{SecurityConfig,UserJwtAuthenticationConverter,JwtService,PermissionEvaluator}.java  # filter Bearer có sẵn của Spring Security
│   ├── config/{WebSocketConfig,OpenApiConfig,RedisConfig}.java
│   └── exception/{GlobalExceptionHandler,ApiException}.java
├── src/main/resources/
│   ├── application.yml (+ application-dev.yml, application-prod.yml)
│   └── db/migration/  # Flyway V1__init.sql, V2__..., seed data
└── src/test/java/com/localspot/          # JUnit 5 + Testcontainers
```

### Frontend
```
frontend/src/
├── api/            # axios client + module theo domain
├── assets/
├── components/
│   ├── ui/         # Button, Modal, Rating, Skeleton
│   ├── place/      # PlaceCard, PlaceMap, PlaceFilters
│   └── review/     # ReviewItem, ReviewForm, PhotoUploader
├── composables/    # useAuth, useGeolocation, useInfiniteScroll
├── layouts/        # DefaultLayout, AdminLayout
├── pages/          # Home, PlaceDetail, Search, Profile, Admin/*
├── router/
├── stores/         # auth, place, filter, notification
├── types/          # interface TypeScript
└── utils/
```

**Quy ước git**: Conventional Commits (`feat:`, `fix:`, `docs:`), nhánh `main` / `develop` / `feature/*`. Lịch sử commit sạch cũng là thứ hội đồng nhìn vào.

---

## 8. Cấu trúc báo cáo tốt nghiệp

**Phần đầu**: Bìa, lời cảm ơn, lời cam đoan, nhận xét GVHD, mục lục, danh mục hình / bảng / từ viết tắt.

**Chương 1 — Tổng quan đề tài** (8–12 trang)
- Lý do chọn đề tài, tính cấp thiết.
- Khảo sát hiện trạng: so sánh Foody, Google Maps, TripAdvisor — bảng đối chiếu ưu/nhược điểm.
- Mục tiêu, phạm vi, đối tượng sử dụng.
- Phương pháp thực hiện, cấu trúc báo cáo.

**Chương 2 — Cơ sở lý thuyết** (12–18 trang)
- Kiến trúc SPA và REST API; so sánh với MPA.
- Vue 3: Composition API, reactivity, Pinia, vòng đời component.
- Spring Boot: Inversion of Control/Dependency Injection, Spring MVC, Spring Data JPA/Hibernate, AOP, filter chain của Spring Security.
- Cơ chế xác thực: session vs JWT vs OAuth2, lý do chọn JWT + Spring Security.
- CSDL quan hệ, chuẩn hóa, chỉ mục, chỉ mục không gian.
- Công cụ tìm kiếm toàn văn và bài toán tiếng Việt.
- Thuật toán xếp hạng có trọng số.

**Chương 3 — Phân tích và thiết kế hệ thống** (25–35 trang, chương nặng nhất)
- Yêu cầu chức năng (bảng đánh số FR-01…) và phi chức năng (hiệu năng, bảo mật, khả dụng).
- Sơ đồ use case tổng quát + đặc tả chi tiết 8–10 use case quan trọng (luồng chính, luồng thay thế, ngoại lệ).
- Sơ đồ hoạt động cho 3–4 nghiệp vụ: viết đánh giá, duyệt địa điểm, check-in.
- Sơ đồ tuần tự cho 3–4 luồng: đăng nhập, tìm kiếm, đăng review kèm ảnh.
- Sơ đồ lớp.
- ERD + mô tả chi tiết từng bảng (tên cột, kiểu, ràng buộc, ý nghĩa).
- Thiết kế giao diện: sitemap, wireframe, prototype.

**Chương 4 — Cài đặt và triển khai** (15–20 trang)
- Môi trường phát triển, cấu hình Docker.
- Cấu trúc mã nguồn, giải thích các module chính.
- Trình bày một vài đoạn mã tiêu biểu (truy vấn không gian, tính Bayesian rating, xử lý ảnh bất đồng bộ).
- Ảnh chụp giao diện kèm mô tả.
- Tài liệu API.
- Quy trình triển khai và CI/CD.

**Chương 5 — Kiểm thử và đánh giá** (8–12 trang)
- Chiến lược kiểm thử: unit, feature, E2E.
- Bảng test case (mã, mô tả, dữ liệu vào, kết quả mong đợi, kết quả thực tế).
- Kết quả đo hiệu năng: thời gian phản hồi API, điểm Lighthouse.
- Đánh giá mức độ hoàn thành so với yêu cầu ban đầu; hạn chế còn tồn tại.

**Chương 6 — Kết luận và hướng phát triển** (3–5 trang)
- Kết quả đạt được, bài học rút ra.
- Hướng mở rộng: gợi ý cá nhân hóa, ứng dụng mobile, mở rộng đa ngôn ngữ.

**Phần cuối**: Tài liệu tham khảo (chuẩn IEEE hoặc APA), phụ lục (bảng CSDL đầy đủ, hướng dẫn cài đặt).

---

## 9. Lộ trình 14 tuần

| Tuần | Nội dung | Đầu ra |
|---|---|---|
| 1 | Khảo sát, chốt phạm vi, viết đặc tả yêu cầu | Chương 1 bản nháp |
| 2 | Use case, ERD, sơ đồ hoạt động/tuần tự | Chương 3 phần phân tích |
| 3 | Wireframe, prototype Figma, thiết kế API | Prototype + tài liệu API |
| 4 | Dựng Docker, migration, seeder, auth | Đăng ký/đăng nhập chạy được |
| 5–6 | CRUD địa điểm, upload ảnh, danh mục, admin duyệt | Module địa điểm hoàn chỉnh |
| 7 | Tìm kiếm, lọc, tích hợp Meilisearch, bản đồ | Trang tìm kiếm chạy được |
| 8–9 | Đánh giá, ảnh review, vote, bình luận, tính rating | Module đánh giá hoàn chỉnh |
| 10 | Check-in, bộ sưu tập, theo dõi, huy hiệu | Tính năng cộng đồng |
| 11 | Trang chủ quán, dashboard admin, thống kê | Hoàn thiện chức năng |
| 12 | Viết test, tối ưu truy vấn, hoàn thiện UI, deploy | Bản chạy thật trên VPS |
| 13 | Viết chương 4–5, chụp màn hình, quay demo | Báo cáo gần hoàn chỉnh |
| 14 | Rà soát toàn bộ, chuẩn bị slide, tập bảo vệ | Bản nộp cuối |

Chừa dư 1–2 tuần đệm nếu lịch cho phép — phần viết báo cáo hầu như luôn ngốn nhiều thời gian hơn dự kiến.

---

## 10. Checklist

### A. Chuẩn bị
- [x] Chốt tên dự án và domain
- [x] Khảo sát 3–5 sản phẩm tương tự, ghi bảng so sánh
- [x] Viết đặc tả yêu cầu chức năng (đánh mã FR-01…) — [docs/requirements.md](docs/requirements.md)
- [x] Viết đặc tả yêu cầu phi chức năng — [docs/requirements.md](docs/requirements.md)
- [x] Chốt phạm vi và ghi rõ phần ngoài phạm vi — MoSCoW + ngoài phạm vi theo §1, xem [docs/requirements.md](docs/requirements.md)
- [x] Tạo repo git, thiết lập nhánh và quy ước commit — [CONTRIBUTING.md](CONTRIBUTING.md), nhánh `main`/`develop` đã push
- [x] Tạo board quản lý task — [GitHub Projects #1](https://github.com/users/hiepfre3101/projects/1)

### B. Phân tích & thiết kế
- [x] Sơ đồ use case tổng quát — [docs/diagrams/use-case-overview.puml](docs/diagrams/use-case-overview.puml)
- [x] Đặc tả chi tiết ít nhất 8 use case — 10 UC, [docs/analysis/use-cases.md](docs/analysis/use-cases.md)
- [x] Sơ đồ hoạt động (≥3 nghiệp vụ) — [docs/diagrams/](docs/diagrams/README.md)
- [x] Sơ đồ tuần tự (≥3 luồng) — [docs/diagrams/](docs/diagrams/README.md)
- [x] Sơ đồ lớp — [class-domain, class-review-module](docs/diagrams/README.md)
- [x] ERD hoàn chỉnh — [docs/diagrams/erd-*.puml](docs/diagrams/README.md)
- [x] Bảng mô tả chi tiết từng bảng CSDL — [docs/design/database.md](docs/design/database.md)
- [x] Sitemap và wireframe — [docs/design/sitemap.md](docs/design/sitemap.md), [sitemap.puml](docs/diagrams/sitemap.puml)
- [x] Prototype các màn hình chính — làm trên **Claude Design** thay cho Figma (design system + màn P01–P12 đã duyệt 2026-09-29; bản xuất cục bộ `docs/design-system/`, không commit)
- [x] Đặc tả API (OpenAPI) — [docs/api/openapi.yaml](docs/api/openapi.yaml)

### C. Thiết lập môi trường
- [x] Docker Compose: app, nginx, mysql, redis, rabbitmq, meilisearch, minio — app + nginx dưới profile `app`; dev chỉ bật hạ tầng
- [x] Khởi tạo Spring Boot (Spring Initializr), cấu hình `application.yml` theo profile (dev/prod)
- [x] Cài Vue 3 + Vite + TypeScript + Tailwind — theme Tailwind = token design system
- [x] Thiết lập ESLint, Prettier (frontend); Spotless, SpotBugs (backend) — bỏ Checkstyle (trùng formatter)
- [x] Thiết lập JUnit 5 + Testcontainers và Vitest
- [x] GitHub Actions chạy lint + test — [.github/workflows/ci.yml](.github/workflows/ci.yml)

### D. Backend — nền tảng
- [x] Toàn bộ Flyway migration + khóa ngoại + index — `V1__init.sql` (32 bảng), `V2__seed_rbac.sql`, `V3__seed_catalog.sql`; xem [database.md §3.5](docs/design/database.md)
- [x] Entity (JPA), quan hệ, dữ liệu mẫu qua seed migration/`CommandLineRunner` — 29 entity + 3 bảng nối `@ManyToMany`; seed demo Flyway `db/seed/dev/R__demo_*.sql` chỉ ở profile dev (60 tài khoản, 300 địa điểm; review demo làm ở E sau khi có service tính rating/trust)
- [x] Cấu hình Spring Security + JWT, API đăng ký / đăng nhập / đăng xuất / refresh token — kèm `GET /me` và trust score (requirements §5.1)
- [x] Xác thực email, quên mật khẩu, đổi mật khẩu — mail qua RabbitMQ (retry 4 lần → DLQ `mail.send.dlq`), Mailpit cho dev
- [x] RBAC: bảng role/permission tự thiết kế + `@PreAuthorize` — `@EnableMethodSecurity`, hằng số `Permissions` (test đối chiếu CSDL ↔ code ↔ openapi, quét endpoint `/admin|/moderation|/owner` thiếu `@PreAuthorize`); API UC31 `/admin/users` (tìm kiếm, khóa / mở khóa, gán role) làm sớm từ E
- [x] Method security / `PermissionEvaluator` cho Place, Review, Comment — `OwnershipPermissionEvaluator`: `hasPermission(id, loại, permission)` cho 6 permission `*-own`, kiểm RBAC → tồn tại (404) → chủ sở hữu; không có đường vượt quyền cho nhân sự; endpoint gắn ở E
- [x] `@RestControllerAdvice` chuẩn hóa response và exception (RFC 7807 `ProblemDetail`) — mọi lỗi có `code`; 400 `MALFORMED_REQUEST` / 422 / 409 (optimistic lock, trùng UNIQUE) / 500 kèm `errorId`; lỗi CSDL phân loại theo mã MySQL (1062, 3819, 1452); `/error` cùng định dạng cho lỗi ngoài Spring MVC; response thành công không bọc envelope
- [x] Rate limiting cho API nhạy cảm (Redis) — cửa sổ trượt bằng Lua (không thêm thư viện); đăng nhập / đăng ký / quên mật khẩu / gửi lại mail xác thực / bình luận (20 / 10 phút / tài khoản — thêm 2026-10-17); 429 + `Retry-After`; Redis sập → cho qua; giới hạn 5 review / 24 giờ đếm trong CSDL ở E

### E. Backend — nghiệp vụ
- [x] CRUD địa điểm + luồng duyệt — đề xuất (multipart; phần `photos` ở E2), chi tiết theo người xem (lượt xem theo ngày, phân bố sao, `claimable`, `myReviewId`), danh sách lọc + keyset tổ hợp, `/me/places`, chủ sửa `/owner/places`, hàng chờ + duyệt / từ chối (chỉ PENDING, cấm tự duyệt). Không có xóa — ẩn qua xử lý báo cáo (UC29); `/places/duplicates` làm ở E4
- [x] Upload ảnh, resize, nén, lưu S3 qua queue — ảnh địa điểm: kèm đề xuất + `/owner/places/{id}/photos` (thêm / xóa); kiểm tra chữ ký file + đuôi + 5 MB + 40 MP; ảnh gốc `incoming/` (riêng tư) → queue `photo.process` (retry → DLQ) → 3 bản JPEG thumb/medium/large, không EXIF; bucket public-read, giới hạn 30 ảnh/địa điểm (chốt 2026-10-06). Ảnh review dùng lại pipeline ở mục "Ảnh đính kèm review"
- [x] Danh mục phân cấp, tiện ích, giờ mở cửa — `GET /categories` (cây ≤ 2 cấp), `GET /amenities` (dùng chung — O7) có Spring Cache + Redis, xóa sau commit; quản trị `/admin/categories`, `/admin/amenities` (tạo / sửa / xóa khi không còn dùng, ghi nhật ký FR-42). Giờ mở cửa đã làm ở E1 (đề xuất, chủ sửa, chi tiết). Chốt 2026-10-06
- [x] Truy vấn địa điểm theo bán kính (spatial index) — `GET /places/nearby` (2 km mặc định, 100 m – 20 km, lọc danh mục gồm con, gần nhất trước + `distanceM`): `MBRContains` + `FORCE INDEX (sx_places_location)` rồi `ST_Distance_Sphere`, test `EXPLAIN` (NFR-03); `GET /places/duplicates` (U7: tên gần giống trong 50 m, đã duyệt + đề xuất chờ của chính mình — chốt 2026-10-06); hàng chờ `/moderation/places` kèm `possibleDuplicates` (gồm đề xuất chờ của mọi người) để kiểm duyệt viên bắt trùng giữa người dùng
- [x] Tích hợp Meilisearch, cấu hình synonym tiếng Việt — `GET /search` (lọc như `/places` + `sort` RELEVANCE / SCORE / NEWEST / MOST_REVIEWED, cursor theo vị trí); Meilisearch tự bỏ dấu + `đ → d`, thêm synonym (`search/synonyms.json`, 30 nhóm), typo từ 4 ký tự, `matchingStrategy = last` + bỏ từ chỉ loại hình quán khỏi truy vấn (đo trên dữ liệu tiếng Việt, chốt 2026-10-08); chỉ trả id → nạp lại MySQL, chỉ giữ địa điểm đang APPROVED. Đồng bộ sau commit qua queue `search.reindex` (retry → DLQ) khi duyệt / chủ sửa / đổi tên danh mục + toàn bộ lúc khởi động; fallback `LIKE` trên tên (U9) với `degraded`, ngừng gọi Meilisearch 30 s sau lỗi. Rating / ẩn địa điểm phát `PlaceIndexChangedEvent` khi làm các mục đó
- [x] CRUD đánh giá + ràng buộc mỗi người một review — viết (multipart, phần `review`; ảnh ở mục sau) với **đủ cổng UC12**: email đã xác thực → validate (≥ 20 ký tự, ngày đến ≤ hôm nay giờ VN) → địa điểm APPROVED → chủ không tự đánh giá → 1 review / địa điểm kể cả đã xóa (D2) → ≤ 5 / 24 h đếm cả đã xóa (khóa dòng user chống request chen) → trust ≥ 30 và luật IP (/24, IPv6 /64) ⇒ PUBLISHED / PENDING + `ip_flagged`; sửa theo trạng thái (HIDDEN khóa, REJECTED gửi lại → PENDING, trust < 30 → PENDING); xóa mềm; danh sách NEWEST / HELPFUL (keyset) + lọc sao, `/me/reviews`, `/users/{id}/reviews`; `votedByMe` / `commentCount` / `ownerReply` nạp theo lô. Phát `ReviewChangedEvent` khi tập PUBLISHED đổi (listener ở mục tính lại rating). Hàng chờ / duyệt (UC28) ở mục trust score. Chốt 2026-10-08
- [x] Ảnh đính kèm review — kèm khi viết (multipart `photos`) + thêm / xóa sau (`/reviews/{id}/photos`), tổng ≤ 10 / review (ảnh lỗi không tính), không đổi trạng thái review, `HIDDEN` khóa; dùng chung pipeline E2 (`PhotoFiles`: kiểm tra upload, ảnh gốc `incoming/reviews/…`, 3 bản JPEG; queue `photo.process` với `target` PLACE / REVIEW). Ảnh chưa READY chỉ tác giả thấy; không gộp vào gallery địa điểm. Chốt 2026-10-09
- [x] Vote hữu ích, bình luận (có phân cấp) — `PUT` / `DELETE /reviews/{id}/vote` idempotent (`INSERT IGNORE` / `DELETE` theo `counted`, bộ đếm chỉ đổi theo số dòng ảnh hưởng), `counted` = trust người vote ≥ 30 lúc vote → `users.helpful_votes_count` của tác giả; không vote review của mình (409); khóa `users` (id tăng dần) → `reviews` trước khi ghi để tránh deadlock khóa ngoại; cột đếm `insertable/updatable = false` chống mất cập nhật. Bình luận 2 cấp, cần email đã xác thực, gốc cũ trước + toàn bộ trả lời, gốc đã xóa / ẩn còn trả lời → placeholder `deleted`. Chỉ trên review đang hiện. Chốt 2026-10-11
- [x] Tính lại rating bằng Bayesian average qua Spring Event + `@Async` listener — `RatingCalculator` (hàm thuần, `v = 0 → 0`), `RatingService` đếm lại từ đầu (không ± 1) sau khi khóa dòng địa điểm, `C` = AVG mọi review PUBLISHED trên địa điểm APPROVED, `m` cấu hình (10); `RecalculateRatingListener` AFTER_COMMIT + `@Async` trên `ReviewChangedEvent`; `RatingRecalculationJob` 03:00 (D4) tính lại toàn bộ + đối soát sự kiện mất; điểm đổi → `PlaceIndexChangedEvent`; cột điểm `insertable/updatable = false`. Seed review demo làm ở mục riêng ngay sau. Chốt 2026-10-11
- [x] Seed review demo — `R__demo_03_reviews.sql` (profile dev, tất định, idempotent): 2046 review (1879 PUBLISHED / 143 PENDING / 24 REJECTED + vi phạm đã hết hạn), độ phổ biến lệch (27 địa điểm chưa có review, 15 địa điểm ≥ 25) để minh họa Bayesian; 8799 vote (bộ đếm đếm lại từ `review_votes`), 908 bình luận + 476 trả lời. Vòng lặp trust phá bằng **nhóm kỳ cựu** (tài khoản ≥ 250 ngày — chỉ vote của họ `counted`, seed chỉ tính thiếu, không tính dư); **người mới** (< 60 ngày) ít review, đều PENDING → còn tài khoản trust < 30 để demo hàng chờ. Điểm + ảnh review qua `DemoDataBootstrap` khi khởi động (`RatingService.recalculateAll()`, ảnh minh họa qua `photo.process`) — không chép công thức ra SQL. Chốt 2026-10-12
- [x] Check-in kèm kiểm tra khoảng cách GPS — `POST /places/{id}/check-in`: email đã xác thực → `accuracyM` ≤ 100 → địa điểm APPROVED → không phải chủ → 1 lần / địa điểm / ngày giờ VN (khóa dòng user → 409 mã riêng khi bấm chen) → ≤ 200 m bằng `ST_Distance_Sphere` (422 kèm `distanceM`); `checkin_count` +1 nguyên tử, cột chỉ đọc trên entity. `GET /me/check-ins` chỉ chính chủ (không công khai lịch sử vị trí); `Review.authorCheckedIn` (check-in vào ngày viết hoặc trước — nhãn P04) nạp theo lô; seed demo `R__demo_04_checkins.sql`. Chốt 2026-10-12
- [x] Bộ sưu tập và chia sẻ công khai — CRUD `/collections`, `PUT/DELETE …/places/{placeId}` idempotent (đã có → chỉ sửa ghi chú), `/me/collections`, `/users/{id}/collections` (chỉ công khai — tab P06), `/shared/collections/{shareSlug}`. Công khai = hiện trên trang cá nhân + link đọc được (tên bỏ dấu + 6 ký tự ngẫu nhiên); về riêng tư thu hồi link, công khai lại sinh link mới. Bộ riêng tư / thao tác lên bộ người khác → 404 (không tiết lộ tồn tại). Chỉ thêm địa điểm APPROVED; bị ẩn sau đó → lọc với người khác, chủ thấy kèm trạng thái. Trần 50 bộ / 100 địa điểm dưới khóa dòng; seed demo `R__demo_05_collections.sql`. Chốt 2026-10-12
- [x] Theo dõi người dùng, huy hiệu, bảng xếp hạng — `PUT/DELETE /users/{id}/follow` idempotent (tự theo dõi 409), danh sách theo dõi công khai (keyset theo thời điểm theo dõi), hồ sơ công khai `GET /users/{id}` (số theo dõi, điểm, huy hiệu, `followedByMe`). O3: điểm **tính từ nội dung đang hiện** (review +10, ảnh +2 ≤ 5 / review, vote hợp lệ +1, check-in +1, đề xuất được duyệt +15) — `contribution_points` là bộ nhớ đệm, tính lại sau sự kiện (listener nền, dùng lại `ReviewChangedEvent` + `ContributionChangedEvent` mới) và job đêm 03:30; 8 huy hiệu (`V4__seed_badges.sql`, `BadgeRule`) trao một lần; `/leaderboard` WEEK / MONTH (7 / 30 ngày trượt) / ALL + lọc thành phố, hạng kiểu thi đấu, cache Redis 5 phút. Chốt 2026-10-12
- [x] Báo cáo vi phạm (quan hệ đa hình) — `POST /reports` (UC17): email đã xác thực, `detail` bắt buộc với WRONG_INFO / OTHER, chỉ nội dung đang hiện công khai (404 theo loại), không tự báo cáo review / bình luận / chính mình (địa điểm thì được — kênh duy nhất báo tên / vị trí sai), 1 lần / đối tượng kể cả đã xử lý (U10). Hàng đợi `/moderation/reports` gom theo đối tượng, FIFO theo báo cáo đầu (keyset `MIN(id)`), kèm lý do, đoạn trích, `targetStatus`; chi tiết `/moderation/reports/{type}/{id}` (thêm vào openapi). `decision` HIDE / DISMISS áp lên mọi báo cáo mở dưới khóa dòng (người sau 409), không tự xử lý nội dung / địa điểm của mình. **Không sửa nội dung** (O8); vi phạm khi ẩn: tác giả review / bình luận, người bị báo cáo (USER chỉ ghi vi phạm, không ẩn), địa điểm → người đề xuất chỉ khi `confirmedReason` SPAM / FAKE / OFFENSIVE (O9). Đối tượng đã xóa → RESOLVED không phạt (3c). `ViolationService` dùng chung với UC28; ghi `activity_log` REPORT_HIDE / REPORT_DISMISS. Thông báo cho tác giả / người báo cáo gắn ở mục thông báo. Chốt 2026-10-14
- [x] Trust score và hàng đợi duyệt review — trust tính lúc đọc từ D3 (`TrustScoreCalculator`, không lưu cột — D1); UC28: `GET /moderation/reviews` (PENDING trên địa điểm APPROVED, FIFO theo id, lọc `ipFlagged`) kèm trust tác giả, số vi phạm còn hiệu lực, cờ IP, số review cùng dải IP ±24 h (một truy vấn cho cả trang); `GET /moderation/reviews/{id}` (thêm vào openapi — route `/admin/moderation/reviews/:id`): lịch sử tác giả theo trạng thái + review cùng dải IP. Quyết định chỉ trên PENDING (409 `REVIEW_ALREADY_MODERATED`), `@Version` cho UC28 3b (người sau 409, vi phạm rollback cùng), không tự duyệt review của mình / về địa điểm mình sở hữu; duyệt → `ReviewChangedEvent` (Bayesian, điểm đóng góp); từ chối → lý do bắt buộc + 1 vi phạm qua `ViolationService`; activity_log REVIEW_APPROVE / REVIEW_REJECT. Thông báo gắn ở mục thông báo. Chốt 2026-10-15
- [x] Yêu cầu xác nhận sở hữu địa điểm — `POST /places/{id}/claims` (UC23): email đã xác thực → 1–5 ảnh JPEG / PNG (kiểm tra như ảnh địa điểm) → địa điểm APPROVED → chưa có chủ (409 `PLACE_ALREADY_OWNED`) → chưa có yêu cầu đang chờ của chính mình (409 `CLAIM_ALREADY_PENDING`, khóa dòng user); minh chứng nguyên bản ở `claims/{placeId}/…` (riêng tư, dọn khi rollback). UC30: hàng chờ FIFO kèm `otherPendingClaimCount`, `GET /moderation/claims/{id}` (thêm — route sitemap), link tạm presigned 5 phút (200 JSON `{url, expiresAt}`, `no-store` — đổi từ 302 vì token chỉ gửi bằng header, S1). Duyệt khóa dòng địa điểm trước → không bao giờ hai chủ (U6); gán owner + role OWNER (hiệu lực ngay), tự từ chối yêu cầu khác, **ẩn review sẵn có của chủ mới** (supervisor chốt 2026-10-16) + tính lại điểm; từ chối cần lý do, không vi phạm, gửi lại được; không tự duyệt; activity_log CLAIM_APPROVE / CLAIM_REJECT. Chốt 2026-10-16
- [x] Phản hồi của chủ quán — `PUT` / `DELETE /reviews/{id}/reply` (UC25, FR-33): `@PreAuthorize` `review:reply-own-place` (chủ **hiện tại** — `OwnershipPermissionEvaluator` có sẵn từ D6); chỉ review đang hiện công khai (404 như vote / bình luận); một PUT tạo hoặc sửa, khóa dòng review chống trùng `UNIQUE(review_id)`; chủ mới sửa phản hồi của chủ cũ → đứng tên chủ mới; xóa hẳn, idempotent, gỡ được cả khi review đã bị ẩn. Hiện ở `Review.ownerReply` (nạp theo lô từ E6). Thông báo cho tác giả review ở mục thông báo. Chốt 2026-10-16
- [x] Thông báo (bảng `notifications` + Spring WebSocket/STOMP) — `NotificationService` ghi **trong transaction** của thao tác gây ra (rollback → không có thông báo), đẩy sau commit (`NotificationPushListener` → `/user/queue/notifications`); `data` là ảnh chụp. 11 loại, đúng các sự kiện trong UC (supervisor chốt 2026-10-16): kết quả duyệt địa điểm / review / yêu cầu sở hữu (gồm tự từ chối), review mới cho chủ, chủ phản hồi lần đầu, ẩn sau báo cáo (người liên quan + người báo cáo; bỏ qua không gửi), yêu cầu sở hữu mới cho mọi kiểm duyệt viên. STOMP qua WebSocket thuần `/ws`, token trong khung CONNECT (`StompAuthInterceptor`, dùng lại JwtDecoder + converter của REST), chỉ SUBSCRIBE kênh cá nhân, không SEND; handshake `/ws` permitAll trong `SecurityConfig`. `GET /me/notifications` (mới nhất trước, `unreadOnly`), `unread-count`, đánh dấu đã đọc (idempotent, của người khác 404), đọc tất cả. Chốt 2026-10-16
- [x] API quản trị và thống kê — `GET /moderation/queue` (hàng chờ gộp PLACE / REVIEW / REPORT / CLAIM: một `UNION ALL` dùng đúng điều kiện của bốn hàng chờ riêng, chỉ ghép loại người gọi có quyền, `type` không có quyền → 403; FIFO `(submittedAt, type, id)`, keyset trên bộ ba, `position` đếm lại theo hàng chờ hiện tại; tiêu đề dùng chung `ReportTargets`, cờ `IP_FLAGGED` / `LOW_TRUST` chỉ cho review); `GET /admin/dashboard` (UC33 — **gộp openapi gốc với AdminDashboard**, supervisor chốt 2026-10-17: người dùng mới / review gửi theo ngày giờ VN, tổng + kỳ trước cùng độ dài, 5 thành phố + 10 địa điểm hot theo **review đang hiện mới viết** trong khoảng, việc tồn + mục cũ nhất cùng định nghĩa hàng chờ gộp; mặc định 14 ngày, tối đa 366; không cache; **không xuất CSV**); `GET /admin/activity-log` (mới nhất trước, lọc `actorId` / `action`, người thao tác đã xóa vẫn hiện, không trả IP); `GET /owner/places/{id}/stats` (UC26 — lượt xem / check-in theo ngày, phân bố sao dùng chung trang chi tiết; mặc định 30 ngày). Đếm thẳng trên bảng gốc (`StatsQueries`, `ModerationQueueQueries`). Chốt 2026-10-17
- [x] Ảnh trang chủ (FR-43, UC34 — mở lại Q4, supervisor chốt 2026-10-17) — `V5__home_banners.sql` (bảng + permission `banner:manage` cho ADMIN); `/admin/banners` (danh sách 3 ô, `PUT …/{slot}/image` multipart, `PATCH` mô tả / bật tắt có `version`, `DELETE` gỡ); xử lý ảnh **ngay trong request** (dùng chung `ImageInspector` + `ImageResizer`, 3 bản bỏ EXIF ở `banners/` đọc công khai, ảnh gốc không lưu), ảnh mới dọn khi rollback, ảnh cũ xóa sau commit; `GET /home` thêm `banners` (cache xóa sau commit); activity_log BANNER_IMAGE_REPLACE / BANNER_UPDATE / BANNER_REMOVE. Frontend làm cùng (mục F)
- [x] Nhật ký thao tác quản trị (FR-42): `@AuditedAction` + Spring AOP aspect ghi `activity_log` — làm cùng mục đầu tiên có thao tác duyệt (CRUD địa điểm + luồng duyệt); gắn lại cho các endpoint ghi `/admin/users` của D5. Ghi trong cùng transaction với thao tác (`TransactionConfig` đặt thứ tự advice); đã gắn `PLACE_APPROVE` / `PLACE_REJECT`, `USER_LOCK` / `USER_UNLOCK` / `USER_ASSIGN_ROLES` (`GET` không ghi). API đọc `/admin/activity-log` đã làm ở "API quản trị và thống kê" (2026-10-17)

### F. Frontend
- [x] Layout, router, navigation guard — 3 layout theo sitemap §1; guard theo permission (`MeResponse.permissions`), `?redirect=` chỉ nhận đường dẫn nội bộ; header / sidebar chỉ hiện mục đã có route
- [x] Axios client, interceptor, xử lý refresh/401 — access token chỉ trong bộ nhớ; 401 → làm mới một lần cho mọi request đồng thời, khóa giữa các tab bằng Web Locks (refresh token xoay vòng — U2); lỗi chuẩn hoá từ RFC 7807
- [x] Pinia store: auth, place, filter, notification — **auth xong** (2026-10-05); **place, filter xong** (2026-10-12: filter đọc / ghi bộ lọc trên URL, place giữ vị trí người dùng + địa điểm đang chọn); **notification xong** (2026-10-19)
- [ ] Thông báo (UC19, FR-27) — **đã code 2026-10-19, chờ supervisor chạy thử**: store `notification` gắn theo phiên (đăng nhập → đọc số chưa đọc + mở STOMP `@stomp/stompjs`; đăng xuất → đóng, xoá); chuông + bảng P07 400px ở header công khai **và chân sidebar quản trị**; lọc Tất cả / Chưa đọc (lọc ở máy chủ `unreadOnly`); "Xem cũ hơn" trong bảng (O4: không có trang riêng); bấm thông báo → đánh dấu đã đọc + tới trang tương ứng; nối lại → đọc lại số + trang đầu. Thông báo yêu cầu sở hữu cho kiểm duyệt viên chưa có liên kết (màn kiểm duyệt chưa làm)
- [x] Màn tài khoản P01 (UC01–UC05) + Cài đặt — Mật khẩu (UC06) + quản trị Người dùng & vai trò P10 (UC31) — làm cùng nền tảng F, dùng API đã có của checklist D
- [x] Trang chủ: nổi bật, danh mục, mới nhất — backend `GET /home` cache Redis + `featuredReview`, thẻ `tags` trên PlaceSummary; supervisor chạy thử trên dữ liệu seed 2026-10-13
- [x] Trang tìm kiếm: bộ lọc, sắp xếp, infinite scroll — bộ lọc trên URL; "Quanh tôi" chỉ lọc danh mục + bán kính (chốt 2026-10-12)
- [x] Bản đồ Leaflet, marker cluster, "gần tôi" — ghim mang điểm, gom cụm, "Tìm trong vùng này"; lớp nền MapTiler / OSM Đức; chỉ đường trong ứng dụng (OSRM)
- [x] Trang chi tiết địa điểm: gallery, giờ mở cửa, tiện ích, review — bản đọc (thao tác ghi ở các mục sau); bỏ khối "Ảnh check-in" (check-in không có ảnh)
- [x] Form viết review: chấm sao, upload nhiều ảnh, crop — **đã code 2026-10-17, chờ supervisor chạy thử**: P04 `/places/:slug/review` (viết + sửa / xoá UC13 cùng trang, `myReviewId` quyết định chế độ); `.ls-rate` 5 radio + chữ mức; ngày đến (API bắt buộc, thiết kế thiếu — mặc định hôm nay giờ VN); ảnh ≤ 10, chuyển JPEG ở trình duyệt khi không phải JPEG / PNG ≤ 5 MB, cắt tùy chọn bằng vue-advanced-cropper; kết quả theo `status` ("Đã đăng" / "Đang chờ duyệt"). **Bỏ bước thẻ** (supervisor chốt 2026-10-17: chưa có API thành viên gắn thẻ địa điểm có sẵn — UC22 ở P05). Thêm `GET /reviews/{id}` cho màn sửa
- [x] Bình luận, vote, báo cáo — **đã code 2026-10-17, chờ supervisor chạy thử**: ReviewItem có Hữu ích (`aria-pressed`, theo kết quả backend), Bình luận (luồng 2 cấp ngay dưới: gửi / trả lời gốc / xoá của mình có xác nhận / báo cáo của người khác, gốc đã xoá thành dòng giữ chỗ, 429 nói rõ thời gian chờ), Báo cáo; đánh giá của mình → Sửa; bản chờ duyệt / bị từ chối của mình ghim đầu danh sách. "Báo cáo" địa điểm ở đầu P03, một hộp thoại báo cáo dùng chung (lý do, mô tả bắt buộc với Thông tin sai / Khác, đã báo cáo → thông báo nhẹ). Khách đọc được bình luận, thao tác ghi → đăng nhập rồi quay lại. Báo cáo người dùng làm ở trang cá nhân (P06)
- [x] Form đề xuất địa điểm mới (chọn tọa độ trên bản đồ) — **đã code 2026-10-17, chờ supervisor chạy thử**: P05 `/propose` một trang + thanh tiến độ; trường theo luồng chính UC11 (tên, danh mục, thẻ, mô tả · địa chỉ, thành phố, ghim · giá, điện thoại, web, giờ mở cửa theo ngày, tiện ích · ảnh); **vị trí = ghim trên bản đồ / "Vị trí của tôi"** (không dùng dịch vụ đổi địa chỉ ra toạ độ — supervisor chốt 2026-10-17); nghi trùng U7 (`/places/duplicates`, phải xác nhận "địa điểm khác" mới gửi — UC11 6a); **nháp tự lưu trong trình duyệt** theo tài khoản, không gồm ảnh (chốt 2026-10-17 — backend không có trạng thái nháp)
- [x] Trang cá nhân: review, ảnh, bộ sưu tập, huy hiệu — **đã code 2026-10-18, chờ supervisor chạy thử**: P06 `/users/:id` — đầu trang (ảnh, tên, ngày tham gia, giới thiệu, dải số liệu: đánh giá · điểm đóng góp · người theo dõi · đang theo dõi — hai ô sau mở danh sách công khai), Theo dõi / bỏ theo dõi (UC20), huy hiệu đã đạt; tab Đánh giá (kèm tên địa điểm; của mình: mọi trạng thái + lý do từ chối) · Bộ sưu tập · Huy hiệu · của mình thêm Check-in và Đề xuất của tôi (UC11). Supervisor chốt 2026-10-18: **chỉ trang cá nhân** (trang bộ sưu tập / chia sẻ, chỉnh sửa hồ sơ, nút "Lưu" ở P03 là mục riêng), **huy hiệu chỉ hiện cái đã đạt**; backend thêm `place {id, slug, name}` vào Review
- [ ] Bộ sưu tập (UC18): trang chi tiết / sửa `/collections/:id`, trang chia sẻ `/c/:shareSlug`, nút "Lưu" trên P03 — API có sẵn (tách từ mục trang cá nhân 2026-10-18) — **đã code 2026-10-18, chờ supervisor chạy thử**: một trang cho hai route (chủ: sửa tên / mô tả / công khai, link chia sẻ + sao chép, ghi chú, bỏ địa điểm, xoá bộ có xác nhận; link chia sẻ luôn chỉ đọc); "+ Lưu" / "✓ Đã lưu" trên P03 mở hộp tick bộ (gọi API ngay) + tạo bộ mới; thẻ trên trang cá nhân bấm được + ô "+ Tạo bộ sưu tập". Supervisor chốt: backend thêm `GET /me/collections?placeId=` → `containsPlace`; nút lưu **chỉ ở P03** (SpotCard để sau)
- [ ] Cài đặt — Hồ sơ (UC06, FR-07): `/settings/profile` (tên, giới thiệu, ảnh đại diện) — backend `PATCH /me`, `PUT /me/avatar` có trong openapi nhưng **chưa cài** (tách từ mục trang cá nhân 2026-10-18; 2026-10-18 supervisor xác nhận dấu [x] là nhầm — làm sau)
- [ ] Trang chủ quán: cập nhật thông tin, phản hồi, thống kê — **đã code 2026-10-18, chờ supervisor chạy thử**: P09 `/owner` (địa điểm đang quản lý; yêu cầu sở hữu đã gửi + trạng thái / lý do từ chối / gửi lại; form UC23 — tìm địa điểm hoặc chọn sẵn từ liên kết "Yêu cầu xác nhận sở hữu" trên P03, điện thoại, 1–5 ảnh minh chứng) và `/owner/places/:id` tab Thống kê (KPI lượt xem / check-in so với kỳ trước, điểm Bayesian, biểu đồ cột SVG tự vẽ, phân bố sao, cột "Cần phản hồi") · Thông tin (UC24 — PATCH kèm `version`, ảnh thêm / xoá ngay) · Đánh giá & phản hồi (UC25 — mặc định chưa phản hồi, trả lời / sửa / xoá tại chỗ). Supervisor chốt: backend thêm `unreplied` cho danh sách review và `GET /me/claims`
- [ ] Trang quản trị: duyệt, báo cáo, người dùng, dashboard — **người dùng xong** (P10); **kiểm duyệt đã code 2026-10-19, chờ supervisor chạy thử**: `/admin/queue` hàng chờ gộp FIFO (lọc loại theo quyền, chỉ hàng đầu "Xử lý tiếp"), 4 màn xử lý `/admin/moderation/places|reviews|reports|claims/…` (chỉ đọc + quyết định; xong một mục → sang mục kế tiếp cùng loại), kiểm duyệt viên dùng chung khu `/admin` (mục ẩn theo quyền). Backend thêm `GET /moderation/places/{id}`. **Còn**: dashboard (UC33)
- [ ] Ảnh trang chủ (UC34) — **đã code 2026-10-17, chờ supervisor chạy thử**: `/admin/banners` 3 khối (xem trước đúng tỷ lệ, thay ảnh = chọn → cắt theo khung gợi ý → tải, mô tả ảnh, bật / tắt, gỡ có xác nhận); HomePage thay hình minh hoạ bằng ảnh (`BannerPicture`, `srcset` 3 cỡ), hero phủ lớp tối đều 65% `on-accent` (không gradient) giữ chữ ≥ 4,5:1
- [ ] Skeleton loading, empty state, error state
- [ ] Responsive mobile
- [ ] Kiểm tra accessibility cơ bản (contrast, focus, alt)
- [ ] SEO: meta tag động, sitemap

### G. Chất lượng & triển khai
- [ ] Feature test cho các luồng chính (≥30 test)
- [ ] Unit test cho service tính rating
- [ ] Component test frontend cho form review
- [ ] E2E test luồng: tìm kiếm → xem chi tiết → viết review
- [ ] Rà soát N+1 query (Hibernate SQL logging / p6spy / Actuator)
- [ ] Cache trang chủ và danh mục
- [ ] Kiểm tra bảo mật: XSS, CSRF, mass assignment (DTO binding), upload file
- [ ] Sinh tài liệu API (springdoc-openapi)
- [ ] Deploy lên VPS, cấu hình HTTPS
- [ ] Cấu hình systemd/Docker cho consumer RabbitMQ chạy nền
- [ ] Sao lưu CSDL định kỳ
- [ ] Kết nối Sentry

### H. Báo cáo & bảo vệ
- [ ] Chương 1 — Tổng quan
- [ ] Chương 2 — Cơ sở lý thuyết
- [ ] Chương 3 — Phân tích và thiết kế
- [ ] Chương 4 — Cài đặt và triển khai
- [ ] Chương 5 — Kiểm thử và đánh giá
- [ ] Chương 6 — Kết luận
- [ ] Tài liệu tham khảo đúng chuẩn trích dẫn
- [ ] Phụ lục: schema đầy đủ, hướng dẫn cài đặt
- [ ] Đánh số và chú thích toàn bộ hình, bảng
- [ ] Kiểm tra đạo văn
- [ ] Ảnh chụp màn hình chất lượng cao
- [ ] Video demo 3–5 phút
- [ ] Slide bảo vệ (15–20 slide)
- [ ] Chuẩn bị câu trả lời cho các câu hỏi dễ gặp:
  - Vì sao chọn Vue 3 thay vì React?
  - Vì sao chọn Spring Boot thay vì Laravel/Node.js?
  - Vì sao tự làm JWT + Spring Security thay vì dùng OAuth2/Keycloak có sẵn?
  - Xử lý review ảo ra sao?
  - Nếu có 1 triệu địa điểm thì hệ thống chịu được không?
  - Vì sao denormalize `avg_rating`?
- [ ] Tập bảo vệ thử ít nhất 2 lần

---

## 11. Rủi ro thường gặp

| Rủi ro | Cách phòng |
|---|---|
| Ôm quá nhiều tính năng, không kịp deadline | Chia MVP (mục A–E cơ bản) và phần mở rộng; cắt phần mở rộng nếu chậm |
| Không có dữ liệu thật để demo | Viết seeder sinh 200–500 địa điểm + 2000 review giả từ đầu |
| Viết báo cáo dồn vào cuối | Viết chương 1–3 ngay trong 3 tuần đầu, khi thiết kế còn nóng |
| Bản đồ / tìm kiếm tốn thời gian hơn dự kiến | Làm bản đơn giản trước (LIKE query), tối ưu sau |
| Mất dữ liệu hoặc hỏng máy | Push git mỗi ngày, backup CSDL hàng tuần |
| Chưa quen Java/Spring Boot, đường cong học tập cao hơn Laravel | Dành hẳn tuần 4 để làm quen (Spring Initializr, Spring Security, JPA) trước khi vào module nghiệp vụ; tránh học song song nhiều khái niệm mới |
