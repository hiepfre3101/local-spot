package com.localspot.exception;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** 429 — vượt giới hạn tần suất (NFR-10). {@link GlobalExceptionHandler} thêm header {@code Retry-After} (giây). */
public class RateLimitExceededException extends ApiException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public RateLimitExceededException(Duration retryAfter) {
        this(Math.max(1, (retryAfter.toMillis() + 999) / 1000)); // làm tròn lên: Retry-After quá sớm sẽ bị chặn tiếp
    }

    private RateLimitExceededException(long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.TOO_MANY_REQUESTS,
                "Bạn thao tác quá nhiều lần. Hãy thử lại sau " + humanize(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String humanize(long seconds) {
        return seconds < 60 ? seconds + " giây" : ((seconds + 59) / 60) + " phút";
    }
}
