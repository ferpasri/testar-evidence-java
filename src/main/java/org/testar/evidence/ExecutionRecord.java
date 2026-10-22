package org.testar.evidence;

public record ExecutionRecord(Execution execution, ExecutionStatus status, String result, String error) {
}
