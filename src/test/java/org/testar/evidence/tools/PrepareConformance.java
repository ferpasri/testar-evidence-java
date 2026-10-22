package org.testar.evidence.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.testar.evidence.EvidenceJson;

public final class PrepareConformance {
    private static final String API = "https://api.github.com/repos/ferpasri/testar-evidence-specs/releases";
    private static final int MEBIBYTE = 1024 * 1024;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();

    private PrepareConformance() {
    }

    public static void main(String[] arguments) throws Exception {
        JsonNode release = latestRelease();
        byte[] archive = download(URI.create(release.path("zipball_url").asText()), 64 * MEBIBYTE, "application/zip");
        Path cache = Path.of("target", "conformance").toAbsolutePath();
        Files.createDirectories(cache);
        String name = "release-" + release.path("id").asText() + "-" + UUID.randomUUID();
        Path root = Files.createDirectory(cache.resolve(name));
        unpack(archive, root);
        Path cases = root.resolve("conformance/cases");
        long count;
        try (var paths = Files.list(cases)) {
            count = paths.filter(Files::isDirectory).peek(directory -> {
                for (String required : new String[] {"manifest.json", "evidence.jsonl", "expected.json"}) {
                    if (!Files.isRegularFile(directory.resolve(required))) {
                        throw new IllegalStateException("Incomplete conformance case: " + directory);
                    }
                }
            }).count();
        }
        if (count == 0) {
            throw new IOException("Release has no conformance cases");
        }
        TreeMap<String, String> hashes = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                hashes.put(root.relativize(path).toString().replace('\\', '/'), sha256(Files.readAllBytes(path)));
            }
        }
        ObjectNode metadata = (ObjectNode) EvidenceJson.parse("{}");
        metadata.put("root", name);
        metadata.put("tag", release.path("tag_name").asText());
        metadata.put("release_id", release.path("id").asText());
        metadata.put("release_url", release.path("html_url").asText());
        metadata.put("published_at", release.path("published_at").asText());
        metadata.put("archive_sha256", sha256(archive));
        metadata.set("files", EvidenceJson.tree(hashes));
        Path temporary = cache.resolve("current-" + UUID.randomUUID() + ".json");
        Files.writeString(temporary, EvidenceJson.write(metadata) + "\n", StandardCharsets.UTF_8);
        Files.move(temporary, cache.resolve("current.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Prepared " + count + " shared conformance cases from " + metadata.path("tag").asText());
        System.out.println("Cache: " + cache);
    }

    private static JsonNode latestRelease() throws Exception {
        JsonNode latest = null;
        for (int page = 1; page <= 20; page++) {
            JsonNode releases = EvidenceJson.parse(new String(download(URI.create(API + "?per_page=100&page=" + page),
                4 * MEBIBYTE, "application/vnd.github+json"), StandardCharsets.UTF_8));
            if (!releases.isArray()) {
                throw new IOException("GitHub releases response is not an array");
            }
            latest = selectLatest(releases, latest);
            if (releases.size() < 100) {
                if (latest == null) {
                    throw new IOException("No published specs release was found");
                }
                return latest;
            }
        }
        throw new IOException("Release listing exceeds the pagination limit");
    }

    static JsonNode selectLatest(JsonNode releases, JsonNode latest) {
        Comparator<JsonNode> order = Comparator.comparing((JsonNode value) -> Instant.parse(value.path("published_at").asText()))
            .thenComparing(value -> value.path("id").bigIntegerValue());
        for (JsonNode release : releases) {
            if (!release.path("draft").asBoolean() && release.hasNonNull("published_at")
                && (latest == null || order.compare(release, latest) > 0)) {
                latest = release;
            }
        }
        return latest;
    }

    private static byte[] download(URI uri, int limit, String accept) throws Exception {
        if (!"https".equals(uri.getScheme())) {
            throw new IOException("Release downloads require HTTPS");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
            .header("User-Agent", "testar-evidence-java-conformance")
            .header("Accept", accept).GET().build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Download failed: HTTP " + response.statusCode() + " from " + uri);
            }
            return bounded(input, limit);
        }
    }

    static void unpack(byte[] archive, Path root) throws IOException {
        int entries = 0;
        long expanded = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > 10000) {
                    throw new IOException("Release archive contains too many entries");
                }
                String name = entry.getName();
                int separator = name.indexOf('/');
                if (separator < 0 || entry.isDirectory()) {
                    continue;
                }
                String relative = name.substring(separator + 1);
                if (!relative.startsWith("conformance/")) {
                    expanded += bounded(input, 32 * MEBIBYTE).length;
                    if (expanded > 128L * MEBIBYTE) {
                        throw new IOException("Expanded archive exceeds the size limit");
                    }
                    continue;
                }
                if (relative.contains("\\") || relative.contains(":")) {
                    throw new IOException("Unsafe release path");
                }
                for (String part : relative.split("/", -1)) {
                    if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                        throw new IOException("Unsafe release path");
                    }
                }
                byte[] bytes = bounded(input, 32 * MEBIBYTE);
                expanded += bytes.length;
                if (expanded > 128L * MEBIBYTE) {
                    throw new IOException("Expanded conformance data exceeds the size limit");
                }
                Path path = root.resolve(relative);
                Files.createDirectories(path.getParent());
                Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
        }
    }

    private static byte[] bounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if ((long) output.size() + count > limit) {
                throw new IOException("Download or archive entry exceeds the size limit");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
