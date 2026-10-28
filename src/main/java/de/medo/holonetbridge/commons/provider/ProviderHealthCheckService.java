package de.medo.holonetbridge.commons.provider;

import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException.Reason;
import org.springframework.stereotype.Service;

@Service
public class ProviderHealthCheckService {

    private final Map<TicketProvider, ProviderHealthCheck> healthChecks = new EnumMap<>(TicketProvider.class);

    public ProviderHealthCheckService(List<ProviderHealthCheck> healthChecks) {
        for (ProviderHealthCheck healthCheck : healthChecks) {
            if (this.healthChecks.putIfAbsent(healthCheck.provider(), healthCheck) != null) {
                throw new IllegalStateException("Duplicate healthcheck for provider " + healthCheck.provider());
            }
        }
    }

    public void check(TicketProvider provider, String baseUrl, String token) {
        ProviderHealthCheck healthCheck = healthChecks.get(provider);
        if (healthCheck == null) {
            throw new ProviderHealthCheckException(Reason.UNSUPPORTED);
        }
        URI uri = parseBaseUrl(baseUrl);
        if (token == null || token.isBlank()) {
            throw new ProviderHealthCheckException(Reason.TOKEN_REQUIRED);
        }
        healthCheck.check(uri, token);
    }

    private static URI parseBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getPort() == 0
                    || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ProviderHealthCheckException(Reason.INVALID_URL);
        }
    }
}
