package org.testar.evidence;

public record ActionExecutionResult(String actionExecution, ActionOutcome outcome, String error) {

    public ActionExecutionResult {
        Validation.text(actionExecution, "Action execution ID");
        Validation.present(outcome, "Outcome");
        Validation.require((outcome == ActionOutcome.RETURNED) == (error == null), "invalid_record",
            "Returned requires a null error; an error outcome requires its reported message");
    }
}
