package de.medo.holonetbridge.commons.domain.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Verifies that a saved {@code /setup} attempt (its {@link HolonetBotInteractionSession} together with the
 * {@link HolonetSetupConnectionModal} row holding the non-secret field values) survives an application
 * restart: {@link TestEntityManager#flush()}/{@code clear()} forces Hibernate to discard its in-memory
 * first-level cache and re-read the rows from the database on the next access, which is the
 * {@code @DataJpaTest}-scoped equivalent of a process restart for an entity whose only persistence is the
 * database row itself.
 *
 * <p>{@code @DataJpaTest} does not run Liquibase, so {@code ddl-auto} is overridden back to
 * {@code create-drop} here instead of inheriting the application-wide {@code validate}; the migration itself
 * is covered separately by the full-context migration smoke test.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetSetupConnectionModalRepositoryTest {

    private static final long GUILD_ID = 100L;
    private static final long USER_ID = 200L;
    private static final long CHANNEL_ID = 300L;

    @Autowired
    private HolonetBotInteractionSessionRepository sessionRepository;

    @Autowired
    private HolonetSetupConnectionModalRepository modalRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void aSavedAttemptSurvivesTheEquivalentOfARestart() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        sessionRepository.save(new HolonetBotInteractionSession(sessionId, GUILD_ID, USER_ID, now, now));
        HolonetSetupConnectionModal modal = new HolonetSetupConnectionModal(sessionId);
        modal.update(CHANNEL_ID, "https://gitea.example.com", "medo", "holonetBridge");
        modalRepository.save(modal);

        entityManager.flush();
        entityManager.clear();

        var reloadedSession = sessionRepository.findById(sessionId);
        assertThat(reloadedSession).hasValueSatisfying(session -> {
            assertThat(session.discordId()).isEqualTo(GUILD_ID);
            assertThat(session.userId()).isEqualTo(USER_ID);
        });

        var reloadedModal = modalRepository.findById(sessionId);
        assertThat(reloadedModal).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.channelId()).isEqualTo(CHANNEL_ID);
            assertThat(reloaded.baseUrl()).isEqualTo("https://gitea.example.com");
            assertThat(reloaded.owner()).isEqualTo("medo");
            assertThat(reloaded.repository()).isEqualTo("holonetBridge");
        });
    }

    @Test
    @DirtiesContext
    void deletingTheModalAndSessionLeavesNoTraceBehind() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        sessionRepository.save(new HolonetBotInteractionSession(sessionId, GUILD_ID, USER_ID, now, now));
        modalRepository.save(new HolonetSetupConnectionModal(sessionId));

        modalRepository.deleteById(sessionId);
        sessionRepository.deleteById(sessionId);

        assertThat(sessionRepository.findById(sessionId)).isEmpty();
        assertThat(modalRepository.findById(sessionId)).isEmpty();
    }
}
