package org.testar.statemodel;

import java.util.Map;

final class CheckReferences {
    private CheckReferences() {
    }

    static void validate(CheckResult check, Map<String, Observation> observations,
                         Map<String, ExecutionRecord> executions) {
        CheckTarget target = check.target();
        if (target instanceof CheckTarget.Observation value) {
            observation(value.observation(), check.run(), "target.observation", observations);
        } else if (target instanceof CheckTarget.StateProperty value) {
            observation(value.observation(), check.run(), "target.observation", observations);
        } else if (target instanceof CheckTarget.Widget value) {
            widget(value.observation(), value.widget(), check.run(), observations);
        } else if (target instanceof CheckTarget.WidgetProperty value) {
            widget(value.observation(), value.widget(), check.run(), observations);
        } else if (target instanceof CheckTarget.Execution value) {
            ExecutionRecord record = executions.get(value.execution());
            require(record != null && record.execution().run().equals(check.run()), "target.execution");
        } else if (target instanceof CheckTarget.Sequence value) {
            Observation start = observation(value.fromObservation(), check.run(), "target.from_observation", observations);
            Observation end = observation(value.toObservation(), check.run(), "target.to_observation", observations);
            require(end.sequence().compareTo(start.sequence()) >= 0, "target.to_observation");
        }
    }

    private static Observation observation(String id, String run, String field, Map<String, Observation> observations) {
        Observation value = observations.get(id);
        require(value != null && value.run().equals(run), field);
        return value;
    }

    private static void widget(String id, String widget, String run, Map<String, Observation> observations) {
        Observation value = observation(id, run, "target.observation", observations);
        require(value.state().widgets().stream().anyMatch(item -> item.id().equals(widget)), "target.widget");
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new ModelException("invalid_check_target", field, "Evaluation has an invalid reference: " + field);
        }
    }
}
