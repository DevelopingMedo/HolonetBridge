package de.medo.holonetbridge.commons.crypto;

import javax.crypto.SecretKey;

/**
 * Resolves the AES master key(s) used by {@link TokenCryptoService}, keyed by version so that an old
 * version keeps decrypting existing ciphertexts after {@link #activeKey()} has moved on to a new one (see
 * {@code docs/token-key-rotation.md}). Implementations must fail with {@link MasterKeyUnavailableException}
 * rather than fall back to a generated or otherwise substitute key when no matching key is configured.
 */
public interface MasterKeyProvider {

    /** The key new encryptions are performed with, together with its version. */
    VersionedKey activeKey();

    /** The key a ciphertext recorded as {@code keyVersion} must be decrypted with. */
    SecretKey keyForVersion(String keyVersion);

    /** A master key together with the version identifier it is stored/looked up under. */
    record VersionedKey(String version, SecretKey key) {}
}
