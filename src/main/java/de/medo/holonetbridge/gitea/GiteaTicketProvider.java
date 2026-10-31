package de.medo.holonetbridge.gitea;

import java.net.URI;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.extapi.TicketReference;
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
    private final String baseUrl;

    public GiteaTicketProvider(
            RestClient.Builder restClientBuilder, URI baseUrl, String token, String owner, String repository,
            MessageSource messageSource) {
        this.baseUrl = normalizeBase(baseUrl);
        this.restClient = restClientBuilder
                .baseUrl(this.baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + token)
                .build();
        this.owner = owner;
        this.repository = repository;
        this.messageSource = messageSource;
    }

    @Override
    public TicketReference create(HolonetReportFormData data) {
        Locale locale = fetchAccountLocale();
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
            long repositoryId = fetchRepositoryId();
            return restClient.post()
                    .uri("/api/v1/repos/{owner}/{repo}/issues", owner, repository)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", prefix + data.getTitle(), "body", body.build()))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("Gitea rejected issue creation (HTTP "
                                    + response.getStatusCode().value() + ")");
                        }
                        Map<?, ?> issue = response.bodyTo(Map.class);
                        validateIssue(issue, null, repositoryId);
                        return new TicketReference("gitea", baseUrl, owner, repository, repositoryId,
                                positiveId(issue.get("id")), positiveId(issue.get("number")));
                    });
        } catch (RestClientException failure) {
            // Response bodies, headers and transport exception messages can contain credentials.
            throw new IllegalStateException("Gitea issue creation failed");
        }
    }

    @Override
    public boolean supports(TicketReference reference) {
        return reference != null && "gitea".equals(reference.provider())
                && baseUrl.equals(reference.baseUrl()) && owner.equals(reference.owner())
                && repository.equals(reference.repository());
    }

    @Override
    public void close(TicketReference reference) {
        // Check locally before using current credentials, including for any identity lookup.
        if (!supports(reference)) throw new IllegalStateException("Ticket target does not match current connection");
        try {
            long repositoryId = fetchRepositoryId();
            if (repositoryId != reference.repositoryId()) throw new IllegalStateException("Repository identity changed");
            Map<?, ?> issue = restClient.get()
                    .uri("/api/v1/repos/{owner}/{repo}/issues/{number}", owner, repository, reference.number())
                    .exchange((_, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Issue lookup failed");
                        return response.bodyTo(Map.class);
                    });
            validateIssue(issue, reference, repositoryId);
            if ("closed".equals(issue.get("state"))) return;
            restClient.patch()
                    .uri("/api/v1/repos/{owner}/{repo}/issues/{number}", owner, repository, reference.number())
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("state", "closed"))
                    .exchange((_, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Issue close failed");
                        Map<?, ?> closed = response.bodyTo(Map.class);
                        validateIssue(closed, reference, repositoryId);
                        if (!"closed".equals(closed.get("state"))) throw new IllegalStateException("Issue closure not confirmed");
                        return null;
                    });
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Gitea issue closure could not be confirmed");
        }
    }

    private long fetchRepositoryId() {
        return restClient.get().uri("/api/v1/repos/{owner}/{repo}", owner, repository)
                .exchange((_, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Repository lookup failed");
                    Map<?, ?> repo = response.bodyTo(Map.class);
                    if (repo == null || !(owner + "/" + repository).equalsIgnoreCase(String.valueOf(repo.get("full_name")))) {
                        throw new IllegalStateException("Repository identity mismatch");
                    }
                    return positiveId(repo.get("id"));
                });
    }

    private static void validateIssue(Map<?, ?> issue, TicketReference expected, long repositoryId) {
        if (issue == null || issue.get("pull_request") != null) throw new IllegalStateException("Invalid issue identity");
        long id = positiveId(issue.get("id"));
        long number = positiveId(issue.get("number"));
        if (!("open".equals(issue.get("state")) || "closed".equals(issue.get("state")))
                || (expected != null && (id != expected.issueId() || number != expected.number()))) {
            throw new IllegalStateException("Issue identity mismatch");
        }
        if (issue.get("repository") != null && (!(issue.get("repository") instanceof Map<?, ?> repo)
                || positiveId(repo.get("id")) != repositoryId)) {
            throw new IllegalStateException("Issue repository mismatch");
        }
    }

    private static long positiveId(Object id) {
        if (!(id instanceof Long || id instanceof Integer) || ((Number) id).longValue() <= 0) {
            throw new IllegalStateException("Invalid external identity");
        }
        return ((Number) id).longValue();
    }

    private static String normalizeBase(URI uri) {
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Invalid provider base URL");
        }
        String path = uri.normalize().getRawPath();
        if (path == null) path = "";
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int port = uri.getPort();
        if (("https".equalsIgnoreCase(uri.getScheme()) && port == 443)
                || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)) port = -1;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + (port < 0 ? "" : ":" + port) + path;
    }

    @Override
    public Locale fetchAccountLocale() {
        try {
            return restClient.get()
                    .uri("/api/v1/user")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((_, response) -> {
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
