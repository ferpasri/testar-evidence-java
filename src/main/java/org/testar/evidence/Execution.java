package org.testar.evidence;

public record Execution(String id, String run, String source, String action, String target, Properties parameters) {
    public Execution {
        Validation.text(id, "Execution ID");
        Validation.text(run, "Run ID");
        Validation.text(source, "Source observation");
        Validation.text(action, "Action ID");
        Validation.present(parameters, "Parameters");
        if (target != null) {
            Validation.text(target, "Target widget");
        }
    }
}
