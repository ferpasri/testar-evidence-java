package org.testar.evidence;

public record RecordedTransition(String actionExecution, String testSequence, String sourceObservation, String resultingObservation, String actionDescription, String targetWidget) {
}
