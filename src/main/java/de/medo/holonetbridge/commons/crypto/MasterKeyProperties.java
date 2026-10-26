package de.medo.holonetbridge.commons.crypto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Raw master-key material for {@link TokenCryptoService}, supplied by the operator's deployment secret
 * mechanism (environment variables or a protected file translated into environment variables), never from
 * the database itself. {@code keys} maps a key version (free-form, for example {@code v1}) to its
 * Base64-encoded 256-bit AES key; {@code activeKeyVersion} selects which of those versions new encryptions
 * use. Deliberately not {@code @Validated}/{@code @NotBlank}: nothing in this codebase decrypts a token yet
 * (that lands in a later work package), so an unconfigured deployment must still start up. Validation and
 * fail-fast behavior happen in {@link EnvironmentMasterKeyProvider}, at the point a key is actually
 * requested, rather than eagerly for every startup.
 *
 * <p>See {@code docs/token-key-rotation.md} for the key-rotation procedure this shape is designed for:
 * both the old and the new key version stay present in {@code keys} across a rotation, so already-encrypted
 * secrets referencing the old version keep decrypting.
 */
@ConfigurationProperties(prefix = "holonet.crypto")
public record MasterKeyProperties(String activeKeyVersion, Map<String, String> keys) {

    public MasterKeyProperties {
        keys = keys == null ? Map.of() : Map.copyOf(keys);
    }

    @Override
    public String toString() {
        return "MasterKeyProperties[activeKeyVersion=" + activeKeyVersion + ", keys=[REDACTED x" + keys.size() + "]]";
    }
}
