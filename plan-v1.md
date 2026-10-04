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
| Form | VeeValidate + Zod | Validate đồng bộ với rule backend |
| HTTP | Axios + interceptor | Tự gắn token, xử lý 401 |
| Data fetching | TanStack Query (Vue Query) | Cache, infinite scroll cho danh sách review |
| Bản đồ | Leaflet + OpenStreetMap | Miễn phí, không cần thẻ tín dụng như Google Maps |
| Ảnh | vue-advanced-cropper | Crop trước khi upload |
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
| Ảnh | Thumbnailator + metadata-extractor | Resize, nén, gỡ EXIF |
| Mapping DTO | MapStruct | Entity ↔ DTO, thay cho API Resource của Laravel |
| Boilerplate | Lombok | Giảm code getter/setter/constructor |
| Tìm kiếm | Meilisearch (Java client chính thức) | Typo-tolerance, synonym tiếng Việt; tự viết service đồng bộ index thay cho Scout |
| Cache | Spring Cache + Redis (Lettuce) | Cache trang chủ, danh mục |
| Queue | RabbitMQ + Spring AMQP (hoặc Spring `@Async`) | Hàng đợi xử lý ảnh, mail, tính lại rating |
| Queue UI | RabbitMQ Management Plugin | Có ảnh chụp màn hình đưa vào báo cáo (thay cho Horizon) |
| Realtime | Spring WebSocket (STOMP + SockJS) | Thông báo review mới cho chủ quán (thay cho Reverb) |
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
- Quản lý danh mục, tiện ích. *(Banner đã cắt khỏi phạm vi — 2026-09-27, xem docs/requirements.md Q4.)*
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
| GET | `/search?q=` | Tìm kiếm qua Meilisearch |
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
│   ├── listener/{RecalculateRatingListener,SendOwnerNotificationListener}.java
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
- [x] Rate limiting cho API nhạy cảm (Redis) — cửa sổ trượt bằng Lua (không thêm thư viện); đăng nhập / đăng ký / quên mật khẩu / gửi lại mail xác thực; 429 + `Retry-After`; Redis sập → cho qua; giới hạn 5 review / 24 giờ đếm trong CSDL ở E

### E. Backend — nghiệp vụ
- [ ] CRUD địa điểm + luồng duyệt
- [ ] Upload ảnh, resize, nén, lưu S3 qua queue
- [ ] Danh mục phân cấp, tiện ích, giờ mở cửa
- [ ] Truy vấn địa điểm theo bán kính (spatial index)
- [ ] Tích hợp Meilisearch, cấu hình synonym tiếng Việt
- [ ] CRUD đánh giá + ràng buộc mỗi người một review
- [ ] Ảnh đính kèm review
- [ ] Vote hữu ích, bình luận (có phân cấp)
- [ ] Tính lại rating bằng Bayesian average qua Spring Event + `@Async` listener
- [ ] Check-in kèm kiểm tra khoảng cách GPS
- [ ] Bộ sưu tập và chia sẻ công khai
- [ ] Theo dõi người dùng, huy hiệu, bảng xếp hạng
- [ ] Báo cáo vi phạm (quan hệ đa hình)
- [ ] Trust score và hàng đợi duyệt review
- [ ] Yêu cầu xác nhận sở hữu địa điểm
- [ ] Phản hồi của chủ quán
- [ ] Thông báo (bảng `notifications` + Spring WebSocket/STOMP)
- [ ] API quản trị và thống kê
- [ ] Nhật ký thao tác quản trị (FR-42): `@Audited` + Spring AOP aspect ghi `activity_logs` — làm cùng mục đầu tiên có thao tác duyệt (CRUD địa điểm + luồng duyệt); gắn lại cho 4 endpoint `/admin/users` của D5

### F. Frontend
- [ ] Layout, router, navigation guard
- [ ] Axios client, interceptor, xử lý refresh/401
- [ ] Pinia store: auth, place, filter, notification
- [ ] Trang chủ: nổi bật, danh mục, mới nhất
- [ ] Trang tìm kiếm: bộ lọc, sắp xếp, infinite scroll
- [ ] Bản đồ Leaflet, marker cluster, "gần tôi"
- [ ] Trang chi tiết địa điểm: gallery, giờ mở cửa, tiện ích, review
- [ ] Form viết review: chấm sao, upload nhiều ảnh, crop
- [ ] Bình luận, vote, báo cáo
- [ ] Form đề xuất địa điểm mới (chọn tọa độ trên bản đồ)
- [ ] Trang cá nhân: review, ảnh, bộ sưu tập, huy hiệu
- [ ] Trang chủ quán: cập nhật thông tin, phản hồi, thống kê
- [ ] Trang quản trị: duyệt, báo cáo, người dùng, dashboard
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
