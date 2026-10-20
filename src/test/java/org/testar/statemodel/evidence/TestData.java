package org.testar.statemodel.evidence;

import java.util.List;
import java.util.Map;

final class TestData {
    private TestData() {
    }

    static StateModel model() {
        StateModel model = new StateModel(new Manifest("sample:model"));
        model.apply(new EventEnvelope("start", new Event.RunStarted(new Run("run", "sample", "session", Properties.empty()))));
        model.apply(new EventEnvelope("define-activate", new Event.ActionDefined(new ActionDefinition("activate", "activate", Properties.empty()))));
        model.apply(new EventEnvelope("define-key", new Event.ActionDefined(new ActionDefinition("key", "key", Properties.empty()))));
        model.apply(new EventEnvelope("observe-1", new Event.Observed(observation("o1", 1))));
        return model;
    }

    static Observation observation(String id, long sequence) {
        ConcreteState state = new ConcreteState("snapshot", Properties.of(Map.of("ui.title", "Example")),
            List.of(new Widget("button", null, Properties.of(Map.of("ui.text", "Continue"))),
                new Widget("link", null, Properties.of(Map.of("ui.text", "Help")))), CaptureScope.COMPLETE);
        return new Observation(id, "run", sequence, sequence * 1000, state,
            List.of(new ActionCandidate("activate", "button"), new ActionCandidate("activate", "link"),
                new ActionCandidate("key", null)), CandidateScope.GENERATED, List.of());
    }

    static Execution execution(String id, String source) {
        return new Execution(id, "run", source, "activate", "button", Properties.of(Map.of("x", 10)));
    }

    static void select(StateModel model) {
        model.apply(new EventEnvelope("select", new Event.ActionSelected(execution("execution", "o1"))));
    }

    static void submit(StateModel model) {
        select(model);
        model.apply(new EventEnvelope("submit", new Event.ActionSubmitted("execution", true, null)));
    }

    static CheckResult check(CheckTarget target) {
        return new CheckResult("run", "Condition", new CheckRule("rule", "1", null), target,
            CheckStatus.PENDING, ExpectedValue.unspecified(), null, Properties.empty());
    }
}
