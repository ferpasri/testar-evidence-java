package org.testar.evidence;

public record EvidenceEvent(int schemaVersion, String id, Event event) {
    public EvidenceEvent {
        Validation.require(schemaVersion == Manifest.SCHEMA_VERSION, "invalid_record",
            "Unsupported schema version: " + schemaVersion);
        Validation.text(id, "Event ID");
        Validation.present(event, "Event");
    }

    public EvidenceEvent(String id, Event event) {
        this(Manifest.SCHEMA_VERSION, id, event);
    }
}
