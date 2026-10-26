package de.medo.holonetbridge.gitea;

/**
 * Thrown by {@link GiteaConnectionValidator} when a candidate connection fails validation: unreachable host,
 * blocked by the SSRF network policy, invalid/insufficient token, or the repository does not exist. The
 * message is always safe to show a Discord user as is - it never includes the token or raw exception detail.
 */
public class GiteaConnectionValidationException extends RuntimeException {

    public GiteaConnectionValidationException(String message) {
        super(message);
    }

    public GiteaConnectionValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
