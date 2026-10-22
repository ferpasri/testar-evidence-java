package org.testar.evidence;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record Observation(String id, String run, BigInteger sequence, BigInteger timestampMicros,
                          ConcreteState state, List<ActionCandidate> candidates,
                          CandidateScope candidateScope, List<ArtifactReference> artifacts) {
    public Observation {
        Validation.text(id, "Observation ID");
        Validation.text(run, "Run ID");
        Validation.unsigned(sequence, "Sequence");
        Validation.unsigned(timestampMicros, "Timestamp");
        Validation.present(state, "State");
        candidates = List.copyOf(Validation.present(candidates, "Candidates"));
        artifacts = List.copyOf(Validation.present(artifacts, "Artifacts"));
        candidateScope = candidateScope == null ? CandidateScope.UNKNOWN : candidateScope;
        Set<String> widgets = new HashSet<>();
        state.widgets().forEach(widget -> widgets.add(widget.id()));
        Set<ActionCandidate> uniqueCandidates = new HashSet<>();
        for (ActionCandidate candidate : candidates) {
            Validation.require(candidate.target() == null || widgets.contains(candidate.target()),
                "unknown_reference", "Unknown candidate widget: " + candidate.target());
            Validation.require(uniqueCandidates.add(candidate), "conflict", "Duplicate action candidate");
        }
        Set<String> artifactIds = new HashSet<>();
        for (ArtifactReference artifact : artifacts) {
            Validation.require(artifactIds.add(artifact.id()), "conflict", "Duplicate artifact ID");
        }
    }

    public Observation(String id, String run, long sequence, long timestampMicros, ConcreteState state,
                       List<ActionCandidate> candidates, CandidateScope candidateScope, List<ArtifactReference> artifacts) {
        this(id, run, BigInteger.valueOf(sequence), BigInteger.valueOf(timestampMicros),
            state, candidates, candidateScope, artifacts);
    }
}
