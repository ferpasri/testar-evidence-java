package org.testar.statemodel.evidence;

import java.util.Locale;

public record ArtifactReference(String id, String path, String mediaType, String sha256, Long width, Long height) {
    public ArtifactReference {
        Validation.text(id, "Artifact ID");
        Validation.text(mediaType, "Media type");
        validatePath(path);
        Validation.require(sha256 != null && sha256.matches("[0-9a-fA-F]{64}"),
            "invalid_record", "Artifact SHA-256 must have 64 hexadecimal characters");
        Validation.require(width == null || width > 0 && width <= 0xffff_ffffL,
            "invalid_record", "Width must be a positive unsigned 32-bit integer");
        Validation.require(height == null || height > 0 && height <= 0xffff_ffffL,
            "invalid_record", "Height must be a positive unsigned 32-bit integer");
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
