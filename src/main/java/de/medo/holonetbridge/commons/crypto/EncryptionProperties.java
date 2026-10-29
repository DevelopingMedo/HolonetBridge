package de.medo.holonetbridge.commons.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The Base64-encoded 256-bit AES master key used by {@link EncryptionService}. Known only to this
 * application: a random value generated once and supplied through the deployment's secret mechanism
 * (environment variable or secrets file), never from the database.
 */
@Validated
@ConfigurationProperties(prefix = "holonet.crypto")
public record EncryptionProperties(@NotBlank String masterKey) {

    @Override
    public String toString() {
        return "EncryptionProperties[masterKey=REDACTED]";
    }
}
