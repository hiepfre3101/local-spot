package com.localspot.repository;

import com.localspot.entity.Taggable;
import com.localspot.entity.TaggableType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaggableRepository extends JpaRepository<Taggable, Taggable.Id> {

    /** Tên thẻ của một đối tượng, theo bảng chữ cái — dùng index {@code (taggable_type, taggable_id)}. */
    @Query("""
            SELECT t.tag.name FROM Taggable t
            WHERE t.id.taggableType = :type AND t.id.taggableId = :targetId
            ORDER BY t.tag.name
            """)
    List<String> findTagNames(@Param("type") TaggableType type, @Param("targetId") Long targetId);

    /** Tên thẻ của nhiều đối tượng một lần — dòng {@code (taggableId, tên)}. */
    @Query("""
            SELECT t.id.taggableId, t.tag.name FROM Taggable t
            WHERE t.id.taggableType = :type AND t.id.taggableId IN :targetIds
            ORDER BY t.tag.name
            """)
    List<Object[]> findTagNamesOf(@Param("type") TaggableType type, @Param("targetIds") Collection<Long> targetIds);
}
