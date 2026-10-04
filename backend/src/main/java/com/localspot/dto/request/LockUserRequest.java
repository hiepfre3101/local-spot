package com.localspot.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code POST /admin/users/{id}/lock} — khóa có thời hạn và lý do (UC29 3b; lý do hiện cho người bị khóa). */
public record LockUserRequest(
        @NotNull @Future Instant until,
        @NotBlank @Size(max = 500) String reason) {}
