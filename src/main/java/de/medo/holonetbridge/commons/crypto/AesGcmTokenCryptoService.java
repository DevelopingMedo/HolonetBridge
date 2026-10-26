package de.medo.holonetbridge.commons.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM implementation of {@link TokenCryptoService}: a fresh random nonce per encryption, and the
 * {@link TokenEncryptionContext} bound in as GCM associated data (authenticated, not encrypted) so
 * decryption fails if the ciphertext is later read back with a different guild/connection context.
 */
@Service
public class AesGcmTokenCryptoService implements TokenCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH_BYTES = 12;

    private final MasterKeyProvider masterKeyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmTokenCryptoService(MasterKeyProvider masterKeyProvider) {
        this.masterKeyProvider = masterKeyProvider;
    }

    @Override
    public EncryptedSecret encrypt(String plaintextToken, TokenEncryptionContext context) {
        Objects.requireNonNull(plaintextToken, "plaintextToken must not be null");
        Objects.requireNonNull(context, "context must not be null");

        MasterKeyProvider.VersionedKey activeKey = masterKeyProvider.activeKey();
        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);

        byte[] plaintextBytes = plaintextToken.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, activeKey.key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(context.associatedData());
            byte[] ciphertext = cipher.doFinal(plaintextBytes);
            return new EncryptedSecret(ciphertext, nonce, activeKey.version());
        } catch (GeneralSecurityException e) {
            throw new TokenCryptoException("Failed to encrypt token", e);
        } finally {
            Arrays.fill(plaintextBytes, (byte) 0);
        }
    }

    @Override
    public String decrypt(EncryptedSecret secret, TokenEncryptionContext context) {
        Objects.requireNonNull(secret, "secret must not be null");
        Objects.requireNonNull(context, "context must not be null");

        var key = masterKeyProvider.keyForVersion(secret.keyVersion());

        byte[] plaintextBytes;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, secret.nonce()));
            cipher.updateAAD(context.associatedData());
            plaintextBytes = cipher.doFinal(secret.ciphertext());
        } catch (GeneralSecurityException e) {
            throw new TokenCryptoException(
                    "Failed to decrypt token: ciphertext, nonce, or context do not match", e);
        }

        try {
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plaintextBytes, (byte) 0);
        }
    }
}
