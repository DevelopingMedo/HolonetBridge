package de.medo.holonetbridge.commons.provider;

import java.net.URI;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.commons.net.ProviderHttpClientFactory;
import de.medo.holonetbridge.gitea.GiteaTicketProvider;
import org.springframework.stereotype.Component;
import org.springframework.context.MessageSource;

/**
 * Builds a separate client per connection so guilds and token rotations never reuse another connection's
 * credentials.
 */
@Component
public class ProviderFactory {

    private final ProviderHttpClientFactory providerHttpClientFactory;
    private final EncryptionService encryptionService;
    private final MessageSource messageSource;

    public ProviderFactory(ProviderHttpClientFactory providerHttpClientFactory, EncryptionService encryptionService,
            MessageSource messageSource) {
        this.providerHttpClientFactory = providerHttpClientFactory;
        this.encryptionService = encryptionService;
        this.messageSource = messageSource;
    }

    public TicketProvider create(de.medo.holonetbridge.commons.domain.TicketProvider provider,
            String baseUrl, String encryptedToken, String target) {
        return switch (provider) {
            case GITEA -> createGitea(baseUrl, encryptionService.decrypt(encryptedToken), target);
            case JIRA, GITHUB, TRELLO -> throw new UnsupportedOperationException("Provider is not implemented");
        };
    }

    private TicketProvider createGitea(String baseUrl, String token, String target) {
        URI uri = ProviderHealthCheckService.parseBaseUrl(baseUrl);
        String[] parts = target == null ? new String[0] : target.split("/", -1);
        if (parts.length != 2 || !validTargetPart(parts[0]) || !validTargetPart(parts[1])) {
            throw new IllegalArgumentException("Gitea target must be owner/repository");
        }
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) {
            throw new IllegalArgumentException("A valid Gitea token is required");
        }
        return new GiteaTicketProvider(providerHttpClientFactory.restClientBuilderFor(uri), uri, token, parts[0], parts[1],
                messageSource);
    }

    private static boolean validTargetPart(String part) {
        return part.matches("[A-Za-z0-9_.-]+") && !part.equals(".") && !part.equals("..");
    }
}
