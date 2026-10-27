package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketBug;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Verifies {@link HolonetTicketSessionService} against a real (in-memory, test-scoped) database: the
 * session+draft upsert behaviour that backs the resume/restart UX, including that a repeated
 * {@link HolonetTicketSessionService#saveDraft} never leaves more than one draft behind for the same
 * channel/user (the unique {@code session_id} constraint that a naive insert-before-delete would violate).
 *
 * <p>{@code @DataJpaTest} does not run Liquibase, so {@code ddl-auto} is overridden back to
 * {@code create-drop} here instead of inheriting the application-wide {@code validate}.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HolonetTicketSessionServiceTest {

    private static final long CHANNEL_ID = 111L;
    private static final long OTHER_CHANNEL_ID = 222L;
    private static final long USER_ID = 7L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private HolonetBotInteractionSessionRepository sessionRepository;

    @Autowired
    private HolonetTicketRepository ticketRepository;

    private HolonetTicketSessionService service;

    @Test
    void noDraftExistsBeforeAnythingWasEverSaved() {
        service = new HolonetTicketSessionService(sessionRepository, ticketRepository, FIXED_CLOCK);

        assertThat(service.findUnfinishedDraft(CHANNEL_ID, USER_ID)).isEmpty();
    }

    @Test
    void aSavedDraftCanBeFoundAgainForTheSameChannelAndUser() {
        service = new HolonetTicketSessionService(sessionRepository, ticketRepository, FIXED_CLOCK);

        service.saveDraft(CHANNEL_ID, USER_ID, HolonetTicket.builder().title("Dark mode").description("d").build());

        assertThat(service.findUnfinishedDraft(CHANNEL_ID, USER_ID))
                .hasValueSatisfying(draft -> assertThat(draft.getTitle()).isEqualTo("Dark mode"));
    }

    @Test
    void aDraftIsScopedToItsOwnChannelAndNotVisibleFromAnotherChannel() {
        service = new HolonetTicketSessionService(sessionRepository, ticketRepository, FIXED_CLOCK);

        service.saveDraft(CHANNEL_ID, USER_ID, HolonetTicket.builder().title("Dark mode").description("d").build());

        assertThat(service.findUnfinishedDraft(OTHER_CHANNEL_ID, USER_ID)).isEmpty();
    }

    @Test
    void savingASecondDraftForTheSameChannelAndUserReplacesTheFirstInsteadOfAddingASecondOne() {
        service = new HolonetTicketSessionService(sessionRepository, ticketRepository, FIXED_CLOCK);
        service.saveDraft(CHANNEL_ID, USER_ID, HolonetTicket.builder().title("First attempt").description("d").build());

        service.saveDraft(CHANNEL_ID, USER_ID, HolonetTicketBug.builder()
                .title("Second attempt")
                .steps("s")
                .expected("e")
                .actual("a")
                .recurrence("Jedes Mal")
                .build());

        assertThat(ticketRepository.findAll()).hasSize(1);
        assertThat(service.findUnfinishedDraft(CHANNEL_ID, USER_ID)).hasValueSatisfying(
                draft -> assertThat(draft.getTitle()).isEqualTo("Second attempt"));
    }

    @Test
    void discardDraftRemovesBothTheTicketAndItsSession() {
        service = new HolonetTicketSessionService(sessionRepository, ticketRepository, FIXED_CLOCK);
        service.saveDraft(CHANNEL_ID, USER_ID, HolonetTicket.builder().title("Dark mode").description("d").build());

        service.discardDraft(CHANNEL_ID, USER_ID);

        assertThat(service.findUnfinishedDraft(CHANNEL_ID, USER_ID)).isEmpty();
        assertThat(ticketRepository.findAll()).isEmpty();
        assertThat(sessionRepository.findFirstByDiscordIdAndUserIdOrderByUpdatedAtDesc(CHANNEL_ID, USER_ID))
                .isEmpty();
    }

    @Test
    void discardingAnAlreadyDiscardedOrNeverSavedDraftIsANoOp() {
        service = new HolonetTicketSessionService(sessionRepository, ticketRepository, FIXED_CLOCK);

        service.discardDraft(CHANNEL_ID, USER_ID);

        assertThat(service.findUnfinishedDraft(CHANNEL_ID, USER_ID)).isEmpty();
    }
}
