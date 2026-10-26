package de.medo.holonetbridge.commons.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import de.medo.holonetbridge.commons.crypto.TokenCryptoService;
import de.medo.holonetbridge.commons.crypto.TokenEncryptionContext;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import de.medo.holonetbridge.gitea.GiteaProviderFactory;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link DefaultProviderConnectionResolver} builds a fresh {@link TicketProvider} from the
 * connection's <em>current</em> decrypted secret on every single call - never a cached one from an earlier
 * resolution - and never mixes up which connection/token belongs to which guild.
 */
class ProviderConnectionResolverTest {

    private static final long GUILD_A = 100L;
    private static final long GUILD_B = 200L;
    private static final String BASE_URL = "https://gitea.example.invalid";
    private static final String OWNER = "acme";
    private static final String REPOSITORY = "support";

    private final HolonetProviderConnectionRepository repository = mock(HolonetProviderConnectionRepository.class);
    private final TokenCryptoService tokenCryptoService = mock(TokenCryptoService.class);
    private final GiteaProviderFactory giteaProviderFactory = mock(GiteaProviderFactory.class);

    private final ProviderConnectionResolver resolver =
            new DefaultProviderConnectionResolver(repository, tokenCryptoService, giteaProviderFactory);

    @Test
    void resolvesTheActiveConnectionForAGuildToAFreshlyBuiltProvider() {
        UUID connectionId = UUID.randomUUID();
        HolonetProviderConnection connection = connection(connectionId, GUILD_A, new byte[] {1}, "v1");
        TicketProvider provider = mock(TicketProvider.class);
        when(repository.findByGuildIdAndActiveTrue(GUILD_A)).thenReturn(Optional.of(connection));
        when(tokenCryptoService.decrypt(connection.encryptedSecret(), new TokenEncryptionContext(GUILD_A, connectionId.toString())))
                .thenReturn("plaintext-token");
        when(giteaProviderFactory.create(URI.create(BASE_URL), "plaintext-token", OWNER, REPOSITORY))
                .thenReturn(provider);

        Optional<ResolvedProvider> resolved = resolver.forGuild(GUILD_A);

        assertThat(resolved).hasValueSatisfying(result -> {
            assertThat(result.connection()).isSameAs(connection);
            assertThat(result.ticketProvider()).isSameAs(provider);
        });
    }

    @Test
    void returnsEmptyWhenTheGuildHasNoActiveConnection() {
        when(repository.findByGuildIdAndActiveTrue(GUILD_A)).thenReturn(Optional.empty());

        assertThat(resolver.forGuild(GUILD_A)).isEmpty();
        verify(giteaProviderFactory, never()).create(any(), any(), any(), any());
    }

    @Test
    void resolvesOneSpecificConnectionByIdRegardlessOfWhetherItIsStillActive() {
        UUID connectionId = UUID.randomUUID();
        HolonetProviderConnection inactiveConnection =
                new HolonetProviderConnection(
                        connectionId,
                        GUILD_A,
                        HolonetProviderType.GITEA,
                        BASE_URL,
                        OWNER,
                        REPOSITORY,
                        false,
                        new EncryptedSecret(new byte[] {9}, new byte[] {9}, "v1"),
                        Instant.EPOCH,
                        Instant.EPOCH);
        TicketProvider provider = mock(TicketProvider.class);
        when(repository.findById(connectionId)).thenReturn(Optional.of(inactiveConnection));
        when(tokenCryptoService.decrypt(
                        inactiveConnection.encryptedSecret(), new TokenEncryptionContext(GUILD_A, connectionId.toString())))
                .thenReturn("old-token");
        when(giteaProviderFactory.create(URI.create(BASE_URL), "old-token", OWNER, REPOSITORY)).thenReturn(provider);

        assertThat(resolver.forConnection(connectionId)).hasValueSatisfying(result -> {
            assertThat(result.connection()).isSameAs(inactiveConnection);
            assertThat(result.ticketProvider()).isSameAs(provider);
        });
        verify(repository, never()).findByGuildIdAndActiveTrue(eq(GUILD_A));
    }

    @Test
    void rebuildsTheProviderWithTheNewTokenAfterARotationInsteadOfReusingAnEarlierClient() {
        UUID connectionId = UUID.randomUUID();
        HolonetProviderConnection beforeRotation = connection(connectionId, GUILD_A, new byte[] {1}, "v1");
        HolonetProviderConnection afterRotation = connection(connectionId, GUILD_A, new byte[] {2}, "v2");
        TicketProvider providerBeforeRotation = mock(TicketProvider.class);
        TicketProvider providerAfterRotation = mock(TicketProvider.class);

        when(repository.findByGuildIdAndActiveTrue(GUILD_A))
                .thenReturn(Optional.of(beforeRotation))
                .thenReturn(Optional.of(afterRotation));
        when(tokenCryptoService.decrypt(beforeRotation.encryptedSecret(), new TokenEncryptionContext(GUILD_A, connectionId.toString())))
                .thenReturn("old-token");
        when(tokenCryptoService.decrypt(afterRotation.encryptedSecret(), new TokenEncryptionContext(GUILD_A, connectionId.toString())))
                .thenReturn("new-token");
        when(giteaProviderFactory.create(URI.create(BASE_URL), "old-token", OWNER, REPOSITORY))
                .thenReturn(providerBeforeRotation);
        when(giteaProviderFactory.create(URI.create(BASE_URL), "new-token", OWNER, REPOSITORY))
                .thenReturn(providerAfterRotation);

        Optional<ResolvedProvider> first = resolver.forGuild(GUILD_A);
        Optional<ResolvedProvider> second = resolver.forGuild(GUILD_A);

        assertThat(first).hasValueSatisfying(result -> assertThat(result.ticketProvider()).isSameAs(providerBeforeRotation));
        assertThat(second).hasValueSatisfying(result -> assertThat(result.ticketProvider()).isSameAs(providerAfterRotation));
        verify(giteaProviderFactory).create(URI.create(BASE_URL), "old-token", OWNER, REPOSITORY);
        verify(giteaProviderFactory).create(URI.create(BASE_URL), "new-token", OWNER, REPOSITORY);
    }

    @Test
    void neverMixesUpTwoGuildsConnectionsOrTokens() {
        UUID connectionIdA = UUID.randomUUID();
        UUID connectionIdB = UUID.randomUUID();
        HolonetProviderConnection connectionA = connection(connectionIdA, GUILD_A, new byte[] {1}, "v1");
        HolonetProviderConnection connectionB = connection(connectionIdB, GUILD_B, new byte[] {2}, "v1");
        TicketProvider providerA = mock(TicketProvider.class);
        TicketProvider providerB = mock(TicketProvider.class);

        when(repository.findByGuildIdAndActiveTrue(GUILD_A)).thenReturn(Optional.of(connectionA));
        when(repository.findByGuildIdAndActiveTrue(GUILD_B)).thenReturn(Optional.of(connectionB));
        when(tokenCryptoService.decrypt(connectionA.encryptedSecret(), new TokenEncryptionContext(GUILD_A, connectionIdA.toString())))
                .thenReturn("token-a");
        when(tokenCryptoService.decrypt(connectionB.encryptedSecret(), new TokenEncryptionContext(GUILD_B, connectionIdB.toString())))
                .thenReturn("token-b");
        when(giteaProviderFactory.create(URI.create(BASE_URL), "token-a", OWNER, REPOSITORY)).thenReturn(providerA);
        when(giteaProviderFactory.create(URI.create(BASE_URL), "token-b", OWNER, REPOSITORY)).thenReturn(providerB);

        Optional<ResolvedProvider> resolvedA = resolver.forGuild(GUILD_A);
        Optional<ResolvedProvider> resolvedB = resolver.forGuild(GUILD_B);

        assertThat(resolvedA).hasValueSatisfying(result -> {
            assertThat(result.connection()).isSameAs(connectionA);
            assertThat(result.ticketProvider()).isSameAs(providerA);
        });
        assertThat(resolvedB).hasValueSatisfying(result -> {
            assertThat(result.connection()).isSameAs(connectionB);
            assertThat(result.ticketProvider()).isSameAs(providerB);
        });
    }

    private static HolonetProviderConnection connection(UUID id, long guildId, byte[] ciphertext, String keyVersion) {
        return new HolonetProviderConnection(
                id,
                guildId,
                HolonetProviderType.GITEA,
                BASE_URL,
                OWNER,
                REPOSITORY,
                true,
                new EncryptedSecret(ciphertext, new byte[] {0}, keyVersion),
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
