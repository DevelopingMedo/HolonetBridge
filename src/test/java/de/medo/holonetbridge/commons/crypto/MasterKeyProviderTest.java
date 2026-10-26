package de.medo.holonetbridge.commons.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * {@code SYNTHETIC_KEY_*} below are freshly generated, test-only AES keys with no relation to any real
 * deployment secret; they exist purely to drive {@link EnvironmentMasterKeyProvider} through valid and
 * invalid configurations.
 */
class MasterKeyProviderTest {

    private static final String SYNTHETIC_KEY_V1 = generateBase64AesKey();

    @Test
    void activeKeyFailsWhenNoActiveVersionIsConfigured() {
        MasterKeyProvider provider = providerWith(null, Map.of("v1", SYNTHETIC_KEY_V1));

        assertThatThrownBy(provider::activeKey)
                .isInstanceOf(MasterKeyUnavailableException.class)
                .hasMessageContaining("active master key version");
    }

    @Test
    void activeKeyFailsWhenActiveVersionHasNoMatchingKeyConfigured() {
        MasterKeyProvider provider = providerWith("v2", Map.of("v1", SYNTHETIC_KEY_V1));

        assertThatThrownBy(provider::activeKey)
                .isInstanceOf(MasterKeyUnavailableException.class)
                .hasMessageContaining("v2");
    }

    @Test
    void keyForVersionFailsOnInvalidBase64() {
        MasterKeyProvider provider = providerWith("v1", Map.of("v1", "not-valid-base64!!"));

        assertThatThrownBy(() -> provider.keyForVersion("v1"))
                .isInstanceOf(MasterKeyUnavailableException.class);
    }

    @Test
    void keyForVersionFailsWhenKeyIsNotTwoHundredFiftySixBits() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        MasterKeyProvider provider = providerWith("v1", Map.of("v1", tooShort));

        assertThatThrownBy(() -> provider.keyForVersion("v1"))
                .isInstanceOf(MasterKeyUnavailableException.class)
                .hasMessageContaining("256-bit");
    }

    @Test
    void exceptionMessageNeverContainsTheConfiguredKeyMaterial() {
        MasterKeyProvider provider = providerWith("v2", Map.of("v1", SYNTHETIC_KEY_V1));

        assertThatThrownBy(provider::activeKey).hasMessageNotContaining(SYNTHETIC_KEY_V1);
    }

    @Test
    void activeKeyReturnsTheConfiguredVersionAndADecodedTwoHundredFiftySixBitKey() {
        MasterKeyProvider provider = providerWith("v1", Map.of("v1", SYNTHETIC_KEY_V1));

        MasterKeyProvider.VersionedKey active = provider.activeKey();

        assertThat(active.version()).isEqualTo("v1");
        assertThat(active.key().getEncoded()).hasSize(32);
    }

    @Test
    void keyForVersionResolvesANonActiveVersionToo() {
        String synthenticKeyV2 = generateBase64AesKey();
        MasterKeyProvider provider =
                providerWith("v2", Map.of("v1", SYNTHETIC_KEY_V1, "v2", synthenticKeyV2));

        SecretKey resolved = provider.keyForVersion("v1");

        assertThat(resolved.getEncoded()).isEqualTo(Base64.getDecoder().decode(SYNTHETIC_KEY_V1));
    }

    private static MasterKeyProvider providerWith(String activeVersion, Map<String, String> keys) {
        return new EnvironmentMasterKeyProvider(new MasterKeyProperties(activeVersion, keys));
    }

    private static String generateBase64AesKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            return Base64.getEncoder().encodeToString(keyGenerator.generateKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
