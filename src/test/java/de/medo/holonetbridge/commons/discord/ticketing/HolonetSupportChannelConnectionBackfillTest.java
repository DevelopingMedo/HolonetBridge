package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import de.medo.holonetbridge.commons.crypto.EncryptedSecret;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnection;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionRepository;
import de.medo.holonetbridge.commons.provider.HolonetProviderType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies {@link HolonetSupportChannelConnectionBackfill}: a legacy row (pre-dating {@code connectionId})
 * is only auto-linked when its guild has exactly one {@code HolonetProviderConnection} to pair it with
 * unambiguously; zero or more than one candidate must leave it unresolved rather than guess.
 *
 * <p>{@code @DataJpaTest} does not run Liquibase, so {@code ddl-auto} is overridden back to
 * {@code create-drop} here instead of inheriting the application-wide {@code validate}.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetSupportChannelConnectionBackfillTest {

    private static final long GUILD_ID = 999L;
    private static final long OTHER_GUILD_ID = 888L;

    @Autowired
    private HolonetSupportChannelRepository supportChannelRepository;

    @Autowired
    private HolonetProviderConnectionRepository providerConnectionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aLegacyRowIsLinkedWhenItsGuildHasExactlyOneCandidateConnection() {
        UUID connectionId = saveConnection(GUILD_ID);
        insertLegacyRow(111L, GUILD_ID, "LEGACY-1");

        new HolonetSupportChannelConnectionBackfill(supportChannelRepository, providerConnectionRepository)
                .backfill();

        assertThat(supportChannelRepository.findById(111L)).hasValueSatisfying(channel -> {
            assertThat(channel.connectionId()).isEqualTo(connectionId);
            assertThat(channel.ticketId()).isEqualTo("LEGACY-1");
        });
    }

    @Test
    void aLegacyRowIsLeftUnresolvedWhenItsGuildHasNoCandidateConnection() {
        insertLegacyRow(222L, GUILD_ID, "LEGACY-2");

        new HolonetSupportChannelConnectionBackfill(supportChannelRepository, providerConnectionRepository)
                .backfill();

        assertThat(supportChannelRepository.findById(222L)).hasValueSatisfying(
                channel -> assertThat(channel.connectionId()).isNull());
    }

    @Test
    void aLegacyRowIsLeftUnresolvedWhenItsGuildHasMoreThanOneCandidateConnection() {
        saveConnection(GUILD_ID);
        saveConnection(GUILD_ID);
        insertLegacyRow(333L, GUILD_ID, "LEGACY-3");

        new HolonetSupportChannelConnectionBackfill(supportChannelRepository, providerConnectionRepository)
                .backfill();

        assertThat(supportChannelRepository.findById(333L)).hasValueSatisfying(
                channel -> assertThat(channel.connectionId()).isNull());
    }

    @Test
    void eachGuildIsResolvedIndependently() {
        UUID connectionId = saveConnection(GUILD_ID);
        saveConnection(OTHER_GUILD_ID);
        saveConnection(OTHER_GUILD_ID);
        insertLegacyRow(111L, GUILD_ID, "LEGACY-1");
        insertLegacyRow(222L, OTHER_GUILD_ID, "LEGACY-2");

        new HolonetSupportChannelConnectionBackfill(supportChannelRepository, providerConnectionRepository)
                .backfill();

        assertThat(supportChannelRepository.findById(111L)).hasValueSatisfying(
                channel -> assertThat(channel.connectionId()).isEqualTo(connectionId));
        assertThat(supportChannelRepository.findById(222L)).hasValueSatisfying(
                channel -> assertThat(channel.connectionId()).isNull());
    }

    private UUID saveConnection(long guildId) {
        UUID id = UUID.randomUUID();
        providerConnectionRepository.save(new HolonetProviderConnection(
                id,
                guildId,
                HolonetProviderType.GITEA,
                "https://gitea.example.invalid",
                "acme",
                "support-" + id,
                true,
                new EncryptedSecret(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, "v1"),
                Instant.EPOCH,
                Instant.EPOCH));
        return id;
    }

    /**
     * {@code HolonetSupportChannel.ticketCreated} can no longer produce {@code ticketId} set with
     * {@code connectionId} still null - that combination only exists as genuinely pre-existing (legacy)
     * data from before {@code connectionId} was introduced. A raw insert is the only way left to construct
     * that scenario in a test; see {@code HolonetSupportChannelRepositoryTest} for the same technique.
     */
    private void insertLegacyRow(long channelId, long guildId, String ticketId) {
        entityManager.createNativeQuery(
                        "insert into holonet_support_channel "
                                + "(channel_id, guild_id, requester_user_id, number, ticket_id, status) "
                                + "values (:channelId, :guildId, 1, 1, :ticketId, 'OPEN')")
                .setParameter("channelId", channelId)
                .setParameter("guildId", guildId)
                .setParameter("ticketId", ticketId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
