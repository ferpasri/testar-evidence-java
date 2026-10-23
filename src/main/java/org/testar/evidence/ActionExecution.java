package org.testar.evidence;

public record ActionExecution(String id, String testSequence, String sourceObservation, String actionDescription, String targetWidget, Properties parameters) {

    public ActionExecution {
        Validation.text(id, "Action execution ID");
        Validation.text(testSequence, "Test sequence ID");
        Validation.text(sourceObservation, "Source observation");
        Validation.text(actionDescription, "Action description ID");
        Validation.present(parameters, "Parameters");
        if (targetWidget != null) {
            Validation.text(targetWidget, "Target widget");
        }
    }
}
