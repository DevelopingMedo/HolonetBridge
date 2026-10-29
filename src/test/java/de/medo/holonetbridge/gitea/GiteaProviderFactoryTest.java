package de.medo.holonetbridge.gitea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.net.ProviderHttpClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.context.MessageSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies {@link GiteaProviderFactory} builds a brand-new {@link GiteaTicketProvider} per call, wired with
 * the exact {@code baseUrl}/token/owner/repository given to it and routed through {@link
 * ProviderHttpClientFactory} - never a single shared instance reused across guilds or token rotations (see
 * that class's Javadoc for why that matters).
 */
class GiteaProviderFactoryTest {

    private static final URI BASE_URL = URI.create("https://gitea.example.invalid");

    @Test
    void createsAProviderWiredWithTheGivenTargetAndToken() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ProviderHttpClientFactory httpClientFactory = mock(ProviderHttpClientFactory.class);
        when(httpClientFactory.restClientBuilderFor(BASE_URL)).thenReturn(restClientBuilder);
        GiteaProviderFactory factory = new GiteaProviderFactory(httpClientFactory, mock(MessageSource.class));

        TicketProvider provider = factory.create("gitea.example.invalid", "a-token", "acme/support");

        server.expect(requestTo("https://gitea.example.invalid/api/v1/repos/acme/support/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "token a-token"))
                .andRespond(withSuccess(
                        """
                        {"number":1,"title":"t","body":"d","html_url":"https://gitea.example.invalid/acme/support/issues/1"}
                        """,
                        MediaType.APPLICATION_JSON));

        provider.create(GiteaTicketProviderTest.form(HolonetTicketKind.FEATURE));
        server.verify();
    }

    @Test
    void everyCallUsesTheHttpClientFactoryForTheGivenBaseUrl() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer.bindTo(restClientBuilder).build();
        ProviderHttpClientFactory httpClientFactory = mock(ProviderHttpClientFactory.class);
        when(httpClientFactory.restClientBuilderFor(BASE_URL)).thenReturn(restClientBuilder);
        GiteaProviderFactory factory = new GiteaProviderFactory(httpClientFactory, mock(MessageSource.class));

        factory.create(BASE_URL.toString(), "token-one", "acme/support");
        factory.create(BASE_URL.toString(), "token-two", "acme/support");

        verify(httpClientFactory, org.mockito.Mockito.times(2)).restClientBuilderFor(BASE_URL);
    }

    @Test
    void twoCallsProduceTwoIndependentProviderInstances() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer.bindTo(restClientBuilder).build();
        ProviderHttpClientFactory httpClientFactory = mock(ProviderHttpClientFactory.class);
        when(httpClientFactory.restClientBuilderFor(BASE_URL)).thenReturn(restClientBuilder);
        GiteaProviderFactory factory = new GiteaProviderFactory(httpClientFactory, mock(MessageSource.class));

        TicketProvider first = factory.create(BASE_URL.toString(), "token-one", "acme/support");
        TicketProvider second = factory.create(BASE_URL.toString(), "token-two", "acme/support");

        assertThat(first).isNotSameAs(second);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "acme", "/support", "acme/", "a/b/c", "../support", "a/b?token=x", "a/b#fragment", "a/b%2fother"})
    void rejectsInvalidTargetsBeforeCreatingAClient(String target) {
        var http = mock(ProviderHttpClientFactory.class);
        var factory = new GiteaProviderFactory(http, mock(MessageSource.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                factory.create(BASE_URL.toString(), "a-token", target)).isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(http);
    }
}
