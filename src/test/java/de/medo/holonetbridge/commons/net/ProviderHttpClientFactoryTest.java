package de.medo.holonetbridge.commons.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.medo.holonetbridge.gitea.GiteaHealthCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.ResourceAccessException;

class ProviderHttpClientFactoryTest {

    private final ProviderHttpClientFactory factory = new ProviderHttpClientFactory();
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.stop(0);
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1"})
    void healthCheckCanReachLocalTargetsWithoutSpecialConfiguration(String host) {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/gitea/api/v1/user", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"id\":42}");
        });

        new GiteaHealthCheck(factory).check(baseUrl(host, "/gitea"), "test-token");

        assertThat(authorization.get()).isEqualTo("token test-token");
    }

    @Test
    void clientWithoutExplicitTimeoutCanAlsoReachLocalTargets() {
        server.createContext("/api/v1/user", exchange -> respond(exchange, 200, "{\"id\":42}"));

        var response = factory.restClientBuilderFor(baseUrl("localhost", ""))
                .build().get().uri("/api/v1/user").retrieve().toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void doesNotFollowRedirects() {
        AtomicInteger redirectedRequests = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/target");
            respond(exchange, 302, "");
        });
        server.createContext("/target", exchange -> {
            redirectedRequests.incrementAndGet();
            respond(exchange, 200, "{}");
        });

        var response = factory.restClientBuilderFor(baseUrl("localhost", ""), Duration.ofSeconds(2))
                .build().get().uri("/redirect").retrieve().toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(redirectedRequests.get()).isZero();
    }

    @Test
    void doesNotRetryPostAfterTheServerDropsTheConnection() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/issues", exchange -> {
            requests.incrementAndGet();
            exchange.close();
        });
        var client = factory.restClientBuilderFor(baseUrl("127.0.0.1", ""), Duration.ofSeconds(2)).build();

        assertThatThrownBy(() -> client.post().uri("/issues").body("issue").retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void doesNotRetryPostAfterAServiceUnavailableResponse() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/issues", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "0");
            respond(exchange, 503, "unavailable");
        });
        var client = factory.restClientBuilderFor(baseUrl("127.0.0.1", ""), Duration.ofSeconds(2)).build();

        assertThatThrownBy(() -> client.post().uri("/issues").body("issue").retrieve().toBodilessEntity())
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void closingFactoryShutsDownTheSharedBackend() throws IOException {
        server.createContext("/ok", exchange -> respond(exchange, 200, "{}"));
        var first = factory.restClientBuilderFor(baseUrl("127.0.0.1", "")).build();
        var second = factory.restClientBuilderFor(baseUrl("127.0.0.1", "")).build();
        first.get().uri("/ok").retrieve().toBodilessEntity();
        second.get().uri("/ok").retrieve().toBodilessEntity();

        factory.close();

        assertThatThrownBy(() -> first.get().uri("/ok").retrieve().toBodilessEntity())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> second.get().uri("/ok").retrieve().toBodilessEntity())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sharedBackendDoesNotCarryCookiesBetweenGuildClients() {
        AtomicReference<String> cookie = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/first", exchange -> {
            exchange.getResponseHeaders().set("Set-Cookie", "session=first-guild; Path=/");
            respond(exchange, 200, "{}");
        });
        server.createContext("/second", exchange -> {
            cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{}");
        });
        var first = factory.restClientBuilderFor(baseUrl("127.0.0.1", ""))
                .defaultHeader("Authorization", "token first-guild").build();
        var second = factory.restClientBuilderFor(baseUrl("127.0.0.1", ""))
                .defaultHeader("Authorization", "token second-guild").build();

        first.get().uri("/first").retrieve().toBodilessEntity();
        second.get().uri("/second").retrieve().toBodilessEntity();

        assertThat(cookie.get()).isNull();
        assertThat(authorization.get()).isEqualTo("token second-guild");
    }

    @Test
    void retainsTheReadTimeout() {
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
                respond(exchange, 200, "{}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var client = factory.restClientBuilderFor(baseUrl("127.0.0.1", ""), Duration.ofMillis(200)).build();

        try {
            assertThatThrownBy(() -> client.get().uri("/slow").retrieve().toBodilessEntity())
                    .isInstanceOfSatisfying(ResourceAccessException.class,
                            e -> assertThat(e.getMostSpecificCause()).isInstanceOf(SocketTimeoutException.class));
        } finally {
            releaseResponse.countDown();
        }
    }

    private URI baseUrl(String host, String path) {
        return URI.create("http://" + host + ":" + server.getAddress().getPort() + path);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
