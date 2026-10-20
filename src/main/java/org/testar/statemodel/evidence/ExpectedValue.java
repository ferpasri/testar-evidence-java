package org.testar.statemodel.evidence;

import com.fasterxml.jackson.databind.JsonNode;

public record ExpectedValue(boolean supplied, JsonNode value) {
    public ExpectedValue {
        Validation.require(supplied == (value != null), "invalid_record", "A supplied expectation requires a JSON value");
        value = value == null ? null : EvidenceJson.normalize(value);
    }

    public static ExpectedValue unspecified() {
        return new ExpectedValue(false, null);
    }

    public static ExpectedValue of(Object value) {
        return new ExpectedValue(true, EvidenceJson.tree(value));
    }

    @Override
    public JsonNode value() {
        return value == null ? null : value.deepCopy();
    }
}
