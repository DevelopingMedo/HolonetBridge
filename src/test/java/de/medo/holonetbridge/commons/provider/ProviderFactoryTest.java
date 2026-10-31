package de.medo.holonetbridge.commons.provider;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URI;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.net.ProviderHttpClientFactory;
import de.medo.holonetbridge.gitea.GiteaTicketProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProviderFactoryTest {

    private static final URI BASE_URL = URI.create("https://gitea.example.invalid/gitea");
    private final ProviderHttpClientFactory providerHttpClientFactory = mock(ProviderHttpClientFactory.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);
    private final ProviderFactory providerFactory = new ProviderFactory(
            providerHttpClientFactory, encryptionService, mock(MessageSource.class));

    @Test
    void selectsGiteaAndWiresTheInstallationPathTargetAndDecryptedToken() {
        var server = mockHttpClient();
        when(encryptionService.decrypt("ciphertext")).thenReturn("a-token");

        var provider = providerFactory.create(TicketProvider.GITEA, "gitea.example.invalid/gitea", "ciphertext", "acme/support");

        assertThat(provider).isInstanceOf(GiteaTicketProvider.class);
        server.expect(requestTo(BASE_URL + "/api/v1/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "token a-token"))
                .andRespond(withSuccess("{\"id\":42,\"language\":\"en\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/repos/acme/support"))
                .andRespond(withSuccess("{\"id\":11,\"full_name\":\"acme/support\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/repos/acme/support/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "token a-token"))
                .andExpect(jsonPath("$.title").value("[Feature] Original title"))
                .andRespond(withSuccess("{\"id\":101,\"number\":3,\"state\":\"open\"}", MediaType.APPLICATION_JSON));
        var report = new HolonetReportFormData();
        var session = new HolonetBotInteractionSession();
        session.setGuildId(1L);
        session.setMemberId(2L);
        report.setSession(session);
        report.setKind(HolonetTicketKind.FEATURE);
        report.setTitle("Original title");
        report.setDescription("Original description");

        provider.create(report);

        server.verify();
        verify(encryptionService).decrypt("ciphertext");
        verify(providerHttpClientFactory).restClientBuilderFor(BASE_URL);
    }

    @Test
    void createsIndependentProvidersAndClientsForConnectionsAndTokenRotations() {
        mockHttpClient();
        when(encryptionService.decrypt("first-ciphertext")).thenReturn("token-one");
        when(encryptionService.decrypt("rotated-ciphertext")).thenReturn("token-two");

        var first = providerFactory.create(TicketProvider.GITEA, BASE_URL.toString(), "first-ciphertext", "acme/support");
        var second = providerFactory.create(TicketProvider.GITEA, BASE_URL.toString(), "rotated-ciphertext", "acme/support");

        assertThat(first).isNotSameAs(second);
        verify(providerHttpClientFactory, times(2)).restClientBuilderFor(BASE_URL);
        verify(encryptionService).decrypt("first-ciphertext");
        verify(encryptionService).decrypt("rotated-ciphertext");
    }

    @ParameterizedTest
    @EnumSource(value = TicketProvider.class, names = {"JIRA", "GITHUB", "TRELLO"})
    void rejectsUnsupportedProvidersWithoutDecryptingOrCreatingAClient(TicketProvider provider) {
        assertThatThrownBy(() -> providerFactory.create(provider, BASE_URL.toString(), "ciphertext", "acme/support"))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage("Provider is not implemented");

        verifyNoInteractions(encryptionService, providerHttpClientFactory);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "acme", "/support", "acme/", "a/b/c", "../support", "a/b?token=x", "a/b#fragment", "a/b%2fother"})
    void rejectsInvalidTargetsBeforeCreatingAClient(String target) {
        when(encryptionService.decrypt("ciphertext")).thenReturn("a-token");

        assertThatThrownBy(() -> providerFactory.create(TicketProvider.GITEA, BASE_URL.toString(), "ciphertext", target))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(providerHttpClientFactory);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "token\rvalue", "token\nvalue"})
    void rejectsInvalidDecryptedTokensBeforeCreatingAClient(String token) {
        when(encryptionService.decrypt("ciphertext")).thenReturn(token);

        assertThatThrownBy(() -> providerFactory.create(TicketProvider.GITEA, BASE_URL.toString(), "ciphertext", "acme/support"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(providerHttpClientFactory);
    }

    @Test
    void decryptionFailurePreventsClientCreation() {
        when(encryptionService.decrypt("corrupt")).thenThrow(new IllegalArgumentException("invalid ciphertext"));

        assertThatThrownBy(() -> providerFactory.create(TicketProvider.GITEA, BASE_URL.toString(), "corrupt", "acme/support"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid ciphertext");

        verifyNoInteractions(providerHttpClientFactory);
    }

    private MockRestServiceServer mockHttpClient() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        when(providerHttpClientFactory.restClientBuilderFor(BASE_URL)).thenReturn(restClientBuilder);
        return server;
    }
}
