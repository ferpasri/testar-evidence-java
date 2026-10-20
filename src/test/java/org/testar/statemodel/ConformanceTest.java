package org.testar.statemodel;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            StateModel model = EvidenceReader.read(source);
            assertEquals(expected, summary(model));
            assertRecords(model, events(source));
        } else {
            ModelException error = assertThrows(ModelException.class, () -> EvidenceReader.read(source));
            assertEquals(expected.path("error").path("code").asText(), error.code());
            assertEquals(expected.path("error").path("field").asText(), error.field());
            StateModel prefix = new StateModel(EvidenceJson.readManifest(Files.readString(source.resolve("manifest.json"))));
            for (EventEnvelope event : events(source)) {
                JsonNode previous = summary(prefix);
                try {
                    prefix.apply(event);
                } catch (ModelException rejected) {
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
        StateModel result;
        try (JsonlStore store = JsonlStore.create(destination, manifest)) {
            for (EventEnvelope event : events(source)) {
                if (event.event() instanceof Event.Observed observed) {
                    for (ArtifactReference artifact : observed.data().artifacts()) {
                        byte[] bytes = Files.readAllBytes(source.resolve(artifact.path()));
                        assertEquals(artifact, store.putArtifact(bytes, artifact.mediaType(), artifact.width(), artifact.height()));
                        assertArrayEquals(bytes, store.readArtifact(artifact));
                    }
                }
                JsonNode previous = summary(store.model());
                byte[] before = Files.readAllBytes(destination.resolve("evidence.jsonl"));
                try {
                    assertTrue(store.record(event));
                    assertFalse(store.record(event), "Identical retry must not append another record");
                    assertRecords(store.model(), List.of(event));
                } catch (ModelException rejected) {
                    failure = error(rejected, event.id());
                    assertEquals(previous, summary(store.model()));
                    assertArrayEquals(before, Files.readAllBytes(destination.resolve("evidence.jsonl")));
                    break;
                }
            }
            result = store.model();
        }
        if (expected.path("status").asText().equals("accepted")) {
            assertNull(failure);
            assertEquals(expected, summary(result));
        } else {
            assertEquals(expected.get("error"), failure);
            assertEquals(expected.get("accepted_prefix"), summary(result));
        }
        try (JsonlStore reopened = JsonlStore.open(destination)) {
            assertEquals(summary(result), summary(reopened.model()));
        }
        assertEquals(EvidenceJson.parse(Files.readString(source.resolve("manifest.json"))),
            EvidenceJson.parse(Files.readString(destination.resolve("manifest.json"))));
        List<EventEnvelope> produced = events(destination);
        assertRecords(result, produced);
        for (Observation observation : result.observations()) {
            for (ArtifactReference artifact : observation.artifacts()) {
                assertArrayEquals(Files.readAllBytes(source.resolve(artifact.path())),
                    Files.readAllBytes(destination.resolve(artifact.path())));
            }
        }
    }

    private static List<EventEnvelope> events(Path source) throws IOException {
        return Files.readAllLines(source.resolve("evidence.jsonl")).stream().map(line -> {
            EventEnvelope event = EvidenceJson.readEvent(line);
            assertEquals(EvidenceJson.parse(line), EvidenceJson.parse(EvidenceJson.writeEvent(event)),
                "Every field must survive serialization");
            return event;
        }).toList();
    }

    private static void assertRecords(StateModel model, List<EventEnvelope> events) {
        for (EventEnvelope envelope : events) {
            Event event = envelope.event();
            if (event instanceof Event.RunStarted value) {
                assertEquals(value.data(), model.run(value.data().id()).orElseThrow().run());
            } else if (event instanceof Event.ActionDefined value) {
                assertEquals(value.data(), model.action(value.data().id()).orElseThrow());
            } else if (event instanceof Event.Observed value) {
                assertEquals(value.data(), model.observation(value.data().id()).orElseThrow());
            } else if (event instanceof Event.ActionSelected value) {
                assertEquals(value.data(), model.execution(value.data().id()).orElseThrow().execution());
            } else if (event instanceof Event.Checked value) {
                assertTrue(model.checks().contains(value.data()));
            }
        }
    }

    private static JsonNode error(ModelException error, String id) {
        return EvidenceJson.tree(Map.of("code", error.code(), "event", id, "field", error.field()));
    }

    static JsonNode summary(StateModel model) {
        ObjectNode result = (ObjectNode) EvidenceJson.parse("{}");
        result.put("status", "accepted");
        result.set("counts", EvidenceJson.tree(Map.of(
            "events", model.eventCount(), "runs", model.runs().size(), "observations", model.observations().size(),
            "actions", model.actions().size(), "executions", model.executions().size(), "checks", model.checks().size(),
            "artifact_references", model.observations().stream().mapToInt(value -> value.artifacts().size()).sum(),
            "concrete_states", model.snapshots().size())));
        result.set("runs", EvidenceJson.tree(model.runs().stream().map(value ->
            Map.of("id", value.run().id(), "status", value.finished() ? "finished" : "running")).toList()));
        result.set("observations", EvidenceJson.tree(model.observations().stream().map(value ->
            Map.of("id", value.id(), "run", value.run(), "sequence", value.sequence(), "concrete_state", value.state().id(),
                "capture", value.state().capture(), "candidate_scope", value.candidateScope(),
                "widget_ids", value.state().widgets().stream().map(Widget::id).toList(),
                "candidates", value.candidates(), "artifact_ids", value.artifacts().stream().map(ArtifactReference::id).toList())).toList()));
        result.set("actions", EvidenceJson.tree(model.actions().stream().map(value ->
            Map.of("id", value.id(), "kind", value.kind())).toList()));
        result.set("executions", EvidenceJson.tree(model.executions()));
        result.set("checks", EvidenceJson.tree(model.checks().stream().map(EvidenceJson::checkTree).toList()));
        result.set("concrete_states", EvidenceJson.tree(model.snapshots().stream().map(ConcreteState::id).toList()));
        return EvidenceJson.parse(EvidenceJson.write(result));
    }
}
