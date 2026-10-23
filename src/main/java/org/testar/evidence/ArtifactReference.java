package org.testar.evidence;

import java.util.Locale;
import java.math.BigInteger;

public record ArtifactReference(String id, String path, String mediaType, String sha256, BigInteger width, BigInteger height) {
    public ArtifactReference {
        Validation.text(id, "Artifact ID");
        Validation.text(mediaType, "Media type");
        validatePath(path);
        Validation.require(sha256 != null && sha256.matches("[0-9a-f]{64}"),
            "invalid_record", "Artifact SHA-256 must have 64 hexadecimal characters");
        if (width != null) {
            Validation.unsigned(width, "Width");
        }
        if (height != null) {
            Validation.unsigned(height, "Height");
        }
    }

    public ArtifactReference(String id, String path, String mediaType, String sha256, long width, long height) {
        this(id, path, mediaType, sha256, BigInteger.valueOf(width), BigInteger.valueOf(height));
    }

    static void validatePath(String path) {
        Validation.text(path, "Artifact path");
        Validation.require(!path.startsWith("/") && !path.contains("\\") && !path.contains(":"),
            "invalid_record", "Artifact path must be portable and relative");
        for (String part : path.split("/", -1)) {
            String stem = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            boolean device = stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");
            Validation.require(!part.isEmpty() && !part.equals(".") && !part.equals("..")
                && !part.endsWith(".") && !part.endsWith(" ") && !part.matches(".*[<>\"|?*].*") && !device,
                "invalid_record", "Unsafe artifact path: " + path);
        }
    }
}
