package de.medo.holonetbridge.commons.crypto;

/**
 * Thrown by {@link MasterKeyProvider} when no usable key is configured for the requested version (missing,
 * not valid Base64, or not 256 bits). Messages intentionally only ever reference the key <em>version</em>
 * identifier, never the configured key material itself.
 */
public class MasterKeyUnavailableException extends RuntimeException {

    public MasterKeyUnavailableException(String message) {
        super(message);
    }
}
