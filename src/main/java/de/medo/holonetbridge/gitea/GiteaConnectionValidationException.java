package de.medo.holonetbridge.gitea;

public class GiteaConnectionValidationException extends RuntimeException {

    public enum Reason { BLOCKED, UNREACHABLE, ACCESS, NOT_FOUND, REJECTED }

    private final Reason reason;
    private final int status;

    public GiteaConnectionValidationException(Reason reason, Throwable cause) {
        this(reason, 0, cause);
    }

    public GiteaConnectionValidationException(Reason reason, int status, Throwable cause) {
        super("Gitea connection validation failed: " + reason + " (HTTP " + status + ")", cause);
        this.reason = reason;
        this.status = status;
    }

    public Reason reason() {
        return reason;
    }

    public int status() {
        return status;
    }
}
