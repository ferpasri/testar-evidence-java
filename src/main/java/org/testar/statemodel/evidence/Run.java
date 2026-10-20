package org.testar.statemodel.evidence;

public record Run(String id, String producer, String sourceRun, Properties properties) {
    public Run {
        Validation.text(id, "Run ID");
        Validation.text(producer, "Producer");
        Validation.text(sourceRun, "Source run");
        Validation.present(properties, "Properties");
    }
}
