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
    @ValueSource(strings = {"gitea.example.invalid", "/gitea", "ftp://gitea.example.invalid",
            "https://user:password@gitea.example.invalid", "https://gitea.example.invalid?token=test-token",
            "https://gitea.example.invalid#fragment", "https://gitea.example.invalid:0",
            "https://gitea.example.invalid:65536", "https://", "not a url"})
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
