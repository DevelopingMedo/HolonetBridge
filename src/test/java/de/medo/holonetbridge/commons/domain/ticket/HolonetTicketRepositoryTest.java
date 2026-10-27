package de.medo.holonetbridge.commons.domain.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies the JPA mapping of {@link HolonetTicket}/{@link HolonetTicketBug} against a real (in-memory,
 * test-scoped) database: single-table inheritance via the {@code kind} discriminator, the {@code sessionId}
 * link, and that a ticket draft saved before a (simulated) restart can still be loaded back afterwards -
 * this is the persistence guarantee the resume/restart UX in {@code HolonetBugReportModalButton}/
 * {@code HolonetFeatureModalButton} relies on instead of the in-memory
 * {@code HolonetTicketCreationSessionStore}.
 *
 * <p>{@code @DataJpaTest} does not run Liquibase, so {@code ddl-auto} is overridden back to
 * {@code create-drop} here instead of inheriting the application-wide {@code validate}; the migration itself
 * is covered separately by the full-context migration smoke test.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetTicketRepositoryTest {

    @Autowired
    private HolonetTicketRepository repository;

    @Test
    void aFeatureDraftSurvivesBeingReloadedBySessionIdAfterASimulatedRestart() {
        UUID sessionId = UUID.randomUUID();
        HolonetTicket ticket = HolonetTicket.builder().title("Dark mode").description("Add a dark theme.").build();
        ticket.assignToSession(sessionId);
        repository.saveAndFlush(ticket);
        repository.flush();

        // A brand-new repository lookup, not the same in-memory object: stands in for the "new process" case.
        assertThat(repository.findBySessionId(sessionId)).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.getTitle()).isEqualTo("Dark mode");
            assertThat(reloaded.getDescription()).isEqualTo("Add a dark theme.");
            assertThat(reloaded).isNotInstanceOf(HolonetTicketBug.class);
        });
    }

    @Test
    void aBugDraftSurvivesBeingReloadedBySessionIdAfterASimulatedRestart() {
        UUID sessionId = UUID.randomUUID();
        HolonetTicketBug ticket = HolonetTicketBug.builder()
                .title("Login fails")
                .steps("did X")
                .expected("should work")
                .actual("crashed")
                .recurrence("Jedes Mal")
                .build();
        ticket.assignToSession(sessionId);
        repository.saveAndFlush(ticket);

        assertThat(repository.findBySessionId(sessionId)).hasValueSatisfying(reloaded -> {
            assertThat(reloaded).isInstanceOf(HolonetTicketBug.class);
            HolonetTicketBug reloadedBug = (HolonetTicketBug) reloaded;
            assertThat(reloadedBug.getTitle()).isEqualTo("Login fails");
            assertThat(reloadedBug.getSteps()).isEqualTo("did X");
            assertThat(reloadedBug.getExpected()).isEqualTo("should work");
            assertThat(reloadedBug.getActual()).isEqualTo("crashed");
            assertThat(reloadedBug.getRecurrence()).isEqualTo("Jedes Mal");
        });
    }

    @Test
    void deleteBySessionIdRemovesTheDraftForThatSessionOnly() {
        UUID sessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        HolonetTicket ticket = HolonetTicket.builder().title("Dark mode").description("d").build();
        ticket.assignToSession(sessionId);
        HolonetTicket otherTicket = HolonetTicket.builder().title("Other").description("d").build();
        otherTicket.assignToSession(otherSessionId);
        repository.saveAll(java.util.List.of(ticket, otherTicket));
        repository.flush();

        repository.deleteBySessionId(sessionId);
        repository.flush();

        assertThat(repository.findBySessionId(sessionId)).isEmpty();
        assertThat(repository.findBySessionId(otherSessionId)).isPresent();
    }

}
