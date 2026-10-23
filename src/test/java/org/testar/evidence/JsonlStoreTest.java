package org.testar.evidence;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlStoreTest {
    @TempDir
    Path temporary;

    @Test
    void evidenceCanBeReopenedAndRecordingContinues() throws IOException {
        Path root = temporary.resolve("evidence");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("evidence"))) {
            seed(store);
        }
        try (JsonlStore store = JsonlStore.open(root)) {
            assertEquals(5, store.evidence().eventCount());
            store.record(new EvidenceEvent("sequence-finish", new Event.TestSequenceFinished("run", "done")));
            store.record(new EvidenceEvent("finish", new Event.TestRunFinished("test-run", "done")));
        }
        ReconstructedEvidence read = EvidenceReader.read(root);
        assertEquals(7, read.eventCount());
        assertTrue(read.testRun("test-run").orElseThrow().finished());
    }

    @Test
    void retriesAndInvalidEventsLeaveTheLogUnchanged() throws IOException {
        Path root = temporary.resolve("evidence");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("evidence"))) {
            EvidenceEvent event = start();
            assertTrue(store.record(event));
            byte[] before = Files.readAllBytes(root.resolve("evidence.jsonl"));
            assertFalse(store.record(event));
            assertThrows(EvidenceException.class, () -> store.record(new EvidenceEvent("invalid",
                new Event.ActionSelected(TestData.actionExecution("execution", "missing")))));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("evidence.jsonl")));
            assertEquals(1, store.evidence().eventCount());
        }
    }

    @Test
    void writerLockPreventsASecondWriterAndIsReleasedOnClose() throws IOException {
        Path root = temporary.resolve("evidence");
        try (JsonlStore ignored = JsonlStore.create(root, new Manifest("evidence"))) {
            assertThrows(IOException.class, () -> JsonlStore.open(root));
            assertThrows(IOException.class, () -> JsonlStore.recoverTail(root));
        }
        try (JsonlStore reopened = JsonlStore.open(root)) {
            assertEquals(0, reopened.evidence().eventCount());
        }
    }

    @Test
    void artifactsAreDeduplicatedVerifiedAndMoveWithTheEvidence() throws IOException {
        Path root = temporary.resolve("evidence");
        byte[] png = png();
        ArtifactReference image;
        try (JsonlStore store = JsonlStore.create(root, new Manifest("evidence"))) {
            image = store.putArtifact(png, "image/png", 2L, 2L);
            assertTrue(image.path().endsWith(".png"));
            assertEquals(image, store.putArtifact(png, "image/png", 2L, 2L));
            assertArrayEquals(png, store.readArtifact(image));
            seed(store);
            Observation next = TestData.observation("o2", 2);
            store.record(new EvidenceEvent("image-observed", new Event.ObservationRecorded(new Observation(next.id(), next.testSequence(),
                next.observationIndex(), next.timestampMicros(), next.recordedState(), next.availableActions(), next.availableActionScope(), List.of(image)))));
        }
        Path moved = temporary.resolve("moved");
        Files.move(root, moved);
        try (JsonlStore reopened = JsonlStore.open(moved)) {
            assertArrayEquals(png, reopened.readArtifact(image));
        }
        try (var paths = Files.list(moved.resolve("artifacts"))) {
            assertEquals(1, paths.count());
        }
        Files.write(moved.resolve(image.path()), new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> EvidenceReader.read(moved));
        assertThrows(IOException.class, () -> JsonlStore.open(moved));
    }

    @Test
    void missingArtifactRejectsObservationBeforeAppend() throws IOException {
        Path root = temporary.resolve("evidence");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("evidence"))) {
            seed(store);
            byte[] before = Files.readAllBytes(root.resolve("evidence.jsonl"));
            Observation next = TestData.observation("o2", 2);
            ArtifactReference missing = new ArtifactReference("image", "artifacts/missing.png", "image/png", "a".repeat(64), null, null);
            Observation withImage = new Observation(next.id(), next.testSequence(), next.observationIndex(), next.timestampMicros(), next.recordedState(),
                next.availableActions(), next.availableActionScope(), List.of(missing));
            assertThrows(IOException.class, () -> store.record(new EvidenceEvent("missing", new Event.ObservationRecorded(withImage))));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("evidence.jsonl")));
            assertEquals(1, store.evidence().observations().size());
        }
    }

    @Test
    void recoveryRemovesOnlyAnUnfinishedTailAfterValidatingThePrefix() throws IOException {
        Path root = temporary.resolve("evidence");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("evidence"))) {
            store.record(start());
        }
        byte[] prefix = Files.readAllBytes(root.resolve("evidence.jsonl"));
        Files.writeString(root.resolve("evidence.jsonl"), "{\"schema_", StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> JsonlStore.open(root));
        assertEquals(9, JsonlStore.recoverTail(root));
        assertArrayEquals(prefix, Files.readAllBytes(root.resolve("evidence.jsonl")));
        assertEquals(1, EvidenceReader.read(root).eventCount());
        assertEquals(0, JsonlStore.recoverTail(root));
        Files.writeString(root.resolve("evidence.jsonl"), "{}\npartial", StandardOpenOption.APPEND);
        byte[] corrupt = Files.readAllBytes(root.resolve("evidence.jsonl"));
        assertThrows(EvidenceException.class, () -> JsonlStore.recoverTail(root));
        assertArrayEquals(corrupt, Files.readAllBytes(root.resolve("evidence.jsonl")));
    }

    @Test
    void invalidUtf8IsRejectedAndExistingEvidenceIsNeverOverwritten() throws IOException {
        Path root = temporary.resolve("evidence");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("evidence"))) {
            store.record(start());
        }
        byte[] before = Files.readAllBytes(root.resolve("evidence.jsonl"));
        assertThrows(IOException.class, () -> JsonlStore.create(root, new Manifest("replacement")));
        assertArrayEquals(before, Files.readAllBytes(root.resolve("evidence.jsonl")));
        Files.write(root.resolve("evidence.jsonl"), new byte[] {(byte) 0xc3, 0x28, 0x0a}, StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> EvidenceReader.read(root));
    }

    @Test
    void aReturnedEvidenceCannotBypassStorageValidation() throws IOException {
        Path root = temporary.resolve("evidence");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("evidence"))) {
            store.record(start());
            store.evidence().apply(new EvidenceEvent("finish-copy", new Event.TestRunFinished("test-run", "done")));
            assertFalse(store.evidence().testRun("test-run").orElseThrow().finished());
            assertEquals(1, store.evidence().eventCount());
        }
    }

    private static EvidenceEvent start() {
        return new EvidenceEvent("start", new Event.TestRunStarted(new TestRun("test-run", "sample", "session", Properties.empty())));
    }

    private static void seed(JsonlStore store) throws IOException {
        store.record(start());
        store.record(new EvidenceEvent("sequence-start", new Event.TestSequenceStarted(
            new TestSequence("run", "test-run", 1, "1", Properties.empty()))));
        store.record(new EvidenceEvent("activate", new Event.ActionDescribed(new ActionDescription("activate", "activate", Properties.empty()))));
        store.record(new EvidenceEvent("key", new Event.ActionDescribed(new ActionDescription("key", "key", Properties.empty()))));
        store.record(new EvidenceEvent("observed", new Event.ObservationRecorded(TestData.observation("o1", 1))));
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", output));
        return output.toByteArray();
    }
}
