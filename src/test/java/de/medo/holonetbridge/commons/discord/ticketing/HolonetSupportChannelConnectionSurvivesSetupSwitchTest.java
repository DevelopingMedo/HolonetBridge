package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import de.medo.holonetbridge.commons.crypto.TokenCryptoService;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnection;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionRepository;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import de.medo.holonetbridge.commons.provider.HolonetProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the binding rule a ticket's {@code connectionId} is frozen under: once
 * {@link HolonetSupportChannel#ticketCreated} has recorded which connection a channel's ticket was actually
 * filed against, a later {@code /setup} target switch for the same guild (via
 * {@link HolonetProviderConnectionService#activate}) must never re-resolve or otherwise change that
 * channel's {@code connectionId} - the ticket keeps pointing at the (now inactive) connection it was
 * created against.
 *
 * <p>{@code @DataJpaTest} does not run Liquibase, so {@code ddl-auto} is overridden back to
 * {@code create-drop} here instead of inheriting the application-wide {@code validate}.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetSupportChannelConnectionSurvivesSetupSwitchTest {

    private static final long GUILD_ID = 999L;

    @Autowired
    private HolonetSupportChannelRepository supportChannelRepository;

    @Autowired
    private HolonetProviderConnectionRepository providerConnectionRepository;

    private HolonetProviderConnectionService providerConnectionService;

    @BeforeEach
    void setUp() {
        TokenCryptoService tokenCryptoService = mock(TokenCryptoService.class);
        when(tokenCryptoService.encrypt(any(), any()))
                .thenReturn(new EncryptedSecret(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, "v1"));
        providerConnectionService = new HolonetProviderConnectionService(providerConnectionRepository, tokenCryptoService);
    }

    @Test
    void switchingTheActiveConnectionAfterATicketWasCreatedLeavesTheChannelsConnectionIdUnchanged() {
        HolonetProviderConnection first = providerConnectionService.activate(
                GUILD_ID, HolonetProviderType.GITEA, "https://gitea.example.invalid", "acme", "support", "token-a");

        HolonetSupportChannel channel = new HolonetSupportChannel(111L, GUILD_ID, 123L, 1L);
        channel.ticketCreated(first.id(), "42", "https://gitea.example.invalid/acme/support/issues/42");
        supportChannelRepository.save(channel);

        providerConnectionService.activate(
                GUILD_ID,
                HolonetProviderType.GITEA,
                "https://gitea.example.invalid",
                "acme",
                "a-different-repository",
                "token-b");

        assertThat(supportChannelRepository.findById(111L)).hasValueSatisfying(saved -> {
            assertThat(saved.connectionId()).isEqualTo(first.id());
            assertThat(saved.ticketId()).isEqualTo("42");
        });
        assertThat(providerConnectionRepository.findById(first.id())).hasValueSatisfying(
                connection -> assertThat(connection.active()).isFalse());
    }

    @Test
    void aSupportChannelOpenedBeforeATicketExistsIsUnaffectedByAnActiveConnectionSwitchSinceItHasNoneYet() {
        providerConnectionService.activate(
                GUILD_ID, HolonetProviderType.GITEA, "https://gitea.example.invalid", "acme", "support", "token-a");

        HolonetSupportChannel channel = new HolonetSupportChannel(111L, GUILD_ID, 123L, 1L);
        supportChannelRepository.save(channel);

        providerConnectionService.activate(
                GUILD_ID,
                HolonetProviderType.GITEA,
                "https://gitea.example.invalid",
                "acme",
                "a-different-repository",
                "token-b");

        assertThat(supportChannelRepository.findById(111L)).hasValueSatisfying(
                saved -> assertThat(saved.connectionId()).isNull());
    }
}
