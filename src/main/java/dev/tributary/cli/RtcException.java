package dev.tributary.cli;

public final class RtcException extends Exception {

    public enum Kind {
        NOT_A_SANDBOX,
        NOT_FOUND,
        LOCKED,
        AUTH_REQUIRED,
        CONFLICT,
        UNCOMMITTED_CHANGES,
        PROCESS_REJECTED,
        TIMEOUT,
        CANCELLED,
        CLI_MISSING,
        FAILED
    }

    private final Kind kind;
    private final String details;

    public RtcException(Kind kind, String message) {
        this(kind, message, "");
    }

    public RtcException(Kind kind, String message, String details) {
        super(message);
        this.kind = kind;
        this.details = details == null ? "" : details;
    }

    public Kind kind() {
        return kind;
    }

    public String details() {
        return details;
    }
}
