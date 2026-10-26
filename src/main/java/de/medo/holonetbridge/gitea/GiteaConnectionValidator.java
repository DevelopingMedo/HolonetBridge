package de.medo.holonetbridge.gitea;

import java.net.URI;

import de.medo.holonetbridge.commons.net.SsrfBlockedTargetException;
import de.medo.holonetbridge.commons.net.SsrfSafeHttpClientFactory;
import de.medo.holonetbridge.gitea.GiteaConnectionValidationException.Reason;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Validates a candidate Gitea connection (used by the {@code /setup} flow before {@code
 * HolonetProviderConnectionService#activate} is ever called) with a single {@code GET} on the repository
 * resource: this proves the base URL is reachable, resolves to an address the SSRF network policy allows,
 * and that the token can actually read {@code owner/repository}. It deliberately proves nothing about write
 * access - a successful read says nothing about whether the token can also create issues - and it never
 * creates a test ticket to find out.
 */
@Component
public class GiteaConnectionValidator {

    private final SsrfSafeHttpClientFactory httpClientFactory;

    public GiteaConnectionValidator(SsrfSafeHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    /** @throws GiteaConnectionValidationException if the base URL, token, or owner/repository do not check out */
    public void verifyReadAccess(URI baseUrl, String token, String owner, String repository) {
        RestClient client;
        try {
            client = httpClientFactory
                    .restClientBuilderFor(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + token)
                    .build();
        } catch (SsrfBlockedTargetException e) {
            throw new GiteaConnectionValidationException(
                    Reason.BLOCKED, e);
        }

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
