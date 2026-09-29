# Progress log

Ghi lại quyết định kỹ thuật: ngày giờ, task, bối cảnh, lý do, đánh đổi.

---

## 2026-09-27 20:44 (+07) — Checklist A: Chuẩn bị

**Task**: Hoàn thành phần còn lại của checklist A trong plan-v1.md (FR, NFR, phạm vi, quy ước git, board task).

**Bối cảnh**: Repo chỉ có tài liệu kế hoạch + docker-compose hạ tầng; chưa scaffold `backend/`, `frontend/`. Hai mục đầu của A (tên dự án, khảo sát) đã xong.

### Quyết định 1 — Đặt đặc tả yêu cầu ở `docs/requirements.md`, không nhét vào plan-v1.md
- **Lý do**: plan-v1.md là tài liệu định hướng tổng; bảng 43 FR + 22 NFR sẽ làm nó phình to. File riêng dễ copy vào Chương 3 báo cáo.
- **Đánh đổi**: thêm một tài liệu phải giữ đồng bộ → mỗi FR có cột "Nguồn" trỏ về mục trong plan-v1.md để truy vết; checklist trong plan link sang file này.

### Quyết định 2 — Phân tầng ưu tiên MoSCoW (Must/Should/Could)
- **Lý do**: plan §11 nêu rủi ro "ôm quá nhiều tính năng" và gợi ý chia MVP vs mở rộng, nhưng chưa nói tính năng nào thuộc tầng nào. MoSCoW là thuật ngữ chuẩn, dễ giải thích trước hội đồng.
- **Nguyên tắc**: 4 điểm khác biệt cốt lõi (chống review ảo, Bayesian, tìm kiếm tiếng Việt, spatial) bắt buộc Must; gamification (follow, huy hiệu, tags, banner) là Could — cắt đầu tiên nếu trễ.
- **Đánh đổi**: xếp check-in và báo cáo vi phạm vào Must (vì là một phần cơ chế chống review ảo / kiểm duyệt) làm MVP nặng hơn (30/43 FR).
- **Trạng thái**: đề xuất, chờ supervisor xác nhận (Q1).

### Quyết định 3 — Không tự chốt các con số/luật chưa có trong plan
- Các tham số (N review/ngày, trust score, ngưỡng IP, độ sâu bình luận, chỉ số NFR, TTL token) được ghi là **(đề xuất)** và liệt kê ở mục "Câu hỏi mở" Q1–Q6 thay vì âm thầm chọn — theo CLAUDE.md §4.
- Phát hiện lệch trong plan: "quản lý banner" có ở §4 nhưng không có bảng ở §5 → Q4.

### Quyết định 4 — Quy ước git ở `CONTRIBUTING.md`, nhánh `develop` tạo local
- Git flow rút gọn: `main` / `develop` / `feature/*` / `fix/*` / `hotfix/*` / `docs/*` theo plan §7; Conventional Commits có scope.
- **Không thêm commitlint/husky**: là dependency Node mới ngoài stack đã chốt (CLAUDE.md §4) và frontend chưa scaffold. Có thể thêm sau khi được đồng ý.
- **Chưa push `develop` / chưa bật branch protection / chưa tạo GitHub Projects board**: là thao tác ra bên ngoài (remote GitHub), chờ supervisor đồng ý.

---

## 2026-09-27 (+07) — Supervisor chốt câu hỏi mở checklist A

- **Q1 ✅** MoSCoW giữ nguyên như đề xuất.
- **Q2 ✅** Moderator: duyệt địa điểm/review/claim, xử lý báo cáo, ẩn nội dung. Admin: toàn bộ quyền moderator + khóa tài khoản, gán role, danh mục, tiện ích, dashboard. → Dùng làm seed `permissions`/`role_permissions`.
- **Q3 ⏳** Chốt `m = 10` cho Bayesian. N review/ngày, ngưỡng cảnh báo IP, giá trị `C`, công thức trust score **vẫn mở** — phải chốt trước khi thiết kế cột trust score trong ERD (checklist B).
- **Q4 ✅** Cắt tính năng banner: bỏ FR-43 (còn 42 FR: 30 M / 8 S / 4 C), sửa plan-v1.md §4 cho khớp. Lý do: không có bảng trong thiết kế CSDL, giá trị demo thấp; không phải xóa dữ liệu vì chưa có schema.
- **Q5 ✅** Bình luận tối đa 2 cấp → `comments.parent_id` chỉ trỏ tới bình luận gốc, không cần truy vấn đệ quy.
- **Q6 ✅** Các chỉ số NFR giữ như đề xuất (p95 < 300 ms / 200 ms search, Lighthouse ≥ 80/90, JWT 15 phút / 7 ngày, ảnh ≤ 5 MB, ≤ 10 ảnh/review, backup hằng ngày giữ 7 bản).

---

## 2026-09-27 (+07) — Chốt trọng số trust score (Q3, một phần)

- Công thức `trust = w1×age_score + w2×helpful_score − w3×penalty_score + base` với **w1 = 1, w2 = 2, w3 = 3, base = 10** (supervisor chốt). Chi tiết ở docs/requirements.md mục 5.1.
- Ý nghĩa: phạt nặng hơn thưởng (1 review bị từ chối = −30, bù lại cần ~8 vote hữu ích) → tài khoản spam khó "rửa" điểm; vote hữu ích nặng hơn tuổi tài khoản → ưu tiên chất lượng hơn thâm niên.
- Còn mở: mức trần các điểm thành phần (20 / 40 / 10 mỗi lỗi), chặn `[0, 100]` (vì max theo công thức = 110), ngưỡng hàng chờ (đề xuất 30).

---

## 2026-09-27 (+07) — Q3: ngưỡng trust score, tham số chống review ảo, đề xuất trần điểm

- **Đã chốt**: ngưỡng hàng chờ = 50; N = 5 review/ngày; cảnh báo IP ≥ 3 review/địa điểm/cùng /24/24 h; `C` tính lại mỗi lần tính lại rating.
- **Đề xuất trần** (chờ xác nhận): age trần 30 (+1/7 ngày), helpful +1/vote trần 30, phạt 20/vi phạm (×3 = −60), vi phạm hết hạn sau 90 ngày, kết quả chặn `[0, 100]`.
- **Lý do**: supervisor yêu cầu phạt đủ nặng để buộc tuân thủ luật → chọn P sao cho một vi phạm kéo người dùng 100 điểm xuống dưới 50 (`3P > 50`). Chọn trần sao cho tổng dương = đúng 100 (không có điểm dư để "đệm" hình phạt), và tuổi tài khoản một mình (40) không qua được ngưỡng → chặn sleeper account.
- **Đánh đổi**: hình phạt hết hạn 90 ngày (không vĩnh viễn) — vì các thành phần dương có trần nên người dùng đã đạt trần không thể cày lại điểm; phạt vĩnh viễn sẽ làm hàng chờ moderator phình theo thời gian. Ngưỡng 50 khá chặt: người dùng mới cần ~3 tháng + ~14 vote hữu ích mới thoát hàng chờ → tải moderator giai đoạn đầu cao hơn.

---

## 2026-09-27 (+07) — Q3: đổi ngưỡng 30, hạn phạt 30 ngày, chốt luật chống vote chéo

- **Đã chốt (supervisor)**: ngưỡng hàng chờ 50 → **30**; hạn hiệu lực vi phạm 90 → **30 ngày**; chỉ tính vote hữu ích từ người có trust ≥ ngưỡng.
- **Hệ quả phải tính lại trần** (đề xuất, chờ xác nhận): với ngưỡng 30, bộ trần cũ phá 2 ràng buộc đã đặt ra:
  - Phạt 20 (−60): người dùng 100 điểm vi phạm còn 40 ≥ 30 → phạt không còn tác dụng. Đổi sang **P = 25** (`100 − 3P < 30` → `P ≥ 24`).
  - Trần tuổi 30: sleeper account đạt 40 ≥ 30 → qua ngưỡng chỉ nhờ chờ. Đổi trần tuổi **18** (10 + 18 = 28 < 30), trần helpful **36** để tổng dương vẫn = 100.
- **Đề xuất kỹ thuật**: chụp trust của người vote tại thời điểm vote, lưu cờ `counted` trên `review_votes` → tránh tính trust đệ quy (trust A phụ thuộc vote từ B, trust B phụ thuộc vote từ A). Đánh đổi: nếu người vote bị phạt sau đó, vote cũ vẫn được tính.
- **Đánh đổi của ngưỡng 30**: người dùng mới thoát hàng chờ nhanh hơn (≈2 tháng + 6 vote, hoặc 10 vote hợp lệ) → giảm tải moderator, nhưng phụ thuộc nhiều hơn vào luật chống vote chéo.

---

## 2026-09-27 (+07) — Chốt Q3 và phạm vi

- **Q3 ✅**: trần age 18, trần helpful 36, phạt 25/vi phạm; chụp trust người vote tại thời điểm vote (cờ `counted` trên `review_votes`). → Ảnh hưởng ERD ở checklist B: cần cột lưu vi phạm có thời điểm (để tính hết hạn 30 ngày) và cột `counted` trên `review_votes`.
- **Phạm vi ✅**: supervisor bỏ 3 mục ngoài phạm vi bổ sung (đăng nhập mạng xã hội, gợi ý cá nhân hóa, kiểm duyệt AI) → danh sách ngoài phạm vi giữ nguyên theo plan-v1.md §1. Tick "Chốt phạm vi" trong checklist A.

---

## 2026-09-27 (+07) — Tạo board quản lý task (GitHub Projects)

- Board: https://github.com/users/hiepfre3101/projects/1 (private), đã link với repo `hiepfre3101/local-spot`.
- 93 thẻ sinh tự động từ checklist A–H của plan-v1.md; field single-select **Giai đoạn** (A…H); Status Done cho 6 mục đã xong ở A, còn lại Todo.
- **Chọn draft item thay vì issue**: tránh tạo 93 issue làm rối tab Issues ngay từ đầu; khi bắt tay làm một thẻ thì "Convert to issue" để link PR (`Refs #n`). Đánh đổi: draft item không có số issue, không tham chiếu được từ commit cho đến khi convert.
- Chọn GitHub Projects thay vì Trello: nằm cùng repo, link được issue/PR, có thể chụp làm minh chứng quy trình cho báo cáo.

---

## 2026-09-27 (+07) — Checklist B: sơ đồ use case tổng quát + đặc tả chi tiết (bản nháp)

- Checklist A hoàn tất (supervisor tự push `develop`). Bắt đầu B; board: 2 thẻ đầu B → In Progress.
- **PlantUML** (`docs/diagrams/*.puml`) thay vì draw.io: plan §2 khuyến nghị; sơ đồ dạng text, diff được trong git. Máy chưa có Java nên chưa render được PNG — xem bằng extension PlantUML của VS Code hoặc render sau khi cài JDK 21 (cần cho backend ở checklist C).
- **Gộp 42 FR thành 33 use case**: sơ đồ tổng quát với 42 use case sẽ quá dày để đưa vào báo cáo. Tác vụ hệ thống (Bayesian FR-22, activity log FR-42, xử lý ảnh) không vẽ thành use case vì không có tác nhân người khởi phát — mô tả ở sơ đồ tuần tự/hoạt động.
- Dùng quan hệ kế thừa tác nhân (Thành viên → Khách, Chủ → Thành viên, Admin → Moderator) để giảm số đường nối, khớp với RBAC đã chốt ở Q2.
- **Chọn 10 use case đặc tả chi tiết** (UC01/02, 03, 08, 09, 11, 12, 16, 23, 28, 29): phủ đủ 5 tác nhân và cả 4 điểm khác biệt cốt lõi.
- Các luật nghiệp vụ chưa có trong requirements.md được đánh dấu (đề xuất) và gom vào 10 câu hỏi mở U1–U10 — không tự chốt.

---

## 2026-09-27 (+07) — Chốt U1–U10; sơ đồ hoạt động & tuần tự (bản nháp)

- **U1–U10 ✅** (supervisor chốt toàn bộ đề xuất) → chép vào docs/requirements.md §5, bỏ nhãn (đề xuất) trong use-cases.md. Tick 2 mục đầu checklist B.
- **Sơ đồ hoạt động** (3, đúng plan §8): viết đánh giá, đề xuất & duyệt địa điểm, check-in — dùng swimlane để thấy rõ ranh giới người dùng / hệ thống / xử lý nền / kiểm duyệt viên.
- **Sơ đồ tuần tự** (3): đăng nhập + làm mới phiên, tìm kiếm (kèm fallback U9 và đồng bộ index), đăng review kèm ảnh. Participant dùng đúng tên lớp dự kiến ở plan §7 để sơ đồ lớp / code khớp nhau.
- **Quyết định kỹ thuật thể hiện trong sơ đồ**:
  - Event tính rating / xử lý ảnh dùng `@TransactionalEventListener(AFTER_COMMIT)`: tránh tính rating hoặc xử lý ảnh cho review mà transaction bị rollback. Đánh đổi: nếu app sập giữa commit và listener thì mất event (chấp nhận được với đồ án; hướng khắc phục: outbox pattern — nêu ở hướng phát triển).
  - Ảnh xuất JPEG thay vì WebP: ImageIO/Thumbnailator không ghi WebP nếu không thêm thư viện (vi phạm CLAUDE.md §4 nếu tự thêm).
  - Fallback tìm kiếm nằm trong `PlaceSearchService`, không lộ ra controller.
- **Chờ supervisor chốt**:
  - S1: refresh token qua cookie HttpOnly + SameSite=Strict, access token chỉ trong bộ nhớ (không localStorage) — chống XSS đánh cắp refresh token; đánh đổi: cần cấu hình CORS `allowCredentials` và đăng nhập lại khi reload trang nếu refresh lỗi.
  - S2: review + ảnh trong một request multipart qua API thay vì presigned URL lên MinIO — server kiểm tra được MIME thật (NFR-09), đơn giản hơn; đánh đổi: ảnh đi qua băng thông của API.
  - Ghi chú cho ERD: cần cột IP (/24) trên `reviews` cho luật cảnh báo IP; cần phân biệt `avg_rating` (trung bình thô) và điểm Bayesian hay dùng chung một cột.

---

## 2026-09-27 (+07) — Render sơ đồ bằng JDK trong WSL

- Supervisor cho dùng JDK 21 có sẵn trong WSL Ubuntu 22.04 (OpenJDK 21.0.12). Tải `plantuml.jar` 1.2026.8 vào `~/tools` của WSL (ngoài repo, không phải dependency dự án).
- Dùng layout **Smetana** (tích hợp trong PlantUML) thay vì cài Graphviz — tránh cần `sudo apt` trong WSL; chất lượng layout đủ cho báo cáo.
- Render 7 sơ đồ ra `docs/diagrams/png/`, kiểm tra từng ảnh: không lỗi cú pháp. Lệnh render ghi ở `docs/diagrams/README.md`.

---

## 2026-09-27 (+07) — Chốt S1–S3; thiết kế CSDL + ERD (bản nháp)

- **S1, S2, S3 ✅** → ghi vào requirements.md §5; tick sơ đồ hoạt động & tuần tự. Board: 3 thẻ B tiếp theo → In Progress.
- **docs/design/database.md**: 32 bảng (plan §5 + `refresh_tokens`, `user_tokens`, `user_violations`; bỏ `banners`). **ERD** tách 3 sơ đồ theo miền — một ERD 32 bảng không đọc được trên trang A4.
- **Quyết định thiết kế** (lý do chi tiết trong database.md §1):
  - Trạng thái dùng `VARCHAR + CHECK` thay `ENUM`: thêm trạng thái không cần `ALTER TABLE` sửa định nghĩa ENUM; map `@Enumerated(STRING)`.
  - `DATETIME(6)` lưu UTC; ngày nghiệp vụ (`checkin_date`, `view_date`) tính theo giờ Việt Nam.
  - Luật U5 (1 check-in/ngày) và U10 (1 báo cáo/người/đối tượng) enforce bằng UNIQUE ở CSDL, không chỉ ở service — chặn race condition.
  - `user_violations.points` lưu mức phạt tại thời điểm ghi → đổi cấu hình không làm sai lịch sử.
  - `review_votes` dùng PK tổ hợp (review_id, user_id) — thỏa `UNIQUE(review_id, user_id)` của plan mà không cần cột id.
  - `reports.target_id` không có FK (quan hệ đa hình) — đánh đổi toàn vẹn tham chiếu lấy tính tổng quát, kiểm tra ở service.
- **Phát hiện khi thiết kế** (chờ supervisor, D1–D7): trust score tính động hay lưu; soft delete xung đột với `UNIQUE(place_id, user_id)` và `users.email`; index plan §5 dùng `avg_rating` nhưng xếp hạng dùng `bayesian_score`; **`C` thay đổi làm bayesian_score của mọi địa điểm khác lỗi thời** → đề xuất job đêm; tags đa hình; số phản hồi chủ quán.
- Sơ đồ lớp để sau khi chốt ERD (entity JPA phản ánh 1-1 bảng).

---

## 2026-09-28 (+07) — Chốt D1, D3–D7 (thiết kế CSDL)

- **D1 ✅** trust score tính khi cần, không lưu cột. **D5 ✅** giữ `tags`/`taggables` đa hình. **D6 ✅** 1 phản hồi chủ quán / review, sửa được.
- **D3 ✅** đổi index `places(category_id, status, avg_rating)` → `(category_id, status, bayesian_score)` và **sửa plan-v1.md §5** trong cùng thay đổi (ràng buộc cốt lõi, CLAUDE.md §3 — đã được supervisor xác nhận).
- **D4 ✅** thêm `RatingRecalculationJob` chạy 03:00 hằng đêm tính lại `bayesian_score` của mọi địa điểm với `C` mới; ghi vào requirements.md §5 và database.md §4.
- **D7 ✅** xóa tài khoản = xóa mềm + ẩn danh hóa email / tên / avatar / bio + thu hồi refresh token.
- **D2 ⏳** supervisor hỏi lại ý nghĩa đề xuất → giải thích bằng ví dụ, chờ quyết định.
- **D2 ✅ = A** (2026-09-28): giữ nguyên `UNIQUE(place_id, user_id)` tính cả review đã xóa mềm. Lý do: chặn chiêu xóa-viết lại để làm mới review / xóa review bị từ chối để thử lại; không đổi ràng buộc cốt lõi. Đánh đổi: người xóa nhầm không viết lại được → UI cảnh báo khi xóa, gợi ý sửa. Ghi vào FR-18. Tick ERD + bảng mô tả CSDL.

---

## 2026-09-28 (+07) — Sơ đồ lớp (bản nháp)

- Tách 2 sơ đồ: **class-domain** (32 bảng → entity, nhóm theo 4 miền như ERD) và **class-review-module** (Controller → DTO → Service → Repository → Entity + event/listener cho module đánh giá). Lý do: sơ đồ lớp gồm cả service của mọi module sẽ không đọc được; module đánh giá chứa đủ 3 điểm nhấn (chống review ảo, trust, Bayesian) nên đại diện tốt cho kiến trúc.
- **Quyết định thể hiện trong sơ đồ**:
  - `BaseEntity` / `SoftDeletableEntity` (`@MappedSuperclass`) gom id + audit + xóa mềm — tránh lặp ở 4 entity xóa mềm.
  - Hành vi nghiệp vụ đặt trên entity (`Review.publish/reject`, `Place.applyRating`, `RefreshToken.isUsable`) thay vì setter trần — giữ bất biến trạng thái ở một chỗ; service chỉ điều phối.
  - Bảng nối có cột thêm (`review_votes`, `collection_place`, `follows`, `user_badges`) → entity riêng với `@EmbeddedId`; bảng nối thuần (`user_roles`, `role_permissions`, `place_amenity`) → `@ManyToMany`.
  - Tham số chống review ảo gom vào `AntiFakeProperties` (`@ConfigurationProperties`) — đổi cấu hình để thử nghiệm Chương 5 mà không sửa code (requirements §5.1).
  - `RatingCalculator` là hàm thuần, không I/O → unit test độc lập (checklist G).
- **Chờ supervisor (C1)**: kiểu Java cho cột `POINT` — `hibernate-spatial` (JTS `Point`) hay `lat`/`lng` + native query.
- **C1 ✅ = A** (2026-09-28): dùng `hibernate-spatial` + JTS `Point` cho `places.location`, `check_ins.location`. Là module chính thức của Hibernate (đã trong stack) nhưng vẫn là dependency mới → đã hỏi trước theo CLAUDE.md §4 và thêm vào bảng stack plan §2. Tick sơ đồ lớp.

---

## 2026-09-29 (+07) — Đặc tả API OpenAPI

- **C1 = A** được supervisor xác nhận bằng "okay" (hiểu là chấp nhận phương án khuyến nghị).
- `docs/api/openapi.yaml`: OpenAPI 3.1, 83 operation, lint hợp lệ bằng Redocly CLI (`--extends=minimal`, 0 lỗi). Công cụ lint chạy qua `npx` (cache npm), không thêm vào dự án. WSL thiếu `python3-venv` nên không dùng openapi-spec-validator.
- **Quyết định & lệch so với plan §6 (đã sửa plan cùng lúc)**:
  - Vote `POST` → `PUT`/`DELETE` idempotent.
  - Tách `/owner/*` và `/moderation/*` khỏi `/admin/*`: nhóm route theo vai trò khớp RBAC Q2, và tránh trùng đường dẫn `/places/{slug}` (GET chi tiết, giữ đúng plan) với `/places/{placeId}` (PATCH của chủ) — Redocly báo `no-ambiguous-paths`.
  - Bỏ "response bọc API Resource" (khái niệm Laravel) → DTO MapStruct, không envelope.
  - Quyết định duyệt dùng `POST .../decision {APPROVE|REJECT, reason, version}` thay vì hai endpoint approve/reject: một chỗ kiểm tra optimistic lock, dễ ghi activity_log.
  - `PlaceUpdateRequest` không cho chủ đổi vị trí / danh mục — tránh "dời" địa điểm để thoát review xấu; đổi phải qua báo cáo WRONG_INFO.
  - `GET /moderation/claims/{id}/evidence/{i}` trả presigned URL 5 phút — minh chứng nằm bucket riêng tư.
- Còn cảnh báo (không phải lỗi): thiếu `operationId` (sẽ lấy từ tên method controller khi springdoc sinh), thiếu mô tả tag.

---

## 2026-09-29 (+07) — Đối chiếu design system với plan; chốt 4 điểm lệch

**Bối cảnh**: đọc `docs/design-system/` (bản xuất từ Claude Design: token, `bundle.css` lớp `.ls-*`, README + preview HTML; không có component code) và đối chiếu với plan / requirements.

- **UI ✅ Tailwind, bỏ shadcn-vue / PrimeVue** (sửa plan §2). Token design system đưa vào theme Tailwind, component Vue tự viết. Lý do: hai thư viện kia mang sẵn bóng đổ, bo góc, style riêng — xung đột quy tắc "không bóng, quản trị bo 2px, không card lồng card". Đánh đổi: tự viết Button / FormField / DataTable / dialog → nhiều việc hơn, bù lại UI khớp design system.
- **Một đánh giá / người / địa điểm ✅** — trả lời câu hỏi mở P04 theo `UNIQUE(place_id, user_id)` (D2). Không đổi schema.
- **Check-in ✅ giữ U5** (200 m, accuracy ≤ 100 m, 1 lần / địa điểm / ngày). Supervisor ban đầu nói "không giới hạn" — mâu thuẫn với U5 đã có trong ERD (`UQ(user_id, place_id, checkin_date)`), API (409) và sơ đồ hoạt động → hỏi lại, supervisor giữ U5. Lý do giữ: check-in cộng điểm xếp hạng; không giới hạn thì cày điểm được.
- **Duyệt đánh giá ✅ giữ luồng lõi**: trust ≥ 30 đăng ngay; < 30 hoặc cảnh báo IP → hàng chờ. **Không** theo design system ("mọi đánh giá vào hàng chờ") vì làm trust score mất tác dụng (điểm bảo vệ), tải moderator tăng tuyến tính theo số review.
- **Hàng chờ gộp FIFO ✅**: thêm `GET /moderation/queue` (openapi) gộp 4 loại, sắp xếp cố định `(submittedAt, type, id)`, cursor keyset (không OFFSET), chỉ trả loại người gọi có quyền. Là màn **xem**; duyệt vẫn qua `decision` riêng từng loại → không đổi luật nghiệp vụ, optimistic lock giữ nguyên. Triển khai dự kiến: native `UNION ALL` 4 bảng dùng index `(status, created_at)` sẵn có. Đánh đổi: báo cáo gom nhóm theo đối tượng nên `submittedAt` = báo cáo đầu tiên của nhóm; `position` cần một `COUNT` các mục cũ hơn.
- Đã sửa: plan-v1.md §2, §6; requirements.md §3.7, §5; openapi.yaml; design-system `DECISIONS.md`, `use-cases.md`.
- **Còn lệch**: các README component (P03, P04, ReviewItem…) và project gốc trên Claude Design vẫn ghi "mọi đánh giá vào hàng chờ" — cần sửa ở nguồn Claude Design rồi xuất lại, không sửa tay bản xuất.

---

## 2026-09-29 (+07) — Duyệt P01–P12; sitemap; đóng checklist B

- **Supervisor duyệt P01–P12** và coi mục "Prototype Figma" là xong (prototype làm trên Claude Design thay Figma — đã sửa dòng checklist plan §10.B cho đúng công cụ thực tế, tránh báo cáo ghi Figma mà không có file Figma).
- **Cách hiểu "duyệt"**: chấp nhận phương án đề xuất của từng màn; câu hỏi nào đã có câu trả lời trong tài liệu chốt (U1, U5, U6, U7, D2, FR-10, FR-26, FR-30, FR-39, ngoài phạm vi) thì **tài liệu chốt thắng**. Phát hiện P02 thiếu bộ lọc khoảng giá so với FR-10 → bổ sung trong sitemap. Bảng kết luận ở `docs/design/sitemap.md` §4.
- **Không tự chốt** 7 câu hỏi không có phương án đề xuất hoặc chạm schema (O1–O7). Đáng chú ý nhất **O2**: giữ bản cũ của đánh giá đã đăng trong lúc bản sửa chờ duyệt cần bảng lưu bản sửa — `reviews` hiện chỉ có một trạng thái. Không ảnh hưởng sitemap, phải chốt trước module đánh giá.
- **Quyết định sitemap**:
  - `/propose` thay vì `/places/new` — tránh đụng route `/places/:slug` (slug "new").
  - Tab trang cá nhân / chủ địa điểm dùng query `?tab=` thay vì route con — giữ link chia sẻ được mà không nhân đôi số route.
  - Danh sách riêng từng loại hàng chờ = `/admin/queue?type=` — một màn DataTable, khớp API `GET /moderation/queue`.
  - Check-in (P12) và thông báo (P07) là hộp thoại / bảng thả xuống, không có route.
- Sơ đồ `sitemap.puml` dạng WBS (PlantUML, cùng công cụ các sơ đồ khác), render bằng JDK trong WSL.
- `docs/design-system/` đưa vào `.gitignore` theo yêu cầu supervisor. Hệ quả: token phải chép vào cấu hình Tailwind trong `frontend/` (được commit) khi scaffold ở checklist C.
