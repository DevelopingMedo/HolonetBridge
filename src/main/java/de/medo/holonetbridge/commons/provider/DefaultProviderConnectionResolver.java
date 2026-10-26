package de.medo.holonetbridge.commons.provider;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import de.medo.holonetbridge.commons.crypto.TokenCryptoService;
import de.medo.holonetbridge.commons.crypto.TokenEncryptionContext;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.gitea.GiteaProviderFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultProviderConnectionResolver implements ProviderConnectionResolver {

    private final HolonetProviderConnectionRepository repository;
    private final TokenCryptoService tokenCryptoService;
    private final GiteaProviderFactory giteaProviderFactory;

    public DefaultProviderConnectionResolver(
            HolonetProviderConnectionRepository repository,
            TokenCryptoService tokenCryptoService,
            GiteaProviderFactory giteaProviderFactory) {
        this.repository = repository;
        this.tokenCryptoService = tokenCryptoService;
        this.giteaProviderFactory = giteaProviderFactory;
    }

    @Override
    public Optional<ResolvedProvider> forGuild(long guildId) {
        return repository.findByGuildIdAndActiveTrue(guildId).map(this::resolve);
    }

    @Override
    public Optional<ResolvedProvider> forConnection(UUID connectionId) {
        return repository.findById(connectionId).map(this::resolve);
    }

    private ResolvedProvider resolve(HolonetProviderConnection connection) {
        String token = tokenCryptoService.decrypt(
                connection.encryptedSecret(),
                new TokenEncryptionContext(connection.guildId(), connection.id().toString()));
        TicketProvider ticketProvider =
                switch (connection.providerType()) {
                    case GITEA -> giteaProviderFactory.create(
                            URI.create(connection.baseUrl()), token, connection.owner(), connection.repository());
                };
        return new ResolvedProvider(connection, ticketProvider);
    }
}
