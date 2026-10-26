package org.testar.evidence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvidenceJsonTest {
    @Test
    void missingAndExplicitNullPropertiesRemainDifferentAndImmutable() {
        ObjectNode input = (ObjectNode) EvidenceJson.parse("{\"nullValue\":null,\"nested\":{\"value\":1}}");
        Properties properties = new Properties(input);
        input.removeAll();
        assertTrue(properties.contains("nullValue"));
        assertTrue(properties.get("nullValue").isNull());
        assertFalse(properties.contains("missing"));
        assertNull(properties.get("missing"));
        ((ObjectNode) properties.get("nested")).put("value", 2);
        properties.asJson().removeAll();
        assertEquals(BigInteger.ONE, properties.get("nested").get("value").bigIntegerValue());
    }

    @Test
    void omittedExpectationAndExplicitNullExpectationSurviveSerialization() {
        OracleResult original = TestData.check(new OracleTarget.TestSequence());
        EvidenceEvent unspecified = new EvidenceEvent("omitted", new Event.OracleResultRecorded(original));
        assertFalse(EvidenceJson.parse(EvidenceJson.writeEvent(unspecified)).path("event").path("data").has("expected_value"));
        OracleResult expectedNull = new OracleResult(original.testRun(), original.testSequence(), original.name(), original.oracle(), original.oracleTarget(),
            original.oracleStatus(), ExpectedValue.of(null), new ObservedValue.Value(EvidenceJson.parse("null")), original.oracleSeverity(), original.oracleDetails());
        EvidenceEvent supplied = new EvidenceEvent("supplied", new Event.OracleResultRecorded(expectedNull));
        assertTrue(EvidenceJson.parse(EvidenceJson.writeEvent(supplied)).path("event").path("data").get("expected_value").isNull());
        assertEquals(supplied, EvidenceJson.readEvent(EvidenceJson.writeEvent(supplied)));
    }

    @Test
    void unsignedPropertyIntegersRetainTheirExactValue() {
        BigInteger integer = new BigInteger("18446744073709551615");
        Properties properties = new Properties(EvidenceJson.parse("{\"fingerprint\":18446744073709551615}"));
        EvidenceEvent event = new EvidenceEvent("large", new Event.TestRunStarted(new TestRun("run", "sample", "session", properties)));
        Event.TestRunStarted decoded = (Event.TestRunStarted) EvidenceJson.readEvent(EvidenceJson.writeEvent(event)).event();
        assertEquals(integer, decoded.data().properties().get("fingerprint").bigIntegerValue());
    }

    @Test
    void availableActionScopeIsRequiredAndNullIsRejected() {
        ObjectNode event = (ObjectNode) EvidenceJson.parse(EvidenceJson.writeEvent(
            new EvidenceEvent("observe", new Event.ObservationRecorded(TestData.observation("o1", 1)))));
        ObjectNode data = (ObjectNode) event.path("event").path("data");
        data.remove("available_action_scope");
        assertThrows(EvidenceException.class, () -> EvidenceJson.readEvent(EvidenceJson.write(event)));
        data.putNull("available_action_scope");
        assertThrows(EvidenceException.class, () -> EvidenceJson.readEvent(EvidenceJson.write(event)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"value_status\":\"value\"}", "null", "{\"value_status\":\"absent\",\"value\":null}"})
    void malformedReadingsAreRejected(String reading) {
        ObjectNode event = (ObjectNode) EvidenceJson.parse(EvidenceJson.writeEvent(
            new EvidenceEvent("check", new Event.OracleResultRecorded(TestData.check(new OracleTarget.TestSequence())))));
        ((ObjectNode) event.path("event").path("data")).set("observed_value", EvidenceJson.parse(reading));
        assertThrows(EvidenceException.class, () -> EvidenceJson.readEvent(EvidenceJson.write(event)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"a\":1,\"a\":2}", "{} {}", "NaN", "", "{\"a\":Infinity}"})
    void malformedJsonIsRejected(String json) {
        assertThrows(EvidenceException.class, () -> EvidenceJson.parse(json));
    }

    @Test
    void unknownStructuralFieldsAreRejected() {
        ObjectNode event = (ObjectNode) EvidenceJson.parse(EvidenceJson.writeEvent(
            new EvidenceEvent("observe", new Event.ObservationRecorded(TestData.observation("o1", 1)))));
        ((ObjectNode) event.path("event").path("data")).put("unexpected", true);
        assertThrows(EvidenceException.class, () -> EvidenceJson.readEvent(EvidenceJson.write(event)));
        assertThrows(EvidenceException.class, () -> EvidenceJson.readManifest("{\"schema_version\":3,\"evidence_id\":\"sample\",\"unexpected\":true}"));
    }

    @Test
    void widgetsRequireAnAcyclicHierarchyWithinTheirSnapshot() {
        Properties empty = Properties.empty();
        assertThrows(EvidenceException.class, () -> new RecordedState("s", empty,
            List.of(new Widget("one", "two", empty), new Widget("two", "one", empty)), CaptureScope.COMPLETE));
        assertThrows(EvidenceException.class, () -> new RecordedState("s", empty,
            List.of(new Widget("one", "missing", empty)), CaptureScope.COMPLETE));
        assertThrows(EvidenceException.class, () -> new RecordedState("s", empty,
            List.of(new Widget("one", null, empty), new Widget("one", null, empty)), CaptureScope.COMPLETE));
        assertThrows(EvidenceException.class, () -> new RecordedState("s", empty,
            List.of(new Widget("one", null, empty)), CaptureScope.UNAVAILABLE));
    }

    @Test
    void observationCopiesItsListsAndRejectsDuplicateAvailableActions() {
        Observation original = TestData.observation("o1", 1);
        List<AvailableAction> candidates = new ArrayList<>(original.availableActions());
        Observation copied = new Observation(original.id(), original.testSequence(), original.observationIndex(), original.timestampMicros(),
            original.recordedState(), candidates, original.availableActionScope(), original.artifacts());
        candidates.clear();
        assertEquals(3, copied.availableActions().size());
        assertThrows(EvidenceException.class, () -> new Observation("duplicate", "run", 1, 1, original.recordedState(),
            List.of(original.availableActions().get(0), original.availableActions().get(0)), AvailableActionScope.GENERATED, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.png", "/outside.png", "a/../b.png", "C:/file.png", "a//b.png", "artifacts/CON.png"})
    void unsafeArtifactPathsAreRejected(String path) {
        assertThrows(EvidenceException.class, () -> new ArtifactReference("image", path, "image/png", "a".repeat(64), null, null));
    }

    @Test
    void invalidTextAndReturnedExecutionErrorsAreRejected() {
        assertThrows(EvidenceException.class, () -> new Manifest(" "));
        assertThrows(EvidenceException.class, () -> new ActionDescription("a\n", "click", Properties.empty()));
        assertThrows(EvidenceException.class, () -> new Event.ActionExecutionReported(new ActionExecutionResult("execution", ActionOutcome.RETURNED, "error")));
    }
}
