package org.testar.evidence;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class StateModelTest {
    @Test
    void discoveredCandidatesRemainAvailableWhenOnlyOneIsExecuted() {
        StateModel model = TestData.model();
        TestData.submit(model);
        model.apply(new EvidenceEvent("observe-2", new Event.Observed(TestData.observation("o2", 2))));
        model.apply(new EvidenceEvent("result", new Event.ActionResult("execution", "o2")));
        assertEquals(3, model.candidates("o1").size());
        assertEquals(1, model.executions().size());
        assertTrue(model.candidates("o1").contains(new ActionCandidate("activate", "link")));
        assertTrue(model.candidates("o1").contains(new ActionCandidate("key", null)));
        assertEquals(List.of(new ExecutionLink("execution", "run", "o1", "activate", "o2")), model.outgoing("o1"));
        assertEquals(model.outgoing("o1"), model.incoming("o2"));
        assertEquals(ExecutionStatus.OBSERVED, model.execution("execution").orElseThrow().status());
    }

    @Test
    void repeatedSnapshotPreservesSeparateVisitsAndTheirOrder() {
        StateModel model = TestData.model();
        model.apply(new EvidenceEvent("observe-2", new Event.Observed(TestData.observation("o2", 2))));
        assertEquals(1, model.snapshots().size());
        assertEquals(2, model.observationsForSnapshot("snapshot").size());
        assertEquals(List.of("o1", "o2"), model.observationsForRun("run").stream().map(Observation::id).toList());
        assertTrue(model.links().isEmpty());
    }

    @Test
    void exactEventRetriesAreIdempotentAndConflictsPreserveTheModel() {
        StateModel model = TestData.model();
        EvidenceEvent event = new EvidenceEvent("check", new Event.Checked(TestData.check(new CheckTarget.Run())));
        assertTrue(model.apply(event));
        assertFalse(model.apply(EvidenceJson.readEvent(EvidenceJson.writeEvent(event))));
        int before = model.eventCount();
        assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("check", new Event.RunFinished("run", "done"))));
        assertEquals(before, model.eventCount());
        assertFalse(model.run("run").orElseThrow().finished());
    }

    @Test
    void selectionUsesLatestObservationAndTheExactCandidateBinding() {
        StateModel model = TestData.model();
        model.apply(new EvidenceEvent("observe-2", new Event.Observed(TestData.observation("o2", 2))));
        assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("stale",
            new Event.ActionSelected(TestData.execution("stale", "o1")))));
        assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("binding",
            new Event.ActionSelected(new Execution("bad", "run", "o2", "key", "button", Properties.empty())))));
        assertEquals(0, model.executions().size());
    }

    @Test
    void oneOutstandingExecutionIsAllowedForEachRun() {
        StateModel model = TestData.model();
        TestData.select(model);
        assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("select-again",
            new Event.ActionSelected(TestData.execution("other", "o1")))));
        assertEquals(1, model.executions().size());
    }

    @Test
    void rejectedSubmissionKeepsItsErrorAndAllowsAnotherSelection() {
        StateModel model = TestData.model();
        TestData.select(model);
        model.apply(new EvidenceEvent("reject", new Event.ActionSubmitted("execution", false, "Target disappeared")));
        assertEquals(ExecutionStatus.REJECTED, model.execution("execution").orElseThrow().status());
        assertEquals("Target disappeared", model.execution("execution").orElseThrow().error());
        model.apply(new EvidenceEvent("next", new Event.ActionSelected(TestData.execution("next", "o1"))));
        assertEquals(2, model.executions().size());
        assertTrue(model.links().isEmpty());
    }

    @Test
    void resultsRequireAcceptedSubmissionAndAForwardObservationInTheSameRun() {
        StateModel model = TestData.model();
        TestData.select(model);
        assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("early", new Event.ActionResult("execution", "o1"))));
        model.apply(new EvidenceEvent("submit", new Event.ActionSubmitted("execution", true, null)));
        assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("same", new Event.ActionResult("execution", "o1"))));
        model.apply(new EvidenceEvent("other-run", new Event.RunStarted(new Run("other", "sample", "other", Properties.empty()))));
        Observation local = TestData.observation("other-observation", 2);
        Observation other = new Observation(local.id(), "other", local.sequence(), local.timestampMicros(), local.state(),
            local.candidates(), local.candidateScope(), local.artifacts());
        model.apply(new EvidenceEvent("other-observed", new Event.Observed(other)));
        assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("cross", new Event.ActionResult("execution", other.id()))));
        assertTrue(model.links().isEmpty());
    }

    @Test
    void finishingRunMarksOutstandingExecutionIncompleteAndAllowsLateEvaluations() {
        StateModel model = TestData.model();
        TestData.submit(model);
        model.apply(new EvidenceEvent("finish", new Event.RunFinished("run", "deadline")));
        assertEquals(ExecutionStatus.INCOMPLETE, model.execution("execution").orElseThrow().status());
        assertTrue(model.apply(new EvidenceEvent("late", new Event.Checked(TestData.check(new CheckTarget.Run())))));
        assertEquals(CheckStatus.PENDING, model.checks().get(0).status());
        assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("after",
            new Event.Observed(TestData.observation("after", 2)))));
        assertTrue(model.links().isEmpty());
    }

    @Test
    void invalidEvaluationTargetsReportTheirFieldWithoutChangingTheModel() {
        StateModel model = TestData.model();
        int before = model.eventCount();
        for (CheckTarget target : List.of(new CheckTarget.Widget("o1", "missing"),
            new CheckTarget.Observation("missing"), new CheckTarget.Execution("missing"))) {
            ModelException error = assertThrows(ModelException.class, () ->
                model.apply(new EvidenceEvent("invalid", new Event.Checked(TestData.check(target)))));
            assertEquals("invalid_check_target", error.code());
            assertNotNull(error.field());
            assertEquals(before, model.eventCount());
            assertTrue(model.checks().isEmpty());
        }
    }

    @Test
    void propertyEvaluationMayReferToAnAbsentProperty() {
        StateModel model = TestData.model();
        assertTrue(model.apply(new EvidenceEvent("absent", new Event.Checked(
            TestData.check(new CheckTarget.WidgetProperty("o1", "button", "ui.alt"))))));
    }

    @Test
    void reversedEvaluationSequenceIsRejected() {
        StateModel model = TestData.model();
        model.apply(new EvidenceEvent("observe-2", new Event.Observed(TestData.observation("o2", 2))));
        ModelException error = assertThrows(ModelException.class, () -> model.apply(new EvidenceEvent("reverse",
            new Event.Checked(TestData.check(new CheckTarget.Sequence("o2", "o1"))))));
        assertEquals("target.to_observation", error.field());
    }

    @Test
    void unsignedSequenceRangeIsPreserved() {
        Observation first = TestData.observation("o1", 1);
        Observation huge = new Observation("huge", "run", new BigInteger("18446744073709551615"),
            new BigInteger("18446744073709551615"), first.state(), first.candidates(), first.candidateScope(), List.of());
        EvidenceEvent event = new EvidenceEvent("huge", new Event.Observed(huge));
        assertEquals(event, EvidenceJson.readEvent(EvidenceJson.writeEvent(event)));
        StateModel model = TestData.model();
        assertTrue(model.apply(event));
        assertThrows(ModelException.class, () -> new Observation("overflow", "run", BigInteger.ONE.shiftLeft(64),
            BigInteger.ZERO, first.state(), List.of(), CandidateScope.UNKNOWN, List.of()));
    }

    @Test
    void modelSnapshotsAreIndependent() {
        StateModel model = TestData.model();
        StateModel copy = model.snapshot();
        copy.apply(new EvidenceEvent("finish-copy", new Event.RunFinished("run", "done")));
        assertFalse(model.run("run").orElseThrow().finished());
        assertThrows(UnsupportedOperationException.class, () -> model.observations().clear());
    }
}
