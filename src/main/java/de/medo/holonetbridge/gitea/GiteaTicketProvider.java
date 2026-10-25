package de.medo.holonetbridge.gitea;

import java.util.Optional;

import de.medo.holonetbridge.commons.ProviderConnectionProperties;
import de.medo.holonetbridge.commons.ticket.TicketDto;
import de.medo.holonetbridge.commons.ticket.TicketProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link TicketProvider} adapter backed by a Gitea instance's issue API, wired up by {@link GiteaConfiguration}.
 *
 * <p>Only {@link #create(String, String)} is implemented so far; {@link #read(String)}, {@link
 * #edit(String, String, String)}, and {@link #comment(String, String)} throw {@link
 * UnsupportedOperationException}.
 */
public class GiteaTicketProvider implements TicketProvider {

    private final RestClient restClient;
    private final String owner;
    private final String repository;

    public GiteaTicketProvider(
            RestClient.Builder restClientBuilder,
            ProviderConnectionProperties providerConnectionProperties,
            GiteaProperties giteaProperties) {
        this.restClient = restClientBuilder
                .baseUrl(providerConnectionProperties.baseUrl().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + providerConnectionProperties.token())
                .build();
        this.owner = giteaProperties.owner();
        this.repository = giteaProperties.repository();
    }

    @Override
    public TicketDto create(String title, String description) {
        GiteaIssueResponse response = restClient.post()
                .uri("/api/v1/repos/{owner}/{repo}/issues", owner, repository)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GiteaCreateIssueRequest(title, description))
                .retrieve()
                .body(GiteaIssueResponse.class);

        if (response == null) {
            throw new IllegalStateException("Gitea returned an empty response body for issue creation");
        }

        return new TicketDto(
                String.valueOf(response.number()), response.title(), response.body() == null ? "" : response.body());
    }

    @Override
    public Optional<TicketDto> read(String id) {
        throw new UnsupportedOperationException("GiteaTicketProvider.read is not yet implemented");
    }

    @Override
    public TicketDto edit(String id, String title, String description) {
        throw new UnsupportedOperationException("GiteaTicketProvider.edit is not yet implemented");
    }

    @Override
    public void comment(String id, String body) {
        throw new UnsupportedOperationException("GiteaTicketProvider.comment is not yet implemented");
    }
}
