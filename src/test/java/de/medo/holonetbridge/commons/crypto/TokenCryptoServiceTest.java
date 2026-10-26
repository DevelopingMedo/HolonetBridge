package de.medo.holonetbridge.commons.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** All keys used here are freshly generated per test run and only ever exist in memory. */
class TokenCryptoServiceTest {

    private final FakeMasterKeyProvider masterKeyProvider = new FakeMasterKeyProvider("v1");
    private final TokenCryptoService cryptoService = new AesGcmTokenCryptoService(masterKeyProvider);
    private final TokenEncryptionContext context = new TokenEncryptionContext(123L, "connection-1");

    @Test
    void decryptingTheEncryptedTokenWithTheSameContextReturnsTheOriginalPlaintext() {
        EncryptedSecret secret = cryptoService.encrypt("super-secret-token", context);

        String decrypted = cryptoService.decrypt(secret, context);

        assertThat(decrypted).isEqualTo("super-secret-token");
    }

    @Test
    void encryptingTheSameTokenTwiceProducesDifferentCiphertextsAndNonces() {
        EncryptedSecret first = cryptoService.encrypt("super-secret-token", context);
        EncryptedSecret second = cryptoService.encrypt("super-secret-token", context);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void decryptingATamperedCiphertextFails() {
        EncryptedSecret secret = cryptoService.encrypt("super-secret-token", context);
        byte[] tamperedCiphertext = secret.ciphertext();
        tamperedCiphertext[0] ^= 0x01;
        EncryptedSecret tampered = new EncryptedSecret(tamperedCiphertext, secret.nonce(), secret.keyVersion());

        assertThatThrownBy(() -> cryptoService.decrypt(tampered, context))
                .isInstanceOf(TokenCryptoException.class);
    }

    @Test
    void decryptingWithATamperedNonceFails() {
        EncryptedSecret secret = cryptoService.encrypt("super-secret-token", context);
        byte[] tamperedNonce = secret.nonce();
        tamperedNonce[0] ^= 0x01;
        EncryptedSecret tampered = new EncryptedSecret(secret.ciphertext(), tamperedNonce, secret.keyVersion());

        assertThatThrownBy(() -> cryptoService.decrypt(tampered, context))
                .isInstanceOf(TokenCryptoException.class);
    }

    @Test
    void decryptingWithADifferentGuildContextFails() {
        EncryptedSecret secret = cryptoService.encrypt("super-secret-token", context);
        TokenEncryptionContext differentGuild = new TokenEncryptionContext(999L, context.connectionId());

        assertThatThrownBy(() -> cryptoService.decrypt(secret, differentGuild))
                .isInstanceOf(TokenCryptoException.class);
    }

    @Test
    void decryptingWithADifferentConnectionContextFails() {
        EncryptedSecret secret = cryptoService.encrypt("super-secret-token", context);
        TokenEncryptionContext differentConnection = new TokenEncryptionContext(context.guildId(), "connection-2");

        assertThatThrownBy(() -> cryptoService.decrypt(secret, differentConnection))
                .isInstanceOf(TokenCryptoException.class);
    }

    @Test
    void oldCiphertextRemainsDecryptableAfterKeyRotation() {
        EncryptedSecret encryptedUnderV1 = cryptoService.encrypt("super-secret-token", context);
        assertThat(encryptedUnderV1.keyVersion()).isEqualTo("v1");

        // Rotation step: a new "v2" key becomes active, but "v1" is kept available for existing ciphertexts.
        masterKeyProvider.addKey("v2");
        masterKeyProvider.activateVersion("v2");

        String decrypted = cryptoService.decrypt(encryptedUnderV1, context);
        assertThat(decrypted).isEqualTo("super-secret-token");

        EncryptedSecret encryptedUnderV2 = cryptoService.encrypt("super-secret-token", context);
        assertThat(encryptedUnderV2.keyVersion()).isEqualTo("v2");
    }

    /**
     * A minimal, mutable stand-in for {@link EnvironmentMasterKeyProvider} so rotation (adding a key,
     * switching the active version) can be exercised without going through configuration-property binding,
     * which {@link MasterKeyProviderTest} already covers on its own.
     */
    private static final class FakeMasterKeyProvider implements MasterKeyProvider {

        private final Map<String, SecretKey> keysByVersion = new HashMap<>();
        private String activeVersion;

        FakeMasterKeyProvider(String initialVersion) {
            addKey(initialVersion);
            activeVersion = initialVersion;
        }

        void addKey(String version) {
            keysByVersion.put(version, generateKey());
        }

        void activateVersion(String version) {
            this.activeVersion = version;
        }

        @Override
        public VersionedKey activeKey() {
            return new VersionedKey(activeVersion, keyForVersion(activeVersion));
        }

        @Override
        public SecretKey keyForVersion(String keyVersion) {
            SecretKey key = keysByVersion.get(keyVersion);
            if (key == null) {
                throw new MasterKeyUnavailableException("No master key configured for version '" + keyVersion + "'");
            }
            return key;
        }

        private static SecretKey generateKey() {
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(256);
                return keyGenerator.generateKey();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
