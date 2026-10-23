package org.testar.evidence;

import java.util.Map;

final class OracleReferences {
    private OracleReferences() {
    }

    static void validate(OracleResult check, Map<String, Observation> observations,
                         Map<String, ActionExecutionRecord> executions) {
        OracleTarget target = check.oracleTarget();
        if (target instanceof OracleTarget.Observation value) {
            observation(value.observation(), check.testSequence(), "oracle_target.observation", observations);
        } else if (target instanceof OracleTarget.StateProperty value) {
            observation(value.observation(), check.testSequence(), "oracle_target.observation", observations);
        } else if (target instanceof OracleTarget.Widget value) {
            widget(value.observation(), value.widget(), check.testSequence(), observations);
        } else if (target instanceof OracleTarget.WidgetProperty value) {
            widget(value.observation(), value.widget(), check.testSequence(), observations);
        } else if (target instanceof OracleTarget.ActionExecution value) {
            ActionExecutionRecord record = executions.get(value.actionExecution());
            require(record != null && record.actionExecution().testSequence().equals(check.testSequence()), "oracle_target.action_execution");
        } else if (target instanceof OracleTarget.ObservationRange value) {
            Observation start = observation(value.firstObservation(), check.testSequence(), "oracle_target.first_observation", observations);
            Observation end = observation(value.lastObservation(), check.testSequence(), "oracle_target.last_observation", observations);
            require(end.observationIndex().compareTo(start.observationIndex()) >= 0, "oracle_target.last_observation");
        }
    }

    private static Observation observation(String id, String run, String field, Map<String, Observation> observations) {
        Observation value = observations.get(id);
        require(value != null && value.testSequence().equals(run), field);
        return value;
    }

    private static void widget(String id, String widget, String run, Map<String, Observation> observations) {
        Observation value = observation(id, run, "oracle_target.observation", observations);
        require(value.recordedState().widgets().stream().anyMatch(item -> item.id().equals(widget)), "oracle_target.widget");
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new EvidenceException("invalid_oracle_target", field, "Evaluation has an invalid reference: " + field);
        }
    }
}
