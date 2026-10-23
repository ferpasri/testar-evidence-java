package org.testar.evidence;

import java.util.List;
import java.util.Map;

final class TestData {
    private TestData() {
    }

    static ReconstructedEvidence evidence() {
        ReconstructedEvidence evidence = new ReconstructedEvidence(new Manifest("sample:evidence"));
        evidence.apply(new EvidenceEvent("start", new Event.TestRunStarted(new TestRun("test-run", "sample", "session", Properties.empty()))));
        evidence.apply(new EvidenceEvent("sequence-start", new Event.TestSequenceStarted(new TestSequence("run", "test-run", 1, "1", Properties.empty()))));
        evidence.apply(new EvidenceEvent("define-activate", new Event.ActionDescribed(new ActionDescription("activate", "activate", Properties.empty()))));
        evidence.apply(new EvidenceEvent("define-key", new Event.ActionDescribed(new ActionDescription("key", "key", Properties.empty()))));
        evidence.apply(new EvidenceEvent("observe-1", new Event.ObservationRecorded(observation("o1", 1))));
        return evidence;
    }

    static Observation observation(String id, long sequence) {
        RecordedState state = new RecordedState("snapshot", Properties.of(Map.of("ui.title", "Example")),
            List.of(new Widget("button", null, Properties.of(Map.of("ui.text", "Continue"))),
                new Widget("link", null, Properties.of(Map.of("ui.text", "Help")))), CaptureScope.COMPLETE);
        return new Observation(id, "run", sequence, sequence * 1000, state,
            List.of(new AvailableAction("activate", "button"), new AvailableAction("activate", "link"),
                new AvailableAction("key", null)), AvailableActionScope.GENERATED, List.of());
    }

    static ActionExecution actionExecution(String id, String source) {
        return new ActionExecution(id, "run", source, "activate", "button", Properties.of(Map.of("x", 10)));
    }

    static void select(ReconstructedEvidence evidence) {
        evidence.apply(new EvidenceEvent("select", new Event.ActionSelected(actionExecution("execution", "o1"))));
    }

    static void submit(ReconstructedEvidence evidence) {
        select(evidence);
        evidence.apply(new EvidenceEvent("submit", new Event.ActionExecutionReported(new ActionExecutionResult("execution", ActionOutcome.RETURNED, null))));
    }

    static OracleResult check(OracleTarget target) {
        return new OracleResult("test-run", "run", "Condition", new Oracle("rule", "1", null), target,
            OracleStatus.PENDING, ExpectedValue.unspecified(), null, null, Properties.empty());
    }
}
