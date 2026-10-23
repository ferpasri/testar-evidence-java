package org.testar.evidence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "target_type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OracleTarget.TestRun.class, name = "test_run"),
    @JsonSubTypes.Type(value = OracleTarget.TestSequence.class, name = "test_sequence"),
    @JsonSubTypes.Type(value = OracleTarget.Observation.class, name = "observation"),
    @JsonSubTypes.Type(value = OracleTarget.StateProperty.class, name = "state_property"),
    @JsonSubTypes.Type(value = OracleTarget.Widget.class, name = "widget"),
    @JsonSubTypes.Type(value = OracleTarget.WidgetProperty.class, name = "widget_property"),
    @JsonSubTypes.Type(value = OracleTarget.ActionExecution.class, name = "action_execution"),
    @JsonSubTypes.Type(value = OracleTarget.ObservationRange.class, name = "observation_range")
})
public sealed interface OracleTarget {
    record TestRun() implements OracleTarget {
    }

    record TestSequence() implements OracleTarget {
    }

    record Observation(String observation) implements OracleTarget {
        public Observation {
            Validation.text(observation, "Observation");
        }
    }

    record StateProperty(String observation, String property) implements OracleTarget {
        public StateProperty {
            Validation.text(observation, "Observation");
            Validation.text(property, "Property");
        }
    }

    record Widget(String observation, String widget) implements OracleTarget {
        public Widget {
            Validation.text(observation, "Observation");
            Validation.text(widget, "Widget");
        }
    }

    record WidgetProperty(String observation, String widget, String property) implements OracleTarget {
        public WidgetProperty {
            Validation.text(observation, "Observation");
            Validation.text(widget, "Widget");
            Validation.text(property, "Property");
        }
    }

    record ActionExecution(String actionExecution) implements OracleTarget {
        public ActionExecution {
            Validation.text(actionExecution, "ActionExecution");
        }
    }

    record ObservationRange(String firstObservation, String lastObservation) implements OracleTarget {
        public ObservationRange {
            Validation.text(firstObservation, "Sequence start");
            Validation.text(lastObservation, "Sequence end");
        }
    }
}
