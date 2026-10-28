package de.medo.holonetbridge.commons.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;

import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderHealthCheckServiceTest {

    private final ProviderHealthCheck gitea = mock(ProviderHealthCheck.class);
    private ProviderHealthCheckService service;

    @BeforeEach
    void setUp() {
        when(gitea.provider()).thenReturn(TicketProvider.GITEA);
        service = new ProviderHealthCheckService(List.of(gitea));
        clearInvocations(gitea);
    }

    @Test
    void dispatchesUrlAndTokenToTheSelectedProvider() {
        service.check(TicketProvider.GITEA, "https://gitea.example.invalid/gitea", "test-token");

        verify(gitea).check(URI.create("https://gitea.example.invalid/gitea"), "test-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://gitea.example.invalid:8443/gitea", "http://gitea.example.invalid:3000",
            "https://gitea.example.invalid", "http://gitea.example.invalid"})
    void preservesThePortSpecifiedInTheUrlAndAllowsSchemeDefaults(String url) {
        service.check(TicketProvider.GITEA, url, "test-token");

        verify(gitea).check(URI.create(url), "test-token");
    }

    @ParameterizedTest
    @CsvSource({
            "gitea.example.invalid, https://gitea.example.invalid",
            "gitea.example.invalid:80, http://gitea.example.invalid:80",
            "gitea.example.invalid:443, https://gitea.example.invalid:443",
            "gitea.example.invalid:30034, https://gitea.example.invalid:30034",
            "gitea.example.invalid/gitea, https://gitea.example.invalid/gitea",
            "gitea.example.invalid:80/gitea, http://gitea.example.invalid:80/gitea",
            "gitea.example.invalid:443/gitea, https://gitea.example.invalid:443/gitea",
            "gitea.example.invalid:30034/gitea, https://gitea.example.invalid:30034/gitea",
            "gitea.example.invalid/gitea:80, https://gitea.example.invalid/gitea:80",
            "localhost:80, http://localhost:80",
            "127.0.0.1:443, https://127.0.0.1:443",
            "[2001:db8::1]:80, http://[2001:db8::1]:80",
            "[2001:db8::1]:443, https://[2001:db8::1]:443",
            "[2001:db8::1], https://[2001:db8::1]",
            "'  gitea.example.invalid:80  ', http://gitea.example.invalid:80"
    })
    void infersTheSchemeFromThePortOnlyWhenTheSchemeIsMissing(String input, String expectedUrl) {
        service.check(TicketProvider.GITEA, input, "test-token");

        verify(gitea).check(URI.create(expectedUrl), "test-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://gitea.example.invalid:443", "https://gitea.example.invalid:80",
            "HTTP://gitea.example.invalid:30034", "HTTPS://gitea.example.invalid:80"})
    void neverOverridesAnExplicitSchemeBasedOnThePort(String url) {
        service.check(TicketProvider.GITEA, url, "test-token");

        verify(gitea).check(URI.create(url), "test-token");
    }

    @Test
    void supportsAdditionalProvidersWithoutChangingTheDispatcher() {
        ProviderHealthCheck jira = mock(ProviderHealthCheck.class);
        when(jira.provider()).thenReturn(TicketProvider.JIRA);
        service = new ProviderHealthCheckService(List.of(gitea, jira));

        service.check(TicketProvider.JIRA, "https://jira.example.invalid", "test-token");

        verify(jira).check(URI.create("https://jira.example.invalid"), "test-token");
        verify(gitea, never()).check(any(), any());
    }

    @Test
    void rejectsProvidersWithoutAnImplementation() {
        assertThatThrownBy(() -> service.check(TicketProvider.JIRA, "https://jira.example.invalid", "test-token"))
                .isInstanceOfSatisfying(ProviderHealthCheckException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.UNSUPPORTED));
        verifyNoInteractions(gitea);
    }

    @Test
    void rejectsDuplicateImplementations() {
        assertThatThrownBy(() -> new ProviderHealthCheckService(List.of(gitea, gitea)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/gitea", "ftp://gitea.example.invalid",
            "https://user:password@gitea.example.invalid", "https://gitea.example.invalid?token=test-token",
            "https://gitea.example.invalid#fragment", "https://gitea.example.invalid:0",
            "https://gitea.example.invalid:65536", "https://", "not a url", "https:/gitea.example.invalid",
            "http:/gitea.example.invalid", "file:/etc", "gitea.example.invalid:", "gitea.example.invalid:0",
            "gitea.example.invalid:65536", "gitea.example.invalid:not-a-port", "//gitea.example.invalid",
            "user:password@gitea.example.invalid", "gitea.example.invalid?token=test-token",
            "gitea.example.invalid#fragment"})
    void rejectsInvalidBaseUrlsBeforeCallingAProvider(String url) {
        assertThatThrownBy(() -> service.check(TicketProvider.GITEA, url, "test-token"))
                .isInstanceOfSatisfying(ProviderHealthCheckException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID_URL));
        verifyNoInteractions(gitea);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void requiresANonblankToken(String token) {
        assertThatThrownBy(() -> service.check(TicketProvider.GITEA, "https://gitea.example.invalid", token))
                .isInstanceOfSatisfying(ProviderHealthCheckException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.TOKEN_REQUIRED));
        verifyNoInteractions(gitea);
    }
}
