package de.medo.holonetbridge.commons.provider;

import java.util.Map;

import de.medo.holonetbridge.commons.domain.TicketProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "holonet.ticket-providers")
public record TicketProviderProperties(
        @NotEmpty Map<TicketProvider, @NotBlank @Pattern(regexp = "/(?!/)[^?#]*") String> apiPaths) {

    public String getApiPath(TicketProvider provider) {
        String apiPath = apiPaths.get(provider);
        if (apiPath == null) {
            throw new IllegalArgumentException("No API path configured for ticket provider " + provider.getId());
        }
        return apiPath;
    }
}
