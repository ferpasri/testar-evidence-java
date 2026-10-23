package org.testar.evidence;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record Observation(String id, String testSequence, BigInteger observationIndex, BigInteger timestampMicros,
                          RecordedState recordedState, List<AvailableAction> availableActions,
                          AvailableActionScope availableActionScope, List<ArtifactReference> artifacts) {
    public Observation {
        Validation.text(id, "Observation ID");
        Validation.text(testSequence, "TestRun ID");
        Validation.unsigned(observationIndex, "Sequence");
        Validation.unsigned(timestampMicros, "Timestamp");
        Validation.present(recordedState, "State");
        availableActions = List.copyOf(Validation.present(availableActions, "Candidates"));
        artifacts = List.copyOf(Validation.present(artifacts, "Artifacts"));
        Validation.present(availableActionScope, "Available-action scope");
        Set<String> widgets = new HashSet<>();
        recordedState.widgets().forEach(widget -> widgets.add(widget.id()));
        Set<AvailableAction> uniqueAvailableActions = new HashSet<>();
        for (AvailableAction availableAction : availableActions) {
            Validation.require(availableAction.targetWidget() == null || widgets.contains(availableAction.targetWidget()),
                "unknown_reference", "Unknown available-action widget: " + availableAction.targetWidget());
            Validation.require(uniqueAvailableActions.add(availableAction), "conflict", "Duplicate available action");
        }
        Set<String> artifactIds = new HashSet<>();
        for (ArtifactReference artifact : artifacts) {
            Validation.require(artifactIds.add(artifact.id()), "conflict", "Duplicate artifact ID");
        }
    }

    public Observation(String id, String testSequence, long observationIndex, long timestampMicros, RecordedState recordedState,
                       List<AvailableAction> availableActions, AvailableActionScope availableActionScope, List<ArtifactReference> artifacts) {
        this(id, testSequence, BigInteger.valueOf(observationIndex), BigInteger.valueOf(timestampMicros),
            recordedState, availableActions, availableActionScope, artifacts);
    }
}
