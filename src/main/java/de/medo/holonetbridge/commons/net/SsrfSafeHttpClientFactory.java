package de.medo.holonetbridge.commons.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds a {@link RestClient.Builder} scoped to exactly one provider-connection target (one host:port), with
 * {@link SsrfSafeUrlValidator} wired in at the one place that actually matters: the connection manager's
 * {@link DnsResolver}. Apache HttpClient5 connects to whatever addresses that resolver returns without
 * resolving the hostname again itself, so returning only already-validated addresses from here closes the
 * resolve-then-reconnect window a "check the URL, then let the HTTP client connect normally" approach would
 * leave open. The {@code Host} header and TLS SNI still use the original hostname either way, since neither
 * one is affected by which concrete address the socket connects to - only {@code getAllByName}/connect is
 * intercepted.
 *
 * <p>Each target gets its own {@link DnsResolver} closed over that one expected host, as defense in depth
 * against this resolver ever being asked to resolve a different name (which should not happen: redirects are
 * disabled below, and every client built here is used for calls to a single configured base URL only).
 *
 * <p>Redirects are disabled outright rather than re-validated per hop, since a provider adapter has no
 * legitimate reason to redirect across hosts and this is the simpler of the two patterns the SSRF design
 * allows for handling them.
 */
@Component
public class SsrfSafeHttpClientFactory {

    private final SsrfSafeUrlValidator validator;

    public SsrfSafeHttpClientFactory(SsrfSafeUrlValidator validator) {
        this.validator = validator;
    }

    /** A {@link RestClient.Builder} with {@code baseUrl} as its base URL, already SSRF-safe wired. */
    public RestClient.Builder restClientBuilderFor(URI baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClientFor(baseUrl)));
    }

    private CloseableHttpClient httpClientFor(URI baseUrl) {
        DnsResolver pinnedResolver = new PinnedValidatingDnsResolver(baseUrl.getHost(), resolvePort(baseUrl));

        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(pinnedResolver)
                        .build())
                .disableRedirectHandling()
                .build();
    }

    /**
     * Resolves and validates exactly one fixed host (the base URL this client was built for); see the class
     * Javadoc for why a request for any other host is refused outright instead of being resolved normally.
     */
    private final class PinnedValidatingDnsResolver implements DnsResolver {

        private final String expectedHost;
        private final int port;

        private PinnedValidatingDnsResolver(String expectedHost, int port) {
            this.expectedHost = expectedHost;
            this.port = port;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (!host.toLowerCase(Locale.ROOT).equals(expectedHost.toLowerCase(Locale.ROOT))) {
                throw new UnknownHostException("Refusing to resolve unexpected host '" + host
                        + "' for a client scoped to '" + expectedHost + "'");
            }
            try {
                return validator.resolveSafeAddresses(host, port).toArray(InetAddress[]::new);
            } catch (SsrfBlockedTargetException e) {
                throw new UnknownHostException(e.getMessage());
            }
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }

    private static int resolvePort(URI baseUrl) {
        if (baseUrl.getPort() != -1) {
            return baseUrl.getPort();
        }
        return "https".equalsIgnoreCase(baseUrl.getScheme()) ? 443 : 80;
    }
}
