package org.testar.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum CheckStatus {
    PASSED, FAILED, PENDING, UNKNOWN, ERROR;

    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static CheckStatus fromJson(String value) {
        Validation.require(value != null && value.equals(value.toLowerCase(Locale.ROOT)),
            "invalid_record", "Enum values must use their lowercase wire names");
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
