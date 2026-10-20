package org.testar.statemodel;

public final class ModelException extends IllegalArgumentException {
    private final String code;
    private final String field;

    public ModelException(String code, String message) {
        this(code, null, message);
    }

    public ModelException(String code, String field, String message) {
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
