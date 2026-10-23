package org.testar.evidence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "value_status")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ObservedValue.Value.class, name = "value"),
    @JsonSubTypes.Type(value = ObservedValue.Absent.class, name = "absent"),
    @JsonSubTypes.Type(value = ObservedValue.Unknown.class, name = "unknown")
})
public sealed interface ObservedValue {
    record Value(@JsonProperty(required = true) JsonNode value) implements ObservedValue {
        public Value {
            value = EvidenceJson.normalize(Validation.present(value, "Reading value"));
        }

        @Override
        public JsonNode value() {
            return value.deepCopy();
        }
    }

    record Absent() implements ObservedValue {
    }

    record Unknown() implements ObservedValue {
    }
}
