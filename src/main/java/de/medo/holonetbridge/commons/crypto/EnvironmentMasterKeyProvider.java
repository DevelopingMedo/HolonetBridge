package de.medo.holonetbridge.commons.crypto;

import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Decodes {@link MasterKeyProperties} on demand. Decoding (rather than caching decoded keys) happens on
 * every call so that failures surface immediately and consistently at the point a key is actually needed,
 * with no automatically generated or otherwise substitute key ever handed out as a fallback.
 */
@Component
public class EnvironmentMasterKeyProvider implements MasterKeyProvider {

    private static final int AES_256_KEY_LENGTH_BYTES = 32;

    private final MasterKeyProperties properties;

    public EnvironmentMasterKeyProvider(MasterKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    public VersionedKey activeKey() {
        String version = properties.activeKeyVersion();
        if (version == null || version.isBlank()) {
            throw new MasterKeyUnavailableException(
                    "No active master key version configured (holonet.crypto.active-key-version)");
        }
        return new VersionedKey(version, keyForVersion(version));
    }

    @Override
    public SecretKey keyForVersion(String keyVersion) {
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new MasterKeyUnavailableException("Master key version must not be blank");
        }

        String encoded = properties.keys().get(keyVersion);
        if (encoded == null || encoded.isBlank()) {
            throw new MasterKeyUnavailableException("No master key configured for version '" + keyVersion + "'");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new MasterKeyUnavailableException("Master key for version '" + keyVersion + "' is not valid Base64");
        }

        try {
            if (keyBytes.length != AES_256_KEY_LENGTH_BYTES) {
                throw new MasterKeyUnavailableException(
                        "Master key for version '" + keyVersion + "' must be a 256-bit (32-byte) AES key but was "
                                + keyBytes.length + " bytes");
            }
            // SecretKeySpec clones keyBytes internally, so it is safe to wipe our copy right after.
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }
}
