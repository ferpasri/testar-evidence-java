package org.testar.evidence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ActionExecutionStatus {
    AWAITING_EXECUTION_RESULT, AWAITING_RESULTING_OBSERVATION, FAILED, COMPLETED, INCOMPLETE;

    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ActionExecutionStatus fromJson(String value) {
        Validation.require(value != null && value.equals(value.toLowerCase(Locale.ROOT)),
            "invalid_record", "Enum values must use their lowercase wire names");
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
