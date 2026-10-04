package com.localspot.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu một thao tác quản trị / kiểm duyệt cần ghi {@code activity_log} (FR-42). Đặt trên phương thức
 * {@code @Transactional} của service; {@link ActivityLogAspect} ghi một dòng sau khi phương thức chạy xong, <b>trong
 * cùng transaction</b> — thao tác rollback thì dòng log cũng mất, ghi log lỗi thì thao tác rollback theo.
 *
 * <p>{@link #targetId()} và {@link #metadata()} là biểu thức SpEL trên tham số phương thức (theo tên, vd.
 * {@code #placeId}); {@code #result} là giá trị trả về.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditedAction {

    /** Mã thao tác ổn định, dạng {@code <ĐỐI_TƯỢNG>_<HÀNH_ĐỘNG>} (database.md §3: {@code PLACE_APPROVE}, {@code USER_LOCK}). */
    String action();

    /** Loại đối tượng bị tác động: {@code PLACE}, {@code REVIEW}, {@code USER}… */
    String targetType();

    /** SpEL trả id đối tượng — bắt buộc khác null. */
    String targetId();

    /** SpEL trả {@code Map} (vd. {@code "{reason: #reason}"}) lưu vào cột JSON; rỗng = không có metadata. */
    String metadata() default "";
}
