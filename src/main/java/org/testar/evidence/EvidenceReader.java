package org.testar.evidence;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class EvidenceReader {
    static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;

    private EvidenceReader() {
    }

    public static ReconstructedEvidence read(Path directory) throws IOException {
        Path root = directory.toRealPath();
        ReconstructedEvidence evidence = new ReconstructedEvidence(manifest(root));
        replay(root, evidence, false);
        return evidence;
    }

    static Manifest manifest(Path root) throws IOException {
        Path path = regular(root, "manifest.json");
        if (Files.size(path) > MAX_RECORD_BYTES) {
            throw new IOException("Manifest exceeds the size limit");
        }
        return EvidenceJson.readManifest(decode(Files.readAllBytes(path)));
    }

    static Path regular(Path root, String name) throws IOException {
        Path path = Artifacts.resolve(root, name);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing regular evidence file: " + name);
        }
        return path;
    }

    static long replay(Path root, ReconstructedEvidence evidence, boolean allowIncompleteTail) throws IOException {
        long offset = 0;
        long completed = 0;
        int lineNumber = 1;
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(regular(root, "evidence.jsonl")))) {
            int next;
            while ((next = input.read()) != -1) {
                offset++;
                if (next == '\n') {
                    String json = decode(line.toByteArray());
                    try {
                        EvidenceEvent event = EvidenceJson.readEvent(json);
                        boolean newEvent = evidence.validate(event);
                        verifyArtifacts(root, event);
                        if (newEvent) {
                            evidence.applyValidated(event);
                        }
                    } catch (EvidenceException error) {
                        throw new EvidenceException(error.code(), error.field(),
                            "evidence.jsonl line " + lineNumber + ": " + error.getMessage());
                    }
                    line.reset();
                    completed = offset;
                    lineNumber++;
                } else {
                    if (line.size() >= MAX_RECORD_BYTES) {
                        throw new IOException("Evidence record exceeds the size limit at line " + lineNumber);
                    }
                    line.write(next);
                }
            }
        }
        if (line.size() != 0 && !allowIncompleteTail) {
            throw new IOException("Evidence has an unfinished final line; use recoverTail before reopening");
        }
        return completed;
    }

    static void verifyArtifacts(Path root, EvidenceEvent event) throws IOException {
        if (event.event() instanceof Event.ObservationRecorded observed) {
            for (ArtifactReference artifact : observed.data().artifacts()) {
                Artifacts.verify(root, artifact);
            }
        }
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
}
