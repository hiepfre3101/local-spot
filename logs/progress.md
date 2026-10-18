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

---

## 2026-09-29 (+07) — Checklist C: thiết lập môi trường

**Quyết định (supervisor chốt qua câu hỏi):**
- **Spring Boot 4.1.1** thay 3.3: dòng 3.x hết hỗ trợ OSS (3.3 từ 06/2025), Initializr chỉ còn 4.0/4.1. Khởi đầu dự án mới trên bản EOL không có bản vá bảo mật là điểm yếu khi bảo vệ. Đánh đổi: kéo theo Spring Security 7, Hibernate 7, Jackson 3, Testcontainers 2 — tài liệu/ví dụ trên mạng ít hơn 3.x. Đã sửa plan §2 và CLAUDE.md.
- **Build backend trong WSL** (JDK 21, Docker đã có). Đánh đổi: I/O qua `/mnt/d` chậm hơn (verify lần đầu ~3,5 phút gồm tải dependency + image).
- **Dev: hạ tầng trong Docker, app chạy host**; backend + nginx dưới profile `app` cho chạy full stack / deploy. Lý do: hot reload & debug trên host; không thay đổi hành vi `docker compose up` hiện có của nhóm.
- **Spotless (palantir-java-format) + SpotBugs, bỏ Checkstyle**: formatter tự sửa được, Checkstyle trùng phần lớn và dễ xung đột rule.

**Quyết định kỹ thuật khác:**
- `application-dev.yml` import `../.env` (`optional:file:../.env[.properties]`) → một nguồn thông tin đăng nhập cho cả compose và backend. `application-prod.yml` không có giá trị mặc định cho bí mật → thiếu biến thì khởi động lỗi ngay.
- `open-in-view: false`, `ddl-auto: validate`, JDBC time zone UTC (khớp database.md §1).
- Testcontainers ghim `mysql:8.4`, `rabbitmq:3.13-management-alpine`, `redis:7-alpine` = đúng image compose (Initializr sinh `:latest` → test có thể chạy trên MySQL khác production).
- **Gỡ oxlint** mà create-vue tự thêm: công cụ ngoài stack (CLAUDE.md §4); ESLint dùng `flat/recommended` thay `essential`.
- **Tailwind 4 theme = design system**: reset `--color-*`, `--shadow-*`, `--text-*`, `--radius-*`, `--font-*` về `initial`, chỉ khai báo token LocalSpot → `bg-blue-500`, `shadow-md`, `text-sm` không sinh CSS, lệch design system lộ khi review. Màu dùng `@theme inline` trỏ `var(--surface)`… để đổi theme bằng `data-theme`. Khoảng cách giữ mặc định (lưới 4px trùng `space-*`). Token màu chép vào `frontend/src/assets/tokens.css` (được commit) vì `docs/design-system/` bị ignore.
- nginx: SPA fallback, proxy `/api` + `/ws` (cùng origin → cookie refresh token SameSite=Strict — S1), `client_max_body_size 55m` (10 ảnh × 5 MB — S2/NFR-09); backend `forward-headers-strategy: framework` để lấy IP thật cho luật cảnh báo IP.
- Font Be Vietnam Pro qua Google Fonts `<link>` — không thêm package.

**Kiểm chứng:** `./mvnw verify` xanh (1 smoke test trên MySQL/RabbitMQ/Redis thật, Spotless, SpotBugs 0 lỗi); frontend lint + format + type-check + Vitest + build xanh; `docker compose --profile app up --build`: backend profile prod kết nối MySQL, Flyway chạy, SPA 200, deep link 200, `/api` qua nginx tới Spring Security (401 — đúng, SecurityConfig làm ở D); profile dev trên host đọc `.env`, health 200. CI chưa chạy trên GitHub (chạy khi push).

---

## 2026-09-30 21:20 (+07) — Checklist D1: Flyway migration + khóa ngoại + index

**Bối cảnh**: bắt đầu D (backend nền tảng) trên nhánh `feature/be-foundation` (tách từ `feature/infra-dev-environment` vì nhánh đó chưa merge vào `develop` mà D cần scaffold backend). Supervisor chọn nhịp **dừng sau mỗi mục** để duyệt.

**Supervisor chốt trước khi viết schema:**
- **O2 = về PENDING cả bài**: tác giả trust < 30 sửa đánh giá đã đăng → đánh giá quay về `PENDING`, rời trang và điểm xếp hạng tới khi duyệt lại. Không cần bảng lưu bản sửa → `reviews` giữ nguyên một trạng thái. Đánh đổi: bài biến mất tạm thời; đổi lại không thêm bảng / loại mục hàng chờ, và event tính lại rating vốn đã xử lý chuyển trạng thái PUBLISHED ↔ khác. Phương án "sửa không cần duyệt" bị loại vì mở lỗ hổng đăng sạch rồi sửa thành spam.
- **Seed demo = Flyway SQL riêng cho dev** (làm ở D2): file SQL đặt ngoài `db/migration`, chỉ bật ở profile dev.

**Quyết định kỹ thuật (D1):**
- 3 migration: `V1__init.sql` (32 bảng, đúng database.md), `V2__seed_rbac.sql`, `V3__seed_catalog.sql`. Tách seed tham chiếu khỏi schema để đọc/duyệt riêng; dữ liệu tham chiếu nằm trong Flyway vì **mọi** môi trường đều cần (khác demo).
- **Khóa ngoại RESTRICT mặc định**, CASCADE chỉ ở bảng nối thuần. Lý do: bảng nghiệp vụ xóa mềm, một lệnh `DELETE` cứng là lỗi lập trình → CSDL nên chặn thay vì xóa dây chuyền review/ảnh (CLAUDE.md §4 cấm mất dữ liệu). Test `refusesToHardDeleteUserWithContent` khẳng định.
- **Permission = đúng 17 giá trị `x-permission` trong openapi.yaml**, không tự thêm. Kế thừa: OWNER ⊇ USER (plan §4), ADMIN ⊇ MODERATOR (Q2); MODERATOR không kèm quyền USER — tài khoản nhân sự được gán thêm role USER. `user:view` chỉ cho ADMIN (Q2 xếp quản lý người dùng vào admin).
- **Danh mục / tiện ích khởi đầu** seed ở V3 (mọi môi trường): UC11 bắt buộc chọn danh mục nên hệ thống mới không dùng được nếu rỗng; admin sửa qua UC32. Danh sách do agent đề xuất theo phạm vi plan (quán ăn / cà phê / check-in) — supervisor có thể đổi bằng migration mới hoặc qua UC32. `icon` NULL (chưa có bộ icon). **Badges không seed** vì O3 chưa chốt.
- Index / CHECK bổ sung ngoài database.md (index hàng chờ `(status, created_at)` cho `place_claims`, `reports`; CHECK điểm trong [0, 5]…) — đã ghi ở database.md §3.5.
- Không `DEFAULT CURRENT_TIMESTAMP`: phụ thuộc `time_zone` session, dễ lệch UTC.
- `TestcontainersConfiguration` đổi sang `public` để test ở package con dùng được.

**Phát hiện**: MySQL vi phạm CHECK (mã 3819) → Spring ném `UncategorizedSQLException`, không phải `DataIntegrityViolationException`. Phải xử lý riêng trong `@RestControllerAdvice` (D7), nếu không sẽ thành 500.

**Kiểm chứng**: `./mvnw verify` xanh — 12 test (11 test schema mới trên MySQL 8.4 thật: đủ 32 bảng, collation, spatial index SRID 4326, thứ tự trục (lat, lng) qua `ST_Distance_Sphere` Hồ Gươm → Văn Miếu ≈ 1,27 km, UNIQUE review tính cả xóa mềm, UNIQUE vote, CHECK, 1 check-in/ngày, FK RESTRICT, RBAC kế thừa, cây danh mục), Spotless, SpotBugs 0 lỗi. Chưa áp dụng lên DB dev cục bộ (sẽ tự chạy khi khởi động backend).

---

## 2026-09-30 22:08 (+07) — Checklist D2: Entity JPA + dữ liệu demo

**Bối cảnh**: supervisor duyệt D1 (đã commit `85e8ebb`) và yêu cầu "fake thêm một số data của users hay địa điểm".

**Supervisor chốt (qua câu hỏi):**
- **hibernate-spatial** để map `POINT SRID 4326` (dependency mới — module chính thức của Hibernate, version do Spring Boot quản lý). Phương án bị loại: tự viết converter sang định dạng nhị phân nội bộ MySQL — ít phụ thuộc hơn nhưng là code tự chế khó giải thích. Đã thêm vào plan §2 và CLAUDE.md §2.
- **Quy mô seed: ~60 tài khoản, ~300 địa điểm** (khớp 200–500 của plan).

**Quyết định kỹ thuật:**
- **29 entity + 3 bảng nối `@ManyToMany`** (`user_roles`, `role_permissions`, `place_amenity` — không mang dữ liệu riêng nên không cần entity). Bảng khóa tổ hợp có cột riêng (`review_votes`, `collection_place`, `follows`, `user_badges`, `taggables`, `place_views`) dùng `@EmbeddedId` là record + `@MapsId`.
- **Không Lombok** (ngoài stack) → getter/setter viết tay; chỉ có setter cho trường nghiệp vụ được sửa. Cột dẫn xuất `review_count / avg_rating / bayesian_score` **không có setter** — chỉ luồng tính lại rating được ghi (điểm lõi CLAUDE.md §3).
- Mọi `@ManyToOne` là `LAZY` (tránh N+1 ngầm, khớp `open-in-view: false`). Chỉ `Place → openingHours` là aggregate con có cascade.
- Thời gian dùng `Instant` + `DateTimeProvider` trả `Clock.instant()` (bean `Clock` UTC — test thay được). `@EnableJpaAuditing` đặt ở `JpaConfig` riêng để test slice web không kéo JPA.
- `GeoPoints.of(lat, lng)` là nơi duy nhất tạo `Point` (JTS dùng x = lng) — test xác nhận hibernate-spatial ghi đúng thứ tự trục (lat, lng) của MySQL cho SRID 4326.
- `day_of_week` dùng converter ISO 1–7 thay vì `@Enumerated(ORDINAL)` (ordinal bắt đầu từ 0 → lệch 1 ngày âm thầm).
- Cột `TINYINT/SMALLINT UNSIGNED` cần `@JdbcTypeCode` để `ddl-auto: validate` chấp nhận `int`.
- Bảng `collections` → entity `PlaceCollection` (tránh trùng `java.util.Collection`).

**Seed demo** (`db/seed/dev/R__demo_01_users.sql`, `R__demo_02_places.sql`, chỉ `application-dev.yml` thêm vào `spring.flyway.locations`):
- **Repeatable `R__` thay vì `V__`**: version dùng chung một dãy với migration schema; nếu seed chiếm V1000 thì V4 thêm sau sẽ bị Flyway coi là "out of order" và từ chối. Đánh đổi: script chạy lại mỗi khi sửa → mọi lệnh phải idempotent (`INSERT IGNORE` theo khóa UNIQUE, `NOT EXISTS`), không xóa / ghi đè (CLAUDE.md §4).
- **Sinh hàng bằng SQL (CTE đệ quy + công thức số học, không `RAND`)** thay vì dán 300 dòng từ script ngoài: vẫn là "Flyway SQL riêng cho dev" như supervisor chọn, nhưng file gọn, sửa danh sách tên / đường phố ngay trong file, tất định giữa các máy, không cần thêm công cụ sinh (Python...) vào repo.
- Email `@localspot.test` (TLD dành riêng), mật khẩu chung `LocalSpot2026`; **phone để trống** vì số bịa có thể trùng số thật. Tên / địa chỉ hư cấu.
- **Chưa seed review / vote / check-in**: `review_count`, `bayesian_score` phải khớp review; seed thẳng SQL sẽ phải chép công thức Bayesian và trust ra SQL (hai nguồn sự thật cho điểm lõi). Làm ở E: seed review thô rồi chạy job tính lại rating. Hệ quả tạm thời: mọi tài khoản demo trust < 30, mọi địa điểm điểm 0.

**Kiểm chứng**: `./mvnw verify` xanh — 24 test (thêm `EntityMappingTests` 6: trục tọa độ, auditing, giờ mở cửa ISO + qua đêm, xóa mềm, JSON, khóa tổ hợp + role/permission; `DemoSeedTests` 6: 60 tài khoản đúng vai trò, mật khẩu khớp `BCryptPasswordEncoder`, 300 địa điểm đúng phân bổ thành phố / trạng thái, tọa độ < 7 km quanh tâm, giờ mở cửa & tiện ích theo loại hình, **chạy lại script không đổi số dòng**), SpotBugs 0 lỗi. Chưa khởi động backend trên DB dev cục bộ — seed sẽ nạp ở lần `spring-boot:run` tới.

---

## 2026-10-01 22:30 (+07) — Checklist D3: Spring Security + JWT, đăng ký / đăng nhập / đăng xuất / refresh

**Bối cảnh**: supervisor duyệt D2 (commit `10699e7`, đã push `feature/be-foundation`) và cho làm tiếp. O3 (bộ huy hiệu / điểm đóng góp) giải thích cho supervisor: chưa cần chốt tới mục huy hiệu ở E.

**Supervisor chốt (qua câu hỏi):**
- **JWT bằng OAuth2 Resource Server (Nimbus)** thay vì jjwt + `JwtFilter` tự viết. Lý do: filter Bearer token của Spring Security đã kiểm chứng rộng rãi (hạn dùng, chữ ký, thuật toán), giảm code bảo mật tự viết — chỗ dễ có lỗ hổng nhất. Đánh đổi: ít "tự tay" hơn khi trình bày cơ chế; bù lại phần tự viết vẫn có (phát hành token, xoay vòng refresh, nạp quyền). Vẫn tự phát hành token, **không** dùng máy chủ OAuth2/Keycloak. **Lưu ý**: plan §2 ghi rõ `jjwt` — câu hỏi lúc đó chỉ nhắc §7 (`JwtFilter`); đã sửa cả §2, §7 và CLAUDE.md cho khớp.
- **Quyền đọc CSDL mỗi request**: JWT chỉ mang `sub` = id người dùng; converter nạp user + role + permission (1 truy vấn `@EntityGraph`). Khóa tài khoản / gỡ role / xóa tài khoản có hiệu lực ngay (test `lockingAnAccountRevokesItsAccessImmediately`). Đánh đổi: +1 truy vấn / request — cache Redis nếu đo thấy cần.
- **Trust score làm ngay** (`TrustScoreCalculator` thuần + `TrustScoreService`), vì `MeResponse.trustScore` cần và công thức đã chốt đủ ở requirements §5.1. Tham số trong `localspot.trust.*` (application.yml).

**Quyết định kỹ thuật:**
- **HS256, khóa base64 ≥ 32 byte**, từ chối khởi động nếu khóa ngắn hơn; decoder chỉ chấp nhận HS256 (chặn đổi thuật toán) + kiểm issuer + hạn dùng. Một backend vừa phát hành vừa xác thực nên khóa đối xứng đủ; RS256 chỉ cần khi có dịch vụ khác xác thực token.
- **Khóa JWT theo môi trường**: dev có mặc định trong `application-dev.yml` (không bắt sửa `.env` hiện có), prod `${JWT_SECRET}` không mặc định; `docker-compose.yml` thêm `JWT_SECRET: ${JWT_SECRET:?...}` cho service backend (thay đổi Compose — cần để profile prod khởi động; báo lỗi rõ nếu thiếu). `.env.example` thêm biến.
- **Refresh token xoay vòng**: 32 byte ngẫu nhiên base64url, DB lưu SHA-256. Đánh dấu đã dùng bằng `UPDATE ... WHERE used_at IS NULL` nguyên tử → hai request đồng thời cùng token chỉ một thắng. Token đã dùng bị gửi lại → **thu hồi mọi token còn hiệu lực của người dùng** (U2), `@Transactional(noRollbackFor = ApiException.class)` để lệnh thu hồi được commit dù trả 401 (test `reusingRotatedTokenRevokesEverySession`). Token đã bị thu hồi do đăng xuất mà gửi lại → chỉ 401, không thu hồi hàng loạt (không đăng xuất các thiết bị khác vô cớ).
  - **Hệ quả cho frontend (checklist F)**: interceptor phải gộp các lần refresh đồng thời (single-flight); hai tab refresh cùng lúc sẽ bị coi là dùng lại → đăng xuất mọi nơi. Chấp nhận vì đúng U2; không thêm "cửa sổ ân hạn".
- **Đăng nhập chống dò email**: email không tồn tại vẫn chạy BCrypt với hash giả → cùng thời gian phản hồi, cùng thông báo lỗi. Chưa xác thực email vẫn đăng nhập được (FR-02 chỉ chặn viết review). Khóa → 403 `ACCOUNT_LOCKED` kèm thời hạn + lý do. Giới hạn 5 lần / 15 phút để ở D8.
- **Đăng ký**: email chuẩn hóa chữ thường; trùng → 409 (kiểm tra trước + bắt `DataIntegrityViolationException` cho trường hợp chen nhau). DTO chỉ có 3 trường → gửi kèm `roles`, `emailVerifiedAt` bị bỏ qua (test mass assignment). Gửi mail xác thực để D4.
- **Mật khẩu**: validator `@ValidPassword` — ≥ 8 ký tự, có chữ và số, **≤ 72 byte UTF-8** (BCrypt bỏ qua phần thừa; tiếng Việt có dấu 2–3 byte / ký tự). BCrypt cost 10, không dùng `{bcrypt}` prefix (khớp seed).
- **CSRF tắt**: access token trong header; cookie duy nhất là refresh token với SameSite=Strict + Path=/api/v1/auth.
- **Danh sách trắng endpoint công khai** (`POST /api/v1/auth/**`, health, info); còn lại mặc định cần đăng nhập — quên khai báo thì lỗi theo hướng an toàn.
- **Một định dạng lỗi**: 401/403 trong filter chuyển qua `HandlerExceptionResolver` tới `GlobalExceptionHandler` → `application/problem+json` có `code`. Khách chạm `@PreAuthorize` nhận 401, không phải 403. Validate → 422 + `errors[]`. Handler hiện bản tối thiểu; D7 bổ sung phần còn lại (404, optimistic lock, mã 3819, 500).
- Thêm **MapStruct** (đã có trong stack) với `unmappedTargetPolicy=ERROR` — trường DTO quên map là lỗi biên dịch. Thêm `GET /api/v1/me` (đã có trong openapi) vì frontend cần nạp lại hồ sơ khi tải lại trang.
- `AuthenticatedUser` (record bất biến) làm principal thay vì entity — không lazy loading ngoài transaction. SpotBugs bắt `Authentication` phải serializable → sửa đúng gốc (record `Serializable`), không thêm loại trừ.

**Kiểm chứng**: `./mvnw verify` xanh — **56 test** (thêm `TrustScoreCalculatorTest` 10 ca = bảng ví dụ §5.1 + biên; `ValidPasswordValidatorTest` 9; `AuthFlowTests` 13 qua HTTP trên MySQL thật: đăng ký/đăng nhập + thuộc tính cookie, trùng email không phân biệt hoa thường, 422, mass assignment, sai mật khẩu ≡ sai email, tài khoản khóa, token hết hạn / sai issuer / giả chữ ký, khóa có hiệu lực ngay, xoay vòng, dùng lại → thu hồi toàn bộ, đăng xuất), SpotBugs 0 lỗi.

---

## 2026-10-01 23:00 (+07) — Checklist D4: xác thực email, quên / đặt lại mật khẩu, đổi mật khẩu

**Bối cảnh**: supervisor duyệt D3 (commit `959bec7`, chưa push).

**Supervisor chốt (qua câu hỏi):** gửi mail bằng **RabbitMQ + spring-boot-starter-mail + Mailpit** (dependency mới chính thức của Spring + container mới trong Compose). Lý do: NFR-13 yêu cầu tác vụ nền có retry + dead-letter; `@Async` mất mail khi restart. Mailpit cho thấy email thật khi demo mà không gửi ra ngoài.

**Quyết định kỹ thuật:**
- **Luồng mail**: service phát `MailRequestedEvent` → `MailQueuePublisher` (`@TransactionalEventListener(AFTER_COMMIT)`) đẩy `EmailMessage` (JSON) lên exchange `localspot.tasks` → queue `mail.send` → `MailConsumer` gửi SMTP. Lỗi SMTP → retry 4 lần (2 s, ×3, tối đa 30 s) → reject → `localspot.dlx` → `mail.send.dlq` (giữ lại để xem / chạy lại trong Management UI).
  - **Sau commit**: không gửi link cho tài khoản bị rollback; token chắc chắn có trong CSDL khi người dùng bấm. Đánh đổi: RabbitMQ sập đúng giữa commit và publish thì mất mail (không có transactional outbox) — người dùng tự khắc phục bằng "gửi lại" / "quên mật khẩu"; outbox đủ tốt hơn nhưng thêm bảng + job, chưa đáng ở quy mô đồ án.
  - Nội dung mail dựng ở phía gửi (text + HTML tự viết, `HtmlUtils.htmlEscape` tên người dùng — không thêm template engine ngoài stack). Consumer generic, không truy cập CSDL. Log lỗi không ghi email người nhận (NFR-11).
- **Token một lần** (`user_tokens`): 32 byte ngẫu nhiên, lưu SHA-256; 24 h / 30 phút (U1, cấu hình `localspot.security.*-ttl`). Cấp link mới → link cũ cùng loại bị vô hiệu (đặt `used_at`, không xóa — UC01 7a). Dùng bằng `UPDATE ... WHERE used_at IS NULL` nguyên tử. Không tồn tại / hết hạn / đã dùng → cùng **410 `TOKEN_INVALID`** (không cho dò).
- **Link** theo sitemap: `{localspot.app.public-url}/verify-email?token=`, `/reset-password?token=`; prod lấy `APP_PUBLIC_URL`.
- **Gửi lại mail xác thực** cần đăng nhập (openapi không có `security: []`) → thêm luật `authenticated()` cho `/api/v1/auth/resend-verification` **trước** luật `permitAll` của `/api/v1/auth/**` trong `SecurityConfig`. Đã xác thực rồi thì 204 mà không gửi.
- **Quên mật khẩu** luôn 204 kể cả email không tồn tại. Còn chênh thời gian xử lý nhỏ (có email thì thêm 1 insert + publish) — chấp nhận; giới hạn tần suất ở D8 làm việc dò không đáng kể.
- **Đặt lại mật khẩu** → đổi hash + thu hồi mọi refresh token (kẻ giữ phiên cũ bị đăng xuất). Không tự đánh dấu email đã xác thực (đặc tả không yêu cầu — không tự thêm hành vi).
- **Đổi mật khẩu** (FR-06 "thu hồi các refresh token khác"): cookie refresh token có `Path=/api/v1/auth` nên `PUT /me/password` không biết token hiện tại → thu hồi tất cả rồi cấp family mới, trả `Set-Cookie` cho thiết bị đang thao tác. Kết quả đúng yêu cầu: thiết bị này giữ phiên, thiết bị khác bị đăng xuất. Access token cũ ở thiết bị khác còn ≤ 15 phút (đánh đổi stateless từ D3).
- **Đề xuất sửa đặc tả (chưa sửa)**: `PUT /me/password` sai mật khẩu hiện tại trả **401** theo openapi. Interceptor frontend thường hiểu 401 = hết phiên → tự refresh / đăng xuất. Hiện phân biệt bằng `code = INVALID_CURRENT_PASSWORD`; đề xuất đổi sang 422 kèm lỗi trường `currentPassword` — chờ supervisor.

**Lỗi phát hiện và đã sửa:**
1. **Lệch múi giờ JDBC**: Hibernate ghi UTC (`hibernate.jdbc.time_zone`), còn JDBC thuần (JdbcTemplate, native query) dùng múi giờ JVM (+07) → giá trị ghi bằng JDBC lệch 7 giờ. Lộ ra khi test "link hết hạn" vẫn được chấp nhận. Sửa ở gốc: `spring.datasource.hikari.data-source-properties.connectionTimeZone=UTC` + `forceConnectionTimeZoneToSession=true`; thêm test hồi quy `hibernateAndPlainJdbcAgreeOnUtcTimestamps`. Dữ liệu hiện có không bị ảnh hưởng (chỉ Hibernate và seed SQL — đều UTC — từng ghi).
2. **Compose hỏng từ commit D3**: `${JWT_SECRET:?...}` làm `docker compose up -d` (chỉ hạ tầng) lỗi khi `.env` chưa có biến, vì Compose nội suy cả service đang tắt. Đổi thành `${JWT_SECRET:-}`; thiếu biến thì backend prod tự từ chối khởi động (`@NotBlank` + kiểm tra độ dài khóa). Commit D3 chưa push nên chưa ảnh hưởng ai; sửa nằm trong commit D4.
3. Mock `JavaMailSender` làm health check mail của Actuator lỗi → tắt `management.health.mail` ở profile test (dev / prod vẫn bật).

**Test**: consumer RabbitMQ tắt ở profile test (`auto-startup: false`), chỉ bật trong `MailFlowTests` — test khác không đụng mail.

**Kiểm chứng**: `./mvnw verify` xanh — **67 test** (thêm `MailFlowTests` 10 ca đầu-cuối API → RabbitMQ → consumer → đọc token trong mail → API: xác thực email một lần + trust lên 10, nội dung mail, gửi lại vô hiệu link cũ, gửi lại cần đăng nhập, link hết hạn 410, đặt lại mật khẩu thu hồi mọi phiên, quên mật khẩu không lộ email, mật khẩu yếu 422, đổi mật khẩu giữ thiết bị này / đăng xuất thiết bị khác, **SMTP lỗi → 4 lần thử → DLQ** mà đăng ký vẫn 201; + 1 test hồi quy múi giờ), SpotBugs 0 lỗi. `docker compose config` hợp lệ cả chế độ hạ tầng lẫn `--profile app`.

---

## 2026-10-03 16:20 (+07) — Checklist D5: RBAC + `@PreAuthorize`

**Bối cảnh**: D4 đã commit (`afbce9e`). Bảng role / permission và việc nạp authority mỗi request đã có từ D1 / D3 — phần còn thiếu của D5 là phân quyền ở tầng phương thức và endpoint thật dùng nó.

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi D5 = hạ tầng + API UC31** (`GET /admin/users`, `POST/DELETE /admin/users/{id}/lock`, `PUT /admin/users/{id}/roles`) — kéo sớm một phần mục E "API quản trị". Lý do: D5 không có endpoint thật nào được bảo vệ thì `@PreAuthorize` chỉ kiểm chứng được bằng controller giả trong test; UC31 chính là màn quản trị RBAC.
- **Luật gán role (đóng O6)**: mọi tài khoản luôn có USER (thiếu → 422 `ROLE_USER_REQUIRED`); **OWNER không gán / gỡ qua API này** — chỉ cấp qua duyệt yêu cầu sở hữu UC30, vì OWNER đi cùng `places.owner_id`: gỡ tay để lại chủ quán mất quyền nhưng vẫn là `owner_id`. Danh sách gửi lên có OWNER khác hiện trạng → 422 `OWNER_ROLE_MANAGED_BY_CLAIM` (báo lỗi thay vì âm thầm bỏ qua; gửi lại nguyên danh sách từ `GET` vẫn hợp lệ).
- **Admin tự bảo vệ**: không tự khóa, không tự gỡ ADMIN của mình (409 `SELF_ACTION_FORBIDDEN`) → luôn còn ≥ 1 admin. Vẫn khóa / hạ quyền admin khác được. Phương án "không đụng admin khác" bị loại vì phải sửa CSDL mới hạ được một admin.
- **`PUT /me/password` sai mật khẩu hiện tại: 401 → 422** (câu treo từ D4) kèm `errors[0].field = currentPassword`. Lý do: interceptor frontend coi 401 là hết phiên → tự refresh / đăng xuất. Đã sửa openapi + test.

**Quyết định kỹ thuật:**
- `@EnableMethodSecurity` trong `SecurityConfig`; luật URL vẫn chỉ phân biệt công khai / cần đăng nhập, **quyền cụ thể khai báo bằng `@PreAuthorize` theo permission** ngay trên endpoint (đọc cạnh `x-permission` của openapi). Theo permission chứ không theo role → đổi quyền của role chỉ cần migration dữ liệu.
- **Hằng số `Permissions`** + nối chuỗi trong annotation (`"hasAuthority('" + Permissions.USER_LOCK + "')"`): gõ sai là lỗi biên dịch. Chuỗi trần `hasAuthority('user:lok')` sẽ âm thầm chặn mọi người.
- **`PermissionCatalogTests`** giữ ba nơi khai báo khớp nhau: bảng `permissions` (V2) = `Permissions.ALL` = mọi `x-permission` trong openapi; đồng thời quét `RequestMappingHandlerMapping`: endpoint `/api/v1/admin|moderation|owner/**` **bắt buộc có `@PreAuthorize`** (quên chú thích = mở cho mọi thành viên đã đăng nhập) và authority trong `@PreAuthorize` phải là permission có thật. Không thêm luật URL `hasRole('ADMIN')` cho `/admin/**` (phòng thủ kép) vì gắn cứng role, trùng nguồn sự thật; test quét thay thế vai trò đó.
- **Tìm kiếm người dùng**: keyset cursor theo id giảm dần (`KeysetCursor`, base64url mờ) — không OFFSET. Truy vấn 2 bước: lấy id có LIMIT, rồi nạp user + roles bằng `@EntityGraph` — gộp fetch collection với LIMIT sẽ buộc Hibernate phân trang trong bộ nhớ. Trust score cả trang tính với **một** truy vấn `GROUP BY` điểm phạt (`trustScoresOf`) thay vì N truy vấn. LIKE thoát `! % _` (ESCAPE `'!'` — không dùng `\` vì MySQL hiểu `\` trong literal là ký tự thoát chuỗi). Collation `utf8mb4_0900_ai_ci` → "quan tri" khớp "Quản Trị". LIKE `%q%` quét toàn bảng `users` — chấp nhận ở quy mô màn quản trị; tìm kiếm người dùng không cần Meilisearch.
- **Khóa**: `until` bắt buộc ở tương lai, lý do 1–500 ký tự; khóa lại = cập nhật thời hạn / lý do; thu hồi mọi refresh token. Access token cũ bị chặn ngay nhờ nạp quyền mỗi request (D3). Mở khóa idempotent. **Không ghi `user_violations`**: requirements §5.1 chỉ tính vi phạm từ review bị từ chối / báo cáo được xác nhận.
- **Gán role có hiệu lực ngay** ở request kế tiếp của người bị đổi quyền (không cần thu hồi token) — test `grantedRoleTakesEffectWithoutNewToken`.
- `ApiException` thêm `errors[]` theo trường (cùng dạng lỗi validate) + `ApiException.fieldError(...)`; mã lỗi mới `USER_NOT_FOUND`, `SELF_ACTION_FORBIDDEN`, `ROLE_USER_REQUIRED`, `OWNER_ROLE_MANAGED_BY_CLAIM`, `INVALID_CURSOR`. `CursorPage<T>` dùng chung cho các danh sách sau.
- **Chưa ghi `activity_logs`** (FR-42, Should): đặc tả yêu cầu ghi qua Spring AOP cho **mọi** thao tác admin / moderator — làm một lần ở E cùng API quản trị, không rải tay từng endpoint.
- SpotBugs: thêm loại trừ hẹp `RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE` chỉ cho lớp `*MapperImpl` — MapStruct sinh `if (user == null) return null;` rồi lại `if (user != null)` cho method một nguồn đối tượng; mã sinh tự động, không sửa được. Cảnh báo NP trong `GlobalExceptionHandler` sửa ở gốc.
- Sitemap: đánh dấu O6 ✅ và O2 ✅ (O2 đã chốt ngày 2026-09-30 ở D1 nhưng bảng chưa cập nhật).

**Kiểm chứng**: `./mvnw verify` xanh — **85 test** (thêm `AdminUserFlowTests` 14 ca qua HTTP trên MySQL thật: khách 401, thành viên 403 trên cả 4 endpoint, moderator không khóa được (UC29 3b), role mới có hiệu lực với access token cũ, tìm không dấu + phân trang cursor, lọc role / trạng thái khóa, LIKE thoát ký tự đại diện, tham số sai 422, khóa thu hồi phiên + mở khóa idempotent, không tự khóa nhưng khóa admin khác được, validate khóa + 404, luật USER / OWNER / tự gỡ ADMIN; `PermissionCatalogTests` 4 ca; sửa ca đổi mật khẩu sang 422), Spotless, SpotBugs 0 lỗi. Chưa commit — chờ supervisor duyệt.
- **Bổ sung sau duyệt (supervisor)**: giữ nguyên các chỗ sửa tài liệu ở trên; thêm mục checklist E "Nhật ký thao tác quản trị (FR-42)" vào plan §10 — làm AOP cùng mục E đầu tiên có thao tác duyệt (duyệt địa điểm, openapi đã ghi "ghi activity_log"), không đợi tới mục cuối "API quản trị" để các thao tác kiểm duyệt làm ở giữa không phải gắn log bù.

---

## 2026-10-03 17:04 (+07) — Checklist D6: `PermissionEvaluator` cho Place, Review, Comment

**Bối cảnh**: supervisor duyệt D5 (commit `cf2ad4c`, chưa push), giữ nguyên các chỗ sửa tài liệu và thêm mục FR-42 vào checklist E. Các endpoint cần kiểm tra quyền sở hữu (`PATCH/DELETE /reviews/{id}`, `DELETE /comments/{id}`, `/owner/places/{id}/**`, `PUT/DELETE /reviews/{id}/reply`) đều thuộc mục E.

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi = chỉ evaluator + test**, không kéo endpoint từ E. Endpoint ở E chỉ cần gắn `@PreAuthorize("hasPermission(#id, 'Review', 'review:update-own')")`. Phương án kéo `/owner/places` bị loại: đụng module địa điểm trước E1 (CRUD + luồng duyệt), dễ phải sửa lại.
- **Không có đường vượt quyền** cho moderator / admin trên quyền `*-own`: nhân sự xử lý nội dung người khác qua endpoint `/moderation` (có log, có tính vi phạm vào trust) — đúng cách openapi đã tách; không cho nhân sự âm thầm sửa chữ trong review của người khác.

**Quyết định kỹ thuật:**
- **`OwnershipPermissionEvaluator`** — bảng luật permission → (loại đối tượng, truy vấn sở hữu): `review:update-own` / `review:delete-own` → tác giả review; `comment:delete-own` → tác giả bình luận; `place:update-own` / `place:stats-own` → `places.owner_id`; `review:reply-own-place` → chủ địa điểm của review. Permission trong `hasPermission` vừa là authority RBAC vừa chọn luật → một biểu thức kiểm cả hai, và `PermissionCatalogTests` (D5) tự kiểm tên permission trong biểu thức.
- **Thứ tự**: (1) thiếu authority → 403 ngay, không chạm CSDL, không cho dò id; (2) bản ghi không tồn tại / đã xóa mềm → **404** mã theo loại (`PLACE_NOT_FOUND`, `REVIEW_NOT_FOUND`, `COMMENT_NOT_FOUND`); (3) không phải chủ → 403. Chọn 404 thay vì gộp vào 403: nội dung review / bình luận / địa điểm vốn công khai nên phân biệt không lộ gì đáng kể; đổi lại client nhận đúng ngữ nghĩa REST (xóa lần hai → 404). Đánh đổi nhỏ: id review đang chờ duyệt của người khác trả 403 thay vì 404 → biết id đó tồn tại.
- **Kiểm theo id, không nạp entity**: mỗi luật là một truy vấn `SELECT CASE WHEN … THEN TRUE ELSE FALSE END` chỉ đọc khóa ngoại → `Optional<Boolean>` (rỗng = không tồn tại, `false` = của người khác). `LEFT JOIN owner` để địa điểm chưa có chủ trả `false` thay vì mất dòng (bị hiểu nhầm là 404). Xóa mềm tự loại nhờ `@SQLRestriction`. Thêm `ReviewRepository`, `CommentRepository` (mới chỉ có truy vấn sở hữu).
- **Luật theo trạng thái** (vd. tác giả có được sửa review đang `HIDDEN` / `REJECTED`?) **không** đặt trong evaluator — đó là luật nghiệp vụ của service ở E; evaluator chỉ trả lời "người này có phải chủ không".
- **Gọi sai là lỗi lập trình**: permission không phải loại sở hữu, hoặc loại đối tượng không khớp permission → `IllegalArgumentException` (500, test bắt được) thay vì âm thầm `false` — chuỗi sai trong annotation sẽ lộ ngay ở lần chạy đầu thay vì chặn mọi người.
- **Đăng ký**: bean `static MethodSecurityExpressionHandler` nhận `@Lazy PermissionEvaluator` — hạ tầng method security tạo rất sớm, tiêm thẳng evaluator (phụ thuộc repository) sẽ kéo JPA khởi tạo sớm theo. Lớp evaluator `final` (SpotBugs `CT_CONSTRUCTOR_THROW`: constructor có thể ném — `Map.of` trùng khóa — lớp con có thể giữ đối tượng dở dang qua finalizer); không cần proxy CGLIB vì tiêm qua interface.
- **Test qua đúng đường chạy thật** (`OwnershipPermissionEvaluatorTests`, 11 ca): bean `Guarded` chỉ có trong test, chú thích `@PreAuthorize` y hệt endpoint E sẽ dùng; quyền nạp từ CSDL bằng `UserJwtAuthenticationConverter` như request có access token. Khẳng định `ApiException` 404 ném ra từ evaluator **không bị SpEL / Spring Security bọc lại** → `GlobalExceptionHandler` sẽ chuyển đúng thành 404.

**Sự cố môi trường**: lần `verify` đầu, container MySQL của Testcontainers mất kết nối khi context MockMvc đầu tiên khởi động (`Communications link failure`) → các lớp dùng chung context bị bỏ qua (ngưỡng lỗi context = 1). Không liên quan code: chạy lại các lớp đó và hai lần `verify` toàn bộ sau đó đều xanh. Theo dõi — nếu lặp lại thường xuyên thì xem tài nguyên Docker trong WSL.

**Kiểm chứng**: `./mvnw verify` xanh — **96 test** (+11: tác giả / người lạ, nhân sự không vượt quyền, tác giả thiếu authority bị chặn, review không tồn tại / xóa mềm → 404, kiểm quyền trước tồn tại, bình luận, chủ / không chủ / địa điểm chưa có chủ, `owner_id` lệch role bị chặn, phản hồi chỉ chủ địa điểm của review, cấu hình sai ném lỗi, mọi permission `*-own` có luật), Spotless, SpotBugs 0 lỗi. Chưa commit — chờ supervisor duyệt.

---

## 2026-10-03 17:33 (+07) — Checklist D7: `@RestControllerAdvice` chuẩn hóa response và exception

**Bối cảnh**: supervisor duyệt D6 (commit `146e44e`, chưa push). `GlobalExceptionHandler` đã có bản tối thiểu từ D3 (lỗi xác thực, 401/403, validate DTO, `ApiException`); D7 phủ phần còn lại.

**Supervisor chốt (qua câu hỏi):**
- **400 `MALFORMED_REQUEST`** cho request không đọc được (JSON hỏng, sai kiểu / thiếu tham số), **422** cho đọc được nhưng sai luật — đúng RFC 9110. Frontend gửi đúng kiểu thì không bao giờ gặp 400 → 400 là lỗi lập trình phía client. Phương án "gộp hết vào 422" bị loại vì lẫn lỗi cú pháp với lỗi nghiệp vụ. openapi thêm quy ước chung + response `BadRequest`, không liệt kê 400 ở từng operation.
- **500 kèm `errorId`** (UUID): body chỉ có câu chung chung (không stack trace, không SQL), log ghi cùng `errorId` + stack trace → người dùng / tester báo mã là tra đúng dòng log. Thêm thuộc tính tùy chọn `errorId` vào schema `Problem`.

**Quyết định kỹ thuật:**
- **Không bọc envelope cho response thành công** — trả thẳng DTO như openapi; HTTP status đã mang thông tin thành công / thất bại. "Chuẩn hóa response" của checklist = chuẩn hóa định dạng lỗi.
- **Mọi lỗi Spring MVC** (lớp cha `ResponseEntityExceptionHandler` xử lý: JSON hỏng, sai kiểu tham số, 404 không có route, 405, 406, 413, 415…) đi qua `handleExceptionInternal` → gắn `code` + câu tiếng Việt **theo HTTP status** (`Problems.forStatus`). Ánh xạ theo status thay vì theo từng lớp ngoại lệ: phủ toàn bộ, không lỗi nào lọt ra thiếu `code` khi Spring thêm loại ngoại lệ mới.
- **Ràng buộc trên tham số controller** (`@Min` trên `@RequestParam`) → 422 `VALIDATION_FAILED` cùng dạng `errors[]` như lỗi DTO, thay vì 400 mặc định.
- **Optimistic lock** (`OptimisticLockingFailureException` của Spring + `OptimisticLockException` JPA gốc) → 409 `CONCURRENT_MODIFICATION` ("tải lại rồi thử lại").
- **Lỗi CSDL phân loại theo mã lỗi MySQL** trong chuỗi nguyên nhân, không theo lớp ngoại lệ Spring — vì Spring dịch không đồng nhất (CHECK 3819 có SQLSTATE HY000 nên thành `UncategorizedSQLException`, phát hiện ở D1): 1062 trùng UNIQUE → 409 `DUPLICATE_RESOURCE`; 3819 CHECK và 1452 khóa ngoại thiếu cha → 422 `VALIDATION_FAILED`; còn lại (mất kết nối, 1451 khi xóa cứng — lỗi lập trình, cú pháp SQL…) → 500. Tới được nhánh 409/422 này nghĩa là service chưa chặn trước (hoặc hai request chen nhau) → log WARN để sửa; service vẫn nên ném `ApiException` mã cụ thể (vd. `EMAIL_ALREADY_EXISTS`).
- **Log không ghi query string** (chỉ method + path) — query có thể chứa dữ liệu cá nhân (NFR-11, vd. `?q=email`).
- **`FallbackErrorController` thay `BasicErrorController` ở `/error`**: lỗi phát sinh ngoài Spring MVC (vd. CSDL sập đúng lúc filter JWT nạp quyền) không tới được `@RestControllerAdvice`; container chuyển tới `/error` và mặc định Spring Boot trả JSON dạng khác (`timestamp, status, error, path`) không có `code` / `errorId`. Không khai báo `produces` để trang lỗi trả được cho mọi `Accept`. Hàm dựng body dùng chung (`Problems`) để hai nơi trả cùng định dạng.

**Kiểm chứng**: `./mvnw verify` xanh — **109 test** (+13 `ErrorHandlingTests`: JSON hỏng 400, sai kiểu / thiếu tham số 400, ràng buộc tham số 422 kèm trường, route không tồn tại 404, sai method 405, sai content type 415, optimistic lock 409, trùng khóa thật trên MySQL 409, CHECK 3819 thật → 422 (không phải 500), FK 1452 → 422, lỗi bất ngờ 500 không lộ chi tiết + `errorId` trùng log, lỗi SQL chưa phân loại 500, `/error` cùng định dạng). Lỗi CSDL thử bằng bảng tạm `TEMPORARY TABLE` hoặc lệnh bị chặn hoàn toàn — không ghi dữ liệu thật. Spotless, SpotBugs 0 lỗi. Chưa commit — chờ supervisor duyệt.

---

## 2026-10-04 14:58 (+07) — Checklist D8: Rate limiting

**Bối cảnh**: D7 đã commit + push (`e3074e4`). Mục cuối của checklist D.

**Supervisor chốt (qua câu hỏi):**
- **Redis, không thêm Bucket4j** — không thêm thư viện ngoài stack (CLAUDE.md §4); Redis đã có trong stack và Compose.
- **Ngưỡng** (ngoài U2 đã chốt — đăng nhập 5 lần sai / 15 phút theo (email, IP)): đăng ký **10 / giờ / IP** (nới vì nhiều máy chung một IP — wifi trường, phòng bảo vệ); quên mật khẩu **3 / giờ / email** (chặn spam hộp thư một người) và **20 / giờ / IP** (chặn dò hàng loạt email); gửi lại mail xác thực **3 / giờ / tài khoản**. Đã ghi vào requirements §5 và openapi `TooManyRequests`.
- **Redis sập → cho qua + log ERROR** (fail-open): Redis là phụ trợ; chặn hết sẽ biến Redis thành điểm hỏng duy nhất (không ai đăng nhập được). Brute force vẫn bị BCrypt làm chậm; health check báo Redis DOWN.
- **Giới hạn 5 review / 24 giờ (FR-23) đếm trong CSDL** (làm ở E), không qua Redis: là một phần cơ chế chống review ảo (lõi — CLAUDE.md §3), phải chính xác, kiểm chứng được bằng dữ liệu và **không tắt theo Redis** (vì đã chọn fail-open). Vẫn trả 429.

**Quyết định kỹ thuật:**
- **Cửa sổ trượt (sliding window log) bằng Lua** (`redis/sliding-window-rate-limit.lua`): sorted set, score = thời điểm ms; một script = một thao tác nguyên tử. Cửa sổ cố định (`INCR` + `EXPIRE`) đơn giản hơn nhưng cho dồn gần 2N lần quanh ranh giới hai cửa sổ (5 lần cuối cửa sổ trước + 5 lần đầu cửa sổ sau trong vài giây) — sai với "5 lần / 15 phút". Đánh đổi: bộ nhớ O(N) mỗi khóa thay vì O(1). Mỗi khóa chứa tối đa `limit` phần tử của luật đó (lần bị từ chối không được ghi, mốc quá cửa sổ bị xóa) — ngưỡng lớn nhất trong cấu hình hiện tại là 20 (`forgot-password-ip`) nên không đáng kể; nếu sau này có luật ngưỡng lớn (hàng nghìn / phút) thì cân nhắc Sliding Window Counter (O(1), xấp xỉ).
- **Lần bị từ chối không được ghi** → thử dồn khi đang bị chặn không kéo dài thời gian chặn; tối đa đúng N lần trong mọi cửa sổ. `Retry-After` = lúc lần cũ nhất rời cửa sổ (làm tròn lên giây).
- **Đăng nhập**: mỗi lần thử chiếm một lượt **trước** khi kiểm mật khẩu (nguyên tử → 100 request song song cũng chỉ 5 lần được thử; kiểm-trước-ghi-sau sẽ cho cả loạt lọt), mật khẩu đúng thì xóa bộ đếm → thực chất chỉ lần sai bị tính (U2). Vượt ngưỡng → 429 **kể cả khi mật khẩu đúng** — không cho kẻ dò biết đã trúng. Giới hạn theo (email, IP) đúng U2: người dùng thật ở mạng khác không bị khóa theo kẻ tấn công. Hạn chế đã biết: kẻ tấn công đổi email liên tục từ một IP (credential stuffing) không bị luật này chặn — U2 không yêu cầu; có thể thêm luật theo IP nếu cần.
- **Đăng ký** tính cả lần thất bại do email trùng (chặn dò email đã đăng ký bằng đăng ký hàng loạt); request sai validate (422) không tới service nên không tính.
- **Quên mật khẩu** đếm theo email cả khi email không tồn tại → 429 không tiết lộ email nào đã đăng ký (giữ nguyên tắc chống dò của D4).
- **Khóa băm SHA-256** (`rl:<luật>:<sha256>`): email / IP không nằm dạng rõ trong Redis (NFR-11); khóa có TTL = một cửa sổ, không tích rác.
- **Timeout Redis 500 ms** (`spring.data.redis.timeout`, `connect-timeout`): mặc định Lettuce chờ 60 s — "cho qua khi Redis sập" sẽ thành "mỗi lần đăng nhập treo 60 s".
- Gọi giới hạn ở **service** (`AuthService`, `AccountService`), không ở controller / interceptor: luật cần dữ liệu trong body (email) và kết quả nghiệp vụ (đăng nhập đúng → xóa bộ đếm); IP truyền qua `ClientInfo` có sẵn — service vẫn không phụ thuộc servlet API.
- Ngưỡng cấu hình ở `localspot.rate-limit.policies.*` (map theo enum `RateLimitPolicy`); thiếu ngưỡng cho một luật → từ chối khởi động. 429 dùng `RateLimitExceededException` + header `Retry-After` (giây) đúng openapi; câu báo "thử lại sau N phút".
- **Profile test** nới ngưỡng đăng ký (mọi request MockMvc từ 127.0.0.1, các lớp test đăng ký hàng chục tài khoản); `RateLimitFlowTests` đặt lại đúng giá trị production (10).

**Kiểm chứng**: `./mvnw verify` xanh — **123 test** (+7 `RateLimiterTests` trên Redis thật với đồng hồ điều khiển được: chặn sau N lần + `Retry-After` đúng, cửa sổ trượt từng lượt (khác cửa sổ cố định), lần bị từ chối không tính, khóa độc lập theo chủ thể / luật, reset, khóa băm + có TTL, **Redis sập → cho qua + log**; +7 `RateLimitFlowTests` qua HTTP với ngưỡng production: lần đăng nhập thứ 6 → 429 kể cả mật khẩu đúng, giới hạn theo (email, IP), đăng nhập đúng xóa bộ đếm, đăng ký thứ 11 / IP → 429, quên mật khẩu thứ 4 / email → 429 cả với email không tồn tại, thứ 21 / IP → 429, gửi lại mail thứ 4 → 429). Spotless, SpotBugs 0 lỗi. **Checklist D hoàn tất.** Chưa commit — chờ supervisor duyệt.

---

## 2026-10-04 21:44 (+07) — Checklist E1: CRUD địa điểm + luồng duyệt (kèm FR-42 nhật ký quản trị)

**Bối cảnh**: checklist D hoàn tất (D8 `4a7104d`, đã merge vào `develop`). Mục E đầu tiên; theo bổ sung ở D5, nhật ký thao tác quản trị (FR-42) làm cùng mục này. Nhánh `feature/be-places`.

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi = đầy đủ CRU**: `POST /places` (multipart, phần `place`; `photos` để E2), `GET /places` (lọc + sắp xếp + cursor), `GET /places/{slug}`, `GET /me/places`, `GET /owner/places`, `PATCH /owner/places/{id}`, `GET /moderation/places`, `POST /moderation/places/{id}/decision` + `@AuditedAction`. Để lại: `/places/duplicates` + `/nearby` (E4), `/search` (E5), ảnh (E2). Không có "D" — openapi không có endpoint xóa địa điểm; ẩn đi qua xử lý báo cáo (UC29).
- **Luật duyệt**: chỉ PENDING → APPROVED / REJECTED, đã xử lý → 409 `PLACE_ALREADY_MODERATED` (không đảo quyết định — APPROVED → REJECTED sẽ đụng review / rating đã có); **cấm tự duyệt** đề xuất của chính mình (409 `SELF_ACTION_FORBIDDEN`).
- **PlaceDetail tính thật ngay** các trường phụ thuộc bảng khác (đã có trong schema): phân bố sao (review PUBLISHED), `claimable`, `myReviewId`, ghi lượt xem `place_views` — mục sau không phải quay lại sửa DTO.

**Quyết định kỹ thuật:**
- **Nhật ký quản trị (FR-42)**: `@AuditedAction(action, targetType, targetId = SpEL, metadata = SpEL)` + `ActivityLogAspect` (`@Around`) — một chỗ thay vì rải lệnh ghi log. **Ghi trong cùng transaction với thao tác**: mặc định advice transaction của Spring Boot và aspect cùng độ ưu tiên thấp nhất → thứ tự bọc không xác định. `TransactionConfig` khai báo lại `@EnableTransactionManagement(order = LOWEST_PRECEDENCE - 1)` để transaction luôn bọc ngoài → log không bao giờ ghi một thao tác đã rollback (vd. 409 optimistic lock lúc commit) và không có thao tác thành công mà thiếu log. Aspect tự kiểm có transaction thật, thiếu thì ném lỗi (cấu hình sai lộ ngay). Đánh đổi: thay một cấu hình tự động của Spring Boot (chỉ để đặt `order`, giữ `proxyTargetClass = true`). Chỉ ghi khi phương thức trả về bình thường (thao tác bị từ chối không phải thao tác đã làm). Actor lấy từ SecurityContext, IP từ request. Không thêm dependency: `aspectjweaver` đã có qua `spring-boot-starter-data-jpa`.
- **Gắn lại D5**: `USER_LOCK` (metadata hạn + lý do), `USER_UNLOCK`, `USER_ASSIGN_ROLES` — `replaceRoles` trả `RoleChange(before, after)` để log có giá trị trước / sau như database.md §3 mô tả. 3 endpoint ghi; `GET /admin/users` là đọc nên không ghi (plan ghi "4 endpoint" là tính gộp).
- **Slug** = tên + thành phố, bỏ dấu (NFD + xóa dấu kết hợp, `đ` đổi tay): "Phở Thìn" ở Hà Nội → `pho-thin-ha-noi`; trùng → `-2`, `-3`… (tìm cả địa điểm đã xóa mềm — UNIQUE tính cả dòng đã xóa). Hai đề xuất cùng tên đồng thời có thể chọn cùng slug → UNIQUE chặn, 409 `DUPLICATE_RESOURCE`; hiếm nên không khóa bảng.
- **Ai xem địa điểm chưa duyệt**: người đề xuất, chủ, kiểm duyệt viên (`place:approve`); người khác **404** như không tồn tại (không lộ đề xuất đang chờ). `rejectReason` chỉ người đề xuất / kiểm duyệt viên thấy. `Viewer` dựng ở controller từ `Authentication` — service không phụ thuộc Spring Security.
- **Luật URL**: thêm `GET /api/v1/places` và `/api/v1/places/*` vào danh sách công khai (`SecurityConfig` — phần lõi, chỉ thêm luật permitAll đúng phạm vi đã chốt). Khai báo trước `GET /places/duplicates` cần đăng nhập — "duplicates" cũng khớp mẫu `{slug}`, không chặn trước thì E4 sẽ vô tình mở công khai.
- **Lượt xem**: upsert nguyên tử `INSERT … ON DUPLICATE KEY UPDATE` theo (địa điểm, ngày giờ Việt Nam), mỗi request +1, chỉ địa điểm APPROVED, **không tính khi chủ tự xem** (không phồng số liệu FR-34). Hạn chế đã biết: chưa lọc bot / F5 liên tục; mỗi lượt xem ghi vào một dòng nóng — đủ ở quy mô đồ án, có thể gom qua Redis nếu cần.
- **`claimable`** = APPROVED + chưa có chủ + người xem chưa có yêu cầu PENDING (UC23 bước 3). **`myReviewId`** đọc native, **kể cả review đã xóa mềm** (D2 — mỗi người một review tính cả review đã xóa).
- **Danh sách `GET /places`**: JPQL dựng động trong fragment `PlaceSearchRepositoryImpl` (chỉ ghép mảnh cố định, giá trị luôn bind — không SQL injection; dễ đối chiếu index hơn Criteria API). Ngữ nghĩa lọc: `categoryId` gồm danh mục con; `amenityIds` **AND** (thêm tiện ích = thu hẹp); `priceMax` = `price_min ≤ priceMax` (có lựa chọn trong ngân sách), chưa khai giá bị loại; `minRating` theo **điểm Bayesian** (điểm hiển thị), không theo trung bình thô; `city` khớp đúng (collation bỏ dấu). Fetch join `category` (ManyToOne) nên LIMIT vẫn chạy ở SQL.
- **Keyset tổ hợp** (`PlaceListCursor`): SCORE = (`bayesian_score`, `id`), MOST_REVIEWED = (`review_count`, `id`), NEWEST = `id`; phụ thêm `id DESC` để đồng điểm không trùng / sót giữa trang. Cursor mang tên thứ tự → dùng nhầm cursor của thứ tự khác bị 422 `INVALID_CURSOR` thay vì trả trang lệch.
- **Hàng chờ `/moderation/places`**: bỏ trống `status` = PENDING, FIFO (cũ nhất trước — như hàng chờ gộp); trạng thái đã xử lý là lịch sử → mới nhất trước.
- **Đồng thời**: hai kiểm duyệt viên cùng duyệt → `@Version` cho người sau 409 `CONCURRENT_MODIFICATION`; client gửi `version` đang thấy thì lệch cũng 409 ngay. Thêm `version` vào `PlaceDetail` (openapi chưa có — đã bổ sung) để frontend gửi lại khi sửa / quyết định.
- **Chủ sửa (PATCH)**: trường vắng = giữ nguyên, chuỗi rỗng xóa mô tả / điện thoại / website, `openingHours` / `amenityIds` thay toàn bộ; không có tên / vị trí / danh mục (openapi). `flush()` trước khi dựng response để `version` mới nằm trong response. Hạn chế: chỉ đổi giờ mở cửa (bảng con) thì `version` không tăng — rủi ro thấp vì mỗi địa điểm chỉ một chủ.
- **Validate**: `priceMin ≤ priceMax` (cả khi PATCH một nửa, đối chiếu giá đang lưu), danh mục / tiện ích phải tồn tại (id lạ báo lỗi, không âm thầm bỏ), giờ mở = giờ đóng bị từ chối (mơ hồ đóng / mở 24 giờ — mở cả ngày ghi 00:00–23:59), website chỉ `http(s)://` (chặn `javascript:` khi hiển thị thành link), thẻ trùng slug gộp làm một. Lỗi gắn theo trường (`openingHours[0].closeTime`…).
- **Chưa làm có chủ đích**: phát event khi duyệt (thông báo người đề xuất, đồng bộ Meilisearch) — chưa có listener nào, thêm cùng mục Thông báo / E5 thay vì để event không ai nghe. Không giới hạn tần suất đề xuất (đặc tả chưa có). Ảnh (`coverPhoto`, `photos`) trả rỗng tới E2.
- MockMvc Spring 7: `MockMultipartHttpServletRequestBuilder` không còn là lớp con của `MockHttpServletRequestBuilder` → helper test nhận lớp cha `AbstractMockHttpServletRequestBuilder<?>`.

**Tài liệu**: openapi (ngữ nghĩa lọc + keyset, `Location` + 401 cho `POST /places`, ai xem chi tiết + lượt xem, 409 / 422 của decision, FIFO hàng chờ, PATCH semantics, `PlaceDetail.version`) — Redocly lint hợp lệ; plan §10 đánh dấu E1 và FR-42.

**Kiểm chứng**: `./mvnw verify` xanh — **152 test** (+29): `PlaceFlowTests` 16 ca qua HTTP trên MySQL thật (đề xuất → PENDING chỉ người đề xuất / kiểm duyệt viên thấy, slug bỏ dấu + hậu tố khi trùng, validate 400 / 401 / 422 theo trường, duyệt công khai + log `PLACE_APPROVE` kèm actor / IP, không quyết định lại, từ chối cần lý do + lý do chỉ người đề xuất thấy, cấm tự duyệt, version lệch 409, member 403 / khách 401, hàng chờ FIFO + lịch sử mới nhất trước, lượt xem + phân bố sao + `myReviewId` gồm review đã xóa + `claimable`, lọc danh mục cây / tiện ích AND / giá / điểm / thành phố + 3 thứ tự, keyset không trùng khi đồng điểm, cursor sai thứ tự 422, `/me/places` mọi trạng thái, chủ sửa + thay giờ / tiện ích + version, chỉ chủ sửa được); `ActivityLogAspectTests` 4 ca (ghi actor / metadata, **log lỗi → thao tác rollback**, thao tác lỗi → không log, thiếu người đăng nhập → lỗi); `AdminUserFlowTests` +1 (lock / unlock / roles ghi log, before / after); `SlugsTest` 8 ca. Spotless, SpotBugs 0 lỗi. Chưa commit — chờ supervisor duyệt.
- **Sau duyệt**: supervisor duyệt, commit `25be0cc` (nhật ký quản trị) + `c2818c3` (địa điểm), PR #5 đã merge vào `develop`, CI xanh.

---

## 2026-10-05 21:55 (+07) — Checklist F (nền tảng): layout, router, axios + làm mới phiên, store auth; màn P01, đổi mật khẩu, P10

**Bối cảnh**: supervisor yêu cầu bắt đầu phần frontend tương ứng với những gì backend đã làm (D: auth, RBAC, UC31; E1: địa điểm). Nhánh `feature/fe-foundation` tạo từ `develop` sau khi PR #5 merge.

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi đợt này = nền tảng + auth + quản trị người dùng**: 3 layout, router + guard theo permission, axios + tự làm mới / 401, store auth, bộ component cơ bản; màn P01 đủ 5 route, `/settings/password`, khung quản trị + P10, trang 403 / 404. **Màn địa điểm để đợt sau**, khi có E3 (`GET /categories`, `/amenities`) — form đề xuất và bộ lọc cần danh sách danh mục / tiện ích.
- **Thư viện đúng plan §2, cài theo nhu cầu**: axios, @tanstack/vue-query, vee-validate + zod, dayjs, vue-toastification. Leaflet cài khi làm bản đồ.
- **O5 (sitemap) — chủ địa điểm sửa thông tin không phải duyệt lại**: giữ như E1 (đã làm theo hướng này mà chưa hỏi — đã báo supervisor). Sitemap đánh dấu O5 ✅.

**Thay đổi backend nhỏ (cần cho frontend):**
- **`MeResponse.permissions`**: danh sách permission gộp từ các role. Sitemap định nghĩa quyền route theo permission (`perm:user:view`), không theo role; frontend tự suy quyền từ tên role sẽ lặp lại bảng `role_permissions` (V2) và lệch khi đổi quyền của role bằng migration. Entity graph đăng nhập / `/me` nạp thêm `roles.permissions` (một truy vấn, không N+1).
- **`AdminUser.emailVerified`**: badge "● Chưa xác thực" của P10.
- openapi cập nhật cả hai trường; test `AuthFlowTests` (permissions của thành viên), `AdminUserFlowTests` (emailVerified). `./mvnw verify` xanh — 152 test, SpotBugs 0 lỗi.

**Quyết định kỹ thuật (frontend):**
- **Access token chỉ trong bộ nhớ** (store), tải lại trang thì khôi phục bằng `POST /auth/refresh` (cookie HttpOnly, S1). Không lưu token vào localStorage — script bị chèn (XSS) không đọc được token. Đánh đổi: mỗi lần mở / tải lại trang tốn một request làm mới.
- **Làm mới một lần cho nhiều request**: backend xoay vòng refresh token và coi việc dùng lại token cũ là bị đánh cắp → thu hồi mọi phiên (U2). Vì vậy (1) các request 401 đồng thời chờ chung một promise làm mới; (2) **khóa giữa các tab bằng Web Locks API** (`navigator.locks`) — hai tab cùng làm mới bằng một cookie sẽ tự đăng xuất người dùng; tab sau chờ tab trước xong, khi đó trình duyệt đã gửi cookie mới. Trình duyệt không hỗ trợ Web Locks thì chạy thẳng (vẫn gộp trong một tab). Đăng xuất báo cho các tab khác qua `BroadcastChannel`.
- **Interceptor axios không import store** (tránh vòng phụ thuộc): store đăng ký cầu nối `connectAuth` ở `main.ts`. Endpoint xác thực công khai (đăng nhập, làm mới, quên mật khẩu…) không mang token và không tự làm mới khi 401 — 401 của đăng nhập là sai mật khẩu. `ACCOUNT_LOCKED` → kết thúc phiên ngay (refresh token đã bị thu hồi khi khóa). Hết phiên: xóa cache Vue Query, thông báo, về `/login?redirect=` nếu đang ở trang cần đăng nhập.
- **Guard theo permission** ở mọi cấp route (`/admin` cần quyền nhân sự bất kỳ, `/admin/users` cần `user:view`); hàm quyết định là hàm thuần `resolveNavigation` để test. `?redirect=` chỉ nhận đường dẫn nội bộ (chặn open redirect `//evil.com`). `/admin` chuyển tới mục sidebar đầu tiên người dùng có quyền (sitemap §2.3).
- **Điều hướng tự cập nhật**: header và sidebar chỉ hiện mục **đã có route và người dùng có quyền** (`router.hasRoute`) — module sau thêm route là mục tự hiện, không có link chết tới trang chưa làm. Hiện tại header chưa có mục nào (Khám phá, Bản đồ… thuộc module địa điểm); sidebar chỉ có "Người dùng & vai trò". Kiểm duyệt viên (không phải admin) vào `/admin` sẽ gặp 403 cho tới khi có hàng chờ.
- **Design system**: port phần cần dùng của `bundle.css` sang `assets/components.css` (giữ tên lớp `ls-*` như README thành phần để đối chiếu), component Vue bọc lớp đó; thêm token khoảng cách `--space-*` vào `tokens.css`. Không dùng màu / cỡ chữ tùy ý trong template (chỗ cần cỡ chữ của P10 / P01 đặt trong CSS scoped, lấy đúng giá trị thiết kế). vue-toastification ghi đè kiểu: nền `surface-raised`, viền 1px, không bóng, trạng thái bằng ký hiệu ✓ ✕ ● + vạch màu.
- **Form**: VeeValidate + **Zod 3** + `@vee-validate/zod` — VeeValidate 4.15 chưa hỗ trợ Zod 4 (thử cài Zod 4: không có Standard Schema trong vee-validate). Luật mật khẩu đồng bộ `@ValidPassword` (≥ 8, chữ + số, ≤ 72 byte). Kiểm khi rời ô / khi gửi, không báo lỗi lúc đang gõ. Lỗi 422 của backend gắn vào đúng trường; trường backend báo mà form không có → gộp vào lỗi chung (không để lỗi biến mất).
- **Đăng ký → đăng nhập luôn → "Kiểm tra hộp thư"**: gửi lại mail xác thực cần đăng nhập (`/auth/resend-verification`), và người dùng không phải gõ lại mật khẩu. Nút gửi lại chờ 45 giây (P01) + tôn trọng `Retry-After` của 429. Thành viên chưa xác thực thấy dải nhắc dưới header kèm nút gửi lại.
- **Đăng nhập sai**: một câu chung trên ô mật khẩu, không nói sai email hay mật khẩu (P01). Đặt lại mật khẩu thành công → bỏ phiên ở trình duyệt (backend đã thu hồi mọi phiên) và về đăng nhập.
- **P10 khác bản thiết kế, có lý do**: (1) vai trò dùng **ô chọn** thay radio — một tài khoản giữ được nhiều vai trò (vd. chủ địa điểm kiêm kiểm duyệt viên) và API nhận cả danh sách; USER luôn chọn và khóa, OWNER chỉ hiển thị (O6 — chỉ cấp qua UC30), admin không tự gỡ ADMIN của mình — luật tách thành hàm thuần `roleRules.ts`, khớp `AdminUserService`; (2) cột "Đánh giá" đổi thành **"Điểm tin cậy"** — API `AdminUser` chưa có số đánh giá (module review chưa làm); (3) phân trang **"Tải thêm"** thay số trang — API cursor không trả tổng số (không COUNT bảng users mỗi lần lọc); (4) bộ lọc trạng thái chỉ Hoạt động / Đã khoá — API chưa lọc theo xác thực email; (5) form khóa (thời hạn 1 ngày – 1 năm + lý do bắt buộc) mở ngay trong bảng chi tiết, không hộp thoại (đúng tinh thần P10).
- **Bỏ khỏi P01**: ô "Ghi nhớ đăng nhập" (backend không có chế độ này — phiên luôn 7 ngày), ô "Đồng ý quy tắc cộng đồng" (chưa có trang quy tắc), liên kết "Đổi địa chỉ email" (chưa có API).
- **Theme Sáng / Tối**: mặc định theo hệ điều hành, nút chuyển ở header / chân sidebar, lựa chọn nhớ trong localStorage (tuỳ chọn hiển thị, không phải dữ liệu nhạy cảm).
- Vue Query: chỉ thử lại lỗi mạng / 5xx (tối đa 1 lần); đổi người đăng nhập thì xóa toàn bộ cache.

**Kiểm chứng**:
- `npm run lint`, `format:check`, `type-check`, `build` sạch; **Vitest 31 test** (7 file): interceptor (gắn token, 3 request 401 đồng thời → một lần làm mới thật rồi gửi lại, làm mới thất bại → hết phiên, endpoint đăng nhập không mang token / không làm mới, tài khoản khóa → hết phiên ngay); chuẩn hoá lỗi RFC 7807 + tách lỗi trường / lỗi chung; guard (khách → login kèm redirect, chặn open redirect, quyền ở mọi cấp route); store (gộp làm mới, khôi phục thất bại → khách, đăng xuất luôn xóa phiên); luật vai trò + trạng thái P10; `LoginPage` (validate trước khi gọi API, sai thông tin, 429, quay về `?redirect=`).
- **Chạy thật** (backend profile dev + Vite proxy, Chrome headless qua DevTools Protocol): đăng nhập qua proxy Vite nhận cookie `refresh_token` HttpOnly + Secure + `Path=/api/v1/auth`; đăng nhập sai hiện lỗi đúng ô; đăng nhập admin → `/admin` tự chuyển `/admin/users`; **tải lại trang vẫn giữ phiên** (khôi phục bằng cookie); chụp màn P01, P10 (Sáng + Tối), Cài đặt mật khẩu — khớp thiết kế. Chưa thử thao tác ghi (gán vai trò, khóa) trên CSDL dev để không đổi dữ liệu seed — đã có test backend `AdminUserFlowTests`.

Chưa commit — chờ supervisor duyệt.

---

## 2026-10-06 10:40 (+07) — Dựng môi trường trên máy Ubuntu mới

**Bối cảnh**: supervisor chuyển sang máy Ubuntu 22.04 mới (không phải WSL). Đã có Docker 29 + Compose v5; thiếu JDK 21 (có JDK 17), Node 24; MySQL 8.0 của hệ điều hành đang giữ cổng 3306.

**Đã làm**: cài JDK 21 (apt, gỡ JDK 17), Node 24 qua nvm; `.env` từ `.env.example` với bí mật ngẫu nhiên; hạ tầng `docker compose up -d`; backend dev + `./mvnw verify` (152 test) và kiểm tra frontend (lint, format, type-check, 31 test, build) đều xanh.

**Supervisor chốt:**
- **Cổng 3306 bị chiếm** → chỉ đổi trên máy này, không commit: `docker-compose.override.yml` (loại khỏi git bằng `.git/info/exclude`) đổi MySQL sang **3307**; backend dev chạy với `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/localspot`.
- **MinIO tạm thời trên máy này**: `quay.io/minio/minio` và `minio/minio` **không còn tải được** (MinIO ngừng phát hành image cộng đồng; lỗi `unauthorized` kể cả không đăng nhập) → `docker compose up -d` đầy đủ lỗi ở bước pull. Override cục bộ dùng `cgr.dev/chainguard/minio:latest` (`user: root` để ghi được volume). **Còn mở**: sửa `docker-compose.yml` cho cả nhóm / CI — phương án: build từ mã nguồn, image thay thế, hoặc đổi sang kho tương thích S3 khác (ảnh hưởng mục công nghệ trong báo cáo → cần supervisor quyết).

**Ghi chú**: lần `npm ci` đầu bỏ sót `axios`, `vue-toastification` trong `node_modules` (type-check vẫn qua, test / build lỗi) — chạy lại `npm ci` là đủ.

---

## 2026-10-06 11:31 (+07) — Checklist E2: Upload ảnh địa điểm, resize, nén, lưu S3 qua queue

**Bối cảnh**: mục kế tiếp của checklist E sau E1 (PR #5) và F nền tảng (PR #6) đã merge vào `develop`. Nhánh `feature/be-place-photos`. Thư viện theo plan §2: AWS S3 SDK v2, Thumbnailator.

**Supervisor chốt (qua câu hỏi):**
- **URL ảnh = bucket đọc công khai, URL ổn định** (không presigned, không proxy qua backend): trình duyệt / CDN cache được, response địa điểm không phải ký URL mỗi lần. Đánh đổi: ai có URL đều mở được, kể cả ảnh của địa điểm chưa duyệt — key chứa UUID ngẫu nhiên nên không đoán được.
- **Tối đa 30 ảnh / địa điểm** (ngoài 10 ảnh / request của openapi), cấu hình trong `application.yml`.
- **Chỉ nhận JPEG / PNG** (openapi cũ ghi cả WebP): ImageIO / Thumbnailator không đọc được WebP / HEIC; frontend crop bằng canvas rồi xuất JPEG nên ảnh WebP / HEIC từ điện thoại vẫn qua được giao diện. Không thêm TwelveMonkeys. Đã sửa openapi + NFR-09.

**Quyết định kỹ thuật:**
- **Luồng**: request kiểm tra cả lô (một ảnh hỏng → 422 `INVALID_IMAGE` đúng trường `photos[i]`, không tạo gì) → ảnh gốc lên `incoming/places/{placeId}/{uuid}` (riêng tư — còn EXIF / GPS) + bản ghi `PROCESSING` → **sau commit** đẩy id vào queue `photo.process` → consumer sinh 3 bản JPEG `-thumb` (320) / `-medium` (960) / `-large` (1920 px cạnh dài, không phóng to), q = 0.8 → `READY`, lưu `width/height` của bản large → xóa ảnh gốc. Message chỉ mang id, không đưa vài MB nhị phân qua RabbitMQ.
- **Kiểm tra upload (NFR-09)** theo chữ ký file (không tin `Content-Type`), đuôi tệp phải khớp định dạng thật, ≤ 5 MB, **≤ 40 MP đọc từ header** (chặn ảnh "bom giải nén" mà không giải mã trong request). Giới hạn request 55 MB khớp nginx → vượt là 413.
- **Gỡ EXIF không cần metadata-extractor**: Thumbnailator tự xoay theo EXIF Orientation khi đọc, ảnh đầu ra là JPEG ghi mới nên không mang EXIF nào (GPS — NFR-11). Bỏ metadata-extractor khỏi plan §2 vì không còn việc. PNG trong suốt vẽ lên nền trắng.
- **Lỗi & thử lại (NFR-13)**: ảnh không giải mã được → `FAILED` ngay, không retry; kho object lỗi → ném ra, listener retry 4 lần rồi sang `photo.process.dlq` (ảnh giữ PROCESSING tới khi chạy lại message bằng tay — giống mail). Xử lý lặp lại an toàn (bỏ qua ảnh không còn PROCESSING). Consumer **không giữ transaction** trong lúc đọc kho / resize — chỉ mở transaction ngắn để đọc / cập nhật trạng thái.
- **Nhất quán CSDL ↔ kho**: ảnh gốc lên kho ngay trong transaction của request (giữ kết nối CSDL lúc upload — chấp nhận ở quy mô đồ án, đổi lại không cần kho tạm thứ hai). `PlacePhotosStoredEvent`: commit → đẩy queue; **rollback → xóa ảnh gốc** vừa upload. Xóa ảnh: xóa bản ghi, object xóa **sau commit**. Kho sập lúc upload → 503, không tạo địa điểm. Không có outbox: RabbitMQ sập đúng lúc sau commit → ảnh kẹt PROCESSING (ghi log).
- **Giới hạn 30 ảnh khi upload đồng thời**: khóa dòng địa điểm (`SELECT … FOR UPDATE`) trước khi đếm — hai request không cùng thấy "còn chỗ". Ảnh `FAILED` không tính.
- **Ảnh bìa**: ảnh đầu tiên của địa điểm chưa có bìa; bìa bị xóa / xử lý lỗi → ảnh chưa lỗi kế tiếp theo `sort_order`. Chủ tự chọn bìa không có trong openapi — để sau nếu cần.
- **Ai thấy ảnh nào**: người ngoài chỉ thấy READY; người đề xuất / chủ / kiểm duyệt viên thấy cả PROCESSING / FAILED (kèm `status`, URL rỗng). Ảnh bìa trên thẻ danh sách nạp một truy vấn cho cả trang (`PlaceSummaries`) — không N+1.
- **Tách `ObjectStorage`** khỏi S3 SDK: service không phụ thuộc nhà cung cấp; test dùng bản trong bộ nhớ — CI không cần container MinIO (image chính thức không tải được). `S3ObjectStorage` là lớp chuyển tiếp mỏng, kiểm tra tay với MinIO dev. Client HTTP `url-connection` (bỏ Netty / Apache client không dùng); checksum CRC chỉ gửi khi API bắt buộc (R2 / MinIO cũ từ chối checksum mới của SDK ≥ 2.30).
- **Bucket**: dev tự tạo bucket + policy đọc công khai chỉ cho `places/*`, `reviews/*` (`localspot.storage.create-bucket`); kho chưa chạy chỉ cảnh báo, không chặn khởi động. Deploy: bucket tạo bằng hạ tầng; R2 không có bucket policy — public qua custom domain (khi đó `incoming/` cũng công khai nếu biết key; ngắn hạn, key ngẫu nhiên — cân nhắc bucket riêng cho ảnh gốc khi deploy).
- **`docker-compose.yml` (phần lõi — chỉ thêm)**: backend profile `app` nhận `STORAGE_*`, mặc định trỏ MinIO trong compose — thiếu thì backend prod không khởi động. `.env.example` thêm mẫu cấu hình R2.

**Còn để lại**: ảnh review dùng lại pipeline ở mục "Ảnh đính kèm review"; dọn ảnh kẹt PROCESSING / object rác định kỳ (chưa có job); giới hạn tần suất upload (đặc tả chưa có).

**Tài liệu**: openapi (định dạng / giới hạn ảnh, response 201 của chủ thêm ảnh, 404 / 413 / 422 / 503, mô tả `Photo`), requirements NFR-09, plan §2 + §10, database.md (`storage_key`, `is_cover`, `width/height`).

**Kiểm chứng**:
- `./mvnw verify` xanh — **170 test** (+18): `PlacePhotoFlowTests` 9 ca qua HTTP + RabbitMQ thật (đề xuất kèm ảnh → READY qua queue, URL đúng, không EXIF, ảnh gốc bị xóa, kích thước sau khi xoay, công khai sau duyệt + bìa trên thẻ danh sách; một ảnh hỏng → 422 không tạo gì; 11 ảnh → 422; kho sập → 503 không tạo gì; chủ thêm / người khác 403 / khách 401; giới hạn 30 không tính ảnh lỗi; xóa bìa → chuyển bìa + xóa object, ảnh của địa điểm khác 404; khách chỉ thấy READY; ảnh không giải mã được → FAILED + mất bìa, message lặp không làm gì); `ImageResizerTest` 4 ca, `ImageInspectorTest` 5 ca (gồm "bom giải nén" 20000 × 20000 chỉ bằng header PNG). Spotless, SpotBugs 0 lỗi.
- **Chạy thật với MinIO dev**: đề xuất kèm JPEG 3000 × 2000 (EXIF Orientation 6 + GPS) và PNG trong suốt → READY trong < 3 giây; 6 bản ảnh tải công khai được (200), bản large 1280 × 1920 đúng chiều, 0 thẻ EXIF; PNG ra nền trắng, không phóng to; ảnh gốc `incoming/` không đọc công khai được (403); địa điểm PENDING trả 404 cho khách.

---

## 2026-10-06 16:06 (+07) — Checklist E3: Danh mục phân cấp, tiện ích (giờ mở cửa đã có từ E1)

**Bối cảnh**: mục kế tiếp sau E2 (lúc bắt đầu E2 còn chờ duyệt — nay đã merge, PR #7; nhánh này rebase lên `develop` mới). E3 không phụ thuộc E2 → nhánh `feature/be-catalog` tách từ `develop` để hai PR duyệt độc lập (hai nhánh cùng thêm vào cuối `ErrorCode`, `plan-v1.md`, file log này — xung đột nhỏ khi merge PR thứ hai). Giờ mở cửa đã làm ở E1 (đề xuất, chủ sửa, chi tiết) — phần còn lại của E3 là API danh mục / tiện ích (FR-39, UC32).

**Supervisor chốt (qua câu hỏi):**
- **Cây danh mục tối đa 2 cấp** (gốc → con, như seed và P11): danh mục con chỉ gắn vào gốc; danh mục đang có con không được làm con; không tự làm cha. Không cần kiểm tra vòng lặp; bộ lọc "danh mục gồm cả danh mục con" của E1 (`c.id = :id OR c.parent.id = :id`) đúng với giới hạn này.
- **O7 — tiện ích dùng chung** cho mọi danh mục: giữ schema, không migration. Sitemap đánh dấu O7 ✅.

**Quyết định kỹ thuật:**
- **API**: `GET /categories` (cây), `GET /amenities` công khai (thêm luật `permitAll` đúng 2 đường dẫn trong `SecurityConfig` — openapi `security: []`); `/admin/categories`, `/admin/amenities` POST / PUT / DELETE với `category:manage` / `amenity:manage` (chỉ admin — Q2). PUT thay toàn bộ (`parentId` rỗng = gốc, `icon` rỗng = bỏ). Mỗi thao tác ghi nhật ký FR-42: `CATEGORY_CREATE/UPDATE/DELETE`, `AMENITY_CREATE/UPDATE/DELETE` (kèm slug).
- **Xóa chỉ khi không còn dùng** (openapi): danh mục còn con / còn địa điểm, tiện ích còn gắn địa điểm → 409 `CATEGORY_IN_USE` / `AMENITY_IN_USE`, **tính cả địa điểm đã xóa mềm** (truy vấn native — `@SQLRestriction` ẩn chúng). Riêng tiện ích: khóa ngoại `place_amenity` là `ON DELETE CASCADE`, không chặn ở service thì xóa tiện ích sẽ **âm thầm gỡ nó khỏi mọi địa điểm** — có test riêng.
- **Slug do quản trị viên nhập** (openapi `pattern ^[a-z0-9-]+$`), trùng → 409 `SLUG_TAKEN` theo trường `slug` (kiểm trước, UNIQUE ở CSDL vẫn là chốt cuối).
- **Cache — Spring Cache + Redis** (plan §2; thêm `spring-boot-starter-cache`, thuộc Spring Boot): mỗi cache serializer JSON đúng kiểu (`List<CategoryNode>`, `List<AmenityResponse>`), prefix `localspot:`, TTL 1 giờ chỉ là lưới an toàn. **Redis lỗi không làm hỏng request** (`CacheErrorHandler` chỉ ghi log → đọc CSDL), như rate limit D8. Cache trang chủ để mục G.
- **Lỗi tìm ra khi chạy cả bộ test — xóa cache bất đồng bộ**: bản đầu dùng `@CacheEvict(allEntries = true)` + `transactionAware()`; chạy riêng thì qua, chạy cả bộ thì test "sửa xong thấy ngay" thỉnh thoảng đọc cây cũ. Truy nguyên (trace `CacheInterceptor` + soi bytecode): ở Spring Data Redis 4, `RedisCache.clear()` **ghi bất đồng bộ** (trả về không chờ Redis), còn `invalidate()` đồng bộ; `@CacheEvict(allEntries)` gọi `clear()`. Dùng `beforeInvocation = true` (gọi `invalidate()`) cũng sai: Spring 7 `TransactionAwareCacheDecorator.invalidate()` xóa ngay, **trước commit** → request đọc chen giữa nạp lại dữ liệu cũ. Sửa: service phát `CatalogChangedEvent`, `CatalogCacheInvalidator` (`@TransactionalEventListener(AFTER_COMMIT)`) gọi `invalidate()` — cùng mẫu với `MailQueuePublisher`. Rollback → không xóa (cache vẫn đúng).

**Tài liệu**: openapi (mô tả cây ≤ 2 cấp / cache, 404 / 409 / 422 của admin, `icon` ≤ 100, ngữ nghĩa PUT), requirements FR-39, sitemap O7 ✅, plan §10 E3, database.md `categories.parent_id`.

**Kiểm chứng**:
- `./mvnw verify` xanh — **159 test** (+7, tính trên `develop`): `CatalogFlowTests` qua HTTP trên MySQL + Redis thật — cây 2 cấp + tiện ích công khai; tạo / sửa (chuyển cha) / xóa danh mục **thấy ngay qua API công khai dù đã nạp cache** + nhật ký đúng người; luật 2 cấp (cháu, gốc có con, tự làm cha, cha không tồn tại; chuyển hết con rồi gốc làm con được); slug trùng 409 / sai định dạng 422 / 404; danh mục còn con / còn địa điểm (kể cả xóa mềm) 409; tiện ích tạo / sửa / trùng slug / đang dùng 409 (liên kết không bị CASCADE mất) / xóa / 404; member + kiểm duyệt viên 403, khách 401. Spotless, SpotBugs 0 lỗi.
- **Kiểm tra test có bắt lỗi**: bỏ việc xóa cache khi tạo danh mục → test cache đỏ (cả cơ chế cũ lẫn mới). Tổ hợp `PlaceFlowTests` + `CatalogFlowTests` từng làm lộ lỗi chạy 3 lần liên tiếp đều xanh.
- **Chạy thật (dev)**: `GET /categories` 3 gốc (6 / 3 / 4 con), `GET /amenities` 14 mục; Redis có `localspot:categories::SimpleKey []`, `localspot:amenities::…` dạng JSON, TTL 3600. Không thử ghi trên CSDL dev để không thêm dữ liệu — đã có test.

Chưa commit — chờ supervisor duyệt.

---

## 2026-10-07 10:18 (+07) — Checklist E4: Truy vấn địa điểm theo bán kính + cảnh báo nghi trùng

**Bối cảnh**: E3 đã merge (PR #8). Nhánh `feature/be-places-nearby` từ `develop`. Theo ghi chú E1, mục này gồm cả `GET /places/duplicates` (U7) vì dùng chung truy vấn không gian.

**Supervisor chốt (qua câu hỏi):**
- **"Tên gần giống" (U7)** = chuẩn hóa (chữ thường, bỏ dấu, bỏ ký tự đặc biệt — như slug), bỏ từ chung chung ("quán", "quán ăn", "nhà hàng", "cà phê", "cafe", "coffee", "tiệm", "cửa hàng", "shop" — theo ranh giới từ: "cá kho" giữ nguyên), rồi giống nếu một tên chứa nguyên từ tên kia (tên ngắn ≥ 4 ký tự) hoặc độ giống Levenshtein ≥ 0.8. Tự viết (vài chục dòng) — không thêm thư viện.
- **Phạm vi nghi trùng** = địa điểm APPROVED + đề xuất PENDING **của chính người hỏi** (chặn tự đề xuất hai lần). Đề xuất chờ của người khác vẫn riêng tư như E1; kiểm duyệt viên bắt trùng giữa người dùng ở hàng chờ.

**Quyết định kỹ thuật:**
- **Hai bước như openapi**: `MBRContains(hộp, location)` lọc thô trên spatial index `sx_places_location`, rồi `ST_Distance_Sphere ≤ radius` lọc chính xác + sắp gần nhất. Hộp tính trong Java: nửa cạnh vĩ độ = r / 111 320, kinh độ chia thêm cos(vĩ độ); **nới 1 % + 10 m** để sai số xấp xỉ độ ↔ mét không làm rơi điểm sát mép (hộp chỉ lọc thô, rộng hơn không sai kết quả). Tọa độ SRID 4326 trong MySQL theo trục **(lat, lng)** — khớp seed `POINT(lat lng)`.
- **`FORCE INDEX (sx_places_location)`** — đo được, không phải đoán: `EXPLAIN` trên 300 địa điểm seed cho `type = ALL` (optimizer chọn quét toàn bảng dù index lọc được: hộp 2 km chỉ còn 33 / 300 dòng); có `FORCE INDEX` → `range` trên spatial index, cùng kết quả. NFR-03 yêu cầu truy vấn bán kính luôn qua spatial index, và khi bảng lớn dần index luôn đúng. Đánh đổi: gợi ý index gắn câu SQL với tên index (đổi tên index phải sửa query — test `EXPLAIN` sẽ báo).
- **SQL native** (ngoài JPQL): nên tự lọc `deleted_at IS NULL` (`@SQLRestriction` không áp cho native). Chuỗi SQL ghép từ mảnh cố định, mọi giá trị bind. Truy vấn trả (id, khoảng cách), rồi nạp địa điểm + danh mục một lần và giữ thứ tự; thẻ dùng `PlaceSummaries` (ảnh bìa một truy vấn) + `distanceM`.
- **`distanceM` trả cả ở `/duplicates`** (openapi cũ ghi "chỉ có ở /nearby") — "cách 12 m" giúp người dùng quyết định; đã sửa mô tả.
- Thông báo validate tham số của `PlaceController` đổi sang tiếng Việt (trước đó lỗi `@DecimalMin` / `@Max` trên `@RequestParam` dùng câu tiếng Anh mặc định của Hibernate Validator — gồm cả bộ lọc E1).

**Tài liệu**: openapi (`/nearby`: chỉ APPROVED, danh mục gồm con, FORCE INDEX, 400 / 422; `/duplicates`: luật giống, phạm vi, 401 / 422; `distanceM`), requirements U7, plan §10 E4.

**Kiểm chứng**:
- `./mvnw verify` xanh — **190 test** (+13): `NearbyFlowTests` 7 ca (gồm ca trùng giữa hai người dùng — xem bổ sung bên dưới) (đúng địa điểm APPROVED trong bán kính theo thứ tự, loại PENDING / REJECTED / đã xóa mềm, `distanceM` sai lệch < 1 %, `radius` / `limit`; **mép bán kính chính xác cả 4 hướng** — 985 m lọt, 1015 m không; lọc danh mục gồm con; 422 / 400 theo tham số; nghi trùng trong 50 m + đề xuất chờ của mình, loại tên khác / ngoài 50 m / đề xuất chờ của người khác; 401 / 422); `PlaceSpatialIndexTests` 2 ca (`EXPLAIN` đúng câu SQL ứng dụng chạy trên 300 dòng sau `ANALYZE TABLE` → `range` / `sx_places_location`; hộp đúng trục và rộng hơn theo kinh độ); `PlaceNamesTest` 4 ca. SpotBugs 0 lỗi.
- **Test có bắt lỗi** (mutation): bỏ `FORCE INDEX` → test `EXPLAIN` đỏ (`type = ALL` cả khi có 300 dòng) — xác nhận gợi ý là cần thiết; bỏ hệ số cos(vĩ độ) của hộp → test mép bán kính đỏ.
- **Chạy thật (dev, 300 địa điểm seed)**: `/nearby` quanh Hồ Gươm 2 km → 25 địa điểm, đúng bằng số đếm bằng SQL toàn bảng, sắp tăng dần (349 m … 1991 m); `radius = 50000` → 422; `/duplicates` tại "Trà sữa Bếp Nhà": "tra sua bep nha 2", "Quán trà sữa Bếp Nhà", "Bếp Nhà" đều cảnh báo, "Phở Thìn" không; khách → 401.


**Bổ sung 2026-10-07 — trùng giữa hai người dùng** (supervisor hỏi: người khác đề xuất địa điểm giống của tôi thì sao?):
- **Lỗ hổng**: phạm vi U7 đã chốt (APPROVED + đề xuất chờ của chính mình) nghĩa là hai người dùng đề xuất cùng một quán sẽ **không ai được cảnh báo**; hàng chờ kiểm duyệt cũng chưa có gì nối hai đề xuất — ghi chú E4 trước đó "kiểm duyệt viên bắt trùng ở hàng chờ" là nói quá, kiểm duyệt viên chỉ có thể tự để ý.
- **Cách xử lý (supervisor đồng ý)**: mỗi mục PENDING của `GET /moderation/places` kèm `possibleDuplicates` — cùng luật tên gần giống trong 50 m nhưng xét **đề xuất chờ của mọi người** (kiểm duyệt viên vốn xem được mọi đề xuất chờ, không lộ gì thêm). Kiểm duyệt viên duyệt một, từ chối cái kia với lý do "Trùng với …" — dùng luồng từ chối có sẵn. Không chặn tự động (U7 chỉ cảnh báo; hai quầy trong cùng chợ có thể khác nhau thật), không gộp đề xuất (không có UC).
- **Đổi response hàng chờ**: `{ place, possibleDuplicates }` (`ModerationPlace`) thay vì thẻ địa điểm trần — trường nghi trùng không lọt vào danh sách công khai. Màn hàng chờ quản trị chưa làm nên không vỡ frontend. Mỗi mục một truy vấn không gian trên spatial index (trang ≤ 50 mục) — chấp nhận ở quy mô hàng chờ, không gộp thành một SQL phức tạp. Lịch sử (trạng thái đã xử lý): danh sách rỗng.
- Test `moderatorSeesDuplicateProposalsFromDifferentUsers`: Alice và Bob đề xuất "Cà phê Giảng" / "Giảng Coffee" cách 20 m — Bob không thấy đề xuất của Alice ở `/duplicates`, kiểm duyệt viên thấy mỗi đề xuất liệt kê đề xuất kia + địa điểm đã duyệt gần đó (loại tên khác, loại ngoài 50 m); lịch sử không có nghi trùng. Mutation: giới hạn truy vấn kiểm duyệt về APPROVED → test đỏ.

Chưa commit — chờ supervisor duyệt.

## 2026-10-08 11:55 (+07) — Checklist E5: Tích hợp Meilisearch, synonym tiếng Việt

**Bối cảnh**: E4 đã merge (PR #9). Nhánh `feature/be-search` từ `develop`. UC08 đã chốt khung: Meilisearch trả id → nạp lại MySQL; lọc khoảng cách chuyển sang UC09; fallback `LIKE` trên tên (U9); đồng bộ qua hàng đợi (plan §7 `SearchReindexConsumer`).

**Đo trước khi chọn** (container Meilisearch thật, dữ liệu tên / địa chỉ tiếng Việt): Meilisearch tự bỏ dấu và đổi `đ → d` cả lúc lập chỉ mục lẫn lúc tìm ("pho", "phỏ" ra "Phở Thìn"; "dong do" ra "Đông Đô") → **không cần cột không dấu riêng**. Thiếu: synonym ("cafe" không ra "Cà phê Giảng"), typo cho âm tiết ngắn, cách bỏ từ khi truy vấn nhiều từ.

**Supervisor chốt (qua câu hỏi):**
- **Meilisearch v1.10 → v1.54**, client Java chính thức 0.22.1 (plan §2). Lý do: client 0.22 gửi `filterableAttributes` dạng mới mà v1.10 từ chối (`invalid_settings_filterable_attributes` — đã thử); v1.10 ra 10/2024. Đổi image trong compose + Testcontainers; máy dev xóa volume `meilisearch_data` một lần (index rỗng, tự dựng lại từ MySQL) — ghi ở README.
- **Synonym**: tôi soạn 30 nhóm (`backend/src/main/resources/search/synonyms.json` — đồ uống, món, loại quán, tên thành phố viết tắt / không dấu), supervisor duyệt sau. Dạng nhóm → mỗi từ nhận các từ còn lại (hai chiều).
- **So khớp — chốt qua 4 vòng đo** (lần lượt đổi khi có số liệu mới, mỗi lần hỏi lại supervisor):
  1. Chọn `frequency` + typo từ 4 ký tự (vòng 1, 7 truy vấn mẫu).
  2. Test thật lộ ra: typo 4 cho tiền tố "thin" khớp "tĩnh" → mô tả "Quán… yên tĩnh" + địa chỉ "Phố" của chỗ khác khớp **đủ** "quan pho thin" — Meilisearch chỉ bỏ từ khi **không** tài liệu nào khớp đủ, nên Phở Thìn **biến mất** khỏi kết quả → đổi về typo 5.
  3. Với typo 5, `frequency` trả **rỗng** cho truy vấn thường ("pho bat dan ngon", "giang thin") và bỏ sót Phở Thìn / Bát Đàn cho "pho ngon ha noi"; `last` (mặc định) đúng hết, chỉ hỏng khi đầu truy vấn là từ chung chung ("quán / tiệm phở thìn"). Stop word "quán" của Meilisearch không có tác dụng (đã thử) → **`last` + bỏ từ chỉ loại hình quán ở backend** (`SearchQueries`: "quán ăn", "quán", "tiệm", "nhà hàng", "cửa hàng", "shop"; chuẩn hóa như U7 nhưng **giữ** "cà phê" / "cafe" / "coffee" — người dùng tìm đúng loại đó — và "quan" trước số = quận; bỏ hết thì gửi nguyên).
  4. Đo lại với `last` + bỏ từ: typo 5 trả rỗng cho "bunn cha", "phoo thin"; typo 4 cho kết quả đúng đứng đầu ở **mọi** truy vấn mẫu (nhiễu chỉ ở sau) — lỗi "tĩnh" ở vòng 2 chỉ xảy ra vì "quán" làm Phở Thìn không khớp đủ, nay đã bỏ → **quay lại typo 4 / 8** (lựa chọn ban đầu của supervisor; báo lại trong tóm tắt phiên).

**Quyết định kỹ thuật:**
- **Tài liệu index chỉ chứa trường để tìm / lọc / xếp** (tên, thẻ, tên danh mục + danh mục cha, địa chỉ, thành phố, mô tả; id danh mục, tiện ích, `priceMin`, `bayesianScore`, `reviewCount`). Kết quả luôn nạp lại từ MySQL theo id và lọc APPROVED → ảnh bìa / điểm không bao giờ cũ, index lệch không làm lộ địa điểm đã ẩn; id lệch được đẩy đi đồng bộ lại (tự sửa). Thứ tự `searchableAttributes` = mức ưu tiên (tên trước mô tả); luật xếp hạng mặc định + `bayesianScore:desc` ở cuối — điểm chỉ phân định khi độ liên quan ngang nhau.
- **Đồng bộ**: `PlaceIndexChangedEvent` → sau commit → queue `search.reindex` (lô ≤ 100 id, retry 4 lần → `search.reindex.dlq`) → consumer đọc trạng thái **hiện tại**: APPROVED thì ghi, còn lại (PENDING / REJECTED / HIDDEN / đã xóa mềm) thì xóa khỏi index — message không cần nói thêm / xóa, chạy lặp vẫn đúng. Chờ tác vụ Meilisearch xong, tác vụ thất bại = lỗi → retry. Phát ở: duyệt, chủ sửa, đổi tên / cha danh mục (chỉ khi tên / cha đổi). Rating, ẩn qua báo cáo phát cùng sự kiện khi làm các mục đó. Không mở transaction trong lúc gọi Meilisearch.
- **Khởi động**: áp cấu hình index (idempotent) + đẩy toàn bộ id đã duyệt **qua cùng queue** (một consumer, ghi theo thứ tự — không có luồng ghi thứ hai chen vào). Bù message bị mất (không có outbox) và dựng index mới. Meilisearch sập lúc khởi động → chỉ log, cấu hình áp ở lần ghi đầu.
- **`GET /search`** thêm `sort` (RELEVANCE mặc định / SCORE / NEWEST / MOST_REVIEWED) — UC08 bước 5 có "đổi sắp xếp" mà openapi cũ thiếu. Bộ lọc giống `/places` (danh mục gồm con, tiện ích AND, giá, điểm). **Cursor theo vị trí** (`SORT|offset`, tối đa 1000 kết quả như Meilisearch) — độ liên quan không có khóa keyset; đánh đổi: dữ liệu đổi giữa hai lần tải có thể lệch một vị trí.
- **Fallback (U9)**: tên chứa **mọi** từ khóa (`LIKE`, ký tự `%` `_` trong từ khóa được thoát bằng `!`), cùng bộ lọc / thứ tự (RELEVANCE → SCORE), `degraded = true`, log WARN. Collation `utf8mb4_0900_ai_ci` không phân biệt dấu, **kể cả `đ = d`** (đã kiểm bằng test). Client Meilisearch không cho đặt timeout HTTP (mặc định 10 s) → sau mỗi lần lỗi **ngừng gọi Meilisearch 30 s** (cấu hình được) thay vì để mọi request chờ một máy chủ treo — không thêm thư viện circuit breaker.
- **OkHttp 5 trên Maven**: artifact `okhttp` của client chỉ là jar rỗng (Kotlin Multiplatform, Gradle metadata) → khai báo `okhttp-jvm` cùng phiên bản. Không phải thư viện mới.
- **Docker / bảo mật**: compose truyền `MEILI_HOST`, `MEILI_API_KEY` (mặc định master key — deploy thật nên tạo khóa riêng) và `depends_on` meilisearch cho backend; `SecurityConfig` thêm `GET /api/v1/search` công khai (openapi `security: []`).

**Tài liệu**: openapi `/search` (luật tìm, `sort`, cursor, fallback, 400 / 422), requirements (U9 chi tiết + dòng tham số tìm kiếm), plan §2 / §6 / §10 E5, README (đổi phiên bản Meilisearch, synonym).

**Kiểm chứng**:
- `./mvnw verify` — **202 test** xanh (+12): `SearchFlowTests` 5 ca trên Meilisearch + MySQL + RabbitMQ thật (tiếng Việt: không dấu, gõ dấu sai, `đ`, typo kể cả âm tiết 4 ký tự, "quán / tiệm phở thìn", synonym, tìm theo thẻ / mô tả, đề xuất chờ không vào index; bộ lọc danh mục gồm con / tiện ích AND / giá / điểm, 4 thứ tự, phân trang + cursor sai thứ tự → 422; đồng bộ qua queue khi duyệt → chủ sửa mô tả → đổi tên danh mục; địa điểm bị ẩn / xóa mềm không đồng bộ vẫn không bị trả và index tự xóa; tham số 400 / 422, công khai), `SearchFallbackFlowTests` 2 ca (**dừng hẳn container Meilisearch**: LIKE mọi từ, không phân biệt dấu kể cả `đ`, thoát `%`, bộ lọc / thứ tự / phân trang, `degraded`; không gọi lại Meilisearch trong thời gian chờ), `SearchQueriesTest`, `PlaceSearchIndexTest` (nhóm synonym hai chiều, biểu thức lọc, thứ tự). SpotBugs: `CT_CONSTRUCTOR_THROW` ở `PlaceSearchIndex` (constructor đọc synonym có thể ném) → lớp `final`; 0 lỗi.
- **Test có bắt lỗi** (mutation, mỗi lần một chỗ): bỏ lọc lại APPROVED khi nạp kết quả → test địa điểm bị ẩn đỏ; bỏ `SearchQueries` → test tiếng Việt đỏ ("quán phở thìn"); bỏ phát sự kiện khi duyệt → test đồng bộ qua queue đỏ.

Chưa commit — chờ supervisor duyệt (gồm danh sách synonym).

## 2026-10-08 (+07) — Checklist E6: CRUD đánh giá + mỗi người một review

**Bối cảnh**: E5 đã merge (PR #10). Nhánh `feature/be-reviews` từ `develop`. Đã có sẵn: entity `Review` (cột IP, `ip_flagged`, `version`), `UNIQUE(place_id, user_id)`, `TrustScoreService`, `OwnershipPermissionEvaluator` cho review, IP thật sau nginx (`ClientInfos`).

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi**: E6 làm **đủ cổng UC12 khi viết** (email → validate → địa điểm → chủ → trùng → 5 / 24 h → trust + IP ⇒ PUBLISHED / PENDING) và phát sự kiện tính lại rating; hàng chờ + duyệt review (UC28) ở mục "Trust score và hàng đợi duyệt review", ảnh ở mục "Ảnh đính kèm review", listener Bayesian ở mục "Tính lại rating". POST vẫn multipart (phần `review` giờ, phần `photos` sau) → API không đổi.
- **Sửa theo trạng thái**: `HIDDEN` (bị ẩn sau báo cáo) → 409 `REVIEW_LOCKED` — không cho sửa lén nội dung bị báo cáo để hiện lại; `REJECTED` → gửi lại: luôn về `PENDING` (kiểm duyệt viên đã phản đối), xóa lý do; `PUBLISHED` / `PENDING` theo openapi (trust < 30 → `PENDING`, ngược lại giữ — review đang chờ, vd. bị cờ IP, không tự được đăng vì sửa).
- **Chủ địa điểm không tự đánh giá** địa điểm của mình → 409 `SELF_ACTION_FORBIDDEN` (cùng tinh thần "kiểm duyệt viên không tự duyệt"); review viết trước khi thành chủ giữ nguyên.

**Quyết định kỹ thuật:**
- **Đếm cả review đã xóa** cho giới hạn 5 / 24 h (xóa không trả lại lượt — nếu không, viết–xóa–viết lách được) và cho luật IP; native query vì `@SQLRestriction` ẩn dòng đã xóa. `Retry-After` = lúc review cũ nhất trong cửa sổ rời cửa sổ.
- **Khóa dòng người dùng** (`SELECT … FOR UPDATE`) khi viết: hai request đồng thời của cùng một người lần lượt đếm giới hạn — không cùng thấy "còn lượt". Hai request cùng địa điểm: `UNIQUE` chặn request sau (409 `DUPLICATE_RESOURCE` qua handler chung).
- **Dải IP**: IPv4 /24 (requirements §5); IPv6 /64 — một thuê bao / hộ thường nhận nguyên /64, tương đương vai trò /24; `::ffff:a.b.c.d` coi là IPv4. Chỉ nhận chuỗi ký tự của địa chỉ, không bao giờ tra DNS.
- **Lệch 7 giờ bắt được bằng test**: native `MIN(created_at)` trả `LocalDateTime` đã bị driver đổi sang múi giờ JVM (+07) → `Retry-After` sai 25 200 s. Sửa bằng `UNIX_TIMESTAMP` tính trong MySQL (phiên ép UTC) trả epoch ms — không phụ thuộc chuyển đổi của driver. Tham số `Instant` khi bind thì đúng (Hibernate `jdbc.time_zone = UTC`).
- **Validate**: nội dung ≥ 20 ký tự **sau khi bỏ khoảng trắng hai đầu** (U4 — 20 dấu cách không phải đánh giá), lưu bản đã bỏ khoảng trắng; `visitedAt` ≤ hôm nay theo **giờ Việt Nam** (23:30 ở Hà Nội đã là "hôm nay" dù UTC còn hôm qua).
- **Sự kiện** `ReviewChangedEvent(reviewId, placeId, before, after)` — gộp `ReviewCreatedEvent` / `ReviewStatusChangedEvent` của sơ đồ lớp: listener nào cũng chỉ cần "địa điểm này phải tính lại". Chỉ phát khi tập review PUBLISHED đổi (tạo PUBLISHED, xóa / sửa sao của review PUBLISHED, đổi trạng thái vào / ra PUBLISHED). Đã cập nhật `class-review-module.puml` (chưa render lại PNG — máy này chưa có PlantUML).
- **Tách `ReviewRateLimiter`, `IpAnomalyDetector`** như sơ đồ lớp (để code khớp báo cáo); `ReviewService` chỉ điều phối.
- **Danh sách**: NEWEST keyset theo id, HELPFUL keyset `(helpful_count, id)` (cursor riêng, dùng nhầm thứ tự → 422); `votedByMe`, `commentCount` (chỉ bình luận VISIBLE), `ownerReply` nạp theo lô cho cả trang (không N+1); `rejectReason` chỉ tác giả thấy; `photos` rỗng tới E7. `/me/reviews` mọi trạng thái; `/users/{id}/reviews` chỉ PUBLISHED, người dùng không tồn tại → 404. `SecurityConfig`: `GET /places/*/reviews`, `/users/*/reviews` công khai.

**Tài liệu**: openapi (thứ tự kiểm tra + mã lỗi của POST, luật sửa của PATCH, 404 / 422 của danh sách), requirements (dải IP, sửa theo trạng thái, chủ không tự đánh giá), plan §10 E6, `class-review-module.puml`.

**Kiểm chứng**:
- `./mvnw verify` — **216 test** xanh (+14): `ReviewFlowTests` 11 ca trên MySQL thật (đăng ngay khi trust ≥ 30 + hiện trên địa điểm + `myReviewId`; trust thấp → PENDING, chỉ tác giả thấy, không phát sự kiện; chưa xác thực email 403; 1 review / địa điểm kể cả sau khi xóa; chủ không tự đánh giá, địa điểm chưa duyệt 404; validate sao / nội dung 19 ký tự sau khi bỏ khoảng trắng / ngày tương lai; 5 / 24 h đếm cả review đã xóa, `Retry-After` ≈ 24 h, review cũ rời cửa sổ thì có lượt; review thứ 4 từ cùng /24 → PENDING + `ip_flagged`, dải khác / địa điểm khác bình thường; luật sửa theo trạng thái + version + 403 người khác; trust tụt → sửa thì về PENDING; NEWEST / HELPFUL + lọc sao + phân trang + cursor sai thứ tự 422, `votedByMe` / `commentCount` (bỏ bình luận ẩn) / `ownerReply`, `rejectReason` chỉ tác giả, `/users/{id}/reviews`), `IpPrefixesTest` 3 ca. Sự kiện kiểm bằng `@RecordApplicationEvents`. SpotBugs 0.
- **Test có bắt lỗi** (mutation, mỗi lần một chỗ): giới hạn bỏ qua review đã xóa; sửa REJECTED không về PENDING; bỏ chặn chủ tự đánh giá; ngưỡng IP lệch một (`>` thay `>=`) — mỗi lỗi làm đúng test tương ứng đỏ.

Chưa commit — chờ supervisor duyệt.

## 2026-10-09 (+07) — Checklist E7: Ảnh đính kèm review

**Bối cảnh**: E6 (`feature/be-reviews`) đã push, chưa merge → nhánh `feature/be-review-photos` tạo từ `feature/be-reviews` (xếp chồng); mở PR sau khi E6 merge. Đã có: bảng `review_photos`, entity `ReviewPhoto`, pipeline ảnh địa điểm E2.

**Supervisor chốt (qua câu hỏi):**
- **Thêm / xóa ảnh sau khi viết**: có — `POST /reviews/{id}/photos`, `DELETE /reviews/{id}/photos/{photoId}` (tác giả, giống endpoint ảnh của chủ địa điểm). Lý do: sửa một ảnh sai mà phải xóa review thì D2 khóa luôn địa điểm đó với người viết. Tổng ≤ 10 ảnh / review kể cả thêm sau; thêm ảnh **không** đổi trạng thái review (ảnh không duyệt lại); review `HIDDEN` khóa cả ảnh như khóa nội dung.
- **Gallery địa điểm**: tách riêng — ảnh review chỉ hiện trong review. Đơn giản hơn, review bị ẩn / xóa thì ảnh tự biến mất theo; tab "ảnh từ đánh giá" làm ở frontend sau nếu cần.

**Quyết định kỹ thuật:**
- **Dùng chung pipeline, không nhân đôi code**: tách `PhotoFiles` khỏi `PlacePhotoService` (kiểm tra upload NFR-09, ảnh gốc riêng tư `incoming/…`, sinh 3 bản JPEG, URL, key các object); `PlacePhotoService` và `ReviewPhotoService` chỉ giữ phần bản ghi (bảng, trạng thái, giới hạn, ảnh bìa). Test E2 (`PlacePhotoFlowTests` 9 ca) vẫn xanh sau khi tách.
- **Một queue `photo.process`**, message thêm `target` (PLACE / REVIEW); `@JsonAlias("placePhotoId")` + `target` rỗng → PLACE để message cũ còn trong queue / DLQ trước E7 vẫn xử lý đúng. Sự kiện `PlacePhotosStoredEvent` → `PhotosStoredEvent(target, …)` (commit → đưa vào queue, rollback → xóa ảnh gốc — như E2).
- **Thứ tự khi viết review**: kiểm tra ảnh cùng nhóm validate (trước khi ghi gì — ảnh hỏng thì review không được tạo), lưu ảnh sau khi review đã INSERT, trong cùng transaction.
- **Khóa dòng review** khi thêm / xóa ảnh — hai lần tải đồng thời lần lượt đếm giới hạn (như khóa địa điểm ở E2). Ảnh `FAILED` không tính vào giới hạn (người dùng tải lại được).
- **Hiển thị**: người khác chỉ thấy ảnh READY; tác giả thấy cả `PROCESSING` / `FAILED` (biết ảnh vừa tải đang ở đâu). Ảnh nạp theo lô cho cả trang review (một truy vấn).
- Thêm `localspot.photo.max-per-review: 10`.

**Tài liệu**: openapi (ảnh khi viết, 2 endpoint mới, hiển thị ảnh trong danh sách), requirements (dòng "Ảnh review sau khi viết"), plan §10 E7.

**Kiểm chứng**:
- `./mvnw verify` — **220 test** xanh (+4): `ReviewPhotoFlowTests` 4 ca qua RabbitMQ thật (ảnh kèm khi viết → PROCESSING → READY, URL đúng key `reviews/{id}/…`, ảnh gốc còn GPS bị xóa, không vào gallery địa điểm; ảnh hỏng → 422 `photos[1]` và review không được tạo; giới hạn 10 khi thêm sau (2 + 9 bị chặn, 2 + 8 được); thêm / xóa không đổi trạng thái review, không phát `ReviewChangedEvent`, object xóa sau commit, ảnh của review khác 404, người khác 403, `HIDDEN` 409 cả thêm lẫn xóa; ảnh lỗi chỉ tác giả thấy và không tính vào giới hạn). `PlacePhotoFlowTests` 9 ca vẫn xanh sau khi tách `PhotoFiles`. SpotBugs 0.
- **Test có bắt lỗi** (mutation): hiện ảnh chưa READY cho mọi người; bỏ đếm giới hạn mỗi review; review `HIDDEN` vẫn nhận ảnh — mỗi lỗi làm đúng test tương ứng đỏ.

Chưa commit — chờ supervisor duyệt.

## 2026-10-11 (+07) — Checklist E8: Vote hữu ích, bình luận phân cấp

**Bối cảnh**: E7 đã merge (PR #12). Nhánh `feature/be-votes-comments` từ `develop`. Đã có sẵn: bảng `review_votes` (PK `(review_id, user_id)`, cột `counted`), `comments` (2 cấp, xóa mềm, `VISIBLE` / `HIDDEN`), entity + `OwnershipPermissionEvaluator` cho `comment:delete-own`, `votedByMe` / `commentCount` trong danh sách review (E6).

**Supervisor chốt (qua câu hỏi):**
- **Xóa gốc còn trả lời**: giữ trả lời, gốc hiện dạng placeholder `deleted = true` (không tác giả / nội dung); hết trả lời thì gốc biến mất. Không làm mất lời người khác vì người viết gốc rút lại. Gốc `HIDDEN` xử lý như gốc đã xóa.
- **Điều kiện**: bình luận cần email đã xác thực (nội dung công khai, chặn tài khoản rác); vote chỉ cần đăng nhập — vote của người trust < 30 (gồm chưa xác thực) vốn có `counted = false`, không ảnh hưởng trust ai.
- **Vote review của mình** → 409 `SELF_ACTION_FORBIDDEN` (openapi cũ ghi 422) — nhất quán với chủ tự đánh giá / kiểm duyệt viên tự duyệt; đã sửa openapi.
- **Thứ tự**: gốc cũ trước (keyset id tăng), mỗi gốc kèm toàn bộ trả lời cũ trước, không phân trang trả lời.

**Quyết định kỹ thuật:**
- **Idempotent không cần đọc trước**: PUT = `INSERT IGNORE` (1 dòng = vừa thêm, 0 = đã vote); DELETE = xóa theo `counted = 1` rồi `counted = 0`. Bộ đếm chỉ đổi theo số dòng ảnh hưởng → request chen nhau không cộng / trừ hai lần. `IGNORE` cũng nuốt lỗi khóa ngoại — chấp nhận vì review / người vote đã kiểm tồn tại trong cùng transaction (review chỉ xóa mềm).
- **Chỉ vote / bình luận review đang hiện** (`PUBLISHED` + địa điểm `APPROVED`, cùng điều kiện danh sách) — còn lại 404 như không tồn tại.
- **Deadlock bắt được bằng test đồng thời** (8 PUT cùng người → 500): `INSERT` vào `review_votes` giữ khóa **S** trên dòng cha `reviews` (kiểm khóa ngoại), rồi `UPDATE helpful_count` cần **X** trên chính dòng đó → hai transaction cùng giữ S, cùng chờ X. Biến thể hiếm hơn: A vote B đúng lúc B vote A (S trên dòng người vote của bên kia, chờ X trên dòng tác giả). Sửa bằng **khóa X trước theo thứ tự chung**: `users` (người vote + tác giả, id tăng dần, `ORDER BY id FOR UPDATE`) rồi `reviews` (`SELECT status … FOR UPDATE` — truy vấn vô hướng để đọc trạng thái mới nhất, không bị entity đã nạp che). Đánh đổi: vote cho cùng một tác giả xếp hàng nối tiếp — không đáng kể ở quy mô này. Lưu ý cho mục sau: luồng nào khóa cả `reviews` lẫn `users` phải theo cùng thứ tự users → reviews.
- **Mất cập nhật bộ đếm** (phát hiện khi đọc code, không phải test): `Review` / `User` không `@DynamicUpdate` → sửa review / người dùng qua entity ghi lại **mọi** cột, giá trị `helpful_count` / `helpful_votes_count` cũ trong bộ nhớ xóa mất vote đến cùng lúc. Sửa: hai cột `insertable = false, updatable = false` (chỉ đổi bằng `UPDATE ± 1` native; INSERT dùng mặc định 0). Không code nào ghi hai cột qua entity (đã grep). `places.review_count` / `avg_rating` sẽ cần xử lý tương tự ở mục tính lại rating.
- **Danh sách bình luận 3 truy vấn / trang** bất kể số gốc: trang gốc (native — `@SQLRestriction` sẽ loại gốc đã xóa cần làm placeholder; `EXISTS` trả lời đang hiện, dùng `ix_comments_parent_id`; thời điểm trả epoch ms tính trong MySQL như E6) → gốc đang hiện + tác giả → trả lời + tác giả. `commentCount` (E6) chỉ đếm bình luận đang hiện → placeholder không tính.
- **`parentId` sai** → 422 theo trường (dữ liệu trong body, không phải tài nguyên trên đường dẫn): không tồn tại / đã xóa / ẩn / thuộc review khác → `VALIDATION_FAILED`; trả lời một trả lời → mã mới `COMMENT_DEPTH_EXCEEDED`. Nội dung kiểm sau khi bỏ khoảng trắng hai đầu.
- **Xóa bình luận luôn được** (kể cả review đã bị ẩn) — rút lại lời của mình không gây hại. Không thêm sửa bình luận (không có UC). Không thêm giới hạn tần suất bình luận — đề xuất nếu cần ở phần chất lượng.
- **RBAC**: vote / viết bình luận không có permission riêng trong 17 permission đã chốt → `authenticated()`; `SecurityConfig` thêm `GET /reviews/*/comments` công khai (openapi `security: []`).

**Tài liệu**: openapi (vote: 401 / 404 / 409, `counted`; comments: thứ tự, placeholder, thứ tự kiểm tra, 401 / 403 / 404 / 422, schema `Comment` thêm `deleted`, `author` / `content` / `replies` nullable), requirements §5 (3 dòng chốt), database.md (placeholder; khóa + cột đếm chỉ đọc), `class-review-module.puml` (`ReviewVoteService` — chưa render PNG), plan §10 E8.

**Kiểm chứng**:
- `./mvnw verify` — **232 test** xanh (+12), SpotBugs 0. `ReviewVoteFlowTests` 6 ca (idempotent + hai bộ đếm, vote trust thấp không cộng trust tác giả, bỏ vote trừ đúng bộ đếm, `votedByMe` theo người xem; HELPFUL theo vote; tự vote 409, khách 401, review PENDING / REJECTED / HIDDEN / đã xóa / địa điểm bị ẩn 404; 8 + 3 PUT đồng thời → đếm đúng 2; A ↔ B vote chéo đồng thời 10 vòng; hồi quy mất cập nhật: nạp entity → bộ đếm đổi → sửa + flush giữ nguyên số vote), `CommentFlowTests` 6 ca (thứ tự hội thoại, `commentCount`; 2 cấp + validate; email / review đang hiện; placeholder khi xóa gốc, 403 / 401 / 404, trả lời gốc đã xóa 422, hết trả lời thì biến mất; gốc HIDDEN; phân trang + cursor sai).
- **Test có bắt lỗi** (mutation, mỗi lần một chỗ, đều đỏ đúng test): bỏ khóa `users` theo thứ tự → vote chéo deadlock; cột đếm `updatable` lại → test mất cập nhật; bỏ điều kiện `counted` → test hai bộ đếm; bỏ nhánh placeholder (`OR EXISTS` → `AND EXISTS`); cho trả lời một trả lời; bỏ kiểm email khi bình luận.

Chưa commit — chờ supervisor duyệt.

## 2026-10-11 (+07) — Checklist E9: Tính lại rating Bayesian qua Spring Event + `@Async`

**Bối cảnh**: E8 đã push (PR #13, chưa merge) → nhánh `feature/be-rating` tạo từ `feature/be-votes-comments` (xếp chồng như E6 → E7, tránh xung đột plan / progress log); mở PR sau khi #13 merge. Đã có: cột `review_count`, `avg_rating DECIMAL(3,2)`, `bayesian_score DECIMAL(4,3)` + index `(category_id, status, bayesian_score)`, `ReviewChangedEvent` (E6), `PlaceIndexChangedEvent` (E5), `m = 10`, D4 (job đêm 03:00).

**Supervisor chốt (qua câu hỏi):**
- **C** = `AVG(rating)` của mọi review `PUBLISHED` trên địa điểm `APPROVED` — mỗi review cùng trọng số (kiểu IMDb), không phải trung bình của các `avg_rating`.
- **v = 0 → điểm 0** ("Chưa có đánh giá", đứng sau mọi địa điểm đã có review) thay vì đúng C theo công thức thuần — tránh quán mới chưa ai đánh giá đứng ngang mức trung bình (≈ 4) trên quán thật có nhiều review 3.9 ở mục "nổi bật".
- **Seed review demo** (~2000 review) tách thành mục riêng ngay sau, giữ PR này tập trung vào phần lõi.

**Quyết định kỹ thuật:**
- **Tách theo sơ đồ lớp**: `RatingCalculator` (hàm thuần, unit test NFR-20), `RatingService` (nơi duy nhất ghi ba cột), `RecalculateRatingListener` (AFTER_COMMIT + `@Async`), `RatingRecalculationJob` (`@Scheduled` cron cấu hình, giờ VN), `RatingProperties` (`m`, cron). `AsyncConfig` bật `@EnableAsync` / `@EnableScheduling`, dùng executor mặc định của Spring Boot, lỗi tác vụ nền ghi ERROR. Sơ đồ lớp cập nhật (`RatingProperties` thay `AntiFakeProperties` cho `m`; calculator không phụ thuộc cấu hình).
- **Đếm lại từ đầu, không cộng dồn ± 1**: idempotent — chạy lặp, trễ, chen nhau đều ra cùng kết quả; mất một sự kiện thì lần tính sau tự sửa. Chi phí: `COUNT/AVG` trên index `(place_id, status, created_at)` + một `AVG` toàn bảng cho C mỗi sự kiện — chấp nhận ở quy mô vài nghìn review (D4).
- **Khóa trước, đếm sau** — tìm ra khi tự rà code, trước khi chạy test: bản đầu đọc C rồi mới khóa dòng địa điểm. Ở REPEATABLE READ (mặc định MySQL), ảnh chụp của transaction chốt ở **lần đọc thường đầu tiên**, nên phép đếm sau khóa vẫn dùng ảnh chụp cũ → lần tính chạy sau có thể ghi đè điểm mới bằng điểm cũ. Sửa: `SELECT … FOR UPDATE` dòng địa điểm là câu lệnh đầu tiên, C đọc sau đó. Hai lần tính cùng địa điểm chạy nối tiếp, lần sau luôn thấy dữ liệu ít nhất mới bằng lần trước. Chỉ khóa `places` → không đụng thứ tự khóa users → reviews của E8.
- **AFTER_COMMIT + `@Async`**: rollback thì không tính; người viết review không chờ; lỗi tính điểm không làm hỏng request đã thành công. Đánh đổi: sự kiện chỉ trong bộ nhớ — máy chủ tắt đúng lúc thì điểm lệch tới job đêm (tự sửa vì đếm lại từ đầu). Không thêm outbox / queue cho giá trị dẫn xuất tự sửa được.
- **Job đêm**: C tính một lần, rồi mỗi địa điểm một transaction ngắn (khóa một dòng; sự kiện xen giữa chỉ chờ đúng địa điểm đó). Gồm cả địa điểm chưa duyệt / bị ẩn (được hiện lại thì điểm đã đúng). Giả định một instance backend; chạy trùng vẫn đúng, chỉ tốn công.
- **Làm tròn** theo kiểu cột, nửa lên; điểm Bayesian tính từ R **chưa làm tròn**. Chỉ ghi và chỉ báo đồng bộ index (`PlaceIndexChangedEvent`, địa điểm APPROVED) khi giá trị thật sự đổi.
- **Mất cập nhật** như E8: ba cột điểm của `Place` thành `insertable = false, updatable = false` — chủ sửa / duyệt địa điểm qua entity không ghi đè điểm vừa tính. (`checkin_count` sẽ cần tương tự ở mục check-in.)
- Còn mở cho các mục sau: duyệt / ẩn địa điểm làm đổi C (C chỉ tính địa điểm APPROVED) — hiện để job đêm cập nhật; duyệt review (UC28) phát `ReviewChangedEvent` như E6 là đủ.

**Tài liệu**: requirements §5 (cách tính C, điểm khi chưa có review), database.md §4, openapi (`bayesianScore`: 0 khi chưa có review, tính lại trễ), `class-review-module.puml` (chưa render PNG), plan §10 E9, `application.yml` (`localspot.rating`).

**Kiểm chứng**:
- `./mvnw verify` — **244 test** xanh (+12), SpotBugs 0. `RatingCalculatorTest` 6 ca (ví dụ plan §5: 5★ × 1 = 4.091 < 4.6★ × 300 = 4.581; v = m nửa đường; nhiều review → R; v = 0 → 0; m = 0 → R; đầu vào âm). `RatingFlowTests` 6 ca qua HTTP + listener thật (viết / sửa sao / xóa / review chờ duyệt không tính; về 0 khi hết review; 6 review đồng thời cùng địa điểm → kết quả cuối đúng và không bị ghi đè sau 500 ms; job đêm sửa địa điểm bị mất sự kiện + điểm lệch, báo đồng bộ index, chạy lại không báo; C bỏ qua địa điểm bị ẩn; sửa địa điểm qua entity giữ nguyên điểm). Điểm mong đợi tính **độc lập trong test** từ `COUNT/AVG` SQL.
- **Test có bắt lỗi** (mutation): listener không làm gì; v = 0 → C; cột điểm `updatable`; luôn coi là "đã đổi"; đảo trọng số m / v; C tính trên mọi địa điểm (lần đầu **sống sót** → thêm test địa điểm bị ẩn có 12 review 1 sao, giờ đỏ). Lỗi "đếm trước khi khóa" là race — test đồng thời chỉ bắt được theo xác suất, không khẳng định bằng mutation.

Chưa commit — chờ supervisor duyệt.

## 2026-10-12 (+07) — Seed review demo (mục riêng sau E9)

**Bối cảnh**: `develop` local chậm 2 PR so với `origin/develop` → fast-forward tới `dc9b446` (#13 vote/bình luận, #14 rating đã merge). Nhánh `feature/be-demo-reviews` tạo từ `develop`. E9 đã chốt tách seed review demo (~2000 review) thành mục riêng; `R__demo_01_users.sql` ghi rõ hoãn tới khi có service rating / trust "để không chép công thức lõi ra SQL".

**Vấn đề cần chốt**: trust tính trực tiếp (§5.1), không lưu. Không có vote hợp lệ thì trust tối đa 10 + 18 = 28 < 30, mà vote chỉ hợp lệ (`counted`) khi người vote đạt ≥ 30 lúc vote → seed đúng luật tuyệt đối thì không ai đạt 30, mọi review PENDING, demo không có điểm.

**Supervisor chốt (qua câu hỏi):**
- **Nhóm kỳ cựu** phá vòng lặp: thành viên tạo tài khoản ≥ 250 ngày trước mốc seed (16 người) — coi như người dùng đầu tiên. Chỉ vote của nhóm này `counted = TRUE`.
- **Điểm tính khi khởi động dev** bằng `RatingService.recalculateAll()` (chính job D4) — công thức Bayesian chỉ ở Java.
- Seed gồm: vote hữu ích, bình luận 2 cấp, trộn trạng thái, **ảnh review**.

**Quyết định kỹ thuật:**
- **SQL tất định, idempotent** như seed 01 / 02 (CTE + số học, không RAND): review `INSERT IGNORE` theo `UNIQUE(place_id, user_id)`, vote theo PK; bình luận / vi phạm không có khóa tự nhiên → `NOT EXISTS`. Khóa ổn định của địa điểm = số cuối slug (không dùng id tự tăng).
- **Seed tôn trọng cổng UC12** (test kiểm): người viết đã xác thực email, địa điểm APPROVED, chủ không tự đánh giá, ≥ 20 ký tự, ngày đến ≤ ngày viết, thời điểm viết sau khi tài khoản tạo và địa điểm được duyệt. Không seed review của nhân sự.
- **`counted` chỉ của nhóm kỳ cựu**, kể cả khi người khác về sau đạt 30 → seed chỉ có thể tính **thiếu** trust, không bao giờ tính dư (bảo vệ được trước hội đồng: không có tài khoản nào được nâng trust trái luật). Không mô phỏng lại lịch sử trust theo thời gian — chi phí cao, không thêm giá trị demo.
- **Người mới** (thành viên < 60 ngày, 8 người) — phát hiện khi chạy test: bản đầu mỗi thành viên ~38 review bất kể tuổi tài khoản → mọi người đã xác thực đều ≥ 30, không còn ai để demo nhánh hàng chờ, và tài khoản 7 ngày tuổi có 38 review là phi thực tế. Sửa: người mới chỉ viết ~1/8 số review, **tất cả PENDING** — đúng theo §5.1 (tài khoản mới chưa có vote hợp lệ ≤ 18 < 30); review chờ không nhận vote → họ vẫn dưới ngưỡng.
- **Trạng thái**: PUBLISHED của nhóm kỳ cựu đăng thẳng (`moderated_by` NULL); của người khác ghi là đã qua kiểm duyệt viên duyệt (nhất quán bất kể trust lúc viết). PENDING nằm trong 10 ngày gần mốc → hàng chờ UC28 trông như vừa gửi. REJECTED chỉ người ngoài nhóm kỳ cựu, cũ hơn 45 ngày + vi phạm `REVIEW_REJECTED` 25 điểm **đã hết hạn** → có lịch sử, không kéo trust hiện tại.
- **Bộ đếm đếm lại từ đầu** từ `review_votes` (cùng triết lý E9): `reviews.helpful_count` = mọi vote, `users.helpful_votes_count` = vote counted nhận được, kể cả trên review đã xóa — khớp `ReviewVoteService`. Đúng cả khi đã có vote thật qua API.
- **Độ phổ biến lệch** (10% chưa có review … 10% 25–40 review) + mức chất lượng theo địa điểm → có sẵn cặp "ít review 5★ đứng sau nhiều review ~4.5★" để minh họa Bayesian (test kiểm cặp này tồn tại).
- **IP** `10.20.<người viết>.x` — mỗi người một /24 trong dải riêng, không kích hoạt luật cảnh báo IP.
- **`DemoDataBootstrap`** (`listener/`, `ApplicationReadyEvent`) bật bằng `localspot.demo.bootstrap` (chỉ `application-dev.yml`) thay vì `@Profile("dev")` — để test bật được trong context có seed. (1) tính lại rating; (2) ảnh minh họa cho ~1/8 review 4–5★ đang hiện chưa có ảnh nào, 1–3 ảnh: hình khối màu phẳng, **không chữ** (không phụ thuộc font của máy / container headless), đi qua đúng đường người dùng (`ReviewPhotoService.store` → `incoming/` → `photo.process` → 3 bản JPEG). Kho ảnh lỗi → log WARN, không chặn khởi động. Giới hạn đã ghi trong Javadoc: xóa volume MinIO nhưng giữ CSDL → ảnh READY trỏ object không còn; xóa hết ảnh của review demo qua UI → khởi động sau thêm lại.

**Số liệu thực tế** (nạp vào MySQL 8.4 sạch): 2046 review (1879 PUBLISHED / 143 PENDING / 24 REJECTED), sao 1★ 58 · 2★ 244 · 3★ 487 · 4★ 795 · 5★ 462; 8799 vote (2617 counted), tối đa 20 vote / review; 908 bình luận gốc + 476 trả lời; 27 địa điểm đã duyệt chưa có review, 15 địa điểm ≥ 25 review (tối đa 33); khởi động: 250 địa điểm đổi điểm trong ~1.7 s, 297 ảnh cho 143 review.

**Tài liệu**: plan §10 (mục mới sau E9), database.md §3.5, README (cách demo trust bằng tài khoản kỳ cựu / người mới), đầu file seed 01 / 02 / 03.

**Kiểm chứng**:
- `./mvnw verify` — **252 test** xanh (+8), SpotBugs 0. `DemoSeedTests` thêm 8 ca (bật `localspot.demo.bootstrap` trong context có seed): quy mô + trộn trạng thái, PENDING trong 10 ngày; cổng UC12; độ phổ biến lệch + cặp minh họa Bayesian; điểm khớp tập PUBLISHED sau khởi động; bộ đếm khớp đếm lại + `counted` ⇔ kỳ cựu, không tự vote, vote chỉ trên review đang hiện; trust có cả ≥ 30 và < 30 (người đã xác thực), người mới chỉ có PENDING, vi phạm đã hết hạn; bình luận 2 cấp đúng tác giả / thời điểm; ảnh minh họa đi qua pipeline (xử lý một ảnh như consumer → READY + 3 bản). Ca chạy lại mở rộng: chạy lại 3 script + bootstrap không đổi 9 số đếm / tổng.
- **Mutation**: `counted` luôn TRUE → đỏ test bộ đếm; bỏ tính lại rating khi khởi động → đỏ 2 test. Cho chủ tự đánh giá quán mình **sống sót** — với dữ liệu hiện tại công thức chọn người viết không bao giờ rơi vào đúng chủ của địa điểm, điều kiện SQL chỉ là phòng hờ; test giữ lại để chặn khi đổi công thức.

Chưa commit — chờ supervisor duyệt.

## 2026-10-12 (+07) — Checklist E10: Check-in kèm kiểm tra khoảng cách GPS

**Bối cảnh**: seed review demo đã commit (`1e373af`, `902c291`, chưa push) → nhánh `feature/be-checkins` tạo từ `feature/be-demo-reviews` (xếp chồng). Đã có sẵn: bảng `check_ins` với `UQ(user_id, place_id, checkin_date)`, entity `CheckIn`, cột `places.checkin_count`, U5 (200 m, accuracy ≤ 100 m, 1 lần / địa điểm / ngày), UC16, openapi `POST /places/{id}/check-in`.

**Supervisor chốt (qua câu hỏi):**
- **Ai được check-in**: email đã xác thực + không phải chủ địa điểm — `checkin_count` vào thống kê của chủ (FR-34) và nhãn trên review, không để chủ / tài khoản rác thổi phồng. Cùng luật với viết review.
- **Nhãn "đã check-in" (P04)**: tác giả check-in tại địa điểm **trước hoặc trong ngày viết** review → `Review.authorCheckedIn`.
- **Lịch sử**: chỉ chính chủ (`GET /me/check-ins`); không công khai vì lộ nơi và lúc một người có mặt.

**Quyết định kỹ thuật:**
- **Nhãn so theo ngày (giờ VN), không theo giờ phút** — người dùng hay viết review ngay tại quán rồi mới bấm check-in; so `created_at` chặt sẽ mất nhãn oan. Check-in sau ngày viết không gắn nhãn ngược cho review cũ. `CONVERT_TZ(…, '+00:00', '+07:00')` — VN không có giờ mùa hè, không cần bảng múi giờ của MySQL. Một truy vấn cho cả trang (`ReviewViews`), dùng đúng `uk(user_id, place_id, checkin_date)`.
- **Thứ tự kiểm tra**: email (403) → validate / `accuracyM` (422 `GPS_INACCURATE`) → địa điểm APPROVED (404) → chủ (409) → đã check-in hôm nay (409 `CHECKIN_ALREADY_TODAY`) → khoảng cách (422 `CHECKIN_TOO_FAR` + `distanceM`). GPS kém báo trước khoảng cách: vị trí sai số 500 m thì "quá xa" là thông tin sai, gợi ý đúng là bật GPS.
- **Khoảng cách tính trong CSDL bằng `ST_Distance_Sphere`** (như UC16 và `/places/nearby`) → "trong 200 m" ở check-in và ở danh sách gần tôi luôn khớp. `distanceM` trong lỗi **làm tròn lên** — 200.4 m bị từ chối không hiện thành "200 m".
- **Khóa dòng `users` trước** (như viết review): hai lần bấm chen nhau xếp hàng → lần sau nhận 409 có mã riêng thay vì chạm UNIQUE của CSDL (`DUPLICATE_RESOURCE` chung chung + log WARN). UNIQUE vẫn là lớp phòng thủ cuối. Thứ tự khóa users → places, không trái với E8 (users → reviews) / E9 (chỉ places).
- **`checkin_count`**: `UPDATE … + 1` nguyên tử, cột `insertable/updatable = false` trên `Place` (E9 đã ghi nhận cần làm) — chủ sửa / duyệt địa điểm qua entity không ghi đè về giá trị cũ.
- **`ApiException` thêm `properties`** (trường mở rộng RFC 7807) để trả `distanceM` như openapi đã thiết kế; handler chỉ thêm một dòng. Không phải phần lõi mục 3 CLAUDE.md.
- **Response không trả tọa độ người dùng gửi lên** (kể cả cho chính chủ) — chỉ lưu để giải trình. `place` là `PlaceSummary` để tab lịch sử dựng thẻ địa điểm không cần gọi thêm.
- Ngưỡng ở `localspot.checkin.*` (`CheckInProperties`) — chỉnh khi thử thực địa không sửa code.
- **Seed demo** `R__demo_04_checkins.sql`: check-in cho ~40% review demo đang hiện, đúng ngày đến (≤ ngày viết → đều có nhãn), cách 20–180 m, sai số 5–60 m; `checkin_count` đếm lại từ đầu.
- Hoãn: số lượt check-in trên trang cá nhân công khai — chưa có endpoint hồ sơ công khai, làm cùng P06.

**Tài liệu**: openapi (thứ tự kiểm tra + mã lỗi, `CheckInResponse` thêm `place` / `checkinDate`, `CheckInPage`, `/me/check-ins`, `Review.authorCheckedIn`), requirements §5 (3 dòng chốt), UC16 (ngoại lệ 3a), sitemap (P04, tab `checkins` chỉ của chính mình), database.md (bộ đếm), plan §10 E10.

**Kiểm chứng**:
- `./mvnw verify` — **261 test** xanh (+9), SpotBugs 0. `CheckInFlowTests` 8 ca (thành công + bộ đếm + 409 cùng ngày, người khác vẫn được, `checkinCount` ở chi tiết; biên 199 / 201 m; GPS 100 nhận, 100.5 từ chối, GPS kém báo trước khoảng cách; email / chủ / PENDING / REJECTED / HIDDEN / không tồn tại / khách / validate; 8 lần bấm đồng thời → đúng 1 bản ghi, không có `DUPLICATE_RESOURCE`; sửa địa điểm qua entity giữ bộ đếm; lịch sử phân trang, chỉ của mình, không có tọa độ; nhãn: trước ngày viết / cùng ngày sau khi viết → có, sau ngày viết / không check-in / người khác check-in → không). `DemoSeedTests` +1 (check-in demo qua đúng luật U5 tính lại bằng `ST_Distance_Sphere`, bộ đếm khớp, mọi check-in demo gắn nhãn).
- **Mutation** (mỗi lần một chỗ, đều đỏ đúng test): bỏ khóa dòng user → test bấm đồng thời; cột đếm `updatable` lại → test sửa qua entity; nhãn so `created_at` chặt → test nhãn cùng ngày; bỏ cổng chủ địa điểm → test cổng.

Chưa commit — chờ supervisor duyệt.

## 2026-10-12 (+07) — Checklist E11: Bộ sưu tập và chia sẻ công khai

**Bối cảnh**: E10 đã commit + push (`8af94d0`, `9f6bc71`); PR seed review và PR check-in đưa link cho supervisor tự mở (máy không có `gh`). Nhánh `feature/be-collections` tạo từ `feature/be-checkins` (xếp chồng). Đã có sẵn: bảng `collections` (`share_slug` UNIQUE, NULL khi riêng tư) + `collection_place` (PK tổ hợp, ON DELETE CASCADE), entity `PlaceCollection` (`publish` / `makePrivate` thu hồi slug), openapi CRUD + `/shared/collections/{shareSlug}` + `/me/collections`. Chưa có: endpoint cho tab bộ sưu tập P06, luật địa điểm bị ẩn, trần số lượng.

**Supervisor chốt (qua câu hỏi):**
- **Công khai = hiện trên trang cá nhân + link chia sẻ** (thêm `GET /users/{id}/collections`, chỉ bộ công khai); riêng tư = chỉ chủ. Slug dễ đọc (tên bỏ dấu + hậu tố).
- **Chỉ thêm địa điểm APPROVED; bị ẩn sau đó → lọc khi xem**: người khác không thấy / không đếm; chủ thấy kèm trạng thái để tự bỏ ra.
- **Trần 50 bộ / người, 100 địa điểm / bộ** — giữ chi tiết không phân trang như openapi.

**Quyết định kỹ thuật:**
- **Không tiết lộ tồn tại**: bộ riêng tư của người khác và mọi thao tác ghi lên bộ của người khác → 404 `COLLECTION_NOT_FOUND` (openapi cũ ghi 403 → sửa). 403 sẽ xác nhận id đó là một bộ sưu tập riêng tư có thật. Không có permission RBAC / quyền vượt cho nhân sự — dữ liệu cá nhân, không có luồng kiểm duyệt; kiểm chủ sở hữu trong service.
- **Slug** = `Slugs.slugify(tên, 30)` + `-` + 6 ký tự `[a-z0-9]` từ `SecureRandom` (36⁶ ≈ 2,2 tỷ — trùng UNIQUE gần như không xảy ra; nếu có thì lớp phòng thủ UNIQUE trả 409). Tên không ra slug (toàn emoji) → tiền tố `bo-suu-tap`. Sửa tên khi vẫn công khai **giữ** slug (link đã chia sẻ không gãy); về riêng tư thu hồi, công khai lại sinh slug mới — rút lại là rút hẳn.
- **Link chia sẻ luôn là góc nhìn công khai**, kể cả chủ tự mở (xem trước như người nhận); `/users/{id}/collections` cũng vậy.
- **`PUT …/places/{placeId}` idempotent**: đã có → chỉ sửa ghi chú khi có body (không body giữ nguyên); vẫn sửa được ghi chú khi bộ đã đủ 100. Trần đếm theo **dòng** (kể cả địa điểm đã bị ẩn) — không để chủ vượt trần nhờ địa điểm bị ẩn.
- **Khóa dòng**: tạo bộ khóa `users` (đếm 50 đúng khi chen nhau), thêm địa điểm khóa `collections` (đếm 100). Không đụng thứ tự khóa của các mục trước.
- **Xóa hẳn** bộ sưu tập (bảng không có `deleted_at` theo thiết kế; mục 3 CLAUDE.md chỉ xóa mềm places / reviews / comments / users), `collection_place` theo CASCADE của CSDL; địa điểm giữ nguyên.
- **Ảnh bìa** = ảnh bìa của địa điểm thêm gần nhất **mà người xem thấy**; `placeCount` theo người xem. Danh sách nhiều bộ: một truy vấn gom (`COUNT` + `GROUP_CONCAT … ORDER BY added_at DESC` lấy địa điểm bìa) + một truy vấn ảnh bìa — không N+1.
- **`SecurityConfig`** (phần lõi mục 3): thêm **một** luật `permitAll` cho 3 GET công khai (`/collections/*`, `/shared/collections/*`, `/users/*/collections`) — cùng mẫu với bình luận / tìm kiếm, khớp openapi (`security: []` / tùy chọn); service tự giới hạn bộ riêng tư. Không đổi luật nào khác.
- Ngưỡng ở `localspot.collection.*` (`CollectionProperties`).
- **Seed demo** `R__demo_05_collections.sql`: 1–3 bộ cho thành viên số lẻ, chủ đề × thành phố, ~nửa công khai (slug cùng dạng nhưng hậu tố tất định), 3–12 địa điểm đã duyệt cùng thành phố. Gặp lỗi khi chạy: MySQL tính `CAST(SUBSTRING(email…))` trên cả dòng bị `WHERE` loại (`admin@…` → `'lo'`), chế độ strict của `INSERT … SELECT` biến cảnh báo thành lỗi → đổi sang `REGEXP_SUBSTR` (trả NULL thay vì lỗi).
- **Lỗi bắt được nhờ test**: bộ rỗng không có địa điểm bìa → `Map.get(null)` trên map bất biến ném NPE (500 ở `/me/collections`) → sửa + thêm test ảnh bìa có giá trị thật.

**Tài liệu**: openapi (mô tả luật, 403 → 404, 422 trần, `/users/{userId}/collections`, mô tả `placeCount` / `coverPhoto`), requirements §5 (3 dòng chốt), sitemap (`/c/:shareSlug`), database.md (xóa hẳn + trần), plan §10 E11.

**Kiểm chứng**:
- `./mvnw verify` — **272 test** xanh (+11), SpotBugs 0. `CollectionFlowTests` 10 ca (riêng tư chỉ chủ — người khác / khách 404, không hiện trên trang cá nhân; công khai → slug dạng `quan-an-ha-noi-nen-thu-xxxxxx`, mở qua id / link / trang cá nhân, đổi tên giữ slug, về riêng tư link chết, công khai lại slug mới và link cũ vẫn chết; thêm idempotent + ghi chú + thứ tự mới nhất + bỏ idempotent + PENDING / REJECTED / HIDDEN 404 + ghi chú quá dài; địa điểm bị ẩn sau khi thêm: chủ thấy kèm `HIDDEN` và đếm, người khác không; ảnh bìa theo người xem; người khác sửa / xóa / thêm / bỏ → 404, khách tạo 401; xóa kéo theo `collection_place` nhưng giữ địa điểm; validate + tên không ra slug; 6 request tạo chen nhau khi đã có 48 → đúng 2 thành công; đủ 100 → 422 nhưng vẫn sửa ghi chú). `DemoSeedTests` +1 (seed bộ sưu tập theo đúng luật).
- **Mutation** (mỗi lần một chỗ, đều đỏ đúng test): bỏ khóa dòng user khi tạo → test chen nhau; bỏ lọc địa điểm bị ẩn → 2 test; không thu hồi slug khi về riêng tư → test link; bỏ kiểm chủ sở hữu → test người khác.

Chưa commit — chờ supervisor duyệt.

## 2026-10-13 (+07) — Checklist E12: Theo dõi người dùng, huy hiệu, bảng xếp hạng

**Bối cảnh**: `develop` đã merge #15 (seed review demo) và #16 (check-in) → fast-forward local. E11 (bộ sưu tập) chưa merge → nhánh `feature/be-community` tạo từ `feature/be-collections` (xếp chồng). Đã có: bảng `follows` (PK + CHECK không tự theo dõi), `badges` / `user_badges`, cột `users.contribution_points`, entity tương ứng, openapi follow / followers / following / leaderboard (`period`) / `PublicProfile`. Chưa có: `GET /users/{id}` (hồ sơ công khai — làm luôn ở đây vì chứa số theo dõi, điểm, huy hiệu), O3 (bộ huy hiệu + công thức điểm) còn mở; cột điểm chỉ một tổng nên không phục vụ được WEEK / MONTH.

**Supervisor chốt O3 (qua câu hỏi):**
- **Công thức**: review đang hiện +10 · ảnh READY trong review +2, tối đa 5 ảnh / review · vote hữu ích hợp lệ nhận được +1 · check-in +1 · đề xuất địa điểm được duyệt +15. Chỉ nội dung đang hiển thị — xóa / ẩn thì mất điểm.
- **Tính từ dữ liệu gốc** (không thêm bảng sổ điểm); `contribution_points` là bộ nhớ đệm tổng, tính lại sau sự kiện + hằng đêm.
- **8 huy hiệu**: REVIEWER_1..5 (1 / 10 / 25 / 50 / 100), EXPLORER (10 địa điểm check-in khác nhau), PHOTOGRAPHER (20 ảnh), HELPFUL (50 vote hợp lệ); trao một lần, không thu hồi.
- **Lọc theo thành phố** cho bảng xếp hạng ("region" của P08).

**Quyết định kỹ thuật:**
- **Một câu SQL nguồn duy nhất** (`ContributionQueries.POINTS`, `UNION ALL` 4 nguồn → `GROUP BY user`) dùng cho cả tổng của một người và bảng xếp hạng — hai nơi không thể lệch công thức. "Đang hiện" định nghĩa một lần (`VISIBLE_PLACE`) cho điểm và tiêu chí huy hiệu. Ảnh tính theo **ngày của review** (trần 5 / review áp đơn giản, không phải chia ảnh theo cửa sổ). Vote theo ngày vote, check-in theo ngày check-in, đề xuất theo ngày duyệt.
- **Cửa sổ trượt** 7 / 30 ngày cho WEEK / MONTH — bảng không về rỗng vào đầu tuần / tháng. **Hạng kiểu thi đấu** (`RANK()`: 1, 2, 2, 4), cùng hạng xếp theo id cho ổn định. Bỏ tài khoản bị khóa / đã xóa, bỏ người 0 điểm. Cache Redis 5 phút (khóa = kỳ + thành phố + số dòng), không xóa khi ghi — chấp nhận trễ tối đa 5 phút cho một bảng xếp hạng.
- **Làm mới điểm + trao huy hiệu** (`ContributionService.refresh`, idempotent — đếm lại từ đầu như rating E9): listener `@Async` AFTER_COMMIT trên **`ReviewChangedEvent` có sẵn** (tra tác giả theo id review, kể cả đã xóa — **không sửa luồng sự kiện rating lõi**) và `ContributionChangedEvent` mới phát từ vote hợp lệ / bỏ vote hợp lệ (tác giả), check-in, ảnh review READY (consumer chạy ngoài transaction → `fallbackExecution`), duyệt đề xuất địa điểm (người đề xuất). Job đêm 03:30 (sau rating 03:00) đối soát sự kiện mất + nội dung bị ẩn qua đường không phát sự kiện. Demo bootstrap gọi `refreshAll()` để dữ liệu seed có điểm / huy hiệu.
- **`contribution_points` chỉ đọc trên entity** (`insertable/updatable = false`, ghi bằng UPDATE trực tiếp) — sửa hồ sơ / khóa tài khoản qua entity không ghi đè điểm vừa tính (cùng mẫu E8 / E9 / E10).
- **Huy hiệu**: danh mục ở `V4__seed_badges.sql` (migration **mới**, mọi môi trường — không sửa migration cũ); tiêu chí trong code (`BadgeRule`); trao bằng `INSERT IGNORE` theo PK. `icon` là khóa trung tính để frontend ánh xạ (thư mục design system không có trong repo này).
- **Theo dõi**: idempotent (`INSERT IGNORE` / `DELETE`), tự theo dõi → **409 `SELF_ACTION_FORBIDDEN`** (openapi cũ ghi 422 → sửa cho đồng nhất với vote review của mình). Danh sách keyset tổ hợp (thời điểm theo dõi, id người kia), gần nhất trước. Đếm qua `JOIN users` để bỏ tài khoản đã xóa — khớp danh sách. Chưa thông báo khi có người theo dõi (làm ở mục thông báo).
- **Hồ sơ công khai** `GET /users/{id}`: không email (NFR-11), `reviewCount` chỉ review đang hiện (cùng định nghĩa với huy hiệu), điểm từ bộ nhớ đệm, huy hiệu theo thứ tự đạt.
- **`SecurityConfig`** (phần lõi): thêm **một** luật `permitAll` cho 4 GET công khai (`/users/*`, `/users/*/followers`, `/users/*/following`, `/leaderboard`) — khớp openapi `security: []`. Không đổi luật khác.
- **Tham số thời gian truyền vào SQL** là `LocalDateTime` theo UTC, không `Timestamp` — `Timestamp` bị đổi theo múi giờ của JVM (+07) trong khi cột lưu UTC.
- Gặp khi chạy: `@Validated` trên controller đưa `@Max` qua đường validate khác (500) → bỏ, dùng validate tham số sẵn có của Spring (422 như các controller khác). SpotBugs `VA_FORMAT_STRING_USES_NEWLINE` cho `String.formatted` trên text block → đổi sang thay token có tên (`{VISIBLE_PLACE}`, `{POINTS}`).

**Tài liệu**: openapi (luật điểm + cửa sổ + `city`, follow 409 / 404, `Badge.description`, mô tả `contributionPoints` / `badges`), requirements §5 (4 dòng chốt), sitemap (O3 ✅, P08 `city`), database.md (cột điểm là bộ nhớ đệm, V4, tiêu chí trong code), plan §10 E12.

**Kiểm chứng**:
- `./mvnw verify` — **281 test** xanh (+9), SpotBugs 0. `CommunityFlowTests` 8 ca (theo dõi idempotent + số đếm hai phía + `followedByMe` + không email; tự theo dõi 409, người không tồn tại 404, khách 401; danh sách mới nhất trước + cursor + cursor sai 422; công thức điểm đủ 5 nguồn — ảnh chưa READY / vote không hợp lệ / review chờ / đề xuất chưa duyệt không tính, trần 5 ảnh; ẩn địa điểm → mất điểm nhưng giữ huy hiệu; huy hiệu đúng ngưỡng, không trao lại, check-in địa điểm bị ẩn không tính; lưu user qua entity giữ điểm; vote / check-in / bỏ vote qua API → điểm cập nhật nền; bảng xếp hạng WEEK / MONTH / ALL theo cửa sổ, cùng điểm cùng hạng, lọc thành phố, bỏ người bị khóa, `limit` / `period` sai 4xx). `DemoSeedTests` +1 (sau khởi động mọi người có review đang hiện đều có điểm, có REVIEWER_3, người mới không có huy hiệu người đánh giá; chạy lại không đổi).
- **Mutation** (mỗi lần một chỗ, đều đỏ đúng test): bỏ trần 5 ảnh; tính cả địa điểm không hiện; cột điểm `updatable`; hiện người bị khóa trên bảng xếp hạng; bỏ phát sự kiện khi vote.

Chưa commit — chờ supervisor duyệt.

---

## 2026-10-12 → 13 (+07) — Checklist F: module khám phá (trang chủ, P02 tìm kiếm & bản đồ, P03 chi tiết — bản đọc)

**Bối cảnh**: `develop` fast-forward tới `ff8c706` (#16 check-in). Nhánh `feature/fe-discovery` tạo từ `develop`. Backend đã có đủ API đọc: `/places`, `/search`, `/places/nearby`, `/places/{slug}`, `/categories`, `/amenities`, review / vote / bình luận / rating / check-in. Máy Windows mới không có JDK / Docker → backend build & test trong WSL (JDK 21, Docker 29).

**Supervisor chốt (qua câu hỏi):**
- **Phạm vi đợt này = lát cắt khám phá**: trang chủ + `/search` (P02, bản đồ, "gần tôi") + `/places/:slug` (P03) bản đọc. Viết review, vote, bình luận, check-in, đề xuất địa điểm làm đợt sau.
- **Thêm `leaflet.markercluster`** (plugin chính thức của Leaflet) — 300 địa điểm demo không đọc được nếu không gom ghim. Plan §2 đã ghi.
- **Làm `GET /home` ở backend** (đã có trong openapi nhưng chưa cài) thay vì frontend tự ghép 3 request; **thêm `featuredReview`** cho ReviewCard; **thêm `tags` vào `PlaceSummary`** cho SpotCard.
- **"Quanh tôi" / "Tìm trong vùng này" chỉ lọc danh mục + bán kính** (đúng UC09 / API `/places/nearby`); ô từ khóa và chip điểm / giá / tiện ích tắt kèm một dòng giải thích. Không mở rộng truy vấn không gian (phần lõi, có test `EXPLAIN`), không lọc phía client (kết quả co lại âm thầm, gây hiểu nhầm).

**Quyết định kỹ thuật — backend:**
- **`/home` dùng lại đúng truy vấn của `GET /places`** (SCORE / NEWEST) — trang chủ không có luật xếp hạng riêng; mục nổi bật bỏ địa điểm điểm 0 ("Chưa có đánh giá").
- **Đánh giá nổi bật**: review đang hiện, **từ 4 sao**, nhiều lượt hữu ích nhất trong **30 ngày** (không có thì mọi thời điểm). Lý do: trang chủ là lời mời khám phá — một review 1 sao nhiều vote đặt trên nền `sun` là sai ngữ cảnh; ưu tiên gần đây để trang chủ "sống". Chỉ trả **đoạn trích ≤ 180 ký tự** (cắt ở ranh giới từ, theo code point — không tách đôi ký tự), không trả toàn văn.
- **Cache Redis `home` TTL 10 phút** (ngắn hơn danh mục 1 giờ vì điểm / vote đổi thường hơn), response không có trường theo người xem → mọi người dùng chung một bản. **Xóa sau commit** bằng `HomeCacheInvalidator`, **nghe lại sự kiện đã có** thay vì phát sự kiện mới: `PlaceIndexChangedEvent` (duyệt, chủ sửa, đổi tên danh mục, điểm Bayesian đổi), `ReviewChangedEvent` (review nổi bật có thể vừa bị xóa / ẩn), `CatalogChangedEvent` của danh mục. Cùng cách với `CatalogCacheInvalidator` (`invalidate()` đồng bộ, AFTER_COMMIT). Đánh đổi: vote và ảnh xử lý xong không xóa cache → trễ tối đa một TTL, chấp nhận cho trang giới thiệu.
- **`PlaceSummary.tags`**: nạp một truy vấn cho cả trang trong `PlaceSummaries` (dùng `findTagNamesOf` đã có, index `(taggable_type, taggable_id)`) → mọi danh sách có thẻ, không N+1. API trả đủ thẻ, giao diện tự giới hạn 3.
- `SecurityConfig` thêm một dòng `permitAll` cho `GET /api/v1/home` (openapi `security: []`) — không đổi luật nào khác.

**Quyết định kỹ thuật — frontend:**
- **URL là nguồn sự thật của bộ lọc P02** (sitemap: `q`, `category` theo slug, `minRating`, `price`, `amenities`, `near`, `sort`; thêm `radius`). Hàm thuần parse / serialize (`utils/searchQuery.ts`) bỏ qua giá trị sai trong URL thay vì ném lỗi. Store `filter` (plan §2) không giữ bản sao mà đọc / ghi route; nhớ lần tìm gần nhất để P03 có "← Kết quả tìm kiếm". Chip lọc dùng `router.replace` (lịch sử không đầy bước bật / tắt), từ khóa mới / bật "Quanh tôi" dùng `push`; đổi query trên `/search` giữ vị trí cuộn.
- **Store `place`**: vị trí người dùng (chỉ trong bộ nhớ — URL ghi `near=me`, không ghi tọa độ thật vì là dữ liệu cá nhân; hỏi lại sau 5 phút; gộp lời gọi đồng thời) + địa điểm đang chọn (hàng danh sách ↔ ghim bản đồ đánh dấu cùng lúc). Dữ liệu từ API nằm trong cache Vue Query, không nhân bản vào Pinia.
- **Ba chế độ lấy kết quả**: duyệt `/places`, tìm `/search` (cursor, infinite scroll bằng IntersectionObserver + nút "Xem thêm" cho bàn phím / trình đọc màn hình), quanh vị trí `/places/nearby` (≤ 50, không phân trang — hiện "50+" khi chạm trần). `keepPreviousData` để danh sách / bản đồ không nháy trắng khi đổi lọc. API không trả tổng số → tiêu đề danh sách không bịa "128 địa điểm" như bản mẫu.
- **"Tìm trong vùng này"**: bán kính = khoảng cách từ tâm tới **cạnh gần hơn** của khung bản đồ (vòng tròn nằm trọn trong vùng nhìn thấy), làm tròn lên 100 m, kẹp 100 m – 20 km (U8). Nút chỉ hiện khi người dùng tự kéo / thu phóng; bản đồ chỉ tự canh khi **trang đầu** kết quả đổi (tải thêm trang không làm bản đồ nhảy).
- **Lớp nền bản đồ = MapTiler (key miễn phí), dự phòng OSM Đức** (supervisor chốt 2026-10-13 qua hai lần chạy thử): (1) `tile.openstreetmap.org` bị DNS nhà mạng chặn (router trả NXDOMAIN, 8.8.8.8 phân giải bình thường) → bản đồ trắng; (2) CARTO nay bắt buộc API key (trả ảnh "API KEY REQUIRED" với mã 200 — kiểm tra bằng mã HTTP không phát hiện được, phải xem ảnh). Đã thử từ mạng này: OSM Đức / Pháp không cần key (nhiều màu), Esri Gray Canvas phẳng nhưng điều khoản yêu cầu tài khoản ArcGIS. Chọn **MapTiler Dataviz / Dataviz Dark** — phẳng, có bản tối thật, ổn định; key đặt ở `frontend/.env` (`VITE_MAPTILER_KEY`, có `frontend/.env.example`), key frontend vốn công khai nên giới hạn theo tên miền ở trang MapTiler. Thiếu key → **OSM Đức** (máy chủ cộng đồng OSM, không cần key) + đảo màu CSS ở theme Tối, để dev / CI không bao giờ thấy bản đồ trắng. `VITE_MAP_TILE_URL*` thay hẳn nhà cung cấp, không sửa code (`utils/mapTiles.ts`). Đánh đổi: gói miễn phí giới hạn lượt tải / phi thương mại — đủ cho đồ án.
- **Nền theme Sáng = trắng** (supervisor yêu cầu 2026-10-13 khi chạy thử): `surface` / `surface-raised` #ffffff (vùng nổi — sidebar, ô nhập, popover — tách bằng viền `line` vốn đã có), `surface-sunk` xám trung tính #f2f2ef để khung chờ tải, chip, thẻ đánh giá nhỏ vẫn nổi trên nền. Kiểm lại tương phản: ink 16.4:1, ink-muted 7.1:1 trên trắng / 6.3:1 trên sunk, line-strong ≥ 3.4:1, màu trạng thái ≥ 4.7:1. Theme Tối không đổi. Theo CLAUDE.md đã sửa trước ở design system (`docs/design-system/tokens.css` + `DECISIONS.md`, bản cục bộ) rồi chép sang `frontend/src/assets/tokens.css`; bản trên Claude Design cần supervisor cập nhật.
- **"Chỉ đường" mở hộp thoại bản đồ lớn trong ứng dụng** (supervisor chốt 2026-10-13 — không chuyển sang trang ngoài): bấm "Chỉ đường" hoặc bản đồ nhỏ ở P03 → bản đồ lớn, ghim tên địa điểm, vị trí người dùng (hỏi khi mở, dùng chung store `place`), **tuyến đường + quãng đường + thời gian** từ OSRM trên máy chủ cộng đồng OSM (`routing.openstreetmap.de` — không key, CORS `*`, hồ sơ ô tô / đi bộ; đã kiểm tra truy cập được từ mạng của supervisor), không thêm thư viện (fetch + `L.polyline`). Gọi bằng `fetch` riêng, không qua axios dùng chung — dịch vụ ngoài không được nhận access token. Đổi máy chủ bằng `VITE_ROUTING_URL` (vd. OSRM tự dựng khi deploy). Đánh đổi: máy chủ cộng đồng không cam kết SLA → luôn có phương án: bị chặn vị trí vẫn có bản đồ + địa chỉ; dịch vụ lỗi (thử lại một lần) → khoảng cách đường chim bay; không có đường → báo rõ, không thử lại. OSRM không có hồ sơ xe máy — dùng hồ sơ ô tô và ghi chú "thời gian là ước tính".
- **Bản đồ**: ghim là pill `olive` mang điểm (P02), đang chọn `coral`, cụm ghi "N địa điểm" bằng chữ; ghim focus được bằng Tab, Enter để chọn, `title` là tên. Leaflet tải lười (chunk riêng ~53 kB gzip) — trang chủ không tải bản đồ.
- **Điểm hiển thị luôn là Bayesian** (thẻ, ghim, đầu trang); P03 ghi thêm "trung bình thô" và một dòng giải thích — điểm nhấn để bảo vệ trước hội đồng.
- **Giờ mở cửa tính theo giờ Việt Nam** (`Intl` + `Asia/Ho_Chi_Minh`), không theo múi giờ của máy xem; khung qua đêm (18:00 – 02:00 thứ Bảy) còn "Đang mở" lúc 01:00 Chủ nhật.
- **Không có link chết**: nút Viết đánh giá / Check-in / Lưu / Báo cáo / Yêu cầu sở hữu / Đề xuất chỉ hiện khi đã có route (như header). Bỏ khối "Ảnh check-in" của P03 — check-in không có ảnh trong mô hình dữ liệu (U5). Review của chính người xem đang chờ duyệt (đầu danh sách P03) để đợt viết review — chưa có API trả review chờ theo địa điểm.
- CSS thành phần công khai tách ra `assets/public.css` (port từ `bundle.css`, giữ tên lớp `ls-*`), thêm biến thể responsive (bản mẫu cố định 1200px), khung chờ tải phẳng (`.ls-skel`, không ánh gradient chạy ngang, tôn trọng `prefers-reduced-motion`).

**Tài liệu**: openapi (`/home` mô tả, `HomeResponse.featuredReview`, `HomeReview`, `PlaceRef`, `PlaceSummary.tags`), sitemap §2.1 (`radius`, `near=lat,lng`, luật lọc của "Quanh tôi"), plan §2 (markercluster) + §10 F (ghi chú tiến độ — **chưa đánh dấu xong** tới khi chạy thử trên dữ liệu seed).

**Kiểm chứng**:
- Backend: `./mvnw verify` (WSL) — **269 test** xanh (+8), SpotBugs 0. `HomeFlowTests` 3 ca (một request đủ 4 phần, nổi bật xếp theo điểm và bỏ địa điểm chưa có review, chỉ APPROVED; duyệt địa điểm → trang chủ thấy ngay dù đã cache, kèm 4 thẻ đúng thứ tự, cùng thẻ ở `/places`; review nổi bật từ 4 sao, bỏ review 3 sao nhiều vote hơn và review trên địa điểm chưa duyệt, đoạn trích ≤ 180 + "…", không lộ `content`; tác giả xóa review → cache tự xóa, trang chủ chọn review khác), `HomeServiceTest` 5 ca (đoạn trích: gộp khoảng trắng, đúng 180 không cắt, cắt ở ranh giới từ, từ dài bất thường cắt cứng, không tách cặp surrogate).
- Frontend: lint, format, type-check, build sạch; **Vitest 74 test** (+43): parse / serialize URL (đi vòng, giá trị sai, mặc định), chế độ & thứ tự; Rating 5 hình tròn (4.7 → 70%, làm tròn 10%), nhãn đọc màn hình; haversine, bán kính "vùng này", định dạng khoảng cách; lớp nền bản đồ theo theme / biến môi trường; chỉ đường (URL OSRM, đổi GeoJSON, không có đường / máy chủ lỗi, thời gian) + hộp thoại (vị trí → tuyến, đổi phương tiện, bị chặn vị trí, lỗi → đường chim bay); giờ mở cửa (nhiều khung, qua đêm, múi giờ máy khác); `SearchPage` (slug → id, `amenityIds`, thứ tự mặc định; `/search` + báo chế độ dự phòng; "Quanh tôi" gọi `/nearby` chỉ với danh mục + bán kính và tắt bộ lọc khác; bị chặn quyền vị trí; chip ghi lên URL; rỗng); `PlaceDetailPage` (Bayesian + trung bình thô, review nổi bật chỉ ≥ 4 sao có vote, lọc theo mức sao, không có nút tới màn chưa làm, 404).
- **Chạy thử trên trình duyệt** (backend profile dev + dữ liệu seed, 2026-10-13): supervisor kiểm tra và yêu cầu 3 chỉnh sửa đã làm ở trên (lớp nền bản đồ, nền trắng, chỉ đường trong ứng dụng) → đánh dấu xong 4 mục F trong plan.

Đã commit `97c21d4`, `6d5268a`, `101320e` và push `feature/fe-discovery`.

---

## 2026-10-14 14:35 (+07) — Quyết định O8: báo cáo WRONG_INFO không có đường sửa tên / vị trí / danh mục

**Bối cảnh**: rà quyền sửa địa điểm theo plan. `PATCH /owner/places/{id}` (`PlaceUpdateRequest`) cố ý không có tên, vị trí, danh mục (O5 — chủ không "dời" địa điểm để thoát review), không có endpoint nào khác sửa các trường này; O5 hướng thông tin sai sang báo cáo `WRONG_INFO`. Nhưng thao tác xử lý báo cáo của UC29 chỉ gồm ẩn nội dung, khóa tài khoản, RESOLVED / DISMISSED → báo cáo đúng về tên sai không thể dẫn tới sửa tên.

**Phương án đã nêu**: (1) cho kiểm duyệt viên sửa tên / vị trí / danh mục khi xử lý `WRONG_INFO`, ghi nhật ký FR-42 — người trung lập sửa nên không trái lý do của O5; (2) giữ nguyên, ghi là giới hạn đã biết. Agent đề xuất (1).

**Supervisor chốt: phương án (2)**. Không thêm thao tác sửa vào UC29, không đổi schema / API. Đánh đổi: ít việc hơn và không mở thêm đường sửa dữ liệu cốt lõi của địa điểm; đổi lại địa điểm có tên / vị trí sai chỉ có thể bị ẩn (và đề xuất lại) — cần nêu là giới hạn trong báo cáo đồ án.

**Tài liệu**: sitemap §O8, plan §10 E (mục "Báo cáo vi phạm").

Chưa commit.

---

## 2026-10-14 (+07) — Quyết định O9: ai nhận vi phạm khi ẩn địa điểm bị báo cáo

**Bối cảnh**: `ReportDecision.HIDE` = ẩn nội dung + ghi 1 vi phạm cho tác giả (+25 `penalty_score`, trust −75 trong 30 ngày). Với review / bình luận tác giả rõ ràng; với địa điểm plan chưa nói là ai.

**Phương án đã nêu**: người đề xuất theo lý do / không ai / người đề xuất luôn luôn / người đã check-in (supervisor gợi ý ban đầu). Agent phản biện phương án check-in: check-in không chứa nội dung nên người check-in không viết thông tin sai; phạt hàng loạt (địa điểm đông có hàng chục check-in) đẩy nhiều người dưới ngưỡng 30; làm người dùng ngại check-in — vốn là một lớp chống review ảo; trái nguyên tắc trust score đo hành vi của chính người đó.

**Supervisor chốt: người đề xuất, theo lý do** — vi phạm chỉ khi lý do xác nhận là `SPAM` / `FAKE` / `OFFENSIVE` (cố ý gửi nội dung xấu); `WRONG_INFO` / `OTHER` → không ai, vì thông tin địa điểm có thể lỗi thời trung thực. Không ghi cho chủ (không sửa được tên / vị trí / danh mục — O5) hay người check-in. Người đề xuất = `places.created_by` (NOT NULL — luôn có).

**Hệ quả thiết kế** (làm cùng module báo cáo): nhóm báo cáo gộp nhiều lý do → `ReportDecision` cần trường lý do được xác nhận khi `HIDE` địa điểm; cập nhật openapi khi cài đặt.

**Tài liệu**: sitemap §O9, plan §10 E.

Chưa commit.

---

## 2026-10-14 15:20 (+07) — Checklist E: báo cáo vi phạm (UC17) + xử lý báo cáo (UC29)

**Bối cảnh**: nhánh `feature/be-reports` từ `develop` (`eeb1e3f`). Bảng `reports`, entity `Report` / `ReportReason` / `ReportStatus` / `ReportTargetType` và `user_violations` đã có từ V1; chưa có endpoint, chưa có chỗ nào ghi vi phạm. Đã chốt trước trong phiên: O8 (không sửa tên / vị trí / danh mục khi xử lý WRONG_INFO), O9 (ai bị phạt khi ẩn địa điểm).

**Supervisor chốt thêm (qua câu hỏi):**
- **Báo cáo người dùng**: HIDE = xác nhận — chỉ ghi vi phạm cho người đó, không có gì để ẩn; khóa tài khoản vẫn là `POST /admin/users/{id}/lock` của admin (UC29 3b). Không xóa / sửa hồ sơ người dùng.
- **Người báo cáo cần email đã xác thực** — cùng cổng với review / bình luận, chặn tài khoản rác làm ngập hàng đợi.
- **`detail` bắt buộc với WRONG_INFO / OTHER** — kiểm duyệt viên không xét được "sai thông tin" nếu không biết sai chỗ nào.

**Quyết định kỹ thuật:**
- **Chỉ báo cáo nội dung đang hiện công khai**, đối tượng không thấy → 404 mã theo loại (như endpoint đọc — không tiết lộ nội dung chưa duyệt / đã ẩn có tồn tại).
- **Không tự báo cáo** review / bình luận / chính mình (409 `SELF_ACTION_FORBIDDEN`); **địa điểm thì được**, kể cả người đề xuất: tên / vị trí / danh mục không ai sửa được (O5, O8) nên báo cáo là kênh duy nhất để báo nhầm.
- **Hàng đợi gom theo đối tượng**, FIFO theo báo cáo đầu tiên; keyset trên `MIN(id)` (id tăng theo thời gian) để dùng lại `KeysetCursor` và không trùng / sót nhóm khi có báo cáo mới giữa hai lần tải. Lý do + đối tượng nạp theo lô (mỗi loại một truy vấn). Thêm `targetStatus` (gồm `DELETED`) để kiểm duyệt viên thấy đối tượng đã bị xóa / ẩn.
- **Thêm `GET /moderation/reports/{targetType}/{targetId}`** (chưa có trong openapi): sitemap đã có màn xử lý theo đối tượng, nhưng `ReportGroup` chỉ có số đếm — kiểm duyệt viên cần đọc mô tả của từng người báo cáo mới xét được WRONG_INFO. Trả kèm `author` = người sẽ bị ghi vi phạm.
- **Quyết định áp lên mọi báo cáo đang mở của đối tượng**, dưới `SELECT … FOR UPDATE` các báo cáo đó: hai kiểm duyệt viên bấm cùng lúc thì người sau chờ, đọc lại thấy hết báo cáo mở → 409 `REPORT_ALREADY_HANDLED` — không ghi hai vi phạm. **Một quyết định một vi phạm** dù có bao nhiêu báo cáo; `source_id` = báo cáo đầu tiên.
- **O9 trong code**: `confirmedReason` bắt buộc khi ẩn địa điểm và phải là một lý do có trong các báo cáo đang mở — không "nâng" WRONG_INFO thành SPAM để phạt người đề xuất.
- **Không tự xử lý** báo cáo về nội dung của mình, hay địa điểm mình sở hữu (như không tự duyệt đề xuất, 2026-10-04).
- **Đối tượng đã xóa** (UC29 3c): HIDE → báo cáo RESOLVED, không phạt; activity_log ghi `targetDeleted`.
- **Ẩn = đổi trạng thái, không xóa dữ liệu.** Ẩn review phát `ReviewChangedEvent` (Bayesian, điểm đóng góp, cache trang chủ tự cập nhật); ẩn địa điểm phát `PlaceIndexChangedEvent` (rời tìm kiếm + trang chủ) và `ContributionChangedEvent` cho người đề xuất + tác giả review trên đó; check-in và hằng số `C` để job đêm đối soát (D4). Đều dùng lại sự kiện có sẵn, không đụng luồng rating.
- **`ViolationService`** (mới, `Propagation.MANDATORY` — chạy trong transaction của thao tác kiểm duyệt) dùng chung cho UC28 sau này; mức phạt / thời hạn ở `localspot.violation.points` (25) / `ttl` (30d) — tách khỏi `TrustProperties` để không đổi record lõi.
- **`@AuditedAction.targetType` nhận SpEL** khi bắt đầu bằng `#` (thay đổi nhỏ, tương thích ngược): nhật ký ghi đúng loại đối tượng bị báo cáo (PLACE / REVIEW / …), không gộp chung "REPORT".
- `ReportTargets` gom bốn nhánh của quan hệ đa hình (tìm, mô tả, ẩn, "người liên quan") vào một chỗ.

**Chưa làm (ghi để nối tiếp):** thông báo cho tác giả và người báo cáo (UC29 bước 5) — làm ở mục thông báo; hàng chờ gộp `/moderation/queue` — làm cùng "API quản trị"; khôi phục nội dung đã ẩn — chưa có trong UC.

**Tài liệu**: openapi (`/reports` mô tả + mã lỗi, endpoint chi tiết mới, `ReportGroup.targetStatus`, `ReportItem`, `ReportGroupDetail`, `ReportDecision.confirmedReason`, `ReportCreateRequest.detail`), plan §10 E.

**Kiểm chứng**: `./mvnw verify` — **302 test** xanh, spotless sạch, SpotBugs 0. `ReportFlowTests` 13 ca: báo cáo đủ bốn loại + một lần / đối tượng; email chưa xác thực / khách / thiếu `detail` / lý do sai; đối tượng không hiện (địa điểm PENDING / REJECTED / HIDDEN, review không PUBLISHED hoặc trên địa điểm chưa duyệt, bình luận ẩn / xóa / trên review ẩn, người dùng không có); không tự báo cáo nhưng người đề xuất báo được địa điểm của mình; hàng đợi gom nhóm FIFO, đi hết bằng `limit=1` không trùng, lọc theo loại; chi tiết; phân quyền (thành viên / chủ 403); ẩn review (1 vi phạm 25 điểm / 30 ngày, mọi báo cáo RESOLVED, activity_log, người thứ hai 409); ẩn địa điểm theo O9 (thiếu / sai `confirmedReason` 422, WRONG_INFO không phạt ai, SPAM phạt người đề xuất — không phạt chủ hay người check-in); ẩn bình luận + xác nhận báo cáo người dùng (không khóa); bỏ qua; đối tượng đã xóa; kiểm duyệt viên không tự xử lý.

## 2026-10-15 15:03 (+07) — Checklist E: trust score + hàng đợi duyệt review (UC28)

**Bối cảnh**: nhánh `feature/be-review-moderation` từ `develop` (`aa44faf`, đã gộp PR #20). Trust score đã tính lúc đọc từ D3 (`TrustScoreCalculator` / `TrustScoreService`, không lưu cột — D1), cổng PENDING khi viết / sửa review đã có từ E6, `ViolationService` từ mục báo cáo. Còn thiếu: hàng chờ và quyết định UC28. Không có câu hỏi mở cần supervisor — các luật dưới đây theo khuôn đã chốt (duyệt địa điểm 2026-10-04, xử lý báo cáo 2026-10-14) hoặc là lựa chọn kỹ thuật.

**Quyết định kỹ thuật:**
- **Hàng chờ = review PENDING trên địa điểm APPROVED, FIFO theo id.** Id tăng theo thời điểm viết, dùng lại `KeysetCursor` như hàng chờ địa điểm. Đánh đổi: review bị từ chối rồi gửi lại giữ vị trí cũ (đứng đầu) thay vì xuống cuối — chấp nhận, vì nó đã chờ từ lần đầu; xếp theo `updated_at` thì mỗi lần tác giả sửa lại bị đẩy xuống cuối và cần index mới (migration). Review trên địa điểm đã bị ẩn không vào hàng chờ (duyệt cũng không ai thấy), nhưng quyết định trên chúng vẫn được phép.
- **Tín hiệu chống review ảo trên mỗi mục** (UC28 bước 1, openapi `ModerationReview`): trust tác giả (`trustScoresOf` theo lô), số vi phạm còn hiệu lực (truy vấn mới, `UserViolationCount`), `ipFlagged`, `sameSubnetReviewCount24h` = review khác cùng địa điểm + cùng dải IP trong 24 giờ **trước hoặc sau**. Tính cả review đã xóa, như luật gắn cờ U3 (chặn kiểu viết rồi xóa). Hai phía thời gian vì "trang trại" review thường tiếp tục sau review bị gắn cờ. Một truy vấn self-join cho cả trang trên index `(place_id, ip_prefix, created_at)`, không N+1.
- **`ModerationReview` đổi từ `allOf Review` sang lồng `{ review, place, … }`**, cùng khuôn với `ModerationPlace`: không cần `@JsonUnwrapped` trên record, chưa có client nào dùng schema cũ.
- **Thêm `GET /moderation/reviews/{id}`** (chưa có trong openapi): sitemap đã có route `/admin/moderation/reviews/:id`, và UC28 bước 2 cần "lịch sử tác giả, các review khác cùng dải IP". Trả về mục hàng chờ + `authorJoinedAt` + số review của tác giả theo trạng thái (đủ 4 khóa) + tối đa 20 review cùng dải IP chưa xóa. Mọi trạng thái đều mở được, để người thua 409 thấy kết quả của người kia; review đã xóa → 404.
- **Quyết định chỉ trên PENDING** → 409 `REVIEW_ALREADY_MODERATED` (mã mới), không đảo quyết định: PUBLISHED → REJECTED là việc của luồng báo cáo (ẩn).
- **UC28 3b bằng `@Version`** như đặc tả: `version` client gửi lệch → 409 ngay; hai kiểm duyệt viên cùng bấm thì người sau bị optimistic lock ở flush → 409 `CONCURRENT_MODIFICATION`, và **vi phạm của người sau rollback cùng transaction** (test 4 luồng đồng thời: đúng 1 vi phạm, 1 dòng activity_log).
- **Từ chối = 1 vi phạm** (UC28 3a, §5.1) qua `ViolationService.record(..., REVIEW_REJECTED, reviewId, ...)`; lý do bắt buộc, cắt khoảng trắng, ≤ 500; tác giả thấy ở `/me/reviews`. Duyệt phát `ReviewChangedEvent(PENDING → PUBLISHED)`, dùng lại listener Bayesian + điểm đóng góp, không đụng luồng rating. Từ chối không phát vì review PENDING chưa từng được tính.
- **Không tự duyệt** review của mình, hay review về địa điểm mình sở hữu (409 `SELF_ACTION_FORBIDDEN`), như duyệt địa điểm / xử lý báo cáo: chủ quán kiêm kiểm duyệt viên không được tự lọc review của quán mình.
- activity_log `REVIEW_APPROVE` / `REVIEW_REJECT` (metadata `reason`) qua `@AuditedAction`.

**Chưa làm (ghi để nối tiếp):** thông báo cho tác giả / chủ địa điểm (UC28 bước 4, 3a) ở mục thông báo; hàng chờ gộp `/moderation/queue` ở "API quản trị"; màn frontend ở "Trang quản trị".

**Tài liệu**: openapi (mô tả hàng chờ, endpoint chi tiết mới, mã lỗi của `decision`, `ModerationReview` lồng, `ModerationReviewDetail`), plan §10 E.

**Kiểm chứng**: `./mvnw verify`: **310 test** xanh (thêm 8), Spotless sạch, SpotBugs 0. `ReviewModerationFlowTests`: hàng chờ FIFO + tín hiệu (trust 10 / 0, vi phạm còn hạn vs. hết hạn, cờ IP, đếm cùng dải) + lọc `ipFlagged` + bỏ review đã đăng / trên địa điểm ẩn + đi hết cursor không trùng; màn chi tiết (lịch sử theo trạng thái, review cùng dải trong 24 h nhưng không lấy review 30 h trước, 404); duyệt (PUBLISHED, `moderated_by`, `review_count` được tính lại, activity_log, quyết định lần hai 409); từ chối (thiếu / trống / quá dài lý do 422; REJECTED + lý do, vi phạm 25 điểm hết hạn sau 30 ngày, metadata, `/me/reviews` thấy lý do, trust về 0); version lệch; 4 kiểm duyệt viên đồng thời; không tự duyệt; phân quyền (thành viên / chủ 403, khách 401, admin được).

## 2026-10-16 15:00 (+07) — Checklist E: yêu cầu xác nhận sở hữu (UC23) + duyệt (UC30)

**Bối cảnh**: nhánh `feature/be-place-claims` từ `develop` (`7377295`, đã gộp PR #21). Bảng `place_claims` + entity `PlaceClaim` có từ V1; `claimable` trên trang chi tiết đã có từ E1; OWNER không gán tay được (O6, D5). Chưa có endpoint, chưa có link tạm cho object riêng tư.

**Supervisor chốt (qua câu hỏi):**
- **Review sẵn có của chủ mới bị ẩn khi duyệt** (HIDDEN, không ghi vi phạm, tính lại điểm). Lý do: chủ không được tự đánh giá địa điểm của mình (UC12), và review viết trước khi thành chủ vẫn đang được tính vào điểm Bayesian. Các phương án bị loại: giữ nguyên (điểm có phần "tự chấm"), chặn yêu cầu (chủ thật từng review bị khóa ngoài).
- **Minh chứng chỉ nhận ảnh**, đúng lời UC23 ("ảnh giấy phép, ảnh chụp tại quán"). Dùng lại `ImageInspector` (JPEG / PNG, chữ ký file, 5 MB, 40 MP), không thêm mã kiểm tra PDF.

**Quyết định kỹ thuật:**
- **Thứ tự kiểm tra khi gửi**: email đã xác thực (403) → minh chứng (422, lỗi đúng `evidence[i]`) → địa điểm APPROVED (404) → chưa có chủ (409 `PLACE_ALREADY_OWNED`) → chưa có yêu cầu đang chờ của chính mình (409 `CLAIM_ALREADY_PENDING`). Khóa dòng user như viết review / check-in, để hai lần bấm gửi không tạo hai yêu cầu chờ. **Người khác đang chờ không chặn**: kiểm duyệt viên so các yêu cầu (`otherPendingClaimCount`), duyệt một thì các yêu cầu còn lại tự bị từ chối (UC23 quy tắc).
- **Minh chứng lưu nguyên bản** ở `claims/{placeId}/{uuid}`, không resize: kiểm duyệt viên cần đọc được chữ trên giấy tờ. Tiền tố này không nằm trong policy public-read (chỉ `places/*`, `reviews/*`), nên file riêng tư theo mặc định. File lên kho trong request; rollback hoặc kho lỗi giữa chừng → `ClaimEvidenceCleanupListener` (AFTER_ROLLBACK) xóa, cùng cách với ảnh gốc E2.
- **Link tạm**: thêm `ObjectStorage.presignedGetUrl` + bean `S3Presigner` (có sẵn trong artifact `s3`, không thêm thư viện), cùng endpoint / path-style với `S3Client`. Dev ký cho `localhost:9000`, trình duyệt mở được. Link 5 phút theo openapi, `Cache-Control: no-store`.
- **Link tạm trả 200 JSON `{url, expiresAt}` thay vì 302 như openapi cũ** (supervisor đồng ý 2026-10-16, sau khi rà luồng frontend). Access token chỉ nằm trong bộ nhớ, gửi bằng header (S1), nên `<img src>` trỏ thẳng vào API bị 401. Nếu gọi bằng axios thì trình duyệt tự theo 302, JavaScript không đọc được `Location` và còn vướng CORS tới MinIO. Frontend gọi API, gán `url` vào ảnh, quá `expiresAt` thì gọi lại. Bảo mật không đổi (quyền `claim:approve`, 5 phút, `no-store`). Test MockMvc không bắt được lỗi này vì MockMvc tự gửi header và không theo redirect.
- **Thêm `GET /moderation/claims/{id}`** (sitemap có route `/admin/moderation/claims/:id`), như màn duyệt review / báo cáo. Mọi trạng thái đều mở được. `Claim` thêm `status`, `rejectReason`, `otherPendingClaimCount`.
- **Duyệt khóa dòng địa điểm trước khi đọc yêu cầu** (lấy `place_id` bằng truy vấn vô hướng). Nếu nạp yêu cầu trước, địa điểm đã nằm trong phiên Hibernate và khóa sau không làm mới được `owner_id`. Hai kiểm duyệt viên duyệt hai yêu cầu khác nhau của cùng địa điểm: người sau chờ khóa, đọc lại thấy yêu cầu của mình đã bị tự từ chối → 409 `CLAIM_ALREADY_MODERATED`. Test 4 luồng đồng thời: đúng 1 APPROVED, chủ khớp người được duyệt (U6).
- **Role OWNER có hiệu lực ngay**: quyền nạp lại từ CSDL mỗi request (`UserJwtAuthenticationConverter`), không cần đăng nhập lại (test: cùng token, `/owner/places` từ 403 thành 200).
- **Ẩn review của chủ mới ở mọi trạng thái chưa xóa**, không chỉ PUBLISHED: PENDING có thể được duyệt sau, REJECTED gửi lại được. HIDDEN thì khóa sửa (E6), nên chủ không có đường đưa review trở lại. `rejectReason` ghi lý do để tác giả hiểu vì sao bị ẩn. Chỉ PUBLISHED mới phát `ReviewChangedEvent` (Bayesian + điểm đóng góp).
- **Từ chối không ghi vi phạm**: §5.1 chỉ tính review bị từ chối và báo cáo được xác nhận; yêu cầu sở hữu sai thường do thiếu giấy tờ, không phải gian lận. Lý do bắt buộc; người dùng gửi lại được.
- Không tự duyệt yêu cầu của mình (409 `SELF_ACTION_FORBIDDEN`); `version` lệch → 409. activity_log `CLAIM_APPROVE` (metadata `placeId`, `claimantId`, `autoRejected`, `hiddenReviewId`) / `CLAIM_REJECT` (`reason`).

**Chưa làm (ghi để nối tiếp):** thông báo cho kiểm duyệt viên khi có yêu cầu mới (UC23 bước 5) và cho người yêu cầu khi có kết quả: mục thông báo. Người dùng chưa có màn "yêu cầu của tôi" (openapi không có `/me/claims`); kết quả đến qua thông báo. Chưa có chính sách xóa minh chứng sau khi xử lý (giữ để đối chiếu). Hàng chờ gộp: "API quản trị".

**Tài liệu**: openapi (mô tả + mã lỗi `POST /places/{id}/claims`, endpoint chi tiết mới, link tạm 200 JSON + schema `EvidenceLink`, luật duyệt / từ chối, trường mới của `Claim`), plan §10 E.

**Kiểm chứng**: `./mvnw verify`: **320 test** xanh (thêm 10), Spotless sạch, SpotBugs 0. `ClaimFlowTests`: gửi (cắt khoảng trắng, key dưới `claims/{placeId}/`, file có trong kho, một yêu cầu chờ / người, `claimable` tắt, người khác vẫn gửi được); luật gửi (chưa xác thực 403, khách 401, 0 / 6 ảnh, file PDF ở `evidence[1]`, số điện thoại sai, địa điểm PENDING 404, đã có chủ 409, không để lại file); kho sập 503 không để lại gì; hàng chờ FIFO + `otherPendingClaimCount` + chi tiết + 404; link tạm (200 JSON không `Location`, key đúng, 300 s, `expiresAt` ≈ +5 phút, `no-store`, chỉ số vượt 404, người yêu cầu 403); duyệt (chủ, role OWNER có hiệu lực với token cũ, yêu cầu đối thủ tự từ chối kèm lý do, review của chủ mới HIDDEN không vi phạm, `review_count` tính lại, metadata, duyệt lại 409, gửi mới 409 `PLACE_ALREADY_OWNED`); 4 duyệt đồng thời → 1 chủ; từ chối (lý do trống 422, không vi phạm, không đổi chủ, metadata, gửi lại được); không tự duyệt + version lệch; phân quyền.

## 2026-10-16 15:48 (+07) — Checklist E: phản hồi của chủ quán (UC25, FR-33)

**Bối cảnh**: nhánh `feature/be-owner-replies` tạo **từ `feature/be-place-claims`** (PR #22 chưa gộp), vì cả hai cùng sửa `plan-v1.md` §10 E và cuối file này; tách từ `develop` sẽ xung đột. Gộp #22 trước rồi mới gộp nhánh này. Có sẵn: bảng `owner_replies` (`UNIQUE(review_id)`, D6), entity, permission `review:reply-own-place` cho OWNER, luật `isOnPlaceOwnedBy` trong `OwnershipPermissionEvaluator` (D6), `Review.ownerReply` nạp theo lô (E6). Không có câu hỏi mở: các luật theo khuôn vote / bình luận / xóa mềm đã chốt.

**Quyết định kỹ thuật:**
- **Chỉ chủ hiện tại**: `@PreAuthorize hasPermission(#reviewId, 'Review', 'review:reply-own-place')` kiểm RBAC → review tồn tại (404) → `places.owner_id` = người gọi. Chủ cũ mất quyền ngay khi đổi chủ. Chủ mới sửa phản hồi của chủ cũ → `user_id` chuyển sang chủ mới (cột này nghĩa là "chủ tại thời điểm phản hồi"), không để phản hồi đứng tên người không còn liên quan.
- **Chỉ phản hồi review đang hiện** (PUBLISHED trên địa điểm APPROVED), review khác → 404 như vote / bình luận (2026-10-11). Riêng xóa không cần review còn hiện: review bị ẩn sau báo cáo, chủ vẫn gỡ được phản hồi của mình.
- **Một `PUT` tạo hoặc sửa** (openapi), khóa dòng review (`findByIdForUpdate`) trước khi đọc phản hồi. Hai lần bấm đồng thời xếp hàng, lần sau thành sửa, không ra 409 `DUPLICATE_RESOURCE` (test 6 luồng: đều 200, đúng 1 dòng).
- **Xóa hẳn, idempotent**: `owner_replies` không có cột xóa mềm, cũng không nằm trong danh sách xóa mềm của CLAUDE.md §3 (places / reviews / comments / users). Đây là người dùng gỡ nội dung của chính mình, không phải xóa dữ liệu qua migration / seed. Chưa có phản hồi vẫn 204, như bỏ vote.
- **Phản hồi của chủ không qua duyệt, không báo cáo được** (supervisor chốt 2026-10-16, phương án A). Cổng trust (FR-23) chỉ áp cho review. Chủ đã qua xác minh sở hữu (UC30), tín hiệu mạnh hơn trust; giữ duyệt theo trust thì hầu hết chủ mới (trust 10) bị giữ lại. Hai phương án bị loại: thêm `OWNER_REPLY` vào báo cáo (vượt danh sách đối tượng của UC17, cần cột trạng thái mới), duyệt phản hồi của chủ trust thấp (không có trong FR nào). Răn đe dựa vào uy tín kinh doanh: phản hồi xấu hiện công khai cạnh tên quán. **Giới hạn đã biết**: phản hồi xấu không gỡ được qua kiểm duyệt. Báo cáo địa điểm chỉ ẩn được cả địa điểm (O8), và không ghi vi phạm cho chủ (O9); báo cáo người dùng (chủ) thì ghi được vi phạm nhưng phản hồi vẫn còn. Hướng mở rộng nếu cần: phương án B.
- `content` cắt khoảng trắng, `@NotBlank` + ≤ 2000 (cột `VARCHAR(2000)`). Không ghi activity_log: đây không phải thao tác quản trị (FR-42). Không đụng cache: `HomeReview` không chứa phản hồi.

**Chưa làm (ghi để nối tiếp):** thông báo cho tác giả review (FR-33) ở mục thông báo. Openapi chưa có danh sách "review chưa phản hồi" cho tab `reviews` của P09; màn chủ quán dùng `GET /places/{id}/reviews` (đã kèm `ownerReply`), bổ sung bộ lọc khi làm frontend P09 nếu cần. Phản hồi chưa báo cáo được, đã chốt là giới hạn (xem trên).

**Tài liệu**: openapi (luật + mã lỗi của `PUT` / `DELETE /reviews/{id}/reply`), plan §10 E.

**Kiểm chứng**: `./mvnw verify`: **327 test** xanh (thêm 7), Spotless sạch, SpotBugs 0. `OwnerReplyFlowTests`: tạo (cắt khoảng trắng, `owner`) → sửa (cùng id, 1 dòng) → hiện ở `ownerReply` của danh sách công khai; phân quyền (tác giả review, thành viên, chủ địa điểm khác, kiểm duyệt viên 403; khách 401); review PENDING / REJECTED / HIDDEN, trên địa điểm ẩn, đã xóa, không tồn tại → 404; validate (rỗng / trắng / 2001 ký tự 422, đúng 2000 được); xóa sau khi review bị ẩn + xóa lặp 204; chủ mới tiếp quản phản hồi, chủ cũ 403; 6 PUT đồng thời → 1 dòng.

## 2026-10-16 16:30 (+07) — Checklist E: thông báo (bảng `notifications` + WebSocket / STOMP — UC19, FR-27)

**Bối cảnh**: nhánh `feature/be-notifications` tạo **từ `feature/be-owner-replies`** (PR chưa gộp) để không xung đột `plan-v1.md` / file này. Có sẵn: bảng `notifications` + entity (V1), `spring-boot-starter-websocket` trong pom, Vite proxy `/ws`. Gom các mục "thông báo… gắn ở mục thông báo" đã ghi ở UC28, UC29, UC30, FR-33.

**Supervisor chốt (qua câu hỏi):**
- **Chỉ các sự kiện trong UC**, không thêm bình luận / theo dõi / huy hiệu (CLAUDE.md: không thêm chức năng ngoài UC). Bỏ qua báo cáo (DISMISS) cũng không gửi (UC29 chỉ nói khi ẩn).
- **Kiểm duyệt viên chỉ nhận yêu cầu sở hữu mới**, mỗi người một dòng (UC23 bước 5). Hàng chờ review / địa điểm / báo cáo xem ở màn kiểm duyệt; gửi cả những thứ đó sẽ làm ngập (seed có 143 review chờ).
- **WebSocket thuần, không SockJS**: mọi trình duyệt mục tiêu hỗ trợ, khỏi thêm `sockjs-client` và nhánh HTTP polling. Sửa dòng stack trong plan §2 cho khớp openapi.

**Quyết định kỹ thuật:**
- **Ghi thông báo trong transaction của thao tác gây ra** (`NotificationService`, `Propagation.MANDATORY`, như `ViolationService`): thao tác rollback thì không có thông báo "ma" (vd. duyệt thua optimistic lock). **Đẩy realtime chỉ sau commit** (`NotificationCreatedEvent` → `NotificationPushListener`, AFTER_COMMIT), không đẩy thứ chưa chắc tồn tại. Gọi thẳng từ service nghiệp vụ, không dựa vào `ReviewChangedEvent`: listener của sự kiện đó chạy `@Async` sau commit, không còn cùng transaction.
- **`data` là ảnh chụp** (tên / slug địa điểm, lý do, đoạn trích…), không join lại lúc đọc: nội dung có thể bị đổi tên / ẩn / xóa sau đó, thông báo vẫn đọc được. 11 loại trong `NotificationType`, bảng loại → người nhận → `data` ghi trong openapi `Notification`.
- **Luật người nhận**: không tự gửi cho người thao tác (kiểm duyệt viên tự gửi yêu cầu sở hữu không nhận thông báo của chính mình; chủ không được báo review của chính mình). Review **viết thẳng** (trust ≥ 30) chỉ báo chủ, tác giả đã thấy kết quả trong response. Review **từ hàng chờ** báo cả tác giả lẫn chủ. Phản hồi của chủ chỉ báo **lần đầu**, sửa không gửi lại. Ẩn địa điểm báo cả người đề xuất **và chủ** (việc kinh doanh của họ biến mất), kèm `violation` để frontend nói rõ có bị trừ trust không. Đối tượng đã xóa (3c) chỉ báo người báo cáo. Yêu cầu bị tự từ chối cũng nhận `CLAIM_REJECTED` kèm lý do tự động.
- **Xác thực WebSocket ở khung STOMP CONNECT** (`StompAuthInterceptor`): access token chỉ nằm trong bộ nhớ (S1), trình duyệt không gắn được header vào handshake. Kiểm bằng đúng `JwtDecoder` + `UserJwtAuthenticationConverter` của REST (chữ ký, hạn, tài khoản bị khóa / đã xóa). Không dùng `?access_token=` trên URL vì token sẽ lọt vào log truy cập. Principal của phiên = id người dùng → `convertAndSendToUser(id, …)`. Chỉ cho SUBSCRIBE `/user/queue/notifications` (broker tự gắn phiên của chính người đó), chặn SEND. Origin giới hạn theo `localspot.app.public-url`.
- **Thay đổi ở `SecurityConfig` (phần cốt lõi §3)**: thêm đúng một luật `GET /ws` permitAll cho handshake, không đổi luật nào khác. Bảo vệ thật nằm ở CONNECT, test chứng minh không token / token sai bị từ chối.
- **Broker đơn giản trong bộ nhớ**: đủ cho một tiến trình trên VPS. Chạy nhiều instance thì đổi sang relay STOMP của RabbitMQ (đã có trong stack) mà không đổi client.
- **Đọc / đánh dấu**: mới nhất trước (keyset theo id, index `(user_id, read_at, created_at)`), `unreadOnly`; đánh dấu đã đọc idempotent, giữ thời điểm đọc cũ; thông báo người khác → 404 `NOTIFICATION_NOT_FOUND` (mã mới); đọc tất cả bằng một câu UPDATE chỉ trên dòng chưa đọc.
- **Giới hạn đã biết**: token chỉ kiểm lúc CONNECT, phiên mở tiếp sau khi token 15 phút hết hạn cho tới khi ngắt (chỉ nhận thông báo của chính mình, không gọi được API). Bảng thông báo chưa có chính sách dọn dẹp (giữ hết).
- SpotBugs bắt `Converter.convert()` có thể trả null → kiểm tường minh, ném lỗi xác thực.

**Chưa làm (ghi để nối tiếp):** Pinia store `notification` + giao diện chuông thông báo (checklist F). Seed demo chưa có thông báo (sẽ phát sinh khi chạy thử các luồng).

**Tài liệu**: openapi (mục Realtime: CONNECT kèm token, chỉ SUBSCRIBE kênh cá nhân; bảng 11 loại + `data`; enum `type`; luật đánh dấu đã đọc), plan §2 (bỏ SockJS) + §7 (tên listener) + §10 E.

**Kiểm chứng**: `./mvnw verify`: **338 test** xanh (thêm 11), Spotless sạch, SpotBugs 0. `NotificationFlowTests` (8): duyệt / từ chối địa điểm → người đề xuất (data + lý do, kiểm duyệt viên không nhận); review viết thẳng → chỉ chủ, duyệt / từ chối từ hàng chờ → tác giả + chủ; phản hồi chỉ báo lần đầu; ẩn review → tác giả (`violation` true) + từng người báo cáo; ẩn địa điểm WRONG_INFO → người đề xuất + chủ (`violation` false); bỏ qua không gửi; yêu cầu sở hữu → kiểm duyệt viên, duyệt → người được duyệt + người bị tự từ chối (lý do); kiểm duyệt viên tự gửi không nhận; danh sách / cursor / `unreadOnly` / đếm / đánh dấu idempotent / của người khác 404 / đọc tất cả không ghi đè / khách 401. `NotificationWebSocketTests` (3, máy chủ thật + client STOMP thật): CONNECT không token / token sai bị từ chối ngay (`ExecutionException`, không phải hết giờ), token hợp lệ kết nối được; thông báo vừa commit tới đúng người nhận với id khớp CSDL, người khác không nhận; SUBSCRIBE kênh lạ → máy chủ đóng phiên.

## 2026-10-17 14:45 (+07) — Checklist E: API quản trị và thống kê (hàng chờ gộp, UC33, FR-42, UC26)

**Bối cảnh**: nhánh `feature/be-admin-stats` từ `develop` (`52973c6`, đã gộp PR #21–#24). Mục cuối của checklist E. Openapi đã có 4 endpoint nhưng chưa có code: `/moderation/queue`, `/admin/dashboard`, `/admin/activity-log`, `/owner/places/{id}/stats`. Bảng `place_views`, `check_ins`, `activity_log` và permission `dashboard:view`, `place:stats-own` có từ V1 / V2.

**Supervisor chốt (qua câu hỏi):**
- **Dashboard gộp openapi gốc với thiết kế AdminDashboard**: giữ `newUsersByDay`, `reviewsByDay`, `hotPlaces`, `pendingCounts`, thêm tổng kỳ này + kỳ trước cùng độ dài (▲▼), "khu vực nhiều đánh giá" theo thành phố, thời điểm của việc tồn cũ nhất, `from` / `to` / `generatedAt`. Các phương án bị loại: theo đúng thiết kế (bỏ "địa điểm hot", lệch lời FR-41), theo đúng openapi (frontend phải bỏ phần so sánh và khu vực).
- **"Hot" = nhiều review đang hiện mới viết trong khoảng nhất**, cùng số thì điểm Bayesian cao hơn trước. Phản ánh hoạt động thật, khó thổi phồng. Các phương án bị loại: lượt xem (dễ thổi phồng, không nói gì về chất lượng), trộn có trọng số (trọng số tùy ý, khó bảo vệ).
- **Làm đủ 4 endpoint, không xuất CSV**: nút "Xuất CSV" trong thiết kế không có FR / UC nào (CLAUDE.md: không thêm chức năng ngoài UC), frontend bỏ nút.

**Quyết định kỹ thuật:**
- **Hàng chờ gộp là một `UNION ALL` (JDBC, `ModerationQueueQueries`)**, mỗi nhánh dùng **đúng điều kiện** của hàng chờ riêng loại đó (review trên địa điểm đã ẩn không vào, như `/moderation/reviews`), để hai màn không lệch nhau. Chỉ ghép nhánh của loại người gọi có quyền, nên nhánh không có quyền không bị quét. Mỗi nhánh tự đặt tên cột, vì nhánh nào đứng đầu `UNION` tùy quyền. Đánh đổi: sắp xếp trên kết quả gộp. Hàng chờ cỡ vài trăm mục nên đủ nhanh; mỗi nhánh vẫn lọc bằng index `(status, …)`.
- **Keyset trên `(submittedAt, type, sortId)`**, cursor `QUEUE|ISO-8601|type|sortId` giữ đủ micro giây (làm tròn giây sẽ lặp / sót mục cùng giây). `sortId` của nhóm báo cáo là id báo cáo đầu tiên (duy nhất), còn `id` trả ra vẫn là id đối tượng như openapi. So sánh từng phần thay vì so bộ `(a, b, c) > (…)`. Thứ tự loại = thứ tự khai báo enum `ModerationItemType` (đã ghi chú: đổi thứ tự sẽ làm hỏng cursor cũ).
- **`position` đếm lại mỗi trang** (`COUNT` các mục ≤ cursor) thay vì mã hóa số thứ tự vào cursor. Mục phía trước được xử lý thì trang sau tự đánh số lùi lên (test: duyệt mục 1 → mục 3 thành `position − 1`). Một truy vấn đếm / trang là chấp nhận được với cỡ hàng chờ này.
- **Lọc `type` không có quyền → 403**, không trả rỗng: giao diện sẽ hiểu nhầm là hết việc. Người không có quyền kiểm duyệt nào → 403 ở `@PreAuthorize hasAnyAuthority`.
- **Tiêu đề dùng chung `ReportTargets`**: địa điểm / review chờ duyệt được mô tả giống hệt khi bị báo cáo ("tên địa điểm — trích đoạn"); yêu cầu sở hữu → tên địa điểm (một truy vấn cho cả trang). Cờ `IP_FLAGGED` / `LOW_TRUST` **chỉ cho review**: đây là lý do review bị giữ lại (FR-23); trust không chặn địa điểm / báo cáo / yêu cầu sở hữu nên gắn cờ cho chúng dễ gây hiểu lầm. Trust tính theo lô (`trustScoresOf`), không N+1.
- **Dashboard đếm thẳng trên bảng gốc (`StatsQueries`), không có bảng tổng hợp, không cache**. Số liệu luôn khớp dữ liệu, và vừa duyệt xong quay lại thấy ngay việc tồn giảm. Đánh đổi: mỗi lần mở dashboard quét theo khoảng ngày; chỉ quản trị viên xem, vài nghìn dòng / bảng. Nếu cần thì thêm cache ngắn sau, không đổi API.
- **Hai định nghĩa có chủ đích**: chuỗi theo ngày + tổng = lượng **hoạt động** (mọi tài khoản / review được gửi / báo cáo nhận, kể cả bị xóa / ẩn / từ chối sau đó), để số liệu quá khứ không tự đổi khi nội dung bị gỡ. Xếp hạng (thành phố, hot) = chỉ review **đang hiện**, vì nội dung chờ duyệt / bị ẩn không được làm một địa điểm "hot". Ghi rõ trong openapi để frontend đặt nhãn đúng.
- **Ngày theo giờ Việt Nam bằng độ lệch cố định `+ INTERVAL 7 HOUR`**, không dùng `CONVERT_TZ(…, 'Asia/Ho_Chi_Minh')`: Việt Nam không có giờ mùa hè, và MySQL khỏi cần nạp bảng múi giờ. Khoảng `[from, to]` cả hai đầu, quy đổi thành `[00:00 from, 00:00 to+1)` UTC ở `StatsRange`. Test kiểm biên: 17:30 UTC đã sang ngày hôm sau, 16:59:59 UTC còn trong ngày.
- **Khoảng mặc định theo thiết kế**: dashboard 14 ngày ("14 ngày qua"), chủ quán 30 ngày. Tối đa 366 ngày để chuỗi theo ngày có chặn trên; `from > to` hoặc quá dài → 422 (không âm thầm sửa). Chuỗi luôn đủ mọi ngày (ngày trống = 0), frontend vẽ thẳng.
- **`pendingCounts` dùng lại truy vấn của hàng chờ gộp**: số trên dashboard luôn bằng số mục khi bấm sang hàng chờ. `reports` là số đối tượng có báo cáo mở (= số mục REPORT), không phải số báo cáo.
- **`hotPlaces` đổi kiểu phần tử** từ `PlaceSummary` sang `{place, newReviewCount}`, vì thiết kế hiện con số xếp hạng (vẫn nằm trong phạm vi "gộp" đã chốt). Thẻ địa điểm nạp theo lô qua `PlaceSummaries` (danh mục, ảnh bìa, thẻ).
- **Nhật ký**: JPQL không nạp `actor`, vì nạp qua entity sẽ bị `@SQLRestriction` của `User` bỏ người đã xóa. Tên người thao tác lấy bằng một truy vấn native bỏ qua xóa mềm, nên nhật ký vẫn đủ người dù tài khoản bị xóa sau này (tên đã ẩn danh hóa theo D7). **Không trả `ip_address`**: màn nhật ký cần biết ai làm gì, không cần lộ IP của đồng nghiệp; cột vẫn ở CSDL để tra cứu khi có sự cố.
- **Thống kê của chủ**: quyền qua `OwnershipPermissionEvaluator` có sẵn (`place:stats-own`, chủ hiện tại, 403 / 404). Địa điểm đang bị ẩn vẫn xem được, vì số liệu là của chủ. `ratingDistribution` dùng chung hàm của trang chi tiết (đổi sang package-private), để hai màn luôn cùng số.

**Chưa làm (ghi để nối tiếp):** các màn frontend "Trang quản trị" (AdminDashboard, hàng chờ) và "Trang chủ quán" (thống kê) ở checklist F. Dashboard chưa có cache (chưa cần ở quy mô hiện tại).

**Tài liệu**: openapi (mô tả + mã lỗi của 4 endpoint, `ModerationQueueItem` `title` / `submittedBy` / `reportCount` có thể null, `flags` chỉ cho review; schema mới `DailyCount`, `DashboardTotals`; `Dashboard` mở rộng; `PlaceStats` thêm `from` / `to`), plan §10 E (mục API quản trị + ghi chú FR-42).

**Kiểm chứng**: `./mvnw verify`: **346 test** xanh (thêm 8). Lần chạy đầu SpotBugs bắt `VA_FORMAT_STRING_USES_NEWLINE` (`.formatted()` trên text block có xuống dòng) ở `StatsQueries` → đổi sang `{VISIBLE_REVIEW}.replace` như `ContributionQueries`, chạy lại: Spotless sạch, SpotBugs 0. `AdminStatsFlowTests`: hàng chờ gộp (cùng thời điểm thì REVIEW trước CLAIM, nhóm báo cáo theo báo cáo đầu, review trên địa điểm ẩn không vào; tiêu đề / người gửi / cờ `IP_FLAGGED` + `LOW_TRUST` / `reportCount`; `position` liên tục); đi cursor 2 mục / trang trùng khớp 50 mục / trang, không lặp, khóa phụ tách 5 mục cùng thời điểm, duyệt mục phía trước → trang sau đánh số lùi; role chỉ có `claim:approve` chỉ thấy CLAIM, lọc PLACE 403, `type` lạ 400, cursor hỏng 422, thành viên / chủ 403, khách 401; dashboard (biên ngày VN 17:30 / 16:59:59 UTC, review gửi gồm chờ / đã xóa / địa điểm ẩn, kỳ trước, thành phố + hot chỉ review đang hiện, thứ tự hot + thẻ địa điểm); mặc định 14 ngày + việc tồn tăng đúng 1 + mục cũ nhất; khoảng ngược / 367 ngày 422, 366 ngày (năm nhuận) được, ngày sai định dạng 400, kiểm duyệt viên 403; nhật ký (mới nhất trước, lọc `actorId` / `action`, cursor, metadata lý do, không có `ipAddress`, người thao tác bị xóa vẫn hiện, kiểm duyệt viên 403); thống kê chủ (chuỗi lượt xem / check-in đủ ngày, ngoài khoảng không tính, phân bố sao bỏ review chờ, mặc định 30 ngày, chủ khác / thành viên 403, không tồn tại 404, khách 401).


## 2026-10-17 15:21 (+07) — Checklist F: form viết / sửa đánh giá (P04 — UC12, UC13, FR-19)

**Bối cảnh**: nhánh `feature/fe-write-review` tạo **từ `feature/be-admin-stats`** (chưa gộp) vì cùng sửa `plan-v1.md` và file này; gộp nhánh admin trước. Backend viết / sửa / xoá review + ảnh có từ E6 / E7; trang chi tiết (P03) đã chừa nút "Viết đánh giá" chờ route `write-review`. Thiết kế P04 (`docs/design-system/components/P04WriteReview`), DECISIONS 29/09 (hai kết quả "Đã đăng" / "Đang chờ duyệt").

**Supervisor chốt (qua câu hỏi):**
- **Bỏ bước "Thẻ (tuỳ chọn)" khỏi P04**: chưa có API cho thành viên gắn thẻ vào địa điểm có sẵn (thẻ chỉ đặt lúc đề xuất / chủ sửa). UC22 vẫn có ở form đề xuất (P05). Các phương án bị loại: thêm API thành viên gắn thẻ (cần thêm luật giới hạn / kiểm duyệt / gỡ thẻ chưa có trong đặc tả), lưu thẻ theo review (cần migration `taggable_type REVIEW` và đổi nghĩa thẻ trên trang địa điểm). Đã ghi vào sitemap.

**Quyết định kỹ thuật:**
- **Thêm `GET /reviews/{id}`** (backend): màn sửa cần nạp đúng review của mình mà openapi chưa có endpoint đọc một review (`/me/reviews` phải lật trang). Tác giả thấy review của mình ở mọi trạng thái kèm lý do từ chối; người khác chỉ review đang hiện (404 như vote / bình luận). **Cần đăng nhập**, nên không đụng `SecurityConfig` (phần cốt lõi §3). Màn sửa là nhu cầu duy nhất, cũng cần đăng nhập.
- **Một trang cho viết + sửa + xoá**, như thiết kế ("Sửa đánh giá dùng lại đúng trang này"): `myReviewId` của trang chi tiết quyết định chế độ. `myReviewId` tính cả review đã xoá (D2), nên nạp review trả 404 thì giải thích "đã xoá, không viết lại được" thay vì hiện form. Review bị ẩn (HIDDEN) → báo không sửa được (backend 409 `REVIEW_LOCKED`). Bị từ chối → hiện lý do, gửi lại về hàng chờ.
- **Kết quả theo `status` backend trả về**, không đoán trước: PUBLISHED "Đã đăng" / "Đã lưu thay đổi", PENDING "Đã gửi — đang chờ duyệt" (DECISIONS 29/09 đã sửa: trust ≥ 30 đăng ngay). Dòng nhắc dưới nút đổi từ "Đánh giá được duyệt trước khi hiển thị" (lỗi thời) thành "tài khoản mới hoặc bị cảnh báo được duyệt trước".
- **Thêm trường "Ngày bạn đến"**: API bắt buộc `visitedAt`, thiết kế không có. Mặc định hôm nay **theo giờ Việt Nam** (`Intl` với `Asia/Ho_Chi_Minh`, không theo múi giờ máy), `max` = hôm nay, như luật backend.
- **Validate trên form đồng bộ với backend** (`validation/review.ts`): 1–5 điểm, nội dung 20–5000 ký tự sau khi bỏ khoảng trắng (cùng cách đếm UTF-16 với `String.strip().length()` của Java), ngày không ở tương lai. Lỗi 422 của backend vẫn gắn vào đúng ô; lỗi `photos[i]` nói rõ "Ảnh thứ i+1".
- **Ảnh chuẩn bị ở trình duyệt** (`utils/images.ts`, chốt 2026-10-06): JPEG / PNG ≤ 5 MB gửi nguyên; còn lại (WebP, HEIC trên Safari, ảnh quá nặng) vẽ lại canvas → JPEG 0.88, cạnh dài ≤ 4096 (đủ cho bản large 1920 px backend sinh ra), nền trắng cho PNG trong suốt, đổi đuôi `.jpg` để khớp chữ ký file. Lỗi định dạng báo ngay khi chọn, trước khi gửi.
- **Cắt ảnh tùy chọn** bằng `vue-advanced-cropper` 2.8.9 (đã có trong stack plan §2, không phải thư viện mới): nút "Cắt" trên từng ảnh, tỷ lệ Tự do / 4:3 / Vuông + xoay 90°, xuất JPEG cùng giới hạn cạnh. Không bắt buộc cắt: bắt buộc thêm một bước cho mỗi ảnh, trong khi ảnh điện thoại thường đã ổn.
- **Sửa = PATCH rồi mới xử lý ảnh** (xoá ảnh bị bỏ → thêm ảnh mới), vì API ảnh tách riêng. `version` mới được lưu ngay sau PATCH, để nếu bước ảnh lỗi thì lần gửi lại không vấp 409 `CONCURRENT_MODIFICATION`. Ảnh đã xoá xong được gạch khỏi danh sách chờ xoá.
- **Xoá xác nhận ngay tại chỗ** (dòng cảnh báo + "Xoá vĩnh viễn" / "Giữ lại"), nói rõ hậu quả D2 "không thể viết lại". Không dùng `window.confirm` (không theo design system, khó đọc với trình đọc màn hình).
- **Trang chi tiết**: "Viết đánh giá" khi chưa có, "Sửa đánh giá của bạn" khi đã có. Ẩn với chủ địa điểm (`ownedPlaceIds` — không tự đánh giá, UC12) và địa điểm chưa duyệt. Khách vẫn thấy nút; guard `requiresAuth` đưa qua đăng nhập rồi quay lại.
- **Chưa xác thực email**: hiện nhắc + khoá nút gửi, không để người dùng viết xong mới nhận 403 `EMAIL_NOT_VERIFIED`. Băng nhắc gửi lại link đã có ở layout.
- Thiết kế ghi "96 / 2.000" nhưng backend cho 5000 ký tự: theo backend. Nút xoá dùng `accent` vì `LsButton` chưa có biến thể `danger`; chữ trên nút nói rõ hành động, không chỉ dựa vào màu.

**Chưa làm (ghi để nối tiếp):** supervisor chạy thử trên trình duyệt (cắt ảnh, ảnh HEIC trên Safari, hàng chờ thật) rồi mới đánh dấu xong mục F. Nhãn "✓ Bạn đã check-in" ở cột phải chưa có (cần API check-in của tôi theo địa điểm, chờ P12). Nút "Xem đánh giá của tôi" chờ trang cá nhân (P06).

**Tài liệu**: openapi (`GET /reviews/{reviewId}`), plan §10 F (ghi chú mục form review), sitemap (P04 bỏ UC22).

**Kiểm chứng**: backend `ReviewFlowTests` 12 test xanh (thêm `authorReadsOwnReviewInAnyStatusOthersOnlyVisibleOnes`: tác giả đọc PENDING / REJECTED kèm lý do, người khác 404, review đang hiện ai cũng đọc được, đã xoá 404, khách 401), Spotless + SpotBugs sạch. Frontend: `vitest` **88 test** xanh (thêm 14). `WriteReviewPage.spec` (8): gửi đúng dữ liệu đã cắt khoảng trắng → "đang chờ duyệt"; validate trên form không gọi API; 422 gắn ô + "Ảnh thứ 2"; chế độ sửa điền sẵn + gửi `version` → "Đã lưu thay đổi"; lý do từ chối; review đã xoá → không có form; chủ địa điểm / chưa xác thực email; xoá cần xác nhận rồi về trang địa điểm. `images.spec` (5): ảnh gửi nguyên / phải chuyển, thu nhỏ giữ tỷ lệ, đổi đuôi, "hôm nay" giờ VN (18:30 UTC → ngày hôm sau), luật schema. `PlaceDetailPage.spec`: nút viết / sửa / chủ không có. `vue-tsc`, `eslint --no-cache` sạch, `vite build` được.

## 2026-10-17 21:19 (+07) — Mở lại Q4: ảnh trang chủ do quản trị viên quản lý (FR-43, UC34)

**Bối cảnh**: supervisor yêu cầu thêm "quản lý banner hero (gồm banner + ảnh trong body trang chủ)" ở phía admin. Banner **đã bị cắt khỏi phạm vi** ở Q4 (requirements.md, bỏ FR-43) và không có trong 33 UC → hỏi lại trước khi làm (CLAUDE.md §4: mâu thuẫn với tài liệu đã chốt). Nhánh `feature/admin-banners` tạo từ `feature/fe-write-review` (chưa gộp — cùng sửa plan / file này). HomePage có đúng 3 chỗ ảnh minh hoạ (`FlatPicture`): nền hero, ảnh băng coral mời đăng ký, ảnh khối "Thành viên làm được gì"; khối chủ quán không có ảnh.

**Supervisor chốt (qua câu hỏi):**
- **Mở lại Q4, phạm vi tối thiểu**: 3 ô cố định theo bố cục HomePage, mỗi ô một ảnh + mô tả + bật / tắt + gỡ. Bị loại: danh sách banner tự do có link / thứ tự / lịch chạy (logic lịch + UI + test vượt nhu cầu đồ án), giữ nguyên cắt.
- **Hero chỉ đổi ảnh**: tiêu đề, ô tìm kiếm, bố cục giữ đúng thiết kế. Bị loại: cho sửa cả tiêu đề / phụ đề / link (độ dài chữ, tương phản trên ảnh phá thiết kế).
- **Permission mới `banner:manage`**, chỉ ADMIN (RBAC là phần cốt lõi §3 → cần supervisor duyệt). Bị loại: dùng lại `category:manage` (tên quyền nói về danh mục mà điều khiển ảnh trang chủ — khó giải thích trong báo cáo).
- **Hero phủ một lớp tối đều**, không gradient (README cấm). Bị loại: để nguyên ảnh (một ảnh sáng làm tiêu đề trắng mất tương phản — kiểm tra accessibility F sẽ bắt).

**Quyết định kỹ thuật:**
- **Migration mới `V5__home_banners.sql`**, chỉ thêm: bảng `home_banners` (id số vì `activity_log.target_id` là BIGINT; `UNIQUE(slot)` + CHECK 3 ô; `storage_key`, `alt_text`, `is_active`, `updated_by`, `version`) và 1 dòng permission + gán cho ADMIN. Không sửa migration cũ, không đổi quyền nào đã có. Test cập nhật: 33 bảng, ADMIN 11 quyền, catalog 18 quyền khớp code / CSDL / openapi.
- **Xử lý ảnh ngay trong request**, không qua hàng đợi `photo.process` như ảnh địa điểm / review: chỉ quản trị viên tải, rất thưa, cần thấy kết quả ngay; trang chủ công khai không bao giờ có trạng thái "đang xử lý"; không phải mở rộng `PhotoProcessMessage.Target`. Dùng chung `ImageInspector` (JPEG / PNG, chữ ký file + đuôi, 5 MB, 40 MP) và `ImageResizer` (3 bản JPEG, bỏ EXIF / GPS). Ảnh gốc không lưu (không cần xử lý lại).
- **Thứ tự ghi an toàn**: lên kho trước, ghi CSDL sau. Rollback → `BannerImageStoredEvent` xóa ảnh mới (như minh chứng sở hữu); ảnh cũ chỉ xóa **sau commit** (`PhotoObjectsDeletedEvent` có sẵn), nên trang chủ không bao giờ trỏ vào ảnh đã mất. Kho lỗi giữa chừng → xóa phần đã ghi, 503, không có dòng CSDL. Thay ảnh khóa dòng của ô (hai quản trị viên cùng thay → người sau chờ, ảnh của người trước được dọn).
- **Kho đọc công khai thêm tiền tố `banners/*`** (`StorageBucketInitializer`), ảnh đã bỏ EXIF như `places/*`, `reviews/*`.
- **Trang chủ lấy ảnh qua `GET /home`** (thêm `banners`, chỉ ô đang bật), không tạo endpoint mới: một request cho cả trang, dùng chung cache Redis. `HomeBannersChangedEvent` xóa cache sau commit. `HomeResponse` đọc bản cache cũ (thiếu trường) thành mảng rỗng, không lỗi khi triển khai.
- **Sửa mô tả / bật tắt có `version`** (409 khi lệch); tắt giữ ảnh, trang chủ về hình minh hoạ. **Gỡ = xóa dòng** (không xóa mềm: đây là cấu hình hiển thị, không phải nội dung người dùng thuộc danh sách xóa mềm §3); object xóa sau commit. Ô chưa có ảnh → 404 `BANNER_NOT_FOUND` (mã mới). activity_log `BANNER_IMAGE_REPLACE` / `BANNER_UPDATE` / `BANNER_REMOVE` kèm `slot`.
- **Frontend quản trị** `/admin/banners` (sidebar "Ảnh trang chủ", nhóm Quản trị, chỉ hiện với `banner:manage`): mỗi ô một khối, xem trước đúng tỷ lệ khung. Thay ảnh = chọn → chuẩn bị ảnh ở trình duyệt (như P04) → **cắt theo khung gợi ý** (hero 15:7, băng 4:3, khối 5:2), huỷ ở bước cắt thì không đổi gì. Trạng thái luôn có ký hiệu + chữ; gỡ cần xác nhận tại chỗ. `PhotoCropDialog` thêm tham số `preset` (dùng chung với P04).
- **Lớp phủ hero**: `on-accent` (tối cố định ở cả hai theme) 65% đều. Tính trên ảnh trắng tinh: nền sau phủ ≈ rgb(106,106,102), chữ `on-olive` ≈ 4,7:1, đạt AA cho chữ thường. Quản trị viên đặt ảnh nào cũng không phá tương phản. Ảnh trong thân trang `alt` theo mô tả, rỗng = trang trí; `srcset` 3 cỡ, hero `eager`, còn lại `lazy`.

**Chưa làm (ghi để nối tiếp):** supervisor chạy thử. Báo cáo Chương 3 thêm UC34 / FR-43 / bảng `home_banners`. Chưa có trang xem trước trước khi bật (bật / tắt đã đủ để thử an toàn).

**Tài liệu**: requirements (Q4 mở lại, FR-43), use-cases (UC34 — phân tích + design system), sitemap (`/admin/banners`), database.md (bảng `home_banners`, 33 bảng, V5), openapi (`/admin/banners…`, `Banner`, `HomeBanner`, `BannerSlot`, `HomeResponse.banners`), plan §4 + §10 E, F.

**Kiểm chứng**: backend `./mvnw verify`: **352 test** xanh (thêm 5 `BannerFlowTests` + 1 test đọc review của nhánh trước), Spotless sạch, SpotBugs 0. `BannerFlowTests`: 3 ô trống ban đầu; thay ảnh hero → 3 bản ở `banners/`, không còn EXIF, không lưu ảnh gốc, mô tả cắt khoảng trắng, `GET /home` thấy ngay dù đã nạp cache, activity_log có `slot`; thay lần hai giữ mô tả cũ + xóa đúng 3 object cũ; `version` lệch 409, mô tả 201 ký tự 422, tắt → trang chủ về minh hoạ nhưng ảnh còn; ô trống 404; gỡ → object xóa, gỡ lại 404; PDF / thiếu ảnh 422 trường `image`, ô lạ 400, kho sập 503 không để lại gì; kiểm duyệt viên / chủ / thành viên 403, khách 401. `SchemaMigrationTests` (33 bảng, ADMIN 11 quyền), `PermissionCatalogTests` (18 quyền khớp CSDL / code / openapi). Chạy riêng `HomeFlowTests` thì test đánh giá nổi bật trượt vì cần dữ liệu của lớp khác (test cô lập kém có từ trước, không do thay đổi này); chạy cả bộ thì xanh. Frontend: `vitest` **94 test** xanh (thêm 6: khối ô ảnh trống / tắt có `version` / lưu mô tả / gỡ có xác nhận; `BannerPicture` có ảnh + lớp phủ / không có ảnh), `vue-tsc`, `eslint --no-cache` sạch, `vite build` được. Chạy thật trên môi trường dev: tải ảnh hero → URL công khai trả `image/jpeg`, `/home` có ô HERO, gỡ → 204 và ảnh 404 (đã gỡ lại, trang chủ dev về minh hoạ).

**Sự cố môi trường**: lần verify đầu chạy cùng backend dev + 6 container trong WSL → VM WSL ~6 GB, máy còn ~2,8 GB, WSL treo ~25 phút. Supervisor đồng ý `wsl --shutdown` (volume giữ nguyên), chạy lại verify một mình → xanh như trên. Bài học: không chạy `./mvnw verify` song song với backend dev trên máy 16 GB.


## 2026-10-17 22:21 (+07) — Checklist F: vote, bình luận, báo cáo trên trang chi tiết (UC14, UC15, UC17) + giới hạn bình luận

**Bối cảnh**: nhánh `feature/fe-review-interactions` từ `feature/admin-banners` (chưa gộp — cùng sửa plan / file này). Backend vote / bình luận / báo cáo có từ E (2026-10-11, 2026-10-14). ReviewItem đang là bản chỉ đọc; README thành phần mô tả hàng thao tác Hữu ích / Bình luận / Báo cáo, bản của mình thay bằng Sửa / Xoá, bản chờ duyệt ghim đầu danh sách.

**Supervisor chốt (qua câu hỏi):**
- **Thêm giới hạn bình luận bằng Redis** (trước đây bình luận không giới hạn — câu hỏi từ khi trả lời "bình luận được bao nhiêu lần"): 20 / 10 phút / tài khoản qua `RateLimiter` sẵn có (không thêm thư viện). Bị loại: không giới hạn (chỉ dựa vào báo cáo), thêm trần theo ngày đếm trong CSDL (nhiều mã + test hơn mức cần).

**Quyết định kỹ thuật:**
- **Giới hạn kiểm sau mọi kiểm tra** (email, nội dung, review đang hiện, `parentId`): gõ sai / review vừa bị ẩn không tiêu suất của người dùng; lần bị từ chối không tính (hành vi sẵn có của script Lua). Redis sập → cho qua như các luật khác (bình luận còn báo cáo + kiểm duyệt). Khác giới hạn 5 review / 24 h (đếm trong CSDL vì là cơ chế chống review ảo cốt lõi, phải có hiệu lực cả khi Redis sập).
- **Vote lấy số từ backend**, không tự cộng trừ: `PUT` / `DELETE` idempotent trả `helpfulCount` + `votedByMe`, nên bấm nhanh hai lần hay mở hai tab vẫn đúng. Khoá nút trong lúc chờ. Không hiện số 0 (README).
- **Bình luận mở ngay dưới đánh giá**, không sang trang khác; khách đọc được (API công khai), thao tác ghi → đăng nhập rồi quay lại đúng trang (`useRequireLogin` dùng chung). Sau khi gửi / xoá thì nạp lại luồng (giữ đúng thứ tự gốc cũ trước của backend) và báo ReviewItem tăng / giảm số bình luận, không tải lại cả danh sách đánh giá. Chỉ gốc có "Trả lời" (2 cấp — Q5); gốc đã xoá còn trả lời → "Bình luận đã bị xoá" và không trả lời được (backend yêu cầu gốc đang hiện).
- **Một hộp thoại báo cáo dùng chung** cho địa điểm / đánh giá / bình luận (ReviewItem và luồng bình luận chỉ phát sự kiện, trang chi tiết mở hộp thoại), thay vì mỗi mục một hộp thoại. Lý do theo enum backend; "Thông tin sai" / "Khác" bắt buộc mô tả (chốt 2026-10-14) kiểm ngay trên form. Đã báo cáo (409 `REPORT_ALREADY_EXISTS`, U10) → thông báo nhẹ và đóng, không coi là lỗi. Báo cáo **người dùng** để ở trang cá nhân (P06) — trang chi tiết không có chỗ hợp lý.
- **Đánh giá của mình**: "Sửa" (P04 — xoá cũng ở đó) thay cho Hữu ích / Báo cáo; vẫn đọc / trả lời bình luận. **Bản chờ duyệt / bị từ chối** của mình nạp riêng bằng `GET /reviews/{id}` (danh sách công khai chỉ có PUBLISHED), ghim đầu danh sách, nền chìm, nhãn "● Đang chờ duyệt — chỉ bạn thấy" / "✕ Bị từ chối: lý do", chỉ có Sửa, như README.
- **"Báo cáo" địa điểm** đặt ở đầu P03 cạnh nút đánh giá — kênh duy nhất báo tên / vị trí sai (O5, O8).

**Chưa làm (ghi để nối tiếp):** supervisor chạy thử. Báo cáo người dùng (P06). Thông báo realtime khi có bình luận mới không thuộc UC (đã chốt 2026-10-16).

**Tài liệu**: openapi (429 + luật giới hạn ở `POST /reviews/{id}/comments`), requirements (NFR-10, bảng giới hạn tần suất), plan §10 D (rate limit) + F (mục bình luận, vote, báo cáo).

**Kiểm chứng**: backend `./mvnw verify` cả bộ **353 test** xanh (chạy sau khi đẩy nhánh — không test nào khác gửi quá 20 bình luận / tài khoản), SpotBugs 0. `CommentFlowTests` 7 (thêm `twentyFirstCommentWithinTenMinutesIs429ButInvalidOnesDoNotCount`: gửi rỗng 422 không tính, 20 bình luận được, thứ 21 → 429 `TOO_MANY_REQUESTS` + `Retry-After`, tài khoản khác vẫn gửi được) + `RateLimitFlowTests` 7 xanh, Spotless + SpotBugs sạch. Frontend `vitest` **104 test** xanh (thêm 10 `ReviewInteractions.spec`: vote / bỏ vote theo backend, không hiện số 0; khách → đăng nhập kèm `redirect`; báo cáo phát đúng đối tượng; đánh giá của mình chỉ có Sửa, bản chờ duyệt có nhãn và không bình luận; luồng 2 cấp + dòng giữ chỗ + chỉ gốc có Trả lời + Xoá / Báo cáo đúng người; gửi gốc / trả lời đúng `parentId` và cắt khoảng trắng; 429 → "thử lại sau 4 phút"; xoá cần xác nhận; hộp thoại: thiếu lý do, "Thông tin sai" thiếu mô tả không gửi, gửi đúng dữ liệu, 409 đã báo cáo → thông báo nhẹ). `vue-tsc`, `eslint --no-cache` sạch, `vite build` được.

## 2026-10-17 22:33 (+07) — Checklist F: form đề xuất địa điểm (P05 — UC11, UC22)

**Bối cảnh**: nhánh `feature/fe-propose-place` từ `feature/fe-review-interactions` (chưa gộp). Backend đề xuất (multipart, ảnh qua hàng đợi) và nghi trùng `/places/duplicates` (U7) có từ E1 / E4. Sitemap đã chốt "một trang có thanh tiến độ; cảnh báo trùng U7"; header và trang chủ đã chừa mục "Đề xuất địa điểm" chờ route `propose`.

**Supervisor chốt (qua câu hỏi):**
- **Nháp tự lưu trong trình duyệt** (localStorage, theo tài khoản, không gồm ảnh): thiết kế có "Lưu nháp" nhưng backend không có trạng thái nháp — đề xuất tạo thẳng PENDING. Bị loại: bỏ nút (form dài, đóng tab là mất), nháp phía máy chủ (trạng thái DRAFT mới, đụng `places` là phần cốt lõi + hàng chờ duyệt, cần migration).
- **Vị trí = ghim trên bản đồ hoặc "Vị trí của tôi"**, địa chỉ nhập bằng chữ — đúng UC11 bước 3 ("kéo marker hoặc dùng vị trí hiện tại"). Bị loại: thêm Nominatim (dịch vụ ngoài mới, có giới hạn tần suất, phải giải thích thêm trong báo cáo).

**Quyết định kỹ thuật:**
- **Trường theo luồng chính UC11**, không chỉ theo bản xem trước P05 (bản này thiếu giá / giờ / tiện ích mà UC11 bước 2 có): Thông tin (tên, danh mục, thẻ, mô tả) · Vị trí (địa chỉ, thành phố, ghim) · Chi tiết, tuỳ chọn (giá, điện thoại, web, giờ mở cửa, tiện ích) · Ảnh. Section tiêu đề trái + trường phải như AdminForm (README: "cùng bộ section" cho UC11 / UC24 / UC27).
- **Validate đồng bộ backend** (`validation/place.ts`): độ dài, mẫu số điện thoại / web http(s) giống `PlaceCreateRequest`; thẻ tách theo dấu phẩy, gộp khoảng trắng, bỏ trùng không phân biệt hoa thường, ≤ 10 thẻ ≤ 50 ký tự. Luật `giá thấp ≤ giá cao` là luật cấp object của zod nên chỉ chạy khi từng trường đã hợp lệ — chấp nhận (backend kiểm lại, lỗi 422 gắn đúng ô `priceMax`). Lỗi `location.*` của backend → "đặt lại ghim"; `photos[i]` → "Ảnh thứ i+1".
- **Giờ mở cửa**: mỗi ngày một khung (đóng cửa / mở–đóng), giờ đóng sớm hơn giờ mở = qua đêm như openapi. Đủ cho hầu hết quán; nhiều khung / ngày (nghỉ trưa) để chủ sửa sau (UC24) nếu cần — API đã hỗ trợ.
- **`LocationPicker` riêng**, không dùng lại `PlaceMap` (bản đồ kết quả: gom cụm, ghim theo điểm): một ghim kéo được, bấm bản đồ để đặt, "Vị trí của tôi" dùng chung store `place`; toạ độ làm tròn 6 chữ số và hiện bằng chữ (không chỉ dựa vào bản đồ). Đổi từ ngoài (vị trí của tôi, khôi phục nháp) mới kéo khung nhìn tới ghim; bấm / kéo trên bản đồ thì không, tránh bản đồ nhảy khi đang thao tác.
- **Nghi trùng (U7, UC11 6a)**: khi có tên ≥ 2 ký tự + ghim, chờ 600 ms rồi hỏi `/places/duplicates`; có ứng viên thì hiện danh sách (mở trang ứng viên ở tab mới, khoảng cách, "đề xuất của bạn đang chờ duyệt") và phải đánh dấu "Đây là địa điểm khác" mới gửi — đúng luồng thay thế 6a; backend chỉ cảnh báo nên đây là bước của giao diện. Đổi tên / ghim → hỏi lại và bỏ xác nhận cũ.
- **Nháp** (`utils/draft.ts`): khóa theo id người dùng (hai tài khoản chung máy không thấy nháp của nhau), lưu sau 800 ms ngừng gõ; mọi lỗi storage (chế độ riêng tư, đầy) bị nuốt, form vẫn chạy. Khôi phục thì báo rõ "ảnh không được lưu" + nút "Bỏ nháp". Gửi thành công thì xoá nháp. "Lưu nháp" lưu ngay và báo thành công / không lưu được.
- **Sau khi gửi**: "Đã gửi — chờ kiểm duyệt" + "Xem đề xuất" (trang chi tiết hiện đề xuất PENDING cho chính người đề xuất) + "Đề xuất địa điểm khác". Trang cá nhân (theo dõi đề xuất — câu hỏi 3 của P05) làm ở P06, dùng `/me/places` có sẵn.
- `PhotoPicker` (P04) dùng lại nguyên vẹn: giới hạn 10 ảnh / request trùng với đề xuất.
- Test chạy đồng hồ thật: đồng hồ giả của vitest làm lệch luồng lỗi 422 của vee-validate; Node 26 có `localStorage` riêng che bản của jsdom → test thay bằng kho trong bộ nhớ.

**Chưa làm (ghi để nối tiếp):** supervisor chạy thử (đặc biệt ghim trên điện thoại, nháp khi mở lại tab). Trang cá nhân theo dõi đề xuất (P06). Nhiều khung giờ / ngày.

**Tài liệu**: plan §10 F (mục form đề xuất).

**Kiểm chứng**: frontend `vitest` **111 test** xanh (thêm 7 `ProposePlacePage.spec`: gửi đúng dữ liệu — thẻ tách + bỏ trùng, giờ theo ngày bật, tiện ích, trường trống bỏ đi, xoá nháp; thiếu trường bắt buộc báo đủ 5 lỗi, giá ngược báo khi các trường khác hợp lệ, không gọi API; nghi trùng hiện ứng viên + khoảng cách, chặn gửi tới khi xác nhận; nháp tự lưu → khôi phục khi vào lại → bỏ nháp xoá sạch; 422 gắn ô `website` + "đặt lại ghim"; `parseTags`, `toOpeningHours`). `vue-tsc`, `eslint --no-cache` sạch, `vite build` được.

## 2026-10-18 16:07 (+07) — Checklist F: trang cá nhân (P06 — UC18, UC20, UC21, UC11)

**Bối cảnh**: nhánh `feature/fe-profile` từ `develop` (`62403f3`, đã gộp #27–#29). Backend hồ sơ công khai, review theo người, bộ sưu tập, theo dõi, `/me/*` có từ E. Sitemap đã chốt 3 câu hỏi của P06 (bộ sưu tập bật / tắt công khai từng bộ, 8 huy hiệu O3, danh sách theo dõi công khai).

**Supervisor chốt (qua câu hỏi):**
- **Chỉ trang cá nhân** trong mục này. Trang bộ sưu tập (chi tiết / sửa / chia sẻ) và nút "Lưu" ở P03 tách thành mục riêng (API có sẵn, khối lượng gấp đôi); chỉnh sửa hồ sơ tách riêng vì backend `PATCH /me`, `PUT /me/avatar` có trong openapi nhưng **chưa cài** (MeController chỉ có `GET /me` + đổi mật khẩu — phát hiện khi rà, ghi vào plan).
- **Huy hiệu chỉ hiện cái đã đạt**: API chỉ trả huy hiệu đã trao; bản mẫu có huy hiệu chưa đạt + "Còn …" cần endpoint mới (danh mục + tiến độ). Bị loại: thêm endpoint đó (lát backend + test).
- **Thêm `place {id, slug, name}` vào Review** (backend): `Review` chỉ có `placeId`, danh sách đánh giá trên trang cá nhân không biết tên / liên kết địa điểm, không có API lấy theo lô. Bị loại: endpoint riêng cho trang cá nhân (lặp logic danh sách review), bỏ tên địa điểm (danh sách khó đọc, không bấm được).

**Quyết định kỹ thuật:**
- `place` nạp **một truy vấn native / trang** từ khóa ngoại trên proxy (không nạp từng địa điểm — N+1), gồm cả địa điểm đã xóa mềm (review vẫn cần biết mình thuộc đâu). Trường thêm vào, client cũ bỏ qua.
- **Đầu trang theo dữ liệu có thật**: bản mẫu có khu vực, số check-in, hạng tháng — hồ sơ công khai không có (check-in là riêng tư, chốt 2026-10-12), nên dải số liệu là Đánh giá · Điểm đóng góp · Người theo dõi · Đang theo dõi; hai ô sau là nút mở danh sách công khai (hộp thoại, "Xem thêm" theo cursor).
- **Tab qua `?tab=`** (sitemap), mỗi tab chỉ tải khi mở. Tab của chính chủ (`checkins`, `places`) gõ tay trên trang người khác → về tab Đánh giá, không gọi API riêng tư. Trang của mình dùng `/me/reviews` (mọi trạng thái + lý do từ chối) thay cho danh sách công khai.
- **Tab "Đề xuất của tôi"** (`/me/places`) — thêm vào danh sách tab của sitemap: UC11 quy định người đề xuất theo dõi đề xuất ở trang cá nhân (câu hỏi 3 của P05). Trạng thái bằng ký hiệu + chữ.
- **Theo dõi**: khách → đăng nhập rồi quay lại; cập nhật ngay nút + số người theo dõi, rồi nạp lại hồ sơ theo máy chủ. "Cài đặt tài khoản" (trang mật khẩu có sẵn) thay cho "Chỉnh sửa hồ sơ" tới khi có mục hồ sơ.
- **Thẻ bộ sưu tập chưa bấm được**: trang bộ sưu tập chưa có — không tạo link chết (quy ước từ đợt khám phá).
- Huy chương: khóa `icon` trung tính của V4 ánh xạ sang hình + cặp màu nền / chữ có sẵn của design system (sun / coral + `on-accent`, olive + `on-olive`) để giữ tương phản ở cả hai theme; khóa lạ dùng hình mặc định. `.ls-tabs`, `.ls-stats`, `.ls-medal` port từ bundle.css (`profile.css`). Tên tác giả trên ReviewItem và menu tài khoản dẫn tới trang cá nhân.

**Chưa làm (ghi để nối tiếp):** supervisor chạy thử. Hai mục mới trong plan: Bộ sưu tập (UC18), Cài đặt — Hồ sơ (UC06, cần backend).

**Tài liệu**: openapi (`Review.place`), sitemap (`/users/:id` thêm tab `places`, nút "Cài đặt tài khoản"), plan §10 F (mục trang cá nhân + 2 mục tách ra).

**Kiểm chứng**: backend `ReviewFlowTests` (thêm kiểm `place.id` / `name` / `slug`), `CommunityFlowTests`, `HomeFlowTests`, `ReviewModerationFlowTests` — 31 test xanh, Spotless + SpotBugs sạch. Frontend `vitest` **118 test** xanh (thêm 7 `ProfilePage.spec`: số liệu, huy hiệu, 3 tab, liên kết địa điểm; theo dõi — khách → đăng nhập, đã đăng nhập → nút + số cập nhật; trang của mình: Cài đặt, 5 tab, đánh giá mọi trạng thái + lý do; Đề xuất của tôi theo trạng thái, chỉ tải tab đang mở; tab riêng tư trên trang người khác → Đánh giá; bộ sưu tập + huy hiệu có mô tả / ngày; 404 → trang không tìm thấy). `vue-tsc`, `eslint --no-cache` sạch, `vite build` được.
