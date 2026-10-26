package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.extapi.HolonetTicket;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the submitted bug-report modal ({@link HolonetBugReportModalButton#MODAL_ID}): reads the title and the
 * four report fields into a {@link HolonetTicket} and submits it as a bug ticket.
 */
@Component
public class HolonetBugReportSubmitModalAction {

    private final HolonetTicketSubmitter ticketSubmitter;

    public HolonetBugReportSubmitModalAction(HolonetTicketSubmitter ticketSubmitter) {
        this.ticketSubmitter = ticketSubmitter;
    }

    public void handle(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        HolonetTicket ticket = HolonetTicket.bug(
                event.getValue(HolonetBugReportModalButton.INPUT_TITLE).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_STEPS).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_EXPECTED).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_ACTUAL).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_RECURRENCE).getAsStringList().get(0));
        ticketSubmitter.submit(event, userId, ticket);
    }
}
