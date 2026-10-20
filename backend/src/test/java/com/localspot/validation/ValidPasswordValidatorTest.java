package com.localspot.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidPasswordValidatorTest {

    private final ValidPasswordValidator validator = new ValidPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"LocalSpot2026", "abcdefg1", "mậtkhẩu99"})
    void acceptsPasswordsWithLettersAndDigits(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"short1", "onlyletters", "12345678", "        "})
    void rejectsWeakPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void limitsBytesNotCharactersBecauseBcryptTruncatesAt72Bytes() {
        String vietnamese = "ố".repeat(30) + "1"; // 31 ký tự nhưng 91 byte UTF-8

        assertThat(vietnamese.length()).isLessThan(72);
        assertThat(validator.isValid(vietnamese, null)).isFalse();
        assertThat(validator.isValid("a".repeat(71) + "1", null)).isTrue();
        assertThat(validator.isValid("a".repeat(72) + "1", null)).isFalse();
    }
}
