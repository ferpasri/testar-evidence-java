package org.testar.evidence;

public record ActionExecutionRecord(ActionExecution actionExecution, ActionExecutionStatus status, ActionExecutionResult executionResult, String resultingObservation) {
}
