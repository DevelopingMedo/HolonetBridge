package de.medo.holonetbridge.gitea;

import java.net.URI;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.markdown.MarkdownBuilder;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
    public void create(HolonetReportFormData data) {
        Locale locale = accountLocale();
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
        try {
            restClient.post()
                    .uri("/api/v1/repos/{owner}/{repo}/issues", owner, repository)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", prefix + data.getTitle(), "body", body.build()))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("Gitea rejected issue creation (HTTP "
                                    + response.getStatusCode().value() + ")");
                        }
                        return null;
                    });
        } catch (RestClientException failure) {
            // Response bodies, headers and transport exception messages can contain credentials.
            throw new IllegalStateException("Gitea issue creation failed");
        }
    }

    private Locale accountLocale() {
        try {
            return restClient.get()
                    .uri("/api/v1/user")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("Gitea account lookup failed");
                        }
                        Map<?, ?> user = response.bodyTo(Map.class);
                        Object id = user == null ? null : user.get("id");
                        if (!(id instanceof Integer || id instanceof Long) || ((Number) id).longValue() <= 0) {
                            throw new IllegalStateException("Gitea account lookup failed");
                        }
                        return localeFor(user.get("language"));
                    });
        } catch (RuntimeException failure) {
            // Provider responses and JSON diagnostics may reflect credentials; retain no original cause.
            throw new IllegalStateException("Gitea account lookup failed");
        }
    }

    private static Locale localeFor(Object language) {
        if (!(language instanceof String tag) || tag.isBlank()) {
            return Locale.ENGLISH;
        }
        try {
            return switch (new Locale.Builder().setLanguageTag(tag).build().getLanguage()) {
                case "de" -> Locale.GERMAN;
                case "hr" -> Locale.of("hr");
                case "ru" -> Locale.of("ru");
                default -> Locale.ENGLISH;
            };
        } catch (IllformedLocaleException invalidTag) {
            return Locale.ENGLISH;
        }
    }
}
