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
import org.testar.evidence.StateModel;

class LocalModelTest {
    @TempDir
    Path parent;

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
