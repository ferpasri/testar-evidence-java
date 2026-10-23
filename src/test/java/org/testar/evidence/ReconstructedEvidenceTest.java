package org.testar.evidence;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconstructedEvidenceTest {
    @Test
    void discoveredCandidatesRemainAvailableWhenOnlyOneIsExecuted() {
        ReconstructedEvidence evidence = TestData.evidence();
        TestData.submit(evidence);
        evidence.apply(new EvidenceEvent("observe-2", new Event.ObservationRecorded(TestData.observation("o2", 2))));
        evidence.apply(new EvidenceEvent("result", new Event.TransitionRecorded("execution", "o2")));
        assertEquals(3, evidence.availableActions("o1").size());
        assertEquals(1, evidence.actionExecutions().size());
        assertTrue(evidence.availableActions("o1").contains(new AvailableAction("activate", "link")));
        assertTrue(evidence.availableActions("o1").contains(new AvailableAction("key", null)));
        assertEquals(List.of(new RecordedTransition("execution", "run", "o1", "o2", "activate", "button")), evidence.outgoing("o1"));
        assertEquals(evidence.outgoing("o1"), evidence.incoming("o2"));
        assertEquals(ActionExecutionStatus.COMPLETED, evidence.actionExecution("execution").orElseThrow().status());
    }

    @Test
    void repeatedSnapshotPreservesSeparateVisitsAndTheirOrder() {
        ReconstructedEvidence evidence = TestData.evidence();
        evidence.apply(new EvidenceEvent("observe-2", new Event.ObservationRecorded(TestData.observation("o2", 2))));
        assertEquals(1, evidence.recordedStates().size());
        assertEquals(2, evidence.observationsForRecordedState("snapshot").size());
        assertEquals(List.of("o1", "o2"), evidence.observationsForSequence("run").stream().map(Observation::id).toList());
        assertTrue(evidence.recordedTransitions().isEmpty());
    }

    @Test
    void exactEventRetriesAreIdempotentAndConflictsPreserveTheEvidence() {
        ReconstructedEvidence evidence = TestData.evidence();
        EvidenceEvent event = new EvidenceEvent("check", new Event.OracleResultRecorded(TestData.check(new OracleTarget.TestSequence())));
        assertTrue(evidence.apply(event));
        assertFalse(evidence.apply(EvidenceJson.readEvent(EvidenceJson.writeEvent(event))));
        int before = evidence.eventCount();
        assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("check", new Event.TestSequenceFinished("run", "done"))));
        assertEquals(before, evidence.eventCount());
        assertFalse(evidence.testSequence("run").orElseThrow().finished());
    }

    @Test
    void selectionUsesLatestObservationAndTheExactCandidateBinding() {
        ReconstructedEvidence evidence = TestData.evidence();
        evidence.apply(new EvidenceEvent("observe-2", new Event.ObservationRecorded(TestData.observation("o2", 2))));
        assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("stale",
            new Event.ActionSelected(TestData.actionExecution("stale", "o1")))));
        assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("binding",
            new Event.ActionSelected(new ActionExecution("bad", "run", "o2", "key", "button", Properties.empty())))));
        assertEquals(0, evidence.actionExecutions().size());
    }

    @Test
    void oneOutstandingExecutionIsAllowedForEachRun() {
        ReconstructedEvidence evidence = TestData.evidence();
        TestData.select(evidence);
        assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("select-again",
            new Event.ActionSelected(TestData.actionExecution("other", "o1")))));
        assertEquals(1, evidence.actionExecutions().size());
    }

    @Test
    void rejectedSubmissionKeepsItsErrorAndAllowsAnotherSelection() {
        ReconstructedEvidence evidence = TestData.evidence();
        TestData.select(evidence);
        evidence.apply(new EvidenceEvent("reject", new Event.ActionExecutionReported(new ActionExecutionResult("execution", ActionOutcome.ERROR, "Target disappeared"))));
        assertEquals(ActionExecutionStatus.FAILED, evidence.actionExecution("execution").orElseThrow().status());
        assertEquals("Target disappeared", evidence.actionExecution("execution").orElseThrow().executionResult().error());
        evidence.apply(new EvidenceEvent("next", new Event.ActionSelected(TestData.actionExecution("next", "o1"))));
        assertEquals(2, evidence.actionExecutions().size());
        assertTrue(evidence.recordedTransitions().isEmpty());
    }

    @Test
    void resultsRequireAcceptedSubmissionAndAForwardObservationInTheSameRun() {
        ReconstructedEvidence evidence = TestData.evidence();
        TestData.select(evidence);
        assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("early", new Event.TransitionRecorded("execution", "o1"))));
        evidence.apply(new EvidenceEvent("submit", new Event.ActionExecutionReported(new ActionExecutionResult("execution", ActionOutcome.RETURNED, null))));
        assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("same", new Event.TransitionRecorded("execution", "o1"))));
        evidence.apply(new EvidenceEvent("other-sequence", new Event.TestSequenceStarted(
            new TestSequence("other", "test-run", 2, "other", Properties.empty()))));
        Observation local = TestData.observation("other-observation", 2);
        Observation other = new Observation(local.id(), "other", local.observationIndex(), local.timestampMicros(), local.recordedState(),
            local.availableActions(), local.availableActionScope(), local.artifacts());
        evidence.apply(new EvidenceEvent("other-observed", new Event.ObservationRecorded(other)));
        assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("cross", new Event.TransitionRecorded("execution", other.id()))));
        assertTrue(evidence.recordedTransitions().isEmpty());
    }

    @Test
    void finishingRunMarksOutstandingExecutionIncompleteAndAllowsLateEvaluations() {
        ReconstructedEvidence evidence = TestData.evidence();
        TestData.submit(evidence);
        evidence.apply(new EvidenceEvent("finish", new Event.TestSequenceFinished("run", "deadline")));
        assertEquals(ActionExecutionStatus.INCOMPLETE, evidence.actionExecution("execution").orElseThrow().status());
        assertTrue(evidence.apply(new EvidenceEvent("late", new Event.OracleResultRecorded(TestData.check(new OracleTarget.TestSequence())))));
        assertEquals(OracleStatus.PENDING, evidence.oracleResults().get(0).oracleStatus());
        assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("after",
            new Event.ObservationRecorded(TestData.observation("after", 2)))));
        assertTrue(evidence.recordedTransitions().isEmpty());
    }

    @Test
    void invalidEvaluationTargetsReportTheirFieldWithoutChangingTheEvidence() {
        ReconstructedEvidence evidence = TestData.evidence();
        int before = evidence.eventCount();
        for (OracleTarget target : List.of(new OracleTarget.Widget("o1", "missing"),
            new OracleTarget.Observation("missing"), new OracleTarget.ActionExecution("missing"))) {
            EvidenceException error = assertThrows(EvidenceException.class, () ->
                evidence.apply(new EvidenceEvent("invalid", new Event.OracleResultRecorded(TestData.check(target)))));
            assertEquals("invalid_oracle_target", error.code());
            assertNotNull(error.field());
            assertEquals(before, evidence.eventCount());
            assertTrue(evidence.oracleResults().isEmpty());
        }
    }

    @Test
    void propertyEvaluationMayReferToAnAbsentProperty() {
        ReconstructedEvidence evidence = TestData.evidence();
        assertTrue(evidence.apply(new EvidenceEvent("absent", new Event.OracleResultRecorded(
            TestData.check(new OracleTarget.WidgetProperty("o1", "button", "ui.alt"))))));
    }

    @Test
    void reversedEvaluationSequenceIsRejected() {
        ReconstructedEvidence evidence = TestData.evidence();
        evidence.apply(new EvidenceEvent("observe-2", new Event.ObservationRecorded(TestData.observation("o2", 2))));
        EvidenceException error = assertThrows(EvidenceException.class, () -> evidence.apply(new EvidenceEvent("reverse",
            new Event.OracleResultRecorded(TestData.check(new OracleTarget.ObservationRange("o2", "o1"))))));
        assertEquals("oracle_target.last_observation", error.field());
    }

    @Test
    void unsignedSequenceRangeIsPreserved() {
        Observation first = TestData.observation("o1", 1);
        Observation huge = new Observation("huge", "run", new BigInteger("18446744073709551615"),
            new BigInteger("18446744073709551615"), first.recordedState(), first.availableActions(), first.availableActionScope(), List.of());
        EvidenceEvent event = new EvidenceEvent("huge", new Event.ObservationRecorded(huge));
        assertEquals(event, EvidenceJson.readEvent(EvidenceJson.writeEvent(event)));
        ReconstructedEvidence evidence = TestData.evidence();
        assertTrue(evidence.apply(event));
        assertThrows(EvidenceException.class, () -> new Observation("overflow", "run", BigInteger.ONE.shiftLeft(64),
            BigInteger.ZERO, first.recordedState(), List.of(), AvailableActionScope.UNKNOWN, List.of()));
    }

    @Test
    void evidenceSnapshotsAreIndependent() {
        ReconstructedEvidence evidence = TestData.evidence();
        ReconstructedEvidence copy = evidence.snapshot();
        copy.apply(new EvidenceEvent("finish-copy", new Event.TestSequenceFinished("run", "done")));
        assertFalse(evidence.testSequence("run").orElseThrow().finished());
        assertThrows(UnsupportedOperationException.class, () -> evidence.observations().clear());
    }
}
