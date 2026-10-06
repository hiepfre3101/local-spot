package com.localspot.storage;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@link ObjectStorage} trong bộ nhớ cho test tích hợp — CI không cần container MinIO. {@link S3ObjectStorage} chỉ là lớp
 * chuyển tiếp mỏng sang SDK; luồng thật với MinIO kiểm bằng tay ở dev (progress 2026-10-06).
 */
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private volatile boolean failing;

    @Override
    public void put(String key, byte[] content, String contentType) {
        checkAvailable();
        objects.put(key, content.clone());
    }

    @Override
    public byte[] get(String key) {
        checkAvailable();
        byte[] content = objects.get(key);
        if (content == null) {
            throw new StorageException("Không có object " + key, null);
        }
        return content.clone();
    }

    @Override
    public void delete(Collection<String> keys) {
        checkAvailable();
        keys.forEach(objects::remove);
    }

    public boolean contains(String key) {
        return objects.containsKey(key);
    }

    public Set<String> keysStartingWith(String prefix) {
        return objects.keySet().stream().filter(k -> k.startsWith(prefix)).collect(Collectors.toSet());
    }

    /** Giả lập kho sập: mọi thao tác ném {@link StorageException}. */
    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    private void checkAvailable() {
        if (failing) {
            throw new StorageException("Kho giả lập đang lỗi", null);
        }
    }
}
