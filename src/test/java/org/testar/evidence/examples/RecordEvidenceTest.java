package org.testar.evidence.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testar.evidence.EvidenceReader;
import org.testar.evidence.Properties;
import org.testar.evidence.ReconstructedEvidence;

class RecordEvidenceTest {
    @TempDir
    Path parent;

    @Test
    void omittedDescriptionsRemainAbsentAfterReopening() throws Exception {
        Path directory = RecordEvidence.recordTrace(parent, "demo", null);
        ReconstructedEvidence evidence = EvidenceReader.read(directory);
        Properties properties = evidence.testRuns().get(0).testRun().properties();
        assertEquals(1, properties.asJson().size());
        assertTrue(properties.contains("example.sut"));
        assertTrue(directory.getFileName().toString().startsWith("demo_run_trace-"));
    }

    @Test
    void descriptionsPreserveFlexibleLabelsUnicodeAndSpacing() throws Exception {
        Path directory = RecordEvidence.recordTrace(parent, "demo", "  Request loan — prêt  ", "  QA équipe / custom CI  ");
        ReconstructedEvidence evidence = EvidenceReader.read(directory);
        Properties properties = evidence.testRuns().get(0).testRun().properties();
        assertEquals("  QA équipe / custom CI  ", properties.get("evidence.origin").asText());
        assertEquals("  Request loan — prêt  ", properties.get("evidence.test_objective").asText());
        assertEquals(3, properties.asJson().size());
    }

    @Test
    void repeatedRunsHaveIndependentRecordsAndPreserveTheEarlierTrace() throws Exception {
        Path first = RecordEvidence.recordTrace(parent, "demo", "submit-form");
        byte[] original = Files.readAllBytes(first.resolve("evidence.jsonl"));
        Path second = RecordEvidence.recordTrace(parent, "demo", "submit-form");
        ReconstructedEvidence left = EvidenceReader.read(first);
        ReconstructedEvidence right = EvidenceReader.read(second);
        assertNotEquals(first, second);
        assertNotEquals(left.manifest().evidenceId(), right.manifest().evidenceId());
        assertNotEquals(left.testRuns().get(0).testRun().id(), right.testRuns().get(0).testRun().id());
        assertEquals(1, left.recordedTransitions().size());
        assertEquals(1, right.recordedTransitions().size());
        assertTrue(Arrays.equals(original, Files.readAllBytes(first.resolve("evidence.jsonl"))));
    }
}
