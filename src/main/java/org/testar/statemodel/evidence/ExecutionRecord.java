package org.testar.statemodel.evidence;

public record ExecutionRecord(Execution execution, ExecutionStatus status, String result, String error) {
}
