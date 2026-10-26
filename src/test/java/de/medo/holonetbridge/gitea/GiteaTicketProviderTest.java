package de.medo.holonetbridge.gitea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import de.medo.holonetbridge.commons.extapi.TicketDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class GiteaTicketProviderTest {

    private static final URI BASE_URL = URI.create("https://tickets.example.invalid");

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private GiteaTicketProvider provider;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new GiteaTicketProvider(restClientBuilder, BASE_URL, "secret-test-token", "acme", "support");
    }

    @Test
    void createsAnIssueAndMapsItsIssueNumberAndFieldsToATicketDto() {
        server.expect(requestTo("https://tickets.example.invalid/api/v1/repos/acme/support/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "token secret-test-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"title":"[Bug] Login fails","body":"Steps to reproduce..."}
                        """))
                .andRespond(withSuccess(
                        """
                        {"number":42,"id":999,"title":"[Bug] Login fails","body":"Steps to reproduce...",
                        "html_url":"https://tickets.example.invalid/acme/support/issues/42"}
                        """,
                        MediaType.APPLICATION_JSON));

        TicketDto ticket = provider.create("[Bug] Login fails", "Steps to reproduce...");

        assertThat(ticket.id()).isEqualTo("42");
        assertThat(ticket.title()).isEqualTo("[Bug] Login fails");
        assertThat(ticket.description()).isEqualTo("Steps to reproduce...");
        assertThat(ticket.url()).isEqualTo("https://tickets.example.invalid/acme/support/issues/42");
        server.verify();
    }

    @Test
    void createsAnIssueWithAMissingHtmlUrlLeavesTicketUrlNull() {
        server.expect(requestTo("https://tickets.example.invalid/api/v1/repos/acme/support/issues"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"number":43,"id":1000,"title":"[Bug] Login fails","body":"Steps to reproduce..."}
                        """,
                        MediaType.APPLICATION_JSON));

        TicketDto ticket = provider.create("[Bug] Login fails", "Steps to reproduce...");

        assertThat(ticket.url()).isNull();
        server.verify();
    }

    @Test
    void createPropagatesAServerErrorFromGiteaAsAnException() {
        server.expect(requestTo("https://tickets.example.invalid/api/v1/repos/acme/support/issues"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.create("[Bug] Login fails", "Steps to reproduce..."))
                .isInstanceOf(HttpServerErrorException.class);
        server.verify();
    }

    @Test
    void readEditAndCommentAreNotYetImplemented() {
        assertThatThrownBy(() -> provider.read("42")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.edit("42", "title", "description"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.comment("42", "a comment"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
