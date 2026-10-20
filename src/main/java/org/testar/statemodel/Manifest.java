package org.testar.statemodel;

public record Manifest(int schemaVersion, String model) {
    public static final int SCHEMA_VERSION = 2;

    public Manifest {
        Validation.require(schemaVersion == SCHEMA_VERSION, "invalid_record", "Unsupported schema version: " + schemaVersion);
        Validation.text(model, "Model ID");
    }

    public Manifest(String model) {
        this(SCHEMA_VERSION, model);
    }
}
