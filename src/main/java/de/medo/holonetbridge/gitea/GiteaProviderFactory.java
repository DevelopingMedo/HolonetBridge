package de.medo.holonetbridge.gitea;

import java.net.URI;

import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.net.ProviderHttpClientFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds a separate client per connection so guilds and token rotations never reuse another connection's
 * credentials.
 */
@Component
public class GiteaProviderFactory {

    private final ProviderHttpClientFactory httpClientFactory;

    public GiteaProviderFactory(ProviderHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    public TicketProvider create(URI baseUrl, String token, String owner, String repository) {
        return new GiteaTicketProvider(httpClientFactory.restClientBuilderFor(baseUrl), baseUrl, token, owner, repository);
    }
}
