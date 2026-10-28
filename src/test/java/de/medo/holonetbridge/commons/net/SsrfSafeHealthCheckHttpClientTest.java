package de.medo.holonetbridge.commons.net;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SsrfSafeHealthCheckHttpClientTest {

    @ParameterizedTest
    @CsvSource({"https://gitea.example.invalid:8443/gitea, 8443", "http://gitea.example.invalid:3000, 3000",
            "https://gitea.example.invalid, 443", "http://gitea.example.invalid, 80"})
    void checksTheExplicitPortOrTheSchemesDefaultPort(String url, int expectedPort) {
        SsrfSafeUrlValidator validator = mock(SsrfSafeUrlValidator.class);
        SsrfSafeHttpClientFactory factory = new SsrfSafeHttpClientFactory(validator);

        factory.restClientBuilderFor(URI.create(url), Duration.ofSeconds(10));

        verify(validator).resolveSafeAddresses("gitea.example.invalid", expectedPort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1", "http://10.0.0.1", "http://169.254.169.254", "http://[::1]"})
    void blocksInternalIpLiteralsBeforeTheHttpClientCanBypassDnsValidation(String url) {
        SsrfSafeHttpClientFactory factory = new SsrfSafeHttpClientFactory(
                new SsrfSafeUrlValidator(new SsrfPolicyProperties(List.of())));

        assertThatThrownBy(() -> factory.restClientBuilderFor(URI.create(url), Duration.ofSeconds(10)))
                .isInstanceOf(SsrfBlockedTargetException.class);
    }
}
