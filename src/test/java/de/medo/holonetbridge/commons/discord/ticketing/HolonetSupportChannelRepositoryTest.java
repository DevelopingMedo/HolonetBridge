package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the JPA mapping of {@link HolonetSupportChannel}, its connection-assignment lifecycle
 * ({@code ticketCreated}/{@code markBroken}), and the paired/backfill lookups used by
 * {@code HolonetTicketSubmitter}/{@code HolonetSupportChannelConnectionBackfill}, against a real (in-memory,
 * test-scoped) database.
 *
 * <p>{@code @DataJpaTest} does not run Liquibase (its auto-configuration slice only pulls in JPA/Hibernate,
 * not {@code LiquibaseAutoConfiguration}), so {@code ddl-auto} is overridden back to {@code create-drop}
 * here instead of inheriting the application-wide {@code validate}; the migration itself is covered
 * separately by the full-context migration smoke test.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetSupportChannelRepositoryTest {

    private static final UUID CONNECTION_ID = UUID.randomUUID();
    private static final UUID OTHER_CONNECTION_ID = UUID.randomUUID();

    @Autowired
    private HolonetSupportChannelRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savedChannelCanBeLoadedBackByChannelId() {
        repository.save(new HolonetSupportChannel(111L, 999L, 123L, 1L));

        assertThat(repository.findById(111L)).hasValueSatisfying(channel -> {
            assertThat(channel.channelId()).isEqualTo(111L);
            assertThat(channel.guildId()).isEqualTo(999L);
            assertThat(channel.requesterUserId()).isEqualTo(123L);
            assertThat(channel.number()).isEqualTo(1L);
            assertThat(channel.connectionId()).isNull();
            assertThat(channel.ticketId()).isNull();
            assertThat(channel.ticketUrl()).isNull();
            assertThat(channel.status()).isEqualTo(HolonetSupportChannelStatus.OPEN);
        });
    }

    @Test
    void ticketCreatedFreezesTheConnectionTicketIdAndUrlAndResetsStatusToOpen() {
        HolonetSupportChannel channel = new HolonetSupportChannel(111L, 999L, 123L, 1L);
        channel.markBroken();
        channel.ticketCreated(CONNECTION_ID, "42", "https://tickets.example.invalid/acme/support/issues/42");
        repository.save(channel);

        assertThat(repository.findById(111L)).hasValueSatisfying(saved -> {
            assertThat(saved.connectionId()).isEqualTo(CONNECTION_ID);
            assertThat(saved.ticketId()).isEqualTo("42");
            assertThat(saved.ticketUrl()).isEqualTo("https://tickets.example.invalid/acme/support/issues/42");
            assertThat(saved.status()).isEqualTo(HolonetSupportChannelStatus.OPEN);
        });
    }

    @Test
    void markBrokenSetsStatusToBrokenWithoutTouchingConnectionOrTicket() {
        HolonetSupportChannel channel = new HolonetSupportChannel(111L, 999L, 123L, 1L);
        channel.ticketCreated(CONNECTION_ID, "42", "https://tickets.example.invalid/acme/support/issues/42");
        repository.save(channel);

        repository.findById(111L).orElseThrow().markBroken();
        repository.flush();

        assertThat(repository.findById(111L)).hasValueSatisfying(saved -> {
            assertThat(saved.status()).isEqualTo(HolonetSupportChannelStatus.BROKEN);
            assertThat(saved.connectionId()).isEqualTo(CONNECTION_ID);
            assertThat(saved.ticketId()).isEqualTo("42");
        });
    }

    @Test
    void findByConnectionIdAndTicketIdOnlyMatchesTheExactConnectionEvenWhenTheTicketNumberCoincides() {
        HolonetSupportChannel first = new HolonetSupportChannel(111L, 999L, 123L, 1L);
        first.ticketCreated(CONNECTION_ID, "42", "https://tickets.example.invalid/a/42");
        HolonetSupportChannel second = new HolonetSupportChannel(222L, 999L, 124L, 2L);
        second.ticketCreated(OTHER_CONNECTION_ID, "42", "https://tickets.example.invalid/b/42");
        repository.save(first);
        repository.save(second);

        assertThat(repository.findByConnectionIdAndTicketId(CONNECTION_ID, "42"))
                .hasValueSatisfying(channel -> assertThat(channel.channelId()).isEqualTo(111L));
        assertThat(repository.findByConnectionIdAndTicketId(OTHER_CONNECTION_ID, "42"))
                .hasValueSatisfying(channel -> assertThat(channel.channelId()).isEqualTo(222L));
    }

    @Test
    void findByConnectionIdIsNullAndTicketIdIsNotNullOnlyReturnsUnresolvedLegacyRows() {
        HolonetSupportChannel resolved = new HolonetSupportChannel(111L, 999L, 123L, 1L);
        resolved.ticketCreated(CONNECTION_ID, "1", null);
        HolonetSupportChannel neverTicketed = new HolonetSupportChannel(222L, 999L, 124L, 2L);
        repository.save(resolved);
        repository.save(neverTicketed);
        insertLegacyRowWithTicketIdButNoConnectionId(333L, 999L, 125L, 3L, "LEGACY-3");

        assertThat(repository.findByConnectionIdIsNullAndTicketIdIsNotNull())
                .extracting(HolonetSupportChannel::channelId)
                .containsExactly(333L);
    }

    @Test
    void linkLegacyConnectionSetsConnectionIdOnAPreExistingLegacyRow() {
        insertLegacyRowWithTicketIdButNoConnectionId(333L, 999L, 125L, 3L, "LEGACY-3");
        HolonetSupportChannel legacy = repository.findById(333L).orElseThrow();

        legacy.linkLegacyConnection(CONNECTION_ID);
        repository.save(legacy);

        assertThat(repository.findById(333L)).hasValueSatisfying(saved -> {
            assertThat(saved.connectionId()).isEqualTo(CONNECTION_ID);
            assertThat(saved.ticketId()).isEqualTo("LEGACY-3");
        });
    }

    /**
     * The current entity API ({@code ticketCreated}) can no longer produce a row with {@code ticketId} set
     * and {@code connectionId} still null - that combination only exists as genuinely pre-existing (legacy)
     * data from before {@code connectionId} was introduced. A raw insert via {@link EntityManager} is
     * therefore the only way left to construct that exact scenario in a test.
     */
    private void insertLegacyRowWithTicketIdButNoConnectionId(
            long channelId, long guildId, long requesterUserId, long number, String ticketId) {
        entityManager.createNativeQuery(
                        "insert into holonet_support_channel "
                                + "(channel_id, guild_id, requester_user_id, number, ticket_id, status) "
                                + "values (:channelId, :guildId, :requesterUserId, :number, :ticketId, 'OPEN')")
                .setParameter("channelId", channelId)
                .setParameter("guildId", guildId)
                .setParameter("requesterUserId", requesterUserId)
                .setParameter("number", number)
                .setParameter("ticketId", ticketId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
