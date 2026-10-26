package de.medo.holonetbridge.commons;

import java.net.URI;
import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The input shape for the {@code dev}-profile provider-connection seeder ({@code
 * de.medo.holonetbridge.devseed}), not a startup-mandatory global connection anymore: {@link
 * de.medo.holonetbridge.HolonetBridgeApplication} deliberately excludes the {@code commons} root package
 * (where this record lives) from its {@code @ConfigurationPropertiesScan}, so this type is bound only where
 * the dev seeder explicitly {@code @EnableConfigurationProperties}'s it - never unconditionally at every
 * startup. See {@code HolonetProviderConnection}/{@code HolonetProviderConnectionService} for the real,
 * per-guild, database-backed connection model that {@code /setup} manages.
 */
@ConfigurationProperties(prefix = "holonet.provider")
@Validated
public record ProviderConnectionProperties(
        @NotBlank String id,
        @NotNull URI baseUrl,
        @NotBlank String token) {

    public ProviderConnectionProperties {
        if (baseUrl != null) {
            String scheme = baseUrl.getScheme();
            String normalizedScheme = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
            if (!baseUrl.isAbsolute()
                    || !(normalizedScheme.equals("http") || normalizedScheme.equals("https"))) {
                throw new IllegalArgumentException("holonet.provider.base-url must be an absolute HTTP(S) URL");
            }
        }
    }

    @Override
    public String toString() {
        return "ProviderConnectionProperties[id=" + id
                + ", baseUrl=" + baseUrl
                + ", token=[REDACTED]]";
    }
}
