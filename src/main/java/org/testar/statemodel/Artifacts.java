package org.testar.statemodel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class Artifacts {
    private Artifacts() {
    }

    static Path resolve(Path root, String relative) throws IOException {
        ArtifactReference.validatePath(relative);
        Path current = root;
        for (String part : relative.split("/")) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Evidence paths cannot contain symbolic links: " + relative);
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !current.toRealPath().startsWith(root)) {
                throw new IOException("Evidence path leaves its model directory: " + relative);
            }
        }
        return current;
    }

    static void verify(Path root, ArtifactReference artifact) throws IOException {
        Path path = resolve(root, artifact.path());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing artifact: " + artifact.path());
        }
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(artifact.sha256())) {
            throw new IOException("Artifact SHA-256 mismatch: " + artifact.path());
        }
    }

    static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
