package de.medo.holonetbridge.commons.discord.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.domain.setup.HolonetSetupConnectionModal;
import de.medo.holonetbridge.commons.domain.setup.HolonetSetupConnectionModalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the upsert/discard behaviour of {@link HolonetSetupSessionStore} against a real (in-memory,
 * test-scoped) database: repeated submissions for the same guild/user must reuse the same row instead of
 * accumulating one per attempt, different guilds/users must stay independent, and a discard must remove both
 * the session and its modal row.
 *
 * <p>{@code @DataJpaTest} does not run Liquibase, so {@code ddl-auto} is overridden back to
 * {@code create-drop} here instead of inheriting the application-wide {@code validate}; the migration itself
 * is covered separately by the full-context migration smoke test.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetSetupSessionStoreTest {

    private static final long GUILD_ID = 100L;
    private static final long USER_ID = 200L;
    private static final long CHANNEL_ID = 300L;
    private static final String BASE_URL = "https://gitea.example.invalid";
    private static final String OWNER = "acme";
    private static final String REPOSITORY = "support";

    @Autowired
    private HolonetBotInteractionSessionRepository sessionRepository;

    @Autowired
    private HolonetSetupConnectionModalRepository connectionModalRepository;

    private HolonetSetupSessionStore store;

    @BeforeEach
    void setUp() {
        store = new HolonetSetupSessionStore(
                sessionRepository, connectionModalRepository, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void firstUpsertCreatesASessionAndModalRowFindableAsUnfinished() {
        HolonetSetupConnectionModal saved = store.upsert(GUILD_ID, USER_ID, CHANNEL_ID, BASE_URL, OWNER, REPOSITORY);

        assertThat(sessionRepository.findById(saved.sessionId())).isPresent();
        assertThat(store.findUnfinished(GUILD_ID, USER_ID)).hasValueSatisfying(modal -> {
            assertThat(modal.channelId()).isEqualTo(CHANNEL_ID);
            assertThat(modal.baseUrl()).isEqualTo(BASE_URL);
            assertThat(modal.owner()).isEqualTo(OWNER);
            assertThat(modal.repository()).isEqualTo(REPOSITORY);
        });
    }

    @Test
    void aRepeatedUpsertForTheSameGuildAndUserUpdatesTheExistingRowInPlace() {
        HolonetSetupConnectionModal first = store.upsert(GUILD_ID, USER_ID, CHANNEL_ID, BASE_URL, OWNER, REPOSITORY);

        HolonetSetupConnectionModal second =
                store.upsert(GUILD_ID, USER_ID, CHANNEL_ID, BASE_URL, OWNER, "a-different-repository");

        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(sessionRepository.findAll()).hasSize(1);
        assertThat(connectionModalRepository.findAll()).hasSize(1);
        assertThat(store.findUnfinished(GUILD_ID, USER_ID))
                .hasValueSatisfying(modal -> assertThat(modal.repository()).isEqualTo("a-different-repository"));
    }

    @Test
    void differentGuildsOrUsersGetIndependentRows() {
        long otherUserId = 999L;
        store.upsert(GUILD_ID, USER_ID, CHANNEL_ID, BASE_URL, OWNER, REPOSITORY);
        store.upsert(GUILD_ID, otherUserId, CHANNEL_ID, BASE_URL, OWNER, "other-repository");

        assertThat(store.findUnfinished(GUILD_ID, USER_ID))
                .hasValueSatisfying(modal -> assertThat(modal.repository()).isEqualTo(REPOSITORY));
        assertThat(store.findUnfinished(GUILD_ID, otherUserId))
                .hasValueSatisfying(modal -> assertThat(modal.repository()).isEqualTo("other-repository"));
    }

    @Test
    void findUnfinishedIsEmptyWhenNoAttemptWasEverSubmitted() {
        assertThat(store.findUnfinished(GUILD_ID, USER_ID)).isEmpty();
    }

    @Test
    void discardRemovesBothTheSessionAndTheModalRow() {
        HolonetSetupConnectionModal saved = store.upsert(GUILD_ID, USER_ID, CHANNEL_ID, BASE_URL, OWNER, REPOSITORY);

        store.discard(GUILD_ID, USER_ID);

        assertThat(sessionRepository.findById(saved.sessionId())).isEmpty();
        assertThat(connectionModalRepository.findById(saved.sessionId())).isEmpty();
        assertThat(store.findUnfinished(GUILD_ID, USER_ID)).isEmpty();
    }

    @Test
    void discardingAnAlreadyDiscardedOrNonExistentAttemptDoesNothing() {
        store.discard(GUILD_ID, USER_ID);

        assertThat(sessionRepository.findAll()).isEmpty();
        assertThat(connectionModalRepository.findAll()).isEmpty();
    }

    @Test
    void anUpsertAfterADiscardStartsACompletelyNewAttempt() {
        HolonetSetupConnectionModal first = store.upsert(GUILD_ID, USER_ID, CHANNEL_ID, BASE_URL, OWNER, REPOSITORY);
        store.discard(GUILD_ID, USER_ID);

        HolonetSetupConnectionModal second =
                store.upsert(GUILD_ID, USER_ID, CHANNEL_ID, BASE_URL, OWNER, REPOSITORY);

        assertThat(second.sessionId()).isNotEqualTo(first.sessionId());
    }
}
