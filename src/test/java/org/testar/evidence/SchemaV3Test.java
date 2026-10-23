package org.testar.evidence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaV3Test {
    private static ReconstructedEvidence started() {
        ReconstructedEvidence evidence = new ReconstructedEvidence(new Manifest("evidence"));
        evidence.apply(new EvidenceEvent("start", new Event.TestRunStarted(new TestRun("parent", "unit", "1", Properties.empty()))));
        return evidence;
    }

    private static void sequence(ReconstructedEvidence evidence, String id, long index) {
        evidence.apply(new EvidenceEvent("start-" + id, new Event.TestSequenceStarted(new TestSequence(id, "parent", index, id, Properties.empty()))));
    }

    private static Observation observation(String sequence, String id, long index) {
        return new Observation(id, sequence, index, index,
            new RecordedState("shared", Properties.empty(), List.of(), CaptureScope.COMPLETE),
            List.of(new AvailableAction("refresh", null)), AvailableActionScope.ELIGIBLE, List.of());
    }

    @Test
    void interleavedSequencesPreserveExecutionAndTransitionOrderIndependently() {
        ReconstructedEvidence evidence = started();
        sequence(evidence, "first", 1);
        sequence(evidence, "second", 2);
        evidence.apply(new EvidenceEvent("describe", new Event.ActionDescribed(new ActionDescription("refresh", "refresh", Properties.empty()))));
        for (String id : List.of("first", "second")) {
            evidence.apply(new EvidenceEvent("observe-" + id, new Event.ObservationRecorded(observation(id, id, 1))));
            evidence.apply(new EvidenceEvent("select-" + id, new Event.ActionSelected(new ActionExecution(id, id, id, "refresh", null, Properties.empty()))));
            evidence.apply(new EvidenceEvent("return-" + id, new Event.ActionExecutionReported(new ActionExecutionResult(id, ActionOutcome.RETURNED, null))));
        }
        for (String id : List.of("second", "first")) {
            evidence.apply(new EvidenceEvent("result-" + id, new Event.ObservationRecorded(observation(id, id + "-result", 2))));
            evidence.apply(new EvidenceEvent("transition-" + id, new Event.TransitionRecorded(id, id + "-result")));
        }
        assertEquals(List.of("first", "second"), evidence.actionExecutions().stream().map(value -> value.actionExecution().id()).toList());
        assertEquals(List.of("second", "first"), evidence.recordedTransitions().stream().map(RecordedTransition::actionExecution).toList());
        assertEquals(1, evidence.recordedStates().size());
        assertEquals(4, evidence.observations().size());
        int before = evidence.eventCount();
        EvidenceException error = assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("early-finish", new Event.TestRunFinished("parent", "done"))));
        assertEquals("invalid_lifecycle", error.code());
        assertEquals(before, evidence.eventCount());
    }

    @Test
    void severityIsOptionalAndPreservesSourceLabelsWithoutChangingOracleStatus() {
        ReconstructedEvidence evidence = started();
        ObjectNode data = (ObjectNode) EvidenceJson.parse("""
            {"test_run":"parent","name":"Condition holds","oracle":{"id":"condition"},
             "oracle_target":{"target_type":"test_run"},"oracle_status":"failed","oracle_details":{}}
            """);
        ObjectNode envelope = (ObjectNode) EvidenceJson.parse("""
            {"schema_version":3,"id":"omitted","event":{"event_type":"oracle_result_recorded","data":{}}}
            """);
        ((ObjectNode) envelope.get("event")).set("data", data);
        evidence.apply(EvidenceJson.readEvent(EvidenceJson.write(envelope)));
        data.set("oracle_severity", EvidenceJson.parse("{\"label\":\"  Priorité équipe  \",\"value\":25}"));
        envelope.put("id", "supplied");
        EvidenceEvent supplied = EvidenceJson.readEvent(EvidenceJson.write(envelope));
        evidence.apply(supplied);
        assertNull(evidence.oracleResults().get(0).oracleSeverity());
        assertEquals("  Priorité équipe  ", evidence.oracleResults().get(1).oracleSeverity().label());
        assertTrue(evidence.oracleResults().stream().allMatch(value -> value.oracleStatus() == OracleStatus.FAILED));
        assertEquals(EvidenceJson.parse(EvidenceJson.write(envelope)), EvidenceJson.parse(EvidenceJson.writeEvent(supplied)));
    }

    @Test
    void requiredNullableTargetsAndUnsignedArtifactDimensionsFollowTheContract() {
        ObjectNode event = (ObjectNode) EvidenceJson.parse(EvidenceJson.writeEvent(new EvidenceEvent("select",
            new Event.ActionSelected(new ActionExecution("execution", "sequence", "source", "action", null, Properties.empty())))));
        ((ObjectNode) event.path("event").path("data")).remove("target_widget");
        assertThrows(EvidenceException.class, () -> EvidenceJson.readEvent(EvidenceJson.write(event)));
        BigInteger maximum = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        ArtifactReference artifact = new ArtifactReference("image", "artifacts/image.png", "image/png", "a".repeat(64), maximum, BigInteger.ZERO);
        JsonNode encoded = EvidenceJson.tree(artifact);
        assertEquals(maximum, encoded.get("width").bigIntegerValue());
        assertEquals(BigInteger.ZERO, encoded.get("height").bigIntegerValue());
    }
}
