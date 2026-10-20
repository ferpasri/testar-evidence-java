package org.testar.statemodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CheckReading.Value.class, name = "value"),
    @JsonSubTypes.Type(value = CheckReading.Absent.class, name = "absent"),
    @JsonSubTypes.Type(value = CheckReading.Unknown.class, name = "unknown")
})
public sealed interface CheckReading {
    record Value(@JsonProperty(required = true) JsonNode value) implements CheckReading {
        public Value {
            value = EvidenceJson.normalize(Validation.present(value, "Reading value"));
        }

        @Override
        public JsonNode value() {
            return value.deepCopy();
        }
    }

    record Absent() implements CheckReading {
    }

    record Unknown() implements CheckReading {
    }
}
