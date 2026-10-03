package com.localspot.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MIN_LENGTH = 8;
    static final int MAX_BCRYPT_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return value.codePointCount(0, value.length()) >= MIN_LENGTH
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES
                && value.codePoints().anyMatch(Character::isLetter)
                && value.codePoints().anyMatch(Character::isDigit);
    }
}
