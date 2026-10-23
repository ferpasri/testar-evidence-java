package org.testar.evidence;

public record TestRun(String id, String producer, String sourceRun, Properties properties) {
    public TestRun {
        Validation.text(id, "TestRun ID");
        Validation.text(producer, "Producer");
        Validation.text(sourceRun, "Source run");
        Validation.present(properties, "Properties");
    }
}
