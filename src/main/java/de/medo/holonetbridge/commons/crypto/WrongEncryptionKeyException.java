package de.medo.holonetbridge.commons.crypto;

/**
 * Thrown by {@link EncryptionService#decrypt(String)} when the ciphertext was not encrypted with the
 * currently configured master key, or has been corrupted or tampered with - both cases surface identically
 * as an AES-GCM authentication-tag failure.
 */
public class WrongEncryptionKeyException extends RuntimeException {

    public WrongEncryptionKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
