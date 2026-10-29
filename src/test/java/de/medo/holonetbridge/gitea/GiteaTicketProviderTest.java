package de.medo.holonetbridge.gitea;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GiteaTicketProviderTest {

    private MockRestServiceServer server;
    private GiteaTicketProvider provider;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GiteaTicketProvider(builder, URI.create("https://tickets.example.invalid/gitea"),
                "secret-test-token", "acme", "support",
                new MessageSourceConfiguration().messageSource(new I18nProperties(null)));
    }

    @Test
    void postsBugWithAuthenticationLocalizedMarkdownAndDiscordContext() {
        var data = form(HolonetTicketKind.BUG);
        data.setSteps("1. **Log in**\n2. Wait");
        data.setExpected("A dashboard");
        data.setActual("An error");
        data.setRecurrence("always");
        data.getSession().setLocale(Locale.GERMAN);
        server.expect(requestTo("https://tickets.example.invalid/gitea/api/v1/repos/acme/support/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "token secret-test-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
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
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(),
                        requestTo("https://tickets.example.invalid/gitea/api/v1/repos/acme/support/issues"))
                .andExpect(content().json("""
                        {"title":"[Feature] Login fails","body":"  Original\\n**text**  \\n\\n## Discord\\n\\nServer: Test server (ID: 1)\\n\\nUser: Tester (ID: 2)"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED));

        provider.create(data);
        provider.create(data);

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308, 401, 403, 404, 429, 500, 503})
    void rejectsNonSuccessWithoutExposingReflectedSecrets(int status) {
        server.expect(requestTo("https://tickets.example.invalid/gitea/api/v1/repos/acme/support/issues"))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .header("Location", "https://secret-test-token.invalid")
                        .body("secret-test-token").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> provider.create(form(HolonetTicketKind.FEATURE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP " + status)
                .hasMessageNotContaining("secret-test-token")
                .hasNoCause();
        server.verify();
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
        return data;
    }
}
