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
import org.testar.evidence.StateModel;

class LocalModelTest {
    @TempDir
    Path parent;

    @Test
    void omittedDescriptionsRemainAbsentAfterReopening() throws Exception {
        Path directory = LocalModel.recordTrace(parent, "demo", null);
        StateModel model = EvidenceReader.read(directory);
        Properties properties = model.runs().get(0).run().properties();
        assertEquals(1, properties.asJson().size());
        assertTrue(properties.contains("example.sut"));
        assertTrue(directory.getFileName().toString().startsWith("demo_run_trace-"));
    }

    @Test
    void descriptionsPreserveFlexibleLabelsUnicodeAndSpacing() throws Exception {
        Path directory = LocalModel.recordTrace(parent, "demo", "  Request loan — prêt  ", "  QA équipe / custom CI  ");
        StateModel model = EvidenceReader.read(directory);
        Properties properties = model.runs().get(0).run().properties();
        assertEquals("  QA équipe / custom CI  ", properties.get("evidence.origin").asText());
        assertEquals("  Request loan — prêt  ", properties.get("evidence.test_objective").asText());
        assertEquals(3, properties.asJson().size());
    }

    @Test
    void repeatedRunsHaveIndependentRecordsAndPreserveTheEarlierTrace() throws Exception {
        Path first = LocalModel.recordTrace(parent, "demo", "submit-form");
        byte[] original = Files.readAllBytes(first.resolve("evidence.jsonl"));
        Path second = LocalModel.recordTrace(parent, "demo", "submit-form");
        StateModel left = EvidenceReader.read(first);
        StateModel right = EvidenceReader.read(second);
        assertNotEquals(first, second);
        assertNotEquals(left.manifest().model(), right.manifest().model());
        assertNotEquals(left.runs().get(0).run().id(), right.runs().get(0).run().id());
        assertEquals(1, left.links().size());
        assertEquals(1, right.links().size());
        assertTrue(Arrays.equals(original, Files.readAllBytes(first.resolve("evidence.jsonl"))));
    }
}
