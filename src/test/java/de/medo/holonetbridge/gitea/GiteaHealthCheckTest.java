package de.medo.holonetbridge.gitea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.net.SsrfBlockedTargetException;
import de.medo.holonetbridge.commons.net.SsrfSafeHttpClientFactory;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GiteaHealthCheckTest {

    private static final URI BASE_URL = URI.create("https://gitea.example.invalid/gitea");
    private static final String USER_URL = "https://gitea.example.invalid/gitea/api/v1/user";
    private final SsrfSafeHttpClientFactory factory = mock(SsrfSafeHttpClientFactory.class);
    private final GiteaHealthCheck healthCheck = new GiteaHealthCheck(factory);
    private final Logger logger = (Logger) LoggerFactory.getLogger(GiteaHealthCheck.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL.toString());
        server = MockRestServiceServer.bindTo(builder).build();
        when(factory.restClientBuilderFor(BASE_URL, Duration.ofSeconds(10))).thenReturn(builder);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void checksTheAuthenticatedUserUsingTheSuppliedToken() {
        assertThat(healthCheck.provider()).isEqualTo(TicketProvider.GITEA);
        server.expect(requestTo(USER_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "token test-token"))
                .andRespond(withSuccess("{\"id\":42,\"login\":\"test-user\"}", MediaType.APPLICATION_JSON));

        healthCheck.check(BASE_URL, "test-token");

        server.verify();
        verify(factory).restClientBuilderFor(BASE_URL, Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://gitea.example.invalid:8443/gitea", "http://gitea.example.invalid:3000"})
    void sendsTheRequestToThePortFromTheBaseUrl(String url) {
        URI baseUrl = URI.create(url);
        RestClient.Builder builder = RestClient.builder().baseUrl(url);
        MockRestServiceServer portServer = MockRestServiceServer.bindTo(builder).build();
        when(factory.restClientBuilderFor(baseUrl, Duration.ofSeconds(10))).thenReturn(builder);
        portServer.expect(requestTo(url + "/api/v1/user"))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        healthCheck.check(baseUrl, "test-token");

        portServer.verify();
    }

    @ParameterizedTest
    @CsvSource({"401, ACCESS", "403, ACCESS", "404, REJECTED", "500, REJECTED", "302, REJECTED"})
    void rejectsHttpErrorsAndRedirects(int status, Reason reason) {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatusCode.valueOf(status)));

        assertFailure(reason);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"id\":0}", "{\"id\":-1}", "{\"id\":null}", "not-json"})
    void rejectsEmptyOrInvalidUserResponses(String body) {
        server.expect(requestTo(USER_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertFailure(Reason.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void rejectsHtmlPagesEvenIfTheyReturnHttp200() {
        server.expect(requestTo(USER_URL)).andRespond(withSuccess("<html>login</html>", MediaType.TEXT_HTML));

        assertFailure(Reason.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void mapsNetworkFailuresToUnreachable() {
        server.expect(requestTo(USER_URL)).andRespond(withException(new IOException("reflected test-token")));

        assertFailure(Reason.UNREACHABLE);
        assertThat(logs.list.getFirst().getFormattedMessage()).contains("diagnostic=IOException", "port=443");
        server.verify();
    }

    @Test
    void mapsSecurityPolicyBlocksToUnreachable() {
        when(factory.restClientBuilderFor(BASE_URL, Duration.ofSeconds(10)))
                .thenThrow(new SsrfBlockedTargetException("blocked test target"));

        assertFailure(Reason.UNREACHABLE);
        assertThat(logs.list.getFirst().getFormattedMessage()).contains("diagnostic=SsrfBlockedTargetException");
    }

    @Test
    void logsDnsFailuresDistinctlyFromSecurityPolicyBlocks() {
        when(factory.restClientBuilderFor(BASE_URL, Duration.ofSeconds(10)))
                .thenThrow(new SsrfBlockedTargetException("reflected test-token", new UnknownHostException("test-token")));

        assertFailure(Reason.UNREACHABLE);

        assertThat(logs.list.getFirst().getFormattedMessage()).contains("diagnostic=UnknownHostException");
    }

    @Test
    void doesNotRetainTheProvidersResponseBodyInExceptions() {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatusCode.valueOf(401))
                .body("reflected test-token"));

        assertThatThrownBy(() -> healthCheck.check(BASE_URL, "test-token"))
                .isInstanceOfSatisfying(ProviderHealthCheckException.class, e -> {
                    assertThat(e.getMessage()).doesNotContain("test-token");
                    assertThat(e.getCause()).isNull();
                });
        assertSafeFailureLog();
    }

    private void assertFailure(Reason reason) {
        assertThatThrownBy(() -> healthCheck.check(BASE_URL, "test-token"))
                .isInstanceOfSatisfying(ProviderHealthCheckException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
        assertSafeFailureLog();
    }

    private void assertSafeFailureLog() {
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(event.getFormattedMessage()).contains("Gitea healthcheck failed", "host=gitea.example.invalid")
                    .doesNotContain("test-token", "reflected");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
