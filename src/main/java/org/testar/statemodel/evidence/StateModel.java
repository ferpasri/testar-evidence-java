package org.testar.statemodel.evidence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class StateModel {
    private final Manifest manifest;
    private final Map<String, RunRecord> runs = new TreeMap<>();
    private final Map<String, Observation> observations = new TreeMap<>();
    private final Map<String, ConcreteState> snapshots = new TreeMap<>();
    private final Map<String, ActionDefinition> actions = new TreeMap<>();
    private final Map<String, ExecutionRecord> executions = new LinkedHashMap<>();
    private final Map<String, EventEnvelope> events = new LinkedHashMap<>();
    private final List<CheckResult> checks = new ArrayList<>();
    private final Map<String, Observation> latest = new HashMap<>();
    private final Map<String, String> pending = new HashMap<>();

    public StateModel(Manifest manifest) {
        this.manifest = Validation.present(manifest, "Manifest");
    }

    public boolean validate(EventEnvelope envelope) {
        Validation.present(envelope, "Event");
        EventEnvelope previous = events.get(envelope.id());
        if (previous != null) {
            Validation.same(previous, envelope, "Conflicting event: " + envelope.id());
            return false;
        }
        Event event = envelope.event();
        if (event instanceof Event.RunStarted value) {
            Validation.require(!runs.containsKey(value.data().id()), "conflict", "Run ID already exists");
        } else if (event instanceof Event.ActionDefined value) {
            Validation.same(actions.get(value.data().id()), value.data(), "Action definition has changed");
        } else if (event instanceof Event.Observed value) {
            validateObservation(value.data());
        } else if (event instanceof Event.ActionSelected value) {
            validateSelection(value.data());
        } else if (event instanceof Event.ActionSubmitted value) {
            ExecutionRecord record = requireExecution(value.execution());
            requireRunning(record.execution().run());
            Validation.require(record.status() == ExecutionStatus.SELECTED, "conflict", "Execution is not awaiting submission");
        } else if (event instanceof Event.ActionResult value) {
            validateResult(value);
        } else if (event instanceof Event.Checked value) {
            Validation.require(runs.containsKey(value.data().run()), "unknown_reference", "Unknown evaluation run");
            CheckReferences.validate(value.data(), observations, executions);
        } else if (event instanceof Event.RunFinished value) {
            requireRunning(value.run());
        }
        return true;
    }

    public boolean apply(EventEnvelope envelope) {
        if (!validate(envelope)) {
            return false;
        }
        Event event = envelope.event();
        if (event instanceof Event.RunStarted value) {
            runs.put(value.data().id(), new RunRecord(value.data(), false, null));
        } else if (event instanceof Event.ActionDefined value) {
            actions.put(value.data().id(), value.data());
        } else if (event instanceof Event.Observed value) {
            Observation observation = value.data();
            observations.put(observation.id(), observation);
            snapshots.put(observation.state().id(), observation.state());
            latest.put(observation.run(), observation);
        } else if (event instanceof Event.ActionSelected value) {
            Execution execution = value.data();
            executions.put(execution.id(), new ExecutionRecord(execution, ExecutionStatus.SELECTED, null, null));
            pending.put(execution.run(), execution.id());
        } else if (event instanceof Event.ActionSubmitted value) {
            ExecutionRecord record = executions.get(value.execution());
            ExecutionStatus status = value.accepted() ? ExecutionStatus.SUBMITTED : ExecutionStatus.REJECTED;
            executions.put(value.execution(), new ExecutionRecord(record.execution(), status, null, value.error()));
            if (!value.accepted()) {
                pending.remove(record.execution().run());
            }
        } else if (event instanceof Event.ActionResult value) {
            ExecutionRecord record = executions.get(value.execution());
            executions.put(value.execution(), new ExecutionRecord(record.execution(), ExecutionStatus.OBSERVED,
                value.observation(), null));
            pending.remove(record.execution().run());
        } else if (event instanceof Event.Checked value) {
            checks.add(value.data());
        } else if (event instanceof Event.RunFinished value) {
            RunRecord run = runs.get(value.run());
            runs.put(value.run(), new RunRecord(run.run(), true, value.reason()));
            String interrupted = pending.remove(value.run());
            if (interrupted != null) {
                ExecutionRecord record = executions.get(interrupted);
                executions.put(interrupted, new ExecutionRecord(record.execution(), ExecutionStatus.INCOMPLETE, null, record.error()));
            }
        }
        events.put(envelope.id(), envelope);
        return true;
    }

    private void validateObservation(Observation observation) {
        requireRunning(observation.run());
        Validation.require(!observations.containsKey(observation.id()), "conflict", "Observation ID already exists");
        Observation last = latest.get(observation.run());
        Validation.require(last == null || observation.sequence().compareTo(last.sequence()) > 0,
            "invalid_record", "Observation sequence must increase within its run");
        Validation.same(snapshots.get(observation.state().id()), observation.state(), "Snapshot content has changed");
        for (ActionCandidate candidate : observation.candidates()) {
            Validation.require(actions.containsKey(candidate.action()), "unknown_reference", "Unknown candidate action");
        }
    }

    private void validateSelection(Execution execution) {
        requireRunning(execution.run());
        Validation.require(!executions.containsKey(execution.id()), "conflict", "Execution ID already exists");
        Observation source = requireObservation(execution.source());
        Validation.require(source.run().equals(execution.run()), "invalid_record", "Execution source belongs to another run");
        Validation.require(Objects.equals(latest.get(execution.run()), source),
            "invalid_record", "Execution source must be the latest observation");
        Validation.require(source.candidates().contains(new ActionCandidate(execution.action(), execution.target())),
            "unknown_reference", "Selected action and target are not a source candidate");
        Validation.require(!pending.containsKey(execution.run()), "conflict", "Run already has a pending action");
    }

    private void validateResult(Event.ActionResult result) {
        ExecutionRecord record = requireExecution(result.execution());
        requireRunning(record.execution().run());
        Validation.require(record.status() == ExecutionStatus.SUBMITTED, "conflict", "Execution is not awaiting a result");
        Observation source = requireObservation(record.execution().source());
        Observation target = requireObservation(result.observation());
        Validation.require(target.run().equals(source.run()) && target.sequence().compareTo(source.sequence()) > 0,
            "invalid_record", "Result must be a later observation in the same run");
    }

    private void requireRunning(String id) {
        RunRecord record = runs.get(id);
        Validation.require(record != null, "unknown_reference", "Unknown run: " + id);
        Validation.require(!record.finished(), "conflict", "Run has finished: " + id);
    }

    private Observation requireObservation(String id) {
        Observation observation = observations.get(id);
        Validation.require(observation != null, "unknown_reference", "Unknown observation: " + id);
        return observation;
    }

    private ExecutionRecord requireExecution(String id) {
        ExecutionRecord record = executions.get(id);
        Validation.require(record != null, "unknown_reference", "Unknown execution: " + id);
        return record;
    }

    public Manifest manifest() {
        return manifest;
    }

    public int eventCount() {
        return events.size();
    }

    public Optional<RunRecord> run(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    public List<RunRecord> runs() {
        return List.copyOf(runs.values());
    }

    public Optional<Observation> observation(String id) {
        return Optional.ofNullable(observations.get(id));
    }

    public List<Observation> observations() {
        return List.copyOf(observations.values());
    }

    public List<Observation> observationsForRun(String run) {
        return observations.values().stream().filter(value -> value.run().equals(run))
            .sorted(Comparator.comparing(Observation::sequence)).toList();
    }

    public List<Observation> observationsForSnapshot(String snapshot) {
        return observations.values().stream().filter(value -> value.state().id().equals(snapshot)).toList();
    }

    public List<ConcreteState> snapshots() {
        return List.copyOf(snapshots.values());
    }

    public Optional<ConcreteState> snapshot(String id) {
        return Optional.ofNullable(snapshots.get(id));
    }

    public List<ActionDefinition> actions() {
        return List.copyOf(actions.values());
    }

    public Optional<ActionDefinition> action(String id) {
        return Optional.ofNullable(actions.get(id));
    }

    public Optional<ExecutionRecord> execution(String id) {
        return Optional.ofNullable(executions.get(id));
    }

    public List<ExecutionRecord> executions() {
        return List.copyOf(executions.values());
    }

    public List<ExecutionRecord> executionsForRun(String run) {
        return executions.values().stream().filter(value -> value.execution().run().equals(run)).toList();
    }

    public List<CheckResult> checks() {
        return List.copyOf(checks);
    }

    public List<ActionCandidate> candidates(String observation) {
        return requireObservation(observation).candidates();
    }

    public List<ExecutionLink> links() {
        return executions.values().stream().filter(record -> record.result() != null)
            .map(record -> new ExecutionLink(record.execution().id(), record.execution().run(),
                record.execution().source(), record.execution().action(), record.result())).toList();
    }

    public List<ExecutionLink> outgoing(String observation) {
        return links().stream().filter(link -> link.source().equals(observation)).toList();
    }

    public List<ExecutionLink> incoming(String observation) {
        return links().stream().filter(link -> link.target().equals(observation)).toList();
    }

    public StateModel snapshot() {
        StateModel copy = new StateModel(manifest);
        copy.runs.putAll(runs);
        copy.observations.putAll(observations);
        copy.snapshots.putAll(snapshots);
        copy.actions.putAll(actions);
        copy.executions.putAll(executions);
        copy.events.putAll(events);
        copy.checks.addAll(checks);
        copy.latest.putAll(latest);
        copy.pending.putAll(pending);
        return copy;
    }
}
