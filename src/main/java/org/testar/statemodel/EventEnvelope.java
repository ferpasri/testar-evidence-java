package org.testar.statemodel;

public record EventEnvelope(int schemaVersion, String id, Event event) {
    public EventEnvelope {
        Validation.require(schemaVersion == Manifest.SCHEMA_VERSION, "invalid_record",
            "Unsupported schema version: " + schemaVersion);
        Validation.text(id, "Event ID");
        Validation.present(event, "Event");
    }

    public EventEnvelope(String id, Event event) {
        this(Manifest.SCHEMA_VERSION, id, event);
    }
}
