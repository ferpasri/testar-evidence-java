package org.testar.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.testar.evidence.tools.PrepareConformance;

final class ConformanceData {
    private ConformanceData() {
    }

    static List<Path> cases() throws IOException {
        Path cache = Path.of("target", "conformance").toAbsolutePath();
        Path current = cache.resolve("current.json");
        if (!Files.isRegularFile(current)) {
            throw new IOException("Prepare shared cases first: see README.md, Testing");
        }
        JsonNode metadata = EvidenceJson.parse(Files.readString(current));
        String folder = metadata.path("root").asText();
        ArtifactReference.validatePath(folder);
        Path root = cache.resolve(folder).toRealPath();
        if (!root.startsWith(cache.toRealPath())) {
            throw new IOException("Conformance cache path leaves its directory");
        }
        Set<Path> expected = new HashSet<>();
        var fields = metadata.path("files").properties().iterator();
        while (fields.hasNext()) {
            var entry = fields.next();
            Path path = Artifacts.resolve(root, entry.getKey());
            if (!PrepareConformance.sha256(Files.readAllBytes(path)).equals(entry.getValue().asText())) {
                throw new IOException("Conformance checksum mismatch: " + path);
            }
            expected.add(path);
        }
        try (var paths = Files.walk(root)) {
            Set<Path> actual = new HashSet<>(paths.filter(Files::isRegularFile).toList());
            if (!actual.equals(expected) || expected.isEmpty()) {
                throw new IOException("Conformance cache contents differ from the prepared release");
            }
        }
        try (var paths = Files.list(root.resolve("conformance/cases"))) {
            List<Path> cases = paths.filter(Files::isDirectory).sorted().toList();
            if (cases.isEmpty()) {
                throw new IOException("Release has no conformance cases");
            }
            for (Path directory : cases) {
                for (String name : List.of("manifest.json", "evidence.jsonl", "expected.json")) {
                    if (!Files.isRegularFile(directory.resolve(name))) {
                        throw new IOException("Incomplete released case: " + directory);
                    }
                }
            }
            return cases;
        }
    }
}
