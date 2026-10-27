package de.medo.holonetbridge.commons.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the JPA mapping of {@link HolonetBotInteractionSession} against a real (in-memory, test-scoped)
 * database, including that more than one unfinished session for the same {@code (discordId, userId)} pair
 * can coexist.
 *
 * <p>{@code @DataJpaTest} does not run Liquibase, so {@code ddl-auto} is overridden back to
 * {@code create-drop} here instead of inheriting the application-wide {@code validate}; the migration itself
 * is covered separately by the full-context migration smoke test.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetBotInteractionSessionRepositoryTest {

    private static final long DISCORD_ID = 100L;
    private static final long USER_ID = 200L;

    @Autowired
    private HolonetBotInteractionSessionRepository repository;

    @Test
    void savedSessionCanBeLoadedBackById() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2024-01-01T00:05:00Z");

        repository.save(new HolonetBotInteractionSession(id, DISCORD_ID, USER_ID, createdAt, updatedAt));

        assertThat(repository.findById(id)).hasValueSatisfying(session -> {
            assertThat(session.discordId()).isEqualTo(DISCORD_ID);
            assertThat(session.userId()).isEqualTo(USER_ID);
            assertThat(session.createdAt()).isEqualTo(createdAt);
            assertThat(session.updatedAt()).isEqualTo(updatedAt);
        });
    }

    @Test
    void theSameUserCanHaveMoreThanOneUnfinishedSessionAtOnce() {
        repository.save(new HolonetBotInteractionSession(
                UUID.randomUUID(), DISCORD_ID, USER_ID, Instant.EPOCH, Instant.EPOCH));
        repository.save(new HolonetBotInteractionSession(
                UUID.randomUUID(), DISCORD_ID, USER_ID, Instant.EPOCH, Instant.EPOCH));

        assertThat(repository.findAll()).hasSize(2);
    }
}
