package com.localspot.repository;

import com.localspot.entity.Tag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * Tạo thẻ nếu slug chưa có. {@code INSERT IGNORE} thay vì kiểm-rồi-thêm: hai người cùng gắn một thẻ mới đồng thời
     * không làm request sau lỗi trùng UNIQUE. Dữ liệu đã được validate độ dài trước — IGNORE chỉ còn bỏ qua trùng slug.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO tags (name, slug) VALUES (:name, :slug)", nativeQuery = true)
    void insertIfAbsent(@Param("name") String name, @Param("slug") String slug);

    List<Tag> findBySlugIn(Collection<String> slugs);
}
