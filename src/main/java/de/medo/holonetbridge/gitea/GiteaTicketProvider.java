package de.medo.holonetbridge.gitea;

import java.net.URI;
import java.util.Optional;
import java.util.Locale;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.markdown.MarkdownBuilder;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link TicketProvider} adapter backed by a Gitea instance's issue API, built per provider-connection by
 * {@link GiteaProviderFactory} (never as a single global/singleton instance - see that class's Javadoc).
 *
 * <p>Only ticket creation is implemented so far; {@link #read(String)}, {@link
 * #edit(String, String, String)}, and {@link #comment(String, String)} throw {@link
 * UnsupportedOperationException}.
 */
public class GiteaTicketProvider implements TicketProvider {

    private final RestClient restClient;
    private final String owner;
    private final String repository;
    private final MessageSource messageSource;

    public GiteaTicketProvider(
            RestClient.Builder restClientBuilder, URI baseUrl, String token, String owner, String repository,
            MessageSource messageSource) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl.toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + token)
                .build();
        this.owner = owner;
        this.repository = repository;
        this.messageSource = messageSource;
    }

    @Override
    public TicketDto create(HolonetReportFormData data) {
        Locale locale = data.getSession().getLocale();
        MarkdownBuilder body = MarkdownBuilder.builder();
        if (data.getDescription() != null && !data.getDescription().isBlank()) {
            body.text(data.getDescription());
        }
        if (data.getKind() == HolonetTicketKind.BUG) {
            String recurrence = switch (data.getRecurrence()) {
                case "always", "sometimes", "once", "not-retried" -> messageSource.getMessage(
                        "bug.recurrence-" + data.getRecurrence(), null, locale);
                default -> data.getRecurrence();
            };
            body.heading2(messageSource.getMessage("bug.steps", null, locale)).text(data.getSteps())
                    .heading2(messageSource.getMessage("bug.expected", null, locale)).text(data.getExpected())
                    .heading2(messageSource.getMessage("bug.actual", null, locale)).text(data.getActual())
                    .heading2(messageSource.getMessage("bug.recurrence", null, locale)).text(recurrence);
        }
        body.heading2("Discord")
                .text("Server: " + data.getGuildName() + " (ID: " + data.getSession().getGuildId() + ")")
                .text("User: " + data.getUsername() + " (ID: " + data.getSession().getMemberId() + ")");
        String prefix = switch (data.getKind()) {
            case BUG -> "[Bug] ";
            case FEATURE -> "[Feature] ";
        };
        return create(prefix + data.getTitle(), body.build());
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
