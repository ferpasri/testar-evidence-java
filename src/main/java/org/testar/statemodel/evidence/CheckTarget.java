package org.testar.statemodel.evidence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CheckTarget.Run.class, name = "run"),
    @JsonSubTypes.Type(value = CheckTarget.Observation.class, name = "observation"),
    @JsonSubTypes.Type(value = CheckTarget.StateProperty.class, name = "state_property"),
    @JsonSubTypes.Type(value = CheckTarget.Widget.class, name = "widget"),
    @JsonSubTypes.Type(value = CheckTarget.WidgetProperty.class, name = "widget_property"),
    @JsonSubTypes.Type(value = CheckTarget.Execution.class, name = "execution"),
    @JsonSubTypes.Type(value = CheckTarget.Sequence.class, name = "sequence")
})
public sealed interface CheckTarget {
    record Run() implements CheckTarget {
    }

    record Observation(String observation) implements CheckTarget {
        public Observation {
            Validation.text(observation, "Observation");
        }
    }

    record StateProperty(String observation, String property) implements CheckTarget {
        public StateProperty {
            Validation.text(observation, "Observation");
            Validation.text(property, "Property");
        }
    }

    record Widget(String observation, String widget) implements CheckTarget {
        public Widget {
            Validation.text(observation, "Observation");
            Validation.text(widget, "Widget");
        }
    }

    record WidgetProperty(String observation, String widget, String property) implements CheckTarget {
        public WidgetProperty {
            Validation.text(observation, "Observation");
            Validation.text(widget, "Widget");
            Validation.text(property, "Property");
        }
    }

    record Execution(String execution) implements CheckTarget {
        public Execution {
            Validation.text(execution, "Execution");
        }
    }

    record Sequence(String fromObservation, String toObservation) implements CheckTarget {
        public Sequence {
            Validation.text(fromObservation, "Sequence start");
            Validation.text(toObservation, "Sequence end");
        }
    }
}
