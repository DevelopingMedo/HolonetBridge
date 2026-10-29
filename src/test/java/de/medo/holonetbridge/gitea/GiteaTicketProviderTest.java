package de.medo.holonetbridge.gitea;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

class GiteaTicketProviderTest {

    private static final String BASE_URL = "https://tickets.example.invalid/gitea";
    private static final String ISSUE_URL = BASE_URL + "/api/v1/repos/acme/support/issues";
    private final MessageSource messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
    private MockRestServiceServer server;
    private GiteaTicketProvider provider;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GiteaTicketProvider(builder, URI.create(BASE_URL), "secret-test-token", "acme", "support", messages);
    }

    @Test
    void postsBugWithAccountLanguageAuthenticationMarkdownAndDiscordContext() {
        var data = form(HolonetTicketKind.BUG);
        data.setSteps("1. **Log in**\n2. Wait");
        data.getSession().setLocale(Locale.of("hr"));
        expectAccount("{\"id\":42,\"language\":\"de-DE\"}");
        issueRequest().andExpect(content().json("""
                        {"title":"[Bug] Login fails","body":"## Was hast du vor dem Fehler gemacht?\\n\\n1. **Log in**\\n2. Wait\\n\\n## Was hättest du erwartet?\\n\\nA dashboard\\n\\n## Was ist stattdessen passiert?\\n\\nAn error\\n\\n## Tritt der Fehler erneut auf?\\n\\nJedes Mal\\n\\n## Discord\\n\\nServer: Test server (ID: 1)\\n\\nUser: Tester (ID: 2)"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED));

        provider.create(data);

        server.verify();
    }

    @Test
    void postsFeatureWithoutNormalizingUserMarkdownAndReusesTheClient() {
        var data = form(HolonetTicketKind.FEATURE);
        data.setDescription("  Original\n**text**  ");
        for (int ticket = 0; ticket < 2; ticket++) {
            expectAccount("{\"id\":42,\"language\":\"en\"}");
            issueRequest().andExpect(content().json("""
                            {"title":"[Feature] Login fails","body":"  Original\\n**text**  \\n\\n## Discord\\n\\nServer: Test server (ID: 1)\\n\\nUser: Tester (ID: 2)"}
                            """))
                    .andRespond(withStatus(HttpStatus.CREATED));
        }

        provider.create(data);
        provider.create(data);

        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"en,en", "en-US,en", "en-GB,en", "de,de", "de-DE,de", "DE-at,de", "hr,hr", "hr-HR,hr", "ru,ru", "ru-RU,ru"})
    void supportsAccountLanguagesAndRegionalTagsForAllHeadingsAndRecurrence(String tag, String expectedLanguage) {
        expectAccount("{\"id\":42,\"language\":\"" + tag + "\"}");
        expectBug(Locale.forLanguageTag(expectedLanguage));

        provider.create(form(HolonetTicketKind.BUG));

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"language\":null", ",\"language\":\"\"", ",\"language\":\" \"",
            ",\"language\":\"de_DE\"", ",\"language\":\"de--DE\"", ",\"language\":\" de-DE \"",
            ",\"language\":\"fr-FR\"", ",\"language\":\"und\"", ",\"language\":42", ",\"language\":true",
            ",\"language\":[\"de\"]", ",\"language\":{\"tag\":\"de\"}"})
    void unusableLanguageFallsBackToEnglishNotGermanSession(String languageField) {
        var data = form(HolonetTicketKind.BUG);
        data.getSession().setLocale(Locale.GERMAN);
        expectAccount("{\"id\":42" + languageField + "}");
        expectBug(Locale.ENGLISH);

        provider.create(data);

        server.verify();
    }

    @Test
    void englishFallbackDoesNotUseTheGermanSystemLocale() {
        var previous = Locale.getDefault();
        var data = form(HolonetTicketKind.BUG);
        data.getSession().setLocale(Locale.GERMAN);
        expectAccount("{\"id\":42,\"language\":\"fr-FR\"}");
        expectBug(Locale.ENGLISH);
        try {
            Locale.setDefault(Locale.GERMAN);
            provider.create(data);
        } finally {
            Locale.setDefault(previous);
        }
        server.verify();
    }

    @Test
    void readsChangedAccountLanguageBeforeEachTicketUsingTheSameProvider() {
        expectAccount("{\"id\":42,\"language\":\"de-DE\"}");
        expectBug(Locale.GERMAN);
        expectAccount("{\"id\":42,\"language\":\"ru-RU\"}");
        expectBug(Locale.of("ru"));

        var data = form(HolonetTicketKind.BUG);
        provider.create(data);
        provider.create(data);

        server.verify();
    }

    @Test
    void failedLookupDoesNotUseThePreviouslySuccessfulLanguageOrRetry() {
        expectAccount("{\"id\":42,\"language\":\"de\"}");
        expectBug(Locale.GERMAN);
        accountRequest().andRespond(withServerError().body("secret-test-token"));
        expectAccount("{\"id\":42,\"language\":\"en\"}");
        expectBug(Locale.ENGLISH);
        var data = form(HolonetTicketKind.BUG);

        provider.create(data);
        assertThatThrownBy(() -> provider.create(data)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Gitea account lookup failed").hasNoCause();
        provider.create(data);

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308, 401, 403, 404, 429, 500, 503})
    void rejectsIssueNonSuccessWithoutExposingReflectedSecrets(int status) {
        expectAccount("{\"id\":42,\"language\":\"en\"}");
        issueRequest().andRespond(withStatus(HttpStatus.valueOf(status))
                .header("Location", "https://secret-test-token.invalid")
                .body("secret-test-token").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> provider.create(form(HolonetTicketKind.FEATURE)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP " + status)
                .hasMessageNotContaining("secret-test-token").hasNoCause();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308, 401, 403, 404, 429, 500, 503})
    void failedAccountLookupDoesNotPostOrExposeReflectedSecrets(int status) {
        accountRequest().andRespond(withStatus(HttpStatus.valueOf(status))
                .header("Location", "https://secret-test-token.invalid")
                .body("secret-test-token").contentType(MediaType.TEXT_PLAIN));
        expectNoIssue();

        assertAccountLookupFails();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not JSON: secret-test-token", "{\"id\":42,\"language\":\"secret-test-token",
            "null", "{}", "{\"id\":0}", "{\"id\":-1}", "{\"id\":\"42\"}", "{\"id\":42.5}",
            "{\"id\":true}", "[]", "[{\"id\":42}]"})
    void invalidAccountResponseDoesNotPostOrExposeResponseDetails(String response) {
        expectAccount(response);
        expectNoIssue();

        assertAccountLookupFails();
    }

    @Test
    void transportFailureDoesNotPostOrExposeOriginalCause() {
        accountRequest().andRespond(withException(new IOException("secret-test-token reflected")));
        expectNoIssue();

        assertAccountLookupFails();
    }

    private void assertAccountLookupFails() {
        assertThatThrownBy(() -> provider.create(form(HolonetTicketKind.BUG)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Gitea account lookup failed").hasNoCause();
        server.verify();
    }

    private ResponseActions accountRequest() {
        return server.expect(requestTo(BASE_URL + "/api/v1/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "token secret-test-token"))
                .andExpect(header("Accept", "application/json"));
    }

    private void expectAccount(String json) {
        accountRequest().andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private ResponseActions issueRequest() {
        return server.expect(requestTo(ISSUE_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "token secret-test-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    private void expectBug(Locale locale) {
        var expected = issueRequest();
        for (String key : new String[] {"steps", "expected", "actual", "recurrence"}) {
            expected.andExpect(jsonPath("$.body").value(containsString("## " + messages.getMessage("bug." + key, null, locale))));
        }
        expected.andExpect(jsonPath("$.body").value(containsString(messages.getMessage("bug.recurrence-always", null, locale))))
                .andRespond(withStatus(HttpStatus.CREATED));
    }

    private void expectNoIssue() {
        server.expect(ExpectedCount.never(), requestTo(ISSUE_URL)).andRespond(withStatus(HttpStatus.CREATED));
    }

    static HolonetReportFormData form(HolonetTicketKind kind) {
        var session = new HolonetBotInteractionSession();
        session.setGuildId(1L);
        session.setMemberId(2L);
        session.setLocale(Locale.US);
        var data = new HolonetReportFormData();
        data.setSession(session);
        data.setKind(kind);
        data.setTitle("Login fails");
        data.setGuildName("Test server");
        data.setUsername("Tester");
        data.setSteps("Original steps");
        data.setExpected("A dashboard");
        data.setActual("An error");
        data.setRecurrence("always");
        return data;
    }
}
