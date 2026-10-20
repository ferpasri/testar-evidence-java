package org.testar.statemodel.evidence.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testar.statemodel.evidence.EvidenceJson;

class PrepareConformanceTest {
    @TempDir
    Path temporary;

    @Test
    void releaseSelectionUsesPublicationTimeIncludesPrereleasesAndIgnoresDrafts() {
        JsonNode releases = EvidenceJson.parse("""
            [
                {"id":1,"tag_name":"v9.0.0","published_at":"2026-01-01T00:00:00Z","draft":false},
                {"id":2,"tag_name":"v0.2.0","published_at":"2026-02-01T00:00:00Z","prerelease":true,"draft":false},
                {"id":3,"tag_name":"v0.3.0","published_at":"2026-03-01T00:00:00Z","draft":true},
                {"id":4,"tag_name":"unpublished","published_at":null,"draft":false}
            ]
            """);
        JsonNode newest = PrepareConformance.selectLatest(releases, null);
        assertEquals("v0.2.0", newest.path("tag_name").asText());
        assertEquals(newest, PrepareConformance.selectLatest(EvidenceJson.parse("[]"), newest));
        assertNull(PrepareConformance.selectLatest(EvidenceJson.parse("[]"), null));
    }

    @Test
    void publicationTiesUseReleaseId() {
        JsonNode releases = EvidenceJson.parse("""
            [
                {"id":5,"published_at":"2026-01-01T00:00:00Z","draft":false},
                {"id":8,"published_at":"2026-01-01T00:00:00Z","draft":false}
            ]
            """);
        assertEquals(8, PrepareConformance.selectLatest(releases, null).path("id").intValue());
    }

    @Test
    void extractionKeepsOnlyConformanceDataAndRejectsTraversal() throws IOException {
        byte[] valid = zip(Map.of("repo/conformance/cases/sample/manifest.json", "{}",
            "repo/README.md", "Repository documentation"));
        PrepareConformance.unpack(valid, temporary);
        assertEquals("{}", Files.readString(temporary.resolve("conformance/cases/sample/manifest.json")));
        assertFalse(Files.exists(temporary.resolve("README.md")));
        byte[] unsafe = zip(Map.of("repo/conformance/../../outside.txt", "outside"));
        assertThrows(IOException.class, () -> PrepareConformance.unpack(unsafe, temporary));
        assertFalse(Files.exists(temporary.getParent().resolve("outside.txt")));
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
