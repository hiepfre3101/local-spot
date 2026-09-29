# Đặc tả API

[openapi.yaml](openapi.yaml) — OpenAPI 3.1, thiết kế trước (design-first): hợp đồng chung cho backend và frontend.
Khi backend chạy, springdoc sinh bản thực tế tại `/v3/api-docs` và Swagger UI `/swagger-ui.html` — đối chiếu hai bản để phát hiện lệch.

## Kiểm tra hợp lệ

```bash
npx -y @redocly/cli@latest lint docs/api/openapi.yaml --extends=minimal
```

## Xem dạng tài liệu

```bash
npx -y @redocly/cli@latest preview-docs docs/api/openapi.yaml
```
