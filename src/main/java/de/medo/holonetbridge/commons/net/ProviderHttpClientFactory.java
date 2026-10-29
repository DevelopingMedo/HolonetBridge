package de.medo.holonetbridge.commons.net;

import java.net.URI;
import java.time.Duration;

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

    public RestClient.Builder restClientBuilderFor(URI baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClientFor(ConnectionConfig.DEFAULT)));
    }

    public RestClient.Builder restClientBuilderFor(URI baseUrl, Duration timeout) {
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClientFor(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(timeout))
                        .setSocketTimeout(Timeout.of(timeout))
                        .build()));
        requestFactory.setConnectionRequestTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl.toString()).requestFactory(requestFactory);
    }

    private CloseableHttpClient httpClientFor(ConnectionConfig connectionConfig) {
        // Do not forward a connection's token to a redirect target.
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig)
                        .build())
                .disableRedirectHandling()
                .build();
    }
}
