package org.testar.evidence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ConformanceTest {
    @TempDir
    Path temporary;

    @TestFactory
    Stream<DynamicTest> publishedCases() throws IOException {
        return ConformanceData.cases().stream().flatMap(source -> Stream.of(
            DynamicTest.dynamicTest("Read " + source.getFileName(), () -> readCase(source)),
            DynamicTest.dynamicTest("Produce " + source.getFileName(), () -> produceCase(source))));
    }

    private void readCase(Path source) throws IOException {
        JsonNode expected = EvidenceJson.parse(Files.readString(source.resolve("expected.json")));
        byte[] before = Files.readAllBytes(source.resolve("evidence.jsonl"));
        if (expected.path("status").asText().equals("accepted")) {
            ReconstructedEvidence evidence = EvidenceReader.read(source);
            assertEquals(expected, summary(evidence));
            assertHistory(evidence, events(source));
        } else {
            EvidenceException error = assertThrows(EvidenceException.class, () -> EvidenceReader.read(source));
            assertEquals(expected.path("error").path("code").asText(), error.code());
            assertEquals(expected.path("error").path("field").asText(), error.field());
            ReconstructedEvidence prefix = new ReconstructedEvidence(EvidenceJson.readManifest(Files.readString(source.resolve("manifest.json"))));
            for (EvidenceEvent event : events(source)) {
                JsonNode previous = summary(prefix);
                try {
                    prefix.apply(event);
                } catch (EvidenceException rejected) {
                    assertEquals(expected.path("error"), error(rejected, event.id()));
                    assertEquals(previous, summary(prefix));
                    break;
                }
            }
            assertEquals(expected.get("accepted_prefix"), summary(prefix));
        }
        assertArrayEquals(before, Files.readAllBytes(source.resolve("evidence.jsonl")));
    }

    private void produceCase(Path source) throws IOException {
        JsonNode expected = EvidenceJson.parse(Files.readString(source.resolve("expected.json")));
        Path destination = temporary.resolve("produce-" + source.getFileName());
        Manifest manifest = EvidenceJson.readManifest(Files.readString(source.resolve("manifest.json")));
        JsonNode failure = null;
        List<EvidenceEvent> accepted = new ArrayList<>();
        ReconstructedEvidence result;
        try (JsonlStore store = JsonlStore.create(destination, manifest)) {
            for (EvidenceEvent event : events(source)) {
                if (event.event() instanceof Event.ObservationRecorded observed) {
                    for (ArtifactReference artifact : observed.data().artifacts()) {
                        byte[] bytes = Files.readAllBytes(source.resolve(artifact.path()));
                        assertEquals(artifact, store.putArtifact(bytes, artifact.mediaType(), artifact.width(), artifact.height()));
                        assertArrayEquals(bytes, store.readArtifact(artifact));
                    }
                }
                JsonNode previous = summary(store.evidence());
                byte[] before = Files.readAllBytes(destination.resolve("evidence.jsonl"));
                try {
                    assertTrue(store.record(event));
                    accepted.add(event);
                    assertFalse(store.record(event), "Identical retry must not append another record");
                    assertRecords(store.evidence(), List.of(event));
                } catch (EvidenceException rejected) {
                    failure = error(rejected, event.id());
                    assertEquals(previous, summary(store.evidence()));
                    assertArrayEquals(before, Files.readAllBytes(destination.resolve("evidence.jsonl")));
                    break;
                }
            }
            result = store.evidence();
        }
        if (expected.path("status").asText().equals("accepted")) {
            assertNull(failure);
            assertEquals(expected, summary(result));
        } else {
            assertEquals(expected.get("error"), failure);
            assertEquals(expected.get("accepted_prefix"), summary(result));
        }
        try (JsonlStore reopened = JsonlStore.open(destination)) {
            assertEquals(summary(result), summary(reopened.evidence()));
            assertHistory(reopened.evidence(), accepted);
        }
        assertEquals(EvidenceJson.parse(Files.readString(source.resolve("manifest.json"))),
            EvidenceJson.parse(Files.readString(destination.resolve("manifest.json"))));
        List<EvidenceEvent> produced = events(destination);
        assertEquals(accepted, produced, "The complete accepted event history must survive storage");
        assertHistory(result, produced);
        for (Observation observation : result.observations()) {
            for (ArtifactReference artifact : observation.artifacts()) {
                assertArrayEquals(Files.readAllBytes(source.resolve(artifact.path())),
                    Files.readAllBytes(destination.resolve(artifact.path())));
            }
        }
    }

    private static List<EvidenceEvent> events(Path source) throws IOException {
        return Files.readAllLines(source.resolve("evidence.jsonl")).stream().map(line -> {
            EvidenceEvent event = EvidenceJson.readEvent(line);
            assertEquals(EvidenceJson.parse(line), EvidenceJson.parse(EvidenceJson.writeEvent(event)),
                "Every field must survive serialization");
            return event;
        }).toList();
    }

    private static void assertHistory(ReconstructedEvidence evidence, List<EvidenceEvent> events) {
        assertRecords(evidence, events);
        List<OracleResult> checks = events.stream()
            .map(EvidenceEvent::event)
            .filter(Event.OracleResultRecorded.class::isInstance)
            .map(Event.OracleResultRecorded.class::cast)
            .map(Event.OracleResultRecorded::data)
            .toList();
        assertEquals(checks, evidence.oracleResults(), "Each reported result remains separate and in recording order");
    }

    private static void assertRecords(ReconstructedEvidence evidence, List<EvidenceEvent> events) {
        for (EvidenceEvent envelope : events) {
            Event event = envelope.event();
            if (event instanceof Event.TestRunStarted value) {
                assertEquals(value.data(), evidence.testRun(value.data().id()).orElseThrow().testRun());
            } else if (event instanceof Event.ActionDescribed value) {
                assertEquals(value.data(), evidence.actionDescription(value.data().id()).orElseThrow());
            } else if (event instanceof Event.ObservationRecorded value) {
                assertEquals(value.data(), evidence.observation(value.data().id()).orElseThrow());
            } else if (event instanceof Event.ActionSelected value) {
                assertEquals(value.data(), evidence.actionExecution(value.data().id()).orElseThrow().actionExecution());
            } else if (event instanceof Event.OracleResultRecorded value) {
                assertTrue(evidence.oracleResults().contains(value.data()));
            } else if (event instanceof Event.TestRunFinished value) {
                TestRunRecord run = evidence.testRun(value.testRun()).orElseThrow();
                assertTrue(run.finished());
                assertEquals(value.reason(), run.reason());
            }
        }
    }

    private static JsonNode error(EvidenceException error, String id) {
        return EvidenceJson.tree(Map.of("code", error.code(), "event", id, "field", error.field()));
    }

    static JsonNode summary(ReconstructedEvidence evidence) {
        ObjectNode result = (ObjectNode) EvidenceJson.parse("{}");
        result.put("status", "accepted");
        result.set("counts", EvidenceJson.tree(Map.of(
            "evidence_events", evidence.eventCount(), "test_runs", evidence.testRuns().size(),
            "test_sequences", evidence.testSequences().size(), "observations", evidence.observations().size(),
            "action_descriptions", evidence.actionDescriptions().size(), "action_executions", evidence.actionExecutions().size(),
            "recorded_transitions", evidence.recordedTransitions().size(), "oracle_results", evidence.oracleResults().size(),
            "artifact_references", evidence.observations().stream().mapToInt(value -> value.artifacts().size()).sum(),
            "recorded_states", evidence.recordedStates().size())));
        result.set("test_runs", EvidenceJson.tree(evidence.testRuns().stream().map(value ->
            Map.of("id", value.testRun().id(), "status", value.finished() ? "finished" : "running")).toList()));
        result.set("test_sequences", EvidenceJson.tree(evidence.testSequences().stream().map(value ->
            Map.of("id", value.testSequence().id(), "test_run", value.testSequence().testRun(),
                "sequence_index", value.testSequence().sequenceIndex(), "status", value.finished() ? "finished" : "running")).toList()));
        result.set("observations", EvidenceJson.tree(evidence.observations().stream().map(value ->
            Map.of("id", value.id(), "test_sequence", value.testSequence(), "observation_index", value.observationIndex(),
                "recorded_state", value.recordedState().id(), "capture", value.recordedState().capture(),
                "available_action_scope", value.availableActionScope(),
                "widget_ids", value.recordedState().widgets().stream().map(Widget::id).toList(),
                "available_actions", value.availableActions(), "artifact_ids", value.artifacts().stream().map(ArtifactReference::id).toList())).toList()));
        result.set("action_descriptions", EvidenceJson.tree(evidence.actionDescriptions().stream().map(value ->
            Map.of("id", value.id(), "action_type", value.actionType())).toList()));
        result.set("action_executions", EvidenceJson.tree(evidence.actionExecutions()));
        result.set("recorded_transitions", EvidenceJson.tree(evidence.recordedTransitions()));
        result.set("oracle_results", EvidenceJson.tree(evidence.oracleResults().stream().map(EvidenceJson::oracleResultTree).toList()));
        result.set("recorded_states", EvidenceJson.tree(evidence.recordedStates().stream().map(RecordedState::id).toList()));
        return result;
    }
}
