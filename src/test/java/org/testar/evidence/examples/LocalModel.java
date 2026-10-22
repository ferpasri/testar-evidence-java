package org.testar.evidence.examples;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.testar.evidence.ActionCandidate;
import org.testar.evidence.ActionDefinition;
import org.testar.evidence.CandidateScope;
import org.testar.evidence.CaptureScope;
import org.testar.evidence.CheckReading;
import org.testar.evidence.CheckResult;
import org.testar.evidence.CheckRule;
import org.testar.evidence.CheckStatus;
import org.testar.evidence.CheckTarget;
import org.testar.evidence.ConcreteState;
import org.testar.evidence.Event;
import org.testar.evidence.EvidenceEvent;
import org.testar.evidence.EvidenceJson;
import org.testar.evidence.Execution;
import org.testar.evidence.ExpectedValue;
import org.testar.evidence.JsonlStore;
import org.testar.evidence.Manifest;
import org.testar.evidence.Observation;
import org.testar.evidence.Properties;
import org.testar.evidence.Run;
import org.testar.evidence.Widget;

public final class LocalModel {
    private LocalModel() {
    }

    public static void main(String[] arguments) throws Exception {
        Path parent = arguments.length > 0 ? Path.of(arguments[0]) : Path.of("target", "traces");
        String sut = arguments.length > 1 ? arguments[1] : "demo";
        String objective = arguments.length > 2 ? arguments[2] : "submit-form";
        recordTrace(parent, sut, objective);
    }

    public static Path recordTrace(Path parent, String sut, String objective) throws Exception {
        Path directory = TraceDirectories.create(parent, sut, objective);
        String identity = UUID.randomUUID().toString();
        String run = "example:run:" + identity;
        String action = run + ":activate";
        String before = run + ":before";
        String after = run + ":after";
        String execution = run + ":execution";
        try (JsonlStore store = JsonlStore.create(directory, new Manifest("example:model:" + identity))) {
            // An adapter supplies real captures; this example constructs demonstration records.
            store.record(new EvidenceEvent(run + ":start", new Event.RunStarted(
                new Run(run, "example", identity,
                    Properties.of(Map.of("example.sut", sut, "example.test_objective", objective))))));
            store.record(new EvidenceEvent(run + ":define", new Event.ActionDefined(
                new ActionDefinition(action, "activate", Properties.empty()))));

            // Both buttons are candidates, although only Continue is selected.
            store.record(new EvidenceEvent(run + ":before", new Event.Observed(observation(run, before, 1, "Ready", action))));
            Execution selected = new Execution(execution, run, before, action, "continue", Properties.empty());
            store.record(new EvidenceEvent(run + ":select", new Event.ActionSelected(selected)));
            store.record(new EvidenceEvent(run + ":submit", new Event.ActionSubmitted(execution, true, null)));
            store.record(new EvidenceEvent(run + ":after", new Event.Observed(observation(run, after, 2, "Finished", action))));
            store.record(new EvidenceEvent(run + ":result", new Event.ActionResult(execution, after)));

            // Capturing text and evaluating it are separate evidence records.
            CheckResult check = new CheckResult(run, "Status text is Finished",
                new CheckRule("example:status-text", "1", null),
                new CheckTarget.WidgetProperty(after, "status", "ui.text"), CheckStatus.PASSED,
                ExpectedValue.of("Finished"), new CheckReading.Value(EvidenceJson.tree("Finished")), Properties.empty());
            store.record(new EvidenceEvent(run + ":check", new Event.Checked(check)));
            store.record(new EvidenceEvent(run + ":finish", new Event.RunFinished(run, "completed")));
            System.out.println("Evidence: " + directory.toAbsolutePath());
            System.out.println("Observations: " + store.model().observations().size());
            System.out.println("Completed actions: " + store.model().links().size());
        }
        return directory;
    }

    private static Observation observation(String run, String id, long sequence, String text, String action) {
        List<Widget> widgets = List.of(
            new Widget("continue", null, Properties.of(Map.of("ui.role", "button", "ui.text", "Continue"))),
            new Widget("help", null, Properties.of(Map.of("ui.role", "button", "ui.text", "Help"))),
            new Widget("status", null, Properties.of(Map.of("ui.role", "text", "ui.text", text))));
        ConcreteState state = new ConcreteState(id + ":snapshot", Properties.empty(), widgets, CaptureScope.COMPLETE);
        return new Observation(id, run, sequence, sequence * 1000, state,
            List.of(new ActionCandidate(action, "continue"), new ActionCandidate(action, "help")),
            CandidateScope.GENERATED, List.of());
    }
}
