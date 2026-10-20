package com.localspot.storage;

import java.io.Serial;

/** Kho object không đọc / ghi được (mạng, quyền, object mất). */
public class StorageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
