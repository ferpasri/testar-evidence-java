package org.testar.statemodel;

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
        CheckResult original = TestData.check(new CheckTarget.Run());
        EventEnvelope unspecified = new EventEnvelope("omitted", new Event.Checked(original));
        assertFalse(EvidenceJson.parse(EvidenceJson.writeEvent(unspecified)).path("event").path("data").has("expected"));
        CheckResult expectedNull = new CheckResult(original.run(), original.name(), original.rule(), original.target(),
            original.status(), ExpectedValue.of(null), new CheckReading.Value(EvidenceJson.parse("null")), original.details());
        EventEnvelope supplied = new EventEnvelope("supplied", new Event.Checked(expectedNull));
        assertTrue(EvidenceJson.parse(EvidenceJson.writeEvent(supplied)).path("event").path("data").get("expected").isNull());
        assertEquals(supplied, EvidenceJson.readEvent(EvidenceJson.writeEvent(supplied)));
    }

    @Test
    void unsignedPropertyIntegersRetainTheirExactValue() {
        BigInteger integer = new BigInteger("18446744073709551615");
        Properties properties = new Properties(EvidenceJson.parse("{\"fingerprint\":18446744073709551615}"));
        EventEnvelope event = new EventEnvelope("large", new Event.RunStarted(new Run("run", "sample", "session", properties)));
        Event.RunStarted decoded = (Event.RunStarted) EvidenceJson.readEvent(EvidenceJson.writeEvent(event)).event();
        assertEquals(integer, decoded.data().properties().get("fingerprint").bigIntegerValue());
    }

    @Test
    void omittedCandidateScopeIsUnknownAndAnExplicitNullScopeIsRejected() {
        ObjectNode event = (ObjectNode) EvidenceJson.parse(EvidenceJson.writeEvent(
            new EventEnvelope("observe", new Event.Observed(TestData.observation("o1", 1)))));
        ObjectNode data = (ObjectNode) event.path("event").path("data");
        data.remove("candidate_scope");
        Event.Observed decoded = (Event.Observed) EvidenceJson.readEvent(EvidenceJson.write(event)).event();
        assertEquals(CandidateScope.UNKNOWN, decoded.data().candidateScope());
        data.putNull("candidate_scope");
        assertThrows(ModelException.class, () -> EvidenceJson.readEvent(EvidenceJson.write(event)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"kind\":\"value\"}", "null", "{\"kind\":\"absent\",\"value\":null}"})
    void malformedReadingsAreRejected(String reading) {
        ObjectNode event = (ObjectNode) EvidenceJson.parse(EvidenceJson.writeEvent(
            new EventEnvelope("check", new Event.Checked(TestData.check(new CheckTarget.Run())))));
        ((ObjectNode) event.path("event").path("data")).set("actual", EvidenceJson.parse(reading));
        assertThrows(ModelException.class, () -> EvidenceJson.readEvent(EvidenceJson.write(event)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"a\":1,\"a\":2}", "{} {}", "NaN", "", "{\"a\":Infinity}"})
    void malformedJsonIsRejected(String json) {
        assertThrows(ModelException.class, () -> EvidenceJson.parse(json));
    }

    @Test
    void unknownStructuralFieldsAreRejected() {
        ObjectNode event = (ObjectNode) EvidenceJson.parse(EvidenceJson.writeEvent(
            new EventEnvelope("observe", new Event.Observed(TestData.observation("o1", 1)))));
        ((ObjectNode) event.path("event").path("data")).put("unexpected", true);
        assertThrows(ModelException.class, () -> EvidenceJson.readEvent(EvidenceJson.write(event)));
        assertThrows(ModelException.class, () -> EvidenceJson.readManifest("{\"schema_version\":2,\"model\":\"sample\",\"unexpected\":true}"));
    }

    @Test
    void widgetsRequireAnAcyclicHierarchyWithinTheirSnapshot() {
        Properties empty = Properties.empty();
        assertThrows(ModelException.class, () -> new ConcreteState("s", empty,
            List.of(new Widget("one", "two", empty), new Widget("two", "one", empty)), CaptureScope.COMPLETE));
        assertThrows(ModelException.class, () -> new ConcreteState("s", empty,
            List.of(new Widget("one", "missing", empty)), CaptureScope.COMPLETE));
        assertThrows(ModelException.class, () -> new ConcreteState("s", empty,
            List.of(new Widget("one", null, empty), new Widget("one", null, empty)), CaptureScope.COMPLETE));
        assertThrows(ModelException.class, () -> new ConcreteState("s", empty,
            List.of(new Widget("one", null, empty)), CaptureScope.UNAVAILABLE));
    }

    @Test
    void observationCopiesItsListsAndRejectsDuplicateCandidates() {
        Observation original = TestData.observation("o1", 1);
        List<ActionCandidate> candidates = new ArrayList<>(original.candidates());
        Observation copied = new Observation(original.id(), original.run(), original.sequence(), original.timestampMicros(),
            original.state(), candidates, original.candidateScope(), original.artifacts());
        candidates.clear();
        assertEquals(3, copied.candidates().size());
        assertThrows(ModelException.class, () -> new Observation("duplicate", "run", 1, 1, original.state(),
            List.of(original.candidates().get(0), original.candidates().get(0)), CandidateScope.GENERATED, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.png", "/outside.png", "a/../b.png", "C:/file.png", "a//b.png", "artifacts/CON.png"})
    void unsafeArtifactPathsAreRejected(String path) {
        assertThrows(ModelException.class, () -> new ArtifactReference("image", path, "image/png", "a".repeat(64), null, null));
    }

    @Test
    void invalidTextAndAcceptedSubmissionErrorsAreRejected() {
        assertThrows(ModelException.class, () -> new Manifest(" "));
        assertThrows(ModelException.class, () -> new ActionDefinition("a\n", "click", Properties.empty()));
        assertThrows(ModelException.class, () -> new Event.ActionSubmitted("execution", true, "error"));
    }
}
