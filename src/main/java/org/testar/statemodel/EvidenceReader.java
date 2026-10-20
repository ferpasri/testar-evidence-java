package org.testar.statemodel;

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

    public static StateModel read(Path directory) throws IOException {
        Path root = directory.toRealPath();
        StateModel model = new StateModel(manifest(root));
        replay(root, model, false);
        return model;
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

    static long replay(Path root, StateModel model, boolean allowIncompleteTail) throws IOException {
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
                        EventEnvelope event = EvidenceJson.readEvent(json);
                        model.validate(event);
                        verifyArtifacts(root, event);
                        model.apply(event);
                    } catch (ModelException error) {
                        throw new ModelException(error.code(), error.field(),
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

    static void verifyArtifacts(Path root, EventEnvelope event) throws IOException {
        if (event.event() instanceof Event.Observed observed) {
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
