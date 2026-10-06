package com.localspot.repository;

import com.localspot.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Cả bảng (vài chục dòng) theo thứ tự hiển thị — cây dựng trong bộ nhớ, một truy vấn. */
    List<Category> findAllByOrderBySortOrderAscIdAsc();

    List<Category> findByParentIdOrderBySortOrderAscIdAsc(Long parentId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByParentId(Long parentId);

    /** Native — tính cả địa điểm đã xóa mềm: khóa ngoại {@code places.category_id} vẫn trỏ tới danh mục. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM places WHERE category_id = :categoryId)", nativeQuery = true)
    long countPlacesIncludingDeleted(@Param("categoryId") Long categoryId);
}
