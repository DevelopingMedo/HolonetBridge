package de.medo.holonetbridge.gitea;

import java.net.URI;

import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.net.SsrfSafeHttpClientFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds a {@link GiteaTicketProvider} for one specific provider-connection's current target and credentials,
 * called by {@link de.medo.holonetbridge.commons.provider.ProviderConnectionResolver} every time it resolves
 * a connection. There is deliberately no {@code @Bean}-registered single {@link TicketProvider} instance
 * here (that is what the removed, {@code @ConditionalOnProperty}-gated {@code GiteaConfiguration} used to
 * be): a single shared instance would mean every guild - and every token rotation within the same guild -
 * reuses one {@link RestClient} built with whichever credentials happened to be configured first.
 *
 * <p>Every call builds a fresh {@link RestClient} wired through {@link SsrfSafeHttpClientFactory}, so the
 * connection's own {@code baseUrl} is resolved and checked against the SSRF network policy on every call
 * rather than once at some earlier point in time.
 */
@Component
public class GiteaProviderFactory {

    private final SsrfSafeHttpClientFactory httpClientFactory;

    public GiteaProviderFactory(SsrfSafeHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    public TicketProvider create(URI baseUrl, String token, String owner, String repository) {
        return new GiteaTicketProvider(httpClientFactory.restClientBuilderFor(baseUrl), baseUrl, token, owner, repository);
    }
}
