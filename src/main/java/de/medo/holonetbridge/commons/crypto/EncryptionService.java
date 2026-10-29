package de.medo.holonetbridge.commons.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Encrypts/decrypts strings with AES-256-GCM, using the single master key configured in
 * {@link EncryptionProperties}. Each encryption uses a fresh random nonce, which is stored alongside the
 * ciphertext in the returned value, so callers have nothing else to persist or pass back in.
 */
@Service
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH_BYTES = 12;

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(EncryptionProperties properties) {
        this.masterKey = new SecretKeySpec(Base64.getDecoder().decode(properties.masterKey()), "AES");
    }

    /** Encrypts {@code plaintext}, returning the Base64-encoded nonce and ciphertext (including the GCM tag). */
    public String encrypt(String plaintext) {
        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                            .put(nonce)
                            .put(ciphertext)
                            .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypts a value produced by {@link #encrypt(String)}.
     *
     * @throws WrongEncryptionKeyException if {@code encrypted} was not encrypted with the configured master
     *     key, or has been corrupted or tampered with
     */
    public String decrypt(String encrypted) {
        byte[] decoded = Base64.getDecoder().decode(encrypted);
        byte[] nonce = Arrays.copyOfRange(decoded, 0, GCM_NONCE_LENGTH_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(decoded, GCM_NONCE_LENGTH_BYTES, decoded.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new WrongEncryptionKeyException("Failed to decrypt value: wrong master key or corrupted ciphertext", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }
}
