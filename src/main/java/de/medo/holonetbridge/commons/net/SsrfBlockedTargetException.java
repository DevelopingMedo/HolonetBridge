package de.medo.holonetbridge.commons.net;

/**
 * Thrown when a provider-connection target (host, or a specific resolved address of that host) is not safe
 * to connect to: it resolves only to blocked address ranges (loopback, private, link-local, IPv6 ULA,
 * multicast) and is not covered by the operator's {@link SsrfPolicyProperties} allowlist.
 */
public class SsrfBlockedTargetException extends RuntimeException {

    public SsrfBlockedTargetException(String message) {
        super(message);
    }

    public SsrfBlockedTargetException(String message, Throwable cause) {
        super(message, cause);
    }
}
