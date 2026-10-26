package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketBug;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the submitted bug-report modal ({@link HolonetBugReportModalButton#MODAL_ID}): reads the title and the
 * four report fields into a {@link HolonetTicketBug} and submits it as a bug ticket.
 */
@Component
public class HolonetBugReportSubmitModalAction {

    private final HolonetTicketSubmitter ticketSubmitter;

    public HolonetBugReportSubmitModalAction(HolonetTicketSubmitter ticketSubmitter) {
        this.ticketSubmitter = ticketSubmitter;
    }

    public void handle(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        HolonetTicketBug ticket = HolonetTicketBug.builder()
                .title(event.getValue(HolonetBugReportModalButton.INPUT_TITLE).getAsString())
                .steps(event.getValue(HolonetBugReportModalButton.INPUT_STEPS).getAsString())
                .expected(event.getValue(HolonetBugReportModalButton.INPUT_EXPECTED).getAsString())
                .actual(event.getValue(HolonetBugReportModalButton.INPUT_ACTUAL).getAsString())
                .recurrence(event.getValue(HolonetBugReportModalButton.INPUT_RECURRENCE).getAsStringList().get(0))
                .build();
        ticketSubmitter.submit(event, userId, HolonetTicketKind.BUG, ticket);
    }
}
