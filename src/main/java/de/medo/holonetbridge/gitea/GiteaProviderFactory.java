package de.medo.holonetbridge.gitea;

import java.net.URI;

import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.net.ProviderHttpClientFactory;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckService;
import org.springframework.stereotype.Component;
import org.springframework.context.MessageSource;

/**
 * Builds a separate client per connection so guilds and token rotations never reuse another connection's
 * credentials.
 */
@Component
public class GiteaProviderFactory {

    private final ProviderHttpClientFactory httpClientFactory;
    private final MessageSource messageSource;

    public GiteaProviderFactory(ProviderHttpClientFactory httpClientFactory, MessageSource messageSource) {
        this.httpClientFactory = httpClientFactory;
        this.messageSource = messageSource;
    }

    public TicketProvider create(String baseUrl, String token, String target) {
        URI uri = ProviderHealthCheckService.parseBaseUrl(baseUrl);
        String[] parts = target == null ? new String[0] : target.split("/", -1);
        if (parts.length != 2 || !validTargetPart(parts[0]) || !validTargetPart(parts[1])) {
            throw new IllegalArgumentException("Gitea target must be owner/repository");
        }
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) {
            throw new IllegalArgumentException("A valid Gitea token is required");
        }
        return new GiteaTicketProvider(httpClientFactory.restClientBuilderFor(uri), uri, token, parts[0], parts[1],
                messageSource);
    }

    private static boolean validTargetPart(String part) {
        return part.matches("[A-Za-z0-9_.-]+") && !part.equals(".") && !part.equals("..");
    }
}
