package com.localspot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mật khẩu theo U1: ≥ 8 ký tự, có chữ và số; tối đa 72 <b>byte</b> UTF-8 vì BCrypt bỏ qua phần vượt quá (mật khẩu
 * tiếng Việt có dấu tốn 2–3 byte / ký tự nên giới hạn ký tự không đủ).
 */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Mật khẩu cần ít nhất 8 ký tự, gồm cả chữ và số, tối đa 72 byte";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
