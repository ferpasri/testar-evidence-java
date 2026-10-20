package org.testar.statemodel.evidence;

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
        Path root = temporary.resolve("model");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("model"))) {
            seed(store);
        }
        try (JsonlStore store = JsonlStore.open(root)) {
            assertEquals(4, store.model().eventCount());
            store.record(new EventEnvelope("finish", new Event.RunFinished("run", "done")));
        }
        StateModel read = EvidenceReader.read(root);
        assertEquals(5, read.eventCount());
        assertTrue(read.run("run").orElseThrow().finished());
    }

    @Test
    void retriesAndInvalidEventsLeaveTheLogUnchanged() throws IOException {
        Path root = temporary.resolve("model");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("model"))) {
            EventEnvelope event = start();
            assertTrue(store.record(event));
            byte[] before = Files.readAllBytes(root.resolve("evidence.jsonl"));
            assertFalse(store.record(event));
            assertThrows(ModelException.class, () -> store.record(new EventEnvelope("invalid",
                new Event.ActionSelected(TestData.execution("execution", "missing")))));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("evidence.jsonl")));
            assertEquals(1, store.model().eventCount());
        }
    }

    @Test
    void writerLockPreventsASecondWriterAndIsReleasedOnClose() throws IOException {
        Path root = temporary.resolve("model");
        try (JsonlStore ignored = JsonlStore.create(root, new Manifest("model"))) {
            assertThrows(IOException.class, () -> JsonlStore.open(root));
            assertThrows(IOException.class, () -> JsonlStore.recoverTail(root));
        }
        try (JsonlStore reopened = JsonlStore.open(root)) {
            assertEquals(0, reopened.model().eventCount());
        }
    }

    @Test
    void artifactsAreDeduplicatedVerifiedAndMoveWithTheModel() throws IOException {
        Path root = temporary.resolve("model");
        byte[] png = png();
        ArtifactReference image;
        try (JsonlStore store = JsonlStore.create(root, new Manifest("model"))) {
            image = store.putArtifact(png, "image/png", 2L, 2L);
            assertTrue(image.path().endsWith(".png"));
            assertEquals(image, store.putArtifact(png, "image/png", 2L, 2L));
            assertArrayEquals(png, store.readArtifact(image));
            seed(store);
            Observation next = TestData.observation("o2", 2);
            store.record(new EventEnvelope("image-observed", new Event.Observed(new Observation(next.id(), next.run(),
                next.sequence(), next.timestampMicros(), next.state(), next.candidates(), next.candidateScope(), List.of(image)))));
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
        Path root = temporary.resolve("model");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("model"))) {
            seed(store);
            byte[] before = Files.readAllBytes(root.resolve("evidence.jsonl"));
            Observation next = TestData.observation("o2", 2);
            ArtifactReference missing = new ArtifactReference("image", "artifacts/missing.png", "image/png", "a".repeat(64), null, null);
            Observation withImage = new Observation(next.id(), next.run(), next.sequence(), next.timestampMicros(), next.state(),
                next.candidates(), next.candidateScope(), List.of(missing));
            assertThrows(IOException.class, () -> store.record(new EventEnvelope("missing", new Event.Observed(withImage))));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("evidence.jsonl")));
            assertEquals(1, store.model().observations().size());
        }
    }

    @Test
    void recoveryRemovesOnlyAnUnfinishedTailAfterValidatingThePrefix() throws IOException {
        Path root = temporary.resolve("model");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("model"))) {
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
        assertThrows(ModelException.class, () -> JsonlStore.recoverTail(root));
        assertArrayEquals(corrupt, Files.readAllBytes(root.resolve("evidence.jsonl")));
    }

    @Test
    void invalidUtf8IsRejectedAndExistingEvidenceIsNeverOverwritten() throws IOException {
        Path root = temporary.resolve("model");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("model"))) {
            store.record(start());
        }
        byte[] before = Files.readAllBytes(root.resolve("evidence.jsonl"));
        assertThrows(IOException.class, () -> JsonlStore.create(root, new Manifest("replacement")));
        assertArrayEquals(before, Files.readAllBytes(root.resolve("evidence.jsonl")));
        Files.write(root.resolve("evidence.jsonl"), new byte[] {(byte) 0xc3, 0x28, 0x0a}, StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> EvidenceReader.read(root));
    }

    @Test
    void aReturnedModelCannotBypassStorageValidation() throws IOException {
        Path root = temporary.resolve("model");
        try (JsonlStore store = JsonlStore.create(root, new Manifest("model"))) {
            store.record(start());
            store.model().apply(new EventEnvelope("finish-copy", new Event.RunFinished("run", "done")));
            assertFalse(store.model().run("run").orElseThrow().finished());
            assertEquals(1, store.model().eventCount());
        }
    }

    private static EventEnvelope start() {
        return new EventEnvelope("start", new Event.RunStarted(new Run("run", "sample", "session", Properties.empty())));
    }

    private static void seed(JsonlStore store) throws IOException {
        store.record(start());
        store.record(new EventEnvelope("activate", new Event.ActionDefined(new ActionDefinition("activate", "activate", Properties.empty()))));
        store.record(new EventEnvelope("key", new Event.ActionDefined(new ActionDefinition("key", "key", Properties.empty()))));
        store.record(new EventEnvelope("observed", new Event.Observed(TestData.observation("o1", 1))));
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", output));
        return output.toByteArray();
    }
}
