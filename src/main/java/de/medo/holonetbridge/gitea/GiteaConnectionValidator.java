package de.medo.holonetbridge.gitea;

import java.net.URI;

import de.medo.holonetbridge.commons.net.ProviderHttpClientFactory;
import de.medo.holonetbridge.gitea.GiteaConnectionValidationException.Reason;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Proves repository read access only, not permission to create issues. Never creates a test ticket.
 */
@Component
public class GiteaConnectionValidator {

    private final ProviderHttpClientFactory httpClientFactory;

    public GiteaConnectionValidator(ProviderHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    /** @throws GiteaConnectionValidationException if the base URL, token, or owner/repository do not check out */
    public void verifyReadAccess(URI baseUrl, String token, String owner, String repository) {
        RestClient client = httpClientFactory
                .restClientBuilderFor(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + token)
                .build();

        try {
            client.get().uri("/api/v1/repos/{owner}/{repo}", owner, repository).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new GiteaConnectionValidationException(reasonFor(e.getStatusCode()), e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new GiteaConnectionValidationException(
                    Reason.UNREACHABLE, e);
        }
    }

    private static Reason reasonFor(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) {
            return Reason.ACCESS;
        }
        if (status.value() == 404) {
            return Reason.NOT_FOUND;
        }
        return Reason.REJECTED;
    }
}
