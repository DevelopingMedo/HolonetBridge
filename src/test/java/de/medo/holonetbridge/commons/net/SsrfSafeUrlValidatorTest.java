package de.medo.holonetbridge.commons.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/**
 * Checks {@link SsrfSafeUrlValidator}'s blocked-address matrix (loopback, private, link-local - including
 * the AWS/Azure/GCP {@code 169.254.169.254} cloud-metadata endpoint - IPv6 unique-local, and multicast) and
 * the operator-only self-hosting allowlist it is deliberately exempt from, plus a component-level proof that
 * {@link SsrfSafeHttpClientFactory} never follows an HTTP redirect to an address it never validated.
 */
class SsrfSafeUrlValidatorTest {

    private final SsrfSafeUrlValidator validator = new SsrfSafeUrlValidator(new SsrfPolicyProperties(List.of()));

    @ParameterizedTest
    @ValueSource(
            strings = {
                "127.0.0.1", // IPv4 loopback
                "127.5.5.5", // still within 127.0.0.0/8
                "::1", // IPv6 loopback
                "10.0.0.1", // IPv4 private (10.0.0.0/8)
                "172.16.0.1", // IPv4 private (172.16.0.0/12)
                "192.168.1.1", // IPv4 private (192.168.0.0/16)
                "169.254.1.1", // IPv4 link-local
                "169.254.169.254", // cloud-metadata endpoint, must stay blocked even though it is "just" link-local
                "fe80::1", // IPv6 link-local
                "fc00::1", // IPv6 unique-local (low end of fc00::/7)
                "fd12:3456:789a::1", // IPv6 unique-local (high end of fc00::/7)
                "224.0.0.1", // IPv4 multicast
                "0.0.0.0", // any-local/wildcard
            })
    void everyBlockedAddressRangeIsRejected(String literalIp) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(literalIp);

        assertThat(validator.isBlockedAddress(address)).as("isBlockedAddress(%s)", literalIp).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "203.0.113.5", // IPv4 public (TEST-NET-3), not covered by any blocked range
                "2001:db8::1", // IPv6 public documentation range
            })
    void publicAddressesAreNotBlocked(String literalIp) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(literalIp);

        assertThat(validator.isBlockedAddress(address)).as("isBlockedAddress(%s)", literalIp).isFalse();
    }

    @Test
    void resolvingAHostThatOnlyHasBlockedAddressesThrows() {
        assertThatThrownBy(() -> validator.resolveSafeAddresses("127.0.0.1", 80))
                .isInstanceOf(SsrfBlockedTargetException.class);
    }

    @Test
    void resolvingAPublicAddressSucceeds() throws UnknownHostException {
        List<InetAddress> safe = validator.resolveSafeAddresses("203.0.113.5", 443);

        assertThat(safe).containsExactly(InetAddress.getByName("203.0.113.5"));
    }

    @Test
    void anOperatorAllowlistedInternalTargetBypassesTheBlockedRangeCheckEntirely() {
        SsrfSafeUrlValidator allowlisting =
                new SsrfSafeUrlValidator(new SsrfPolicyProperties(List.of("gitea:3000")));

        // "gitea" does not resolve in this test environment; isAllowlisted() is what the self-hosting
        // allowlist actually needs to prove here - that a docker-compose service name on the operator's own
        // internal network is permitted without having to widen the blocked-range check for everyone else.
        assertThat(allowlisting.isAllowlisted("gitea", 3000)).isTrue();
        assertThat(allowlisting.isAllowlisted("GITEA", 3000)).as("host match is case-insensitive").isTrue();
        assertThat(allowlisting.isAllowlisted("gitea", 8080)).as("port must match exactly too").isFalse();
        assertThat(allowlisting.isAllowlisted("some-other-internal-host", 3000)).isFalse();
    }

    @Test
    void anAllowlistedLoopbackTargetResolvesDespiteOnlyHavingBlockedAddresses() {
        SsrfSafeUrlValidator allowlisting =
                new SsrfSafeUrlValidator(new SsrfPolicyProperties(List.of("127.0.0.1:9999")));

        List<InetAddress> safe = allowlisting.resolveSafeAddresses("127.0.0.1", 9999);

        assertThat(safe).isNotEmpty();
    }

    @Test
    void guildUsersCannotReachALoopbackTargetThroughSetupBecauseTheAllowlistIsOperatorOnlyConfiguration() {
        // The allowlist is only ever populated from operator-controlled configuration (SsrfPolicyProperties
        // is a @ConfigurationProperties record, never written to by the /setup flow), so a guild submitting
        // a loopback/private base URL always hits the default, empty-allowlist validator used by the rest
        // of this test class - which rejects it, as proven by resolvingAHostThatOnlyHasBlockedAddressesThrows.
        assertThat(validator.isAllowlisted("127.0.0.1", 80)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Component-level: SsrfSafeHttpClientFactory must never follow a redirect
    // -----------------------------------------------------------------------

    private HttpServer redirectServer;

    @AfterEach
    void stopServer() {
        if (redirectServer != null) {
            redirectServer.stop(0);
        }
    }

    @Test
    void theHttpClientNeverFollowsARedirectToAnUnvalidatedAddress() throws IOException {
        redirectServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        redirectServer.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        redirectServer.createContext("/target", exchange -> {
            byte[] body = "reached-target".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        redirectServer.start();
        int port = redirectServer.getAddress().getPort();

        SsrfSafeUrlValidator loopbackAllowed =
                new SsrfSafeUrlValidator(new SsrfPolicyProperties(List.of("127.0.0.1:" + port)));
        SsrfSafeHttpClientFactory factory = new SsrfSafeHttpClientFactory(loopbackAllowed);
        RestClient client =
                factory.restClientBuilderFor(URI.create("http://127.0.0.1:" + port)).build();

        HttpStatus status = (HttpStatus)
                client.get().uri("/start").retrieve().toBodilessEntity().getStatusCode();

        assertThat(status).isEqualTo(HttpStatus.FOUND);
    }
}
