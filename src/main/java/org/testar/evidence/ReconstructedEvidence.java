package org.testar.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Recorded facts and relationships held in memory for validation and inspection. */
public final class ReconstructedEvidence {
    private final Manifest manifest;
    private final Map<String, TestRunRecord> testRuns = new TreeMap<>();
    private final Map<String, TestSequenceRecord> testSequences = new TreeMap<>();
    private final Map<String, Observation> observations = new TreeMap<>();
    private final Map<String, RecordedState> recordedStates = new TreeMap<>();
    private final Map<String, ActionDescription> actionDescriptions = new TreeMap<>();
    private final Map<String, ActionExecutionRecord> actionExecutions = new LinkedHashMap<>();
    private final Map<String, EvidenceEvent> events = new LinkedHashMap<>();
    private final List<OracleResult> oracleResults = new ArrayList<>();
    private final List<RecordedTransition> recordedTransitions = new ArrayList<>();
    private final Map<String, Observation> latest = new HashMap<>();
    private final Map<String, String> awaiting = new HashMap<>();

    public ReconstructedEvidence(Manifest manifest) {
        this.manifest = Validation.present(manifest, "Manifest");
    }

    public boolean validate(EvidenceEvent envelope) {
        Validation.present(envelope, "Evidence event");
        EvidenceEvent previous = events.get(envelope.id());
        if (previous != null) {
            Validation.same(previous, envelope, "Conflicting event identity");
            return false;
        }
        Event event = envelope.event();
        if (event instanceof Event.TestRunStarted value) {
            Validation.require(!testRuns.containsKey(value.data().id()), "conflict", "Test run already exists");
        } else if (event instanceof Event.TestSequenceStarted value) {
            TestSequence sequence = value.data();
            TestRunRecord run = testRuns.get(sequence.testRun());
            require(run != null, "invalid_reference", "test_run", "Unknown parent run");
            require(!run.finished(), "invalid_lifecycle", "test_run", "Parent run finished");
            Validation.require(!testSequences.containsKey(sequence.id()), "conflict", "Test sequence already exists");
            require(testSequencesForRun(sequence.testRun()).stream().allMatch(record ->
                record.testSequence().sequenceIndex().compareTo(sequence.sequenceIndex()) < 0),
                "invalid_order", "sequence_index", "Sequence indexes must increase within the run");
        } else if (event instanceof Event.ActionDescribed value) {
            Validation.same(actionDescriptions.get(value.data().id()), value.data(), "Action description content changed");
        } else if (event instanceof Event.ObservationRecorded value) {
            validateObservation(value.data());
        } else if (event instanceof Event.ActionSelected value) {
            validateSelection(value.data());
        } else if (event instanceof Event.ActionExecutionReported value) {
            ActionExecutionRecord record = requireExecution(value.data().actionExecution());
            requireActiveSequence(record.actionExecution().testSequence());
            require(record.status() == ActionExecutionStatus.AWAITING_EXECUTION_RESULT,
                "invalid_lifecycle", "action_execution", "Execution result already reported");
        } else if (event instanceof Event.TransitionRecorded value) {
            validateTransition(value);
        } else if (event instanceof Event.OracleResultRecorded value) {
            validateOracle(value.data());
        } else if (event instanceof Event.TestSequenceFinished value) {
            requireActiveSequence(value.testSequence());
        } else if (event instanceof Event.TestRunFinished value) {
            TestRunRecord run = testRuns.get(value.testRun());
            require(run != null && !run.finished(), "invalid_lifecycle", "test_run", "Test run is not active");
            require(testSequencesForRun(value.testRun()).stream().allMatch(TestSequenceRecord::finished),
                "invalid_lifecycle", "test_run", "Finish the run's sequences first");
        }
        return true;
    }

    public boolean apply(EvidenceEvent envelope) {
        if (!validate(envelope)) {
            return false;
        }
        Event event = envelope.event();
        if (event instanceof Event.TestRunStarted value) {
            testRuns.put(value.data().id(), new TestRunRecord(value.data(), false, null));
        } else if (event instanceof Event.TestSequenceStarted value) {
            testSequences.put(value.data().id(), new TestSequenceRecord(value.data(), false, null));
        } else if (event instanceof Event.ActionDescribed value) {
            actionDescriptions.put(value.data().id(), value.data());
        } else if (event instanceof Event.ObservationRecorded value) {
            Observation observation = value.data();
            observations.put(observation.id(), observation);
            recordedStates.put(observation.recordedState().id(), observation.recordedState());
            latest.put(observation.testSequence(), observation);
        } else if (event instanceof Event.ActionSelected value) {
            ActionExecution action = value.data();
            actionExecutions.put(action.id(), new ActionExecutionRecord(action, ActionExecutionStatus.AWAITING_EXECUTION_RESULT, null, null));
            awaiting.put(action.testSequence(), action.id());
        } else if (event instanceof Event.ActionExecutionReported value) {
            ActionExecutionResult result = value.data();
            ActionExecution action = actionExecutions.get(result.actionExecution()).actionExecution();
            ActionExecutionStatus status = result.outcome() == ActionOutcome.RETURNED
                ? ActionExecutionStatus.AWAITING_RESULTING_OBSERVATION : ActionExecutionStatus.FAILED;
            actionExecutions.put(action.id(), new ActionExecutionRecord(action, status, result, null));
            if (result.outcome() == ActionOutcome.ERROR) {
                awaiting.remove(action.testSequence());
            }
        } else if (event instanceof Event.TransitionRecorded value) {
            ActionExecutionRecord record = actionExecutions.get(value.actionExecution());
            ActionExecution action = record.actionExecution();
            actionExecutions.put(action.id(), new ActionExecutionRecord(action, ActionExecutionStatus.COMPLETED,
                record.executionResult(), value.resultingObservation()));
            awaiting.remove(action.testSequence());
            recordedTransitions.add(new RecordedTransition(action.id(), action.testSequence(), action.sourceObservation(),
                value.resultingObservation(), action.actionDescription(), action.targetWidget()));
        } else if (event instanceof Event.OracleResultRecorded value) {
            oracleResults.add(value.data());
        } else if (event instanceof Event.TestSequenceFinished value) {
            TestSequenceRecord sequence = testSequences.get(value.testSequence());
            testSequences.put(value.testSequence(), new TestSequenceRecord(sequence.testSequence(), true, value.reason()));
            String interrupted = awaiting.remove(value.testSequence());
            if (interrupted != null) {
                ActionExecutionRecord record = actionExecutions.get(interrupted);
                actionExecutions.put(interrupted, new ActionExecutionRecord(record.actionExecution(),
                    ActionExecutionStatus.INCOMPLETE, record.executionResult(), null));
            }
        } else if (event instanceof Event.TestRunFinished value) {
            TestRunRecord run = testRuns.get(value.testRun());
            testRuns.put(value.testRun(), new TestRunRecord(run.testRun(), true, value.reason()));
        }
        events.put(envelope.id(), envelope);
        return true;
    }

    private void validateObservation(Observation observation) {
        requireActiveSequence(observation.testSequence());
        Validation.require(!observations.containsKey(observation.id()), "conflict", "Observation already exists");
        Observation last = latest.get(observation.testSequence());
        require(last == null || observation.observationIndex().compareTo(last.observationIndex()) > 0,
            "invalid_order", "observation_index", "Observation index must increase within the sequence");
        Validation.same(recordedStates.get(observation.recordedState().id()), observation.recordedState(), "Recorded-state content changed");
        for (AvailableAction available : observation.availableActions()) {
            require(actionDescriptions.containsKey(available.actionDescription()), "invalid_reference",
                "available_actions.action_description", "Unknown action description");
        }
    }

    private void validateSelection(ActionExecution execution) {
        requireActiveSequence(execution.testSequence());
        Validation.require(!actionExecutions.containsKey(execution.id()), "conflict", "Execution already exists");
        Observation source = observations.get(execution.sourceObservation());
        require(source != null && source.testSequence().equals(execution.testSequence())
            && Objects.equals(latest.get(execution.testSequence()), source),
            "invalid_reference", "source_observation", "Source must be the sequence's latest observation");
        require(source.availableActions().contains(new AvailableAction(execution.actionDescription(), execution.targetWidget())),
            "invalid_reference", "action_description", "Selected action and target are not available at source");
        require(!awaiting.containsKey(execution.testSequence()), "invalid_lifecycle", "test_sequence", "Sequence has an unfinished execution");
    }

    private void validateTransition(Event.TransitionRecorded transition) {
        ActionExecutionRecord record = requireExecution(transition.actionExecution());
        requireActiveSequence(record.actionExecution().testSequence());
        require(record.status() == ActionExecutionStatus.AWAITING_RESULTING_OBSERVATION,
            "invalid_lifecycle", "action_execution", "Execution is not awaiting an observation");
        Observation source = observations.get(record.actionExecution().sourceObservation());
        Observation result = observations.get(transition.resultingObservation());
        require(result != null && result.testSequence().equals(source.testSequence())
            && result.observationIndex().compareTo(source.observationIndex()) > 0,
            "invalid_transition", "resulting_observation", "Result must be later in the same sequence");
    }

    private void validateOracle(OracleResult result) {
        require(testRuns.containsKey(result.testRun()), "invalid_reference", "test_run", "Unknown test run");
        if (result.oracleSeverity() != null) {
            String label = result.oracleSeverity().label();
            require(label != null && !label.isBlank() && label.codePoints().noneMatch(Character::isISOControl),
                "invalid_oracle_severity", "oracle_severity.label", "Severity label must be nonblank text");
        }
        if (result.oracleTarget() instanceof OracleTarget.TestRun) {
            require(result.testSequence() == null, "invalid_oracle_target", "test_sequence", "Whole-run target has no sequence");
        } else {
            TestSequenceRecord sequence = testSequences.get(result.testSequence());
            require(sequence != null && sequence.testSequence().testRun().equals(result.testRun()),
                "invalid_oracle_target", "test_sequence", "Sequence must belong to the result's run");
            OracleReferences.validate(result, observations, actionExecutions);
        }
        validateAssessment(result);
    }

    private void validateAssessment(OracleResult result) {
        JsonNode context = result.oracleDetails().get("evidence.assessment");
        if (context == null) {
            return;
        }
        String phase = context.path("phase").asText();
        String semantics = context.path("semantics").asText();
        require(semantics.equals("open_ended") || semantics.equals("recorded_interval"),
            "invalid_record", "oracle_details.evidence.assessment.semantics", "Unknown temporal semantics");
        require(List.of("during_run", "end_of_run", "during_sequence", "end_of_sequence").contains(phase),
            "invalid_record", "oracle_details.evidence.assessment.phase", "Unknown reporting phase");
        boolean finished;
        String reason;
        if (phase.endsWith("sequence")) {
            TestSequenceRecord sequence = testSequences.get(result.testSequence());
            require(sequence != null, "invalid_oracle_target", "test_sequence", "Sequence phase needs a sequence");
            finished = sequence.finished();
            reason = sequence.reason();
        } else {
            TestRunRecord run = testRuns.get(result.testRun());
            finished = run.finished();
            reason = run.reason();
        }
        require(finished == phase.startsWith("end_"), "invalid_lifecycle",
            "oracle_details.evidence.assessment.phase", "Reporting phase does not match history status");
        if (context.has("termination_reason")) {
            require(finished && context.get("termination_reason").isTextual()
                && Objects.equals(reason, context.get("termination_reason").textValue()),
                "invalid_record", "oracle_details.evidence.assessment.termination_reason", "Reason must match the history ending");
        }
    }

    private void requireActiveSequence(String id) {
        TestSequenceRecord record = testSequences.get(id);
        require(record != null, "invalid_reference", "test_sequence", "Unknown test sequence");
        require(!record.finished(), "invalid_lifecycle", "test_sequence", "Test sequence finished");
    }

    private ActionExecutionRecord requireExecution(String id) {
        ActionExecutionRecord record = actionExecutions.get(id);
        require(record != null, "invalid_reference", "action_execution", "Unknown action execution");
        return record;
    }

    private static void require(boolean condition, String code, String field, String message) {
        if (!condition) {
            throw new EvidenceException(code, field, message);
        }
    }

    public Manifest manifest() {
        return manifest;
    }
    public int eventCount() {
        return events.size();
    }
    public List<TestRunRecord> testRuns() {
        return List.copyOf(testRuns.values());
    }
    public Optional<TestRunRecord> testRun(String id) {
        return Optional.ofNullable(testRuns.get(id));
    }
    public List<TestSequenceRecord> testSequences() {
        return List.copyOf(testSequences.values());
    }
    public Optional<TestSequenceRecord> testSequence(String id) {
        return Optional.ofNullable(testSequences.get(id));
    }
    public List<Observation> observations() {
        return List.copyOf(observations.values());
    }
    public Optional<Observation> observation(String id) {
        return Optional.ofNullable(observations.get(id));
    }
    public List<RecordedState> recordedStates() {
        return List.copyOf(recordedStates.values());
    }
    public Optional<RecordedState> recordedState(String id) {
        return Optional.ofNullable(recordedStates.get(id));
    }
    public List<ActionDescription> actionDescriptions() {
        return List.copyOf(actionDescriptions.values());
    }
    public Optional<ActionDescription> actionDescription(String id) {
        return Optional.ofNullable(actionDescriptions.get(id));
    }
    public List<ActionExecutionRecord> actionExecutions() {
        return List.copyOf(actionExecutions.values());
    }
    public Optional<ActionExecutionRecord> actionExecution(String id) {
        return Optional.ofNullable(actionExecutions.get(id));
    }
    public List<RecordedTransition> recordedTransitions() {
        return List.copyOf(recordedTransitions);
    }
    public List<OracleResult> oracleResults() {
        return List.copyOf(oracleResults);
    }

    public List<TestSequenceRecord> testSequencesForRun(String id) {
        return testSequences.values().stream().filter(value -> value.testSequence().testRun().equals(id))
            .sorted(Comparator.comparing(value -> value.testSequence().sequenceIndex())).toList();
    }

    public List<Observation> observationsForSequence(String id) {
        return observations.values().stream().filter(value -> value.testSequence().equals(id))
            .sorted(Comparator.comparing(Observation::observationIndex)).toList();
    }

    public List<Observation> observationsForRecordedState(String id) {
        return observations.values().stream().filter(value -> value.recordedState().id().equals(id)).toList();
    }

    public List<ActionExecutionRecord> executionsForSequence(String id) {
        return actionExecutions.values().stream().filter(value -> value.actionExecution().testSequence().equals(id)).toList();
    }

    public List<AvailableAction> availableActions(String observation) {
        return observations.get(observation).availableActions();
    }

    public List<RecordedTransition> outgoing(String observation) {
        return recordedTransitions.stream().filter(value -> value.sourceObservation().equals(observation)).toList();
    }

    public List<RecordedTransition> incoming(String observation) {
        return recordedTransitions.stream().filter(value -> value.resultingObservation().equals(observation)).toList();
    }

    public ReconstructedEvidence snapshot() {
        ReconstructedEvidence copy = new ReconstructedEvidence(manifest);
        copy.testRuns.putAll(testRuns);
        copy.testSequences.putAll(testSequences);
        copy.observations.putAll(observations);
        copy.recordedStates.putAll(recordedStates);
        copy.actionDescriptions.putAll(actionDescriptions);
        copy.actionExecutions.putAll(actionExecutions);
        copy.events.putAll(events);
        copy.oracleResults.addAll(oracleResults);
        copy.recordedTransitions.addAll(recordedTransitions);
        copy.latest.putAll(latest);
        copy.awaiting.putAll(awaiting);
        return copy;
    }
}
