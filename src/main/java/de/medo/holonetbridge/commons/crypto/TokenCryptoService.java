package de.medo.holonetbridge.commons.crypto;

/**
 * Encrypts/decrypts a single provider token for persistence. Callers must decrypt only immediately before
 * the actual outbound call that needs the plaintext token and must never log it or keep it in a field or
 * cache beyond that call's scope.
 */
public interface TokenCryptoService {

    /** Encrypts {@code plaintextToken}, authenticating it against {@code context}. */
    EncryptedSecret encrypt(String plaintextToken, TokenEncryptionContext context);

    /**
     * Decrypts {@code secret}, verifying it was encrypted under {@code context}.
     *
     * @throws TokenCryptoException if the ciphertext, nonce, or context do not match what the token was
     *     originally encrypted with (tampering, corruption, or a mismatched guild/connection), or if the
     *     key version it was encrypted under is no longer available.
     */
    String decrypt(EncryptedSecret secret, TokenEncryptionContext context);
}
