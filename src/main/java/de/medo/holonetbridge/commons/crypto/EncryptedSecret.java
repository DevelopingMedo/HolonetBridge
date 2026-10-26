package de.medo.holonetbridge.commons.crypto;

import java.util.Arrays;
import java.util.Objects;

/**
 * An AES-GCM-encrypted token: the ciphertext (including its authentication tag), the nonce it was encrypted
 * with, and the master-key version it was encrypted under (see {@link MasterKeyProvider}). This is the
 * exact tuple a provider-connection entity is expected to persist; none of the three fields is meaningful
 * or recoverable on its own without the matching {@link TokenEncryptionContext}.
 *
 * <p>Defensively copies its array fields on construction and on every access, since this is a security
 * value object that may be held by callers for longer than the moment it was built.
 */
public record EncryptedSecret(byte[] ciphertext, byte[] nonce, String keyVersion) {

    public EncryptedSecret {
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        Objects.requireNonNull(keyVersion, "keyVersion must not be null");
        ciphertext = ciphertext.clone();
        nonce = nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EncryptedSecret other)) {
            return false;
        }
        return Arrays.equals(ciphertext, other.ciphertext)
                && Arrays.equals(nonce, other.nonce)
                && keyVersion.equals(other.keyVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(ciphertext), Arrays.hashCode(nonce), keyVersion);
    }

    @Override
    public String toString() {
        return "EncryptedSecret[keyVersion=" + keyVersion
                + ", ciphertextLength=" + ciphertext.length
                + ", nonceLength=" + nonce.length + "]";
    }
}
