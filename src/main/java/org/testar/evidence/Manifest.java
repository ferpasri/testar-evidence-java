package org.testar.evidence;

public record Manifest(int schemaVersion, String evidenceId) {

    public static final int SCHEMA_VERSION = 3;

    public Manifest {
        Validation.require(schemaVersion == SCHEMA_VERSION, "invalid_record", "Unsupported schema version: " + schemaVersion);
        Validation.text(evidenceId, "Evidence ID");
    }
    public Manifest(String evidenceId) {
        this(SCHEMA_VERSION, evidenceId);
    }
}
