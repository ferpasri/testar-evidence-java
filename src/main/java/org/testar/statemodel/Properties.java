package org.testar.statemodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

public final class Properties {
    private final ObjectNode values;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Properties(JsonNode values) {
        Validation.require(values != null && values.isObject(), "invalid_record", "Properties must be a JSON object");
        values.fieldNames().forEachRemaining(name -> Validation.text(name, "Property name"));
        this.values = (ObjectNode) EvidenceJson.normalize(values);
    }

    public static Properties empty() {
        return new Properties(EvidenceJson.object());
    }

    public static Properties of(Map<String, ?> values) {
        return new Properties(EvidenceJson.tree(values));
    }

    public boolean contains(String name) {
        return values.has(name);
    }

    public JsonNode get(String name) {
        JsonNode value = values.get(name);
        return value == null ? null : value.deepCopy();
    }

    @JsonValue
    public ObjectNode asJson() {
        return values.deepCopy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Properties properties && values.equals(properties.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
