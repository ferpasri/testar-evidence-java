package org.testar.evidence;

import java.math.BigInteger;
import java.util.Objects;

final class Validation {
    private static final BigInteger MAX_UNSIGNED_LONG = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private Validation() {
    }

    static String text(String value, String field) {
        require(value != null && !value.isBlank() && value.codePoints().noneMatch(Character::isISOControl),
            "invalid_record", field + " must be nonblank text without control characters");
        return value;
    }

    static <T> T present(T value, String field) {
        if (value == null) {
            throw new ModelException("invalid_record", field + " is required");
        }
        return value;
    }

    static void unsigned(BigInteger value, String field) {
        present(value, field);
        require(value.signum() >= 0 && value.compareTo(MAX_UNSIGNED_LONG) <= 0,
            "invalid_record", field + " must be an unsigned 64-bit integer");
    }

    static void require(boolean condition, String code, String message) {
        if (!condition) {
            throw new ModelException(code, message);
        }
    }

    static void same(Object previous, Object value, String description) {
        require(previous == null || Objects.equals(previous, value), "conflict", description);
    }
}
