package de.medo.holonetbridge.commons;

import java.net.URI;
import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
