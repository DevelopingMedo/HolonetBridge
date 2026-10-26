package de.medo.holonetbridge.commons.crypto;

/**
 * Thrown when a token fails to encrypt or decrypt. In particular covers AES-GCM authentication-tag failures
 * on decryption: a tampered ciphertext/nonce, or a {@link TokenEncryptionContext} that does not match the
 * one the token was encrypted under, both surface as this exception rather than as corrupted plaintext or a
 * silently accepted result. The message and cause never include plaintext token or key material.
 */
public class TokenCryptoException extends RuntimeException {

    public TokenCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
