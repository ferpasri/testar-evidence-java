package org.testar.statemodel;

public record ExecutionRecord(Execution execution, ExecutionStatus status, String result, String error) {
}
