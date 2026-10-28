package de.medo.holonetbridge.commons.provider;

public class ProviderHealthCheckException extends RuntimeException {

    public enum Reason {
        INVALID_URL,
        TOKEN_REQUIRED,
        UNSUPPORTED,
        UNREACHABLE,
        ACCESS,
        INVALID_RESPONSE,
        REJECTED
    }

    private final Reason reason;

    public ProviderHealthCheckException(Reason reason) {
        // HTTP exception messages and response bodies can contain credentials; do not retain them.
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
