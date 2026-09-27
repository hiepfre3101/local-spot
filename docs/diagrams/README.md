# Sơ đồ (PlantUML)

Nguồn là các file `*.puml`; ảnh PNG sinh ra nằm ở `png/` để chèn vào báo cáo.

## Render lại

Chạy trong WSL (cần JDK 21 và `plantuml.jar`, không cần Graphviz — dùng layout Smetana có sẵn):

```bash
cd /mnt/d/HOU/IT43/btl/v1/source/local-spot/docs/diagrams
java -Djava.awt.headless=true -jar ~/tools/plantuml.jar -Playout=smetana -charset UTF-8 -o png *.puml
```

Tải `plantuml.jar` (một lần): `mkdir -p ~/tools && curl -fsSL -o ~/tools/plantuml.jar https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar`

Xem nhanh trong VS Code: extension "PlantUML" (jebbs.plantuml), `Alt+D`.

## Danh sách

| File | Nội dung | Use case |
|---|---|---|
| use-case-overview | Sơ đồ use case tổng quát | UC01–UC33 |
| activity-write-review | Hoạt động: viết đánh giá | UC12 |
| activity-approve-place | Hoạt động: đề xuất & duyệt địa điểm | UC11, UC27 |
| activity-check-in | Hoạt động: check-in | UC16 |
| sequence-login | Tuần tự: đăng nhập & làm mới phiên | UC03, UC04 |
| sequence-search | Tuần tự: tìm kiếm & đồng bộ index | UC08 |
| sequence-post-review | Tuần tự: đăng đánh giá kèm ảnh | UC12 |
| erd-account | ERD 1/3: tài khoản & phân quyền | — |
| erd-place-review | ERD 2/3: địa điểm & đánh giá | — |
| erd-community | ERD 3/3: cộng đồng & vận hành | — |
