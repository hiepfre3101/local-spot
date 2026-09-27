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
