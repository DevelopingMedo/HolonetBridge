package de.medo.holonetbridge.gitea;

import java.net.URI;
import java.time.Duration;

import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.net.SsrfBlockedTargetException;
import de.medo.holonetbridge.commons.net.SsrfSafeHttpClientFactory;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheck;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException.Reason;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
                throw new ProviderHealthCheckException(Reason.REJECTED);
            }
            if (response.getBody() == null || response.getBody().id() <= 0) {
                throw new ProviderHealthCheckException(Reason.INVALID_RESPONSE);
            }
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new ProviderHealthCheckException(status == 401 || status == 403 ? Reason.ACCESS : Reason.REJECTED);
        } catch (ResourceAccessException | SsrfBlockedTargetException e) {
            throw new ProviderHealthCheckException(Reason.UNREACHABLE);
        } catch (RestClientException e) {
            throw new ProviderHealthCheckException(Reason.INVALID_RESPONSE);
        }
    }

    private record AuthenticatedUser(long id) {}
}
