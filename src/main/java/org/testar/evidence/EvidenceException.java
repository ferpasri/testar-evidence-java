package org.testar.evidence;

public final class EvidenceException extends IllegalArgumentException {
    private final String code;
    private final String field;

    public EvidenceException(String code, String message) {
        this(code, null, message);
    }

    public EvidenceException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }
}
