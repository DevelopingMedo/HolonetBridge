package de.medo.holonetbridge.gitea;

import java.net.URI;
import java.util.Optional;

import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link TicketProvider} adapter backed by a Gitea instance's issue API, built per provider-connection by
 * {@link GiteaProviderFactory} (never as a single global/singleton instance - see that class's Javadoc).
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
            RestClient.Builder restClientBuilder, URI baseUrl, String token, String owner, String repository) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl.toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + token)
                .build();
        this.owner = owner;
        this.repository = repository;
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
                String.valueOf(response.number()),
                response.title(),
                response.body() == null ? "" : response.body(),
                response.htmlUrl());
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
