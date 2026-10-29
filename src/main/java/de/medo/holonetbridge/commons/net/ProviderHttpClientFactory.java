package de.medo.holonetbridge.commons.net;

import java.net.URI;
import java.time.Duration;
import java.io.IOException;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProviderHttpClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private final CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
                            .setSocketTimeout(Timeout.of(READ_TIMEOUT))
                            .build())
                    .build())
            // Never forward a token to a redirect target or automatically repeat an issue POST.
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .build();

    public RestClient.Builder restClientBuilderFor(URI baseUrl) {
        return restClientBuilderFor(baseUrl, READ_TIMEOUT);
    }

    public RestClient.Builder restClientBuilderFor(URI baseUrl, Duration timeout) {
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectionRequestTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl.toString()).requestFactory(requestFactory);
    }

    @PreDestroy
    public void close() throws IOException {
        httpClient.close();
    }
}
