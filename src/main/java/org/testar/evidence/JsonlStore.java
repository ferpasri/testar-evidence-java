package org.testar.evidence;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JsonlStore implements AutoCloseable {
    private final Path root;
    private final WriterLock writerLock;
    private final FileChannel log;
    private final ReconstructedEvidence evidence;
    private boolean closed;
    private boolean failed;

    private JsonlStore(Path root, WriterLock writerLock, FileChannel log, ReconstructedEvidence evidence) {
        this.root = root;
        this.writerLock = writerLock;
        this.log = log;
        this.evidence = evidence;
    }

    public static JsonlStore create(Path directory, Manifest manifest) throws IOException {
        Validation.present(manifest, "Manifest");
        Files.createDirectories(directory);
        Path root = directory.toRealPath();
        WriterLock lock = WriterLock.acquire(root);
        try {
            if (Files.exists(root.resolve("manifest.json"), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(root.resolve("evidence.jsonl"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Evidence already exists; open it to continue recording");
            }
            byte[] bytes = (EvidenceJson.writeManifest(manifest) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel metadata = FileChannel.open(root.resolve("manifest.json"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                writeFully(metadata, bytes);
                metadata.force(true);
            }
            FileChannel log = FileChannel.open(root.resolve("evidence.jsonl"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new JsonlStore(root, lock, log, new ReconstructedEvidence(manifest));
        } catch (IOException | RuntimeException error) {
            lock.close();
            throw error;
        }
    }

    public static JsonlStore open(Path directory) throws IOException {
        Path root = directory.toRealPath();
        WriterLock lock = WriterLock.acquire(root);
        try {
            ReconstructedEvidence evidence = EvidenceReader.read(root);
            FileChannel log = FileChannel.open(EvidenceReader.regular(root, "evidence.jsonl"),
                StandardOpenOption.WRITE, StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
            return new JsonlStore(root, lock, log, evidence);
        } catch (IOException | RuntimeException error) {
            lock.close();
            throw error;
        }
    }

    public synchronized boolean record(EvidenceEvent event) throws IOException {
        requireWritable();
        if (!evidence.validate(event)) {
            return false;
        }
        EvidenceReader.verifyArtifacts(root, event);
        byte[] bytes = (EvidenceJson.writeEvent(event) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length - 1 > EvidenceReader.MAX_RECORD_BYTES) {
            throw new IOException("Evidence record exceeds the size limit");
        }
        try {
            writeFully(log, bytes);
            log.force(true);
        } catch (IOException error) {
            failed = true;
            throw error;
        }
        evidence.apply(event);
        return true;
    }

    public synchronized ArtifactReference putArtifact(byte[] bytes, String mediaType, long width, long height) throws IOException {
        return putArtifact(bytes, mediaType, BigInteger.valueOf(width), BigInteger.valueOf(height));
    }

    public synchronized ArtifactReference putArtifact(byte[] bytes, String mediaType, BigInteger width, BigInteger height) throws IOException {
        requireWritable();
        Validation.present(bytes, "Artifact bytes");
        Validation.text(mediaType, "Media type");
        String hash = Artifacts.hash(bytes);
        String extension = switch (mediaType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpeg";
            default -> ".bin";
        };
        ArtifactReference reference = new ArtifactReference(hash, "artifacts/" + hash + extension, mediaType, hash, width, height);
        Path path = Artifacts.resolve(root, reference.path());
        Files.createDirectories(path.getParent());
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            try (FileChannel output = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                writeFully(output, bytes);
                output.force(true);
            }
        }
        Artifacts.verify(root, reference);
        return reference;
    }

    public synchronized byte[] readArtifact(ArtifactReference reference) throws IOException {
        requireOpen();
        Artifacts.verify(root, reference);
        return Files.readAllBytes(Artifacts.resolve(root, reference.path()));
    }

    public synchronized ReconstructedEvidence evidence() {
        requireOpen();
        return evidence.snapshot();
    }

    public static long recoverTail(Path directory) throws IOException {
        Path root = directory.toRealPath();
        try (WriterLock ignored = WriterLock.acquire(root)) {
            ReconstructedEvidence evidence = new ReconstructedEvidence(EvidenceReader.manifest(root));
            long completed = EvidenceReader.replay(root, evidence, true);
            Path path = EvidenceReader.regular(root, "evidence.jsonl");
            long removed = Files.size(path) - completed;
            if (removed > 0) {
                try (FileChannel output = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    output.truncate(completed);
                    output.force(true);
                }
            }
            return removed;
        }
    }

    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Store is closed");
        }
    }

    private void requireWritable() throws IOException {
        requireOpen();
        if (failed) {
            throw new IOException("A write failed; reopen the store before continuing");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                log.close();
            } finally {
                writerLock.close();
            }
        }
    }

    private record WriterLock(FileChannel channel, FileLock lock) implements AutoCloseable {
        static WriterLock acquire(Path root) throws IOException {
            Path path = Artifacts.resolve(root, ".writer.lock");
            FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try {
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    throw new IOException("Another writer has this evidence open");
                }
                return new WriterLock(channel, lock);
            } catch (IOException | RuntimeException error) {
                channel.close();
                if (error instanceof OverlappingFileLockException) {
                    throw new IOException("Another writer has this evidence open", error);
                }
                throw error;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                lock.release();
            } finally {
                channel.close();
            }
        }
    }
}
