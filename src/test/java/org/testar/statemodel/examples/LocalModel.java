package org.testar.statemodel.examples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.testar.statemodel.ActionCandidate;
import org.testar.statemodel.ActionDefinition;
import org.testar.statemodel.CandidateScope;
import org.testar.statemodel.CaptureScope;
import org.testar.statemodel.CheckReading;
import org.testar.statemodel.CheckResult;
import org.testar.statemodel.CheckRule;
import org.testar.statemodel.CheckStatus;
import org.testar.statemodel.CheckTarget;
import org.testar.statemodel.ConcreteState;
import org.testar.statemodel.Event;
import org.testar.statemodel.EventEnvelope;
import org.testar.statemodel.EvidenceJson;
import org.testar.statemodel.Execution;
import org.testar.statemodel.ExpectedValue;
import org.testar.statemodel.JsonlStore;
import org.testar.statemodel.Manifest;
import org.testar.statemodel.Observation;
import org.testar.statemodel.Properties;
import org.testar.statemodel.Run;
import org.testar.statemodel.Widget;

public final class LocalModel {
    private LocalModel() {
    }

    public static void main(String[] arguments) throws Exception {
        Path directory = arguments.length == 0 ? Path.of("target", "example-model") : Path.of(arguments[0]);
        try (JsonlStore store = Files.exists(directory.resolve("manifest.json"))
            ? JsonlStore.open(directory) : JsonlStore.create(directory, new Manifest("example:model"))) {
            // An adapter supplies actual observations; this example constructs a small demonstration.
            store.record(new EventEnvelope("example:start", new Event.RunStarted(
                new Run("example:run", "example", "session-1", Properties.empty()))));
            store.record(new EventEnvelope("example:activate", new Event.ActionDefined(
                new ActionDefinition("example:activate", "activate", Properties.empty()))));

            // Both buttons are candidates, although only Continue is selected.
            store.record(new EventEnvelope("example:before", new Event.Observed(observation("example:before", 1, "Ready"))));
            Execution selected = new Execution("example:execution", "example:run", "example:before",
                "example:activate", "continue", Properties.empty());
            store.record(new EventEnvelope("example:select", new Event.ActionSelected(selected)));
            store.record(new EventEnvelope("example:submit", new Event.ActionSubmitted("example:execution", true, null)));

            // A later observation supplies the result of the accepted action.
            store.record(new EventEnvelope("example:after", new Event.Observed(observation("example:after", 2, "Finished"))));
            store.record(new EventEnvelope("example:result", new Event.ActionResult("example:execution", "example:after")));

            // Capturing text and evaluating it are separate records.
            CheckResult check = new CheckResult("example:run", "Status text is Finished",
                new CheckRule("example:status-text", "1", null),
                new CheckTarget.WidgetProperty("example:after", "status", "ui.text"), CheckStatus.PASSED,
                ExpectedValue.of("Finished"), new CheckReading.Value(EvidenceJson.tree("Finished")), Properties.empty());
            store.record(new EventEnvelope("example:check", new Event.Checked(check)));
            store.record(new EventEnvelope("example:finish", new Event.RunFinished("example:run", "completed")));
            System.out.println("Model: " + directory.toAbsolutePath());
            System.out.println("Observations: " + store.model().observations().size());
            System.out.println("Completed executions: " + store.model().links().size());
        }
    }

    private static Observation observation(String id, long sequence, String text) {
        List<Widget> widgets = List.of(
            new Widget("continue", null, Properties.of(Map.of("ui.role", "button", "ui.text", "Continue"))),
            new Widget("help", null, Properties.of(Map.of("ui.role", "button", "ui.text", "Help"))),
            new Widget("status", null, Properties.of(Map.of("ui.role", "text", "ui.text", text))));
        ConcreteState state = new ConcreteState(id + ":snapshot", Properties.empty(), widgets, CaptureScope.COMPLETE);
        return new Observation(id, "example:run", sequence, sequence * 1000, state,
            List.of(new ActionCandidate("example:activate", "continue"), new ActionCandidate("example:activate", "help")),
            CandidateScope.GENERATED, List.of());
    }
}
