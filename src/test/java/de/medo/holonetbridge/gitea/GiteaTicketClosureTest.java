package de.medo.holonetbridge.gitea;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URI;

import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import de.medo.holonetbridge.commons.extapi.TicketReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GiteaTicketClosureTest {
    private static final String BASE = "https://gitea.example.invalid/subpath";
    private static final String REPO = BASE + "/api/v1/repos/acme/support";
    private static final String ISSUE = REPO + "/issues/3";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GiteaTicketProvider provider = new GiteaTicketProvider(builder,
            URI.create("https://GITEA.example.invalid:443/subpath/"), "rotated-test-token", "acme", "support",
            new MessageSourceConfiguration().messageSource(new I18nProperties(null)));
    private final TicketReference reference = new TicketReference("gitea", BASE, "acme", "support", 11L, 101L, 3L);

    @Test
    void closesOnlyStateAfterVerifyingIdentityAndSupportsTokenRotationAndSubpaths() {
        expectRepo(11);
        expectIssue("open");
        server.expect(requestTo(ISSUE)).andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "token rotated-test-token"))
                .andExpect(content().json("{\"state\":\"closed\"}", org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess(issue("closed"), MediaType.APPLICATION_JSON));

        assertThat(provider.supports(reference)).isTrue();
        provider.close(reference);
        server.verify();
    }

    @Test
    void alreadyClosedDoesNotPatch() {
        expectRepo(11);
        expectIssue("closed");
        provider.close(reference);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://other.invalid/subpath", "https://gitea.example.invalid/otherpath"})
    void targetChangesNeverSendCurrentCredentialsToAnOldTarget(String oldBase) {
        var old = new TicketReference("gitea", oldBase, "acme", "support", 11L, 101L, 3L);
        assertThat(provider.supports(old)).isFalse();
        assertThatThrownBy(() -> provider.close(old)).isInstanceOf(IllegalStateException.class).hasNoCause();
        server.verify();
    }

    @Test
    void repositoryChangeIsRejectedBeforeAnyLookup() {
        var other = new TicketReference("gitea", BASE, "acme", "other", 11L, 101L, 3L);
        assertThatThrownBy(() -> provider.close(other)).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void replacedRepositoryIsRejectedBeforeLookingUpTheIssue() {
        expectRepo(12);
        assertClosureFails();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":102,\"number\":3,\"state\":\"open\"}",
            "{\"id\":101,\"number\":4,\"state\":\"open\"}",
            "{\"id\":101,\"number\":3,\"state\":\"open\",\"pull_request\":{}}",
            "{\"id\":101,\"number\":3,\"state\":\"open\",\"repository\":{\"id\":12}}",
            "{\"id\":101,\"number\":3,\"state\":\"unknown\"}", "{}", "null", "not-json rotated-test-token"})
    void invalidIssueOrPullRequestIsNeverPatched(String json) {
        expectRepo(11);
        server.expect(requestTo(ISSUE)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertClosureFails();
    }

    @Test
    void failedPatchCanBeRetriedAndAlreadyClosedIsSuccess() {
        expectRepo(11);
        expectIssue("open");
        server.expect(requestTo(ISSUE)).andExpect(method(HttpMethod.PATCH))
                .andRespond(withServerError().body("rotated-test-token"));
        expectRepo(11);
        expectIssue("closed");
        assertThatThrownBy(() -> provider.close(reference)).hasMessageNotContaining("rotated-test-token").hasNoCause();
        provider.close(reference);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"open", "invalid"})
    void successfulHttpWithoutClosedStateIsNotSuccess(String state) {
        expectRepo(11);
        expectIssue("open");
        server.expect(requestTo(ISSUE)).andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess(issue(state), MediaType.APPLICATION_JSON));
        assertClosureFails();
    }

    private void expectRepo(long id) {
        server.expect(requestTo(REPO)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":" + id + ",\"full_name\":\"acme/support\"}", MediaType.APPLICATION_JSON));
    }
    private void expectIssue(String state) {
        server.expect(requestTo(ISSUE)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(issue(state), MediaType.APPLICATION_JSON));
    }
    private static String issue(String state) {
        return "{\"id\":101,\"number\":3,\"state\":\"" + state + "\",\"repository\":{\"id\":11}}";
    }
    private void assertClosureFails() {
        assertThatThrownBy(() -> provider.close(reference)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Gitea issue closure could not be confirmed").hasNoCause();
        server.verify();
    }
}
