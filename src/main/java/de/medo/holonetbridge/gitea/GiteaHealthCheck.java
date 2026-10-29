package de.medo.holonetbridge.gitea;

import java.net.URI;
import java.time.Duration;

import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.net.SsrfBlockedTargetException;
import de.medo.holonetbridge.commons.net.SsrfSafeHttpClientFactory;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheck;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException.Reason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GiteaHealthCheck implements ProviderHealthCheck {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final SsrfSafeHttpClientFactory httpClientFactory;

    public GiteaHealthCheck(SsrfSafeHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public TicketProvider provider() {
        return TicketProvider.GITEA;
    }

    @Override
    public void check(URI baseUrl, String token) {
        try {
            RestClient client = httpClientFactory.restClientBuilderFor(baseUrl, TIMEOUT)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + token)
                    .build();
            // The version endpoint is public and would also succeed with an invalid token.
            ResponseEntity<AuthenticatedUser> response = client.get()
                    .uri("/api/v1/user")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(AuthenticatedUser.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw failure(baseUrl, Reason.REJECTED, "HTTP " + response.getStatusCode().value());
            }
            if (response.getBody() == null || response.getBody().id() <= 0) {
                throw failure(baseUrl, Reason.INVALID_RESPONSE, "Missing authenticated user");
            }
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw failure(baseUrl, status == 401 || status == 403 ? Reason.ACCESS : Reason.REJECTED, "HTTP " + status);
        } catch (ResourceAccessException e) {
            throw failure(baseUrl, Reason.UNREACHABLE, e.getMostSpecificCause().getClass().getSimpleName());
        } catch (SsrfBlockedTargetException e) {
            throw failure(baseUrl, Reason.UNREACHABLE,
                    e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName());
        } catch (RestClientException e) {
            throw failure(baseUrl, Reason.INVALID_RESPONSE, e.getClass().getSimpleName());
        }
    }

    private static ProviderHealthCheckException failure(URI baseUrl, Reason reason, String diagnostic) {
        int port = baseUrl.getPort() == -1
                ? ("https".equalsIgnoreCase(baseUrl.getScheme()) ? 443 : 80)
                : baseUrl.getPort();
        // Log only controlled diagnostics: HTTP errors can reflect tokens in messages, bodies, and headers.
        log.warn("Gitea healthcheck failed: reason={}, scheme={}, host={}, port={}, diagnostic={}",
                reason, baseUrl.getScheme(), baseUrl.getHost(), port, diagnostic);
        return new ProviderHealthCheckException(reason);
    }

    private record AuthenticatedUser(long id) {}
}
