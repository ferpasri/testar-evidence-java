package org.testar.evidence.examples;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.testar.evidence.AvailableAction;
import org.testar.evidence.ActionDescription;
import org.testar.evidence.AvailableActionScope;
import org.testar.evidence.CaptureScope;
import org.testar.evidence.ObservedValue;
import org.testar.evidence.OracleResult;
import org.testar.evidence.Oracle;
import org.testar.evidence.OracleStatus;
import org.testar.evidence.OracleTarget;
import org.testar.evidence.RecordedState;
import org.testar.evidence.Event;
import org.testar.evidence.EvidenceEvent;
import org.testar.evidence.EvidenceJson;
import org.testar.evidence.ActionExecution;
import org.testar.evidence.ExpectedValue;
import org.testar.evidence.JsonlStore;
import org.testar.evidence.Manifest;
import org.testar.evidence.Observation;
import org.testar.evidence.Properties;
import org.testar.evidence.TestRun;
import org.testar.evidence.TestSequence;
import org.testar.evidence.ActionExecutionResult;
import org.testar.evidence.ActionOutcome;
import org.testar.evidence.Widget;

public final class RecordEvidence {
    private RecordEvidence() {
    }

    public static void main(String[] arguments) throws Exception {
        Path parent = arguments.length > 0 ? Path.of(arguments[0]) : Path.of("target", "traces");
        String sut = arguments.length > 1 ? arguments[1] : "demo";
        String testObjective = arguments.length > 2 ? arguments[2] : null;
        String origin = arguments.length > 3 ? arguments[3] : null;
        recordTrace(parent, sut, testObjective, origin);
    }

    public static Path recordTrace(Path parent, String sut, String testObjective) throws Exception {
        return recordTrace(parent, sut, testObjective, null);
    }

    public static Path recordTrace(Path parent, String sut, String testObjective, String origin) throws Exception {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("example.sut", sut);
        if (testObjective != null) {
            if (testObjective.isBlank()) {
                throw new IllegalArgumentException("Test objective must be nonblank when supplied");
            }
            properties.put("evidence.test_objective", testObjective);
        }
        if (origin != null) {
            if (origin.isBlank()) {
                throw new IllegalArgumentException("Origin must be nonblank when supplied");
            }
            properties.put("evidence.origin", origin);
        }
        Path directory = TraceDirectories.create(parent, sut, testObjective == null ? "run" : testObjective);
        String identity = UUID.randomUUID().toString();
        String run = "example:run:" + identity;
        String testSequence = run + ":sequence:1";
        String action = run + ":activate";
        String before = run + ":before";
        String after = run + ":after";
        String execution = run + ":execution";
        try (JsonlStore store = JsonlStore.create(directory, new Manifest("example:evidence:" + identity))) {
            // An adapter supplies real captures; this example constructs demonstration records.
            store.record(new EvidenceEvent(run + ":start", new Event.TestRunStarted(
                new TestRun(run, "example", identity,
                    Properties.of(properties)))));
            store.record(new EvidenceEvent(run + ":sequence-start", new Event.TestSequenceStarted(
                new TestSequence(testSequence, run, 1, "1", Properties.empty()))));
            store.record(new EvidenceEvent(run + ":define", new Event.ActionDescribed(
                new ActionDescription(action, "activate", Properties.empty()))));

            // Both buttons are available actions, although only Continue is selected.
            store.record(new EvidenceEvent(run + ":before", new Event.ObservationRecorded(observation(testSequence, before, 1, "Ready", action))));
            ActionExecution selected = new ActionExecution(execution, testSequence, before, action, "continue", Properties.empty());
            store.record(new EvidenceEvent(run + ":select", new Event.ActionSelected(selected)));
            store.record(new EvidenceEvent(run + ":submit", new Event.ActionExecutionReported(new ActionExecutionResult(execution, ActionOutcome.RETURNED, null))));
            store.record(new EvidenceEvent(run + ":after", new Event.ObservationRecorded(observation(testSequence, after, 2, "Finished", action))));
            store.record(new EvidenceEvent(run + ":result", new Event.TransitionRecorded(execution, after)));

            // Capturing text and evaluating it are separate evidence records.
            OracleResult check = new OracleResult(run, testSequence, "Status text is Finished",
                new Oracle("example:status-text", "1", null),
                new OracleTarget.WidgetProperty(after, "status", "ui.text"), OracleStatus.PASSED,
                ExpectedValue.of("Finished"), new ObservedValue.Value(EvidenceJson.tree("Finished")), null, Properties.empty());
            store.record(new EvidenceEvent(run + ":check", new Event.OracleResultRecorded(check)));
            store.record(new EvidenceEvent(run + ":sequence-finish", new Event.TestSequenceFinished(testSequence, "completed")));
            store.record(new EvidenceEvent(run + ":finish", new Event.TestRunFinished(run, "completed")));
            System.out.println("Evidence: " + directory.toAbsolutePath());
            System.out.println("Observations: " + store.evidence().observations().size());
            System.out.println("Completed actions: " + store.evidence().recordedTransitions().size());
        }
        return directory;
    }

    private static Observation observation(String run, String id, long sequence, String text, String action) {
        List<Widget> widgets = List.of(
            new Widget("continue", null, Properties.of(Map.of("ui.role", "button", "ui.text", "Continue"))),
            new Widget("help", null, Properties.of(Map.of("ui.role", "button", "ui.text", "Help"))),
            new Widget("status", null, Properties.of(Map.of("ui.role", "text", "ui.text", text))));
        RecordedState state = new RecordedState(id + ":snapshot", Properties.empty(), widgets, CaptureScope.COMPLETE);
        return new Observation(id, run, sequence, sequence * 1000, state,
            List.of(new AvailableAction(action, "continue"), new AvailableAction(action, "help")),
            AvailableActionScope.GENERATED, List.of());
    }
}
