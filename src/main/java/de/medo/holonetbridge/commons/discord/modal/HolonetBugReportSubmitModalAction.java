package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the submitted bug-report modal ({@link HolonetBugReportModalButton#MODAL_ID}): reads the title and the
 * four report fields, assembles them into a single description, and submits them as a bug ticket.
 */
@Component
public class HolonetBugReportSubmitModalAction {

    private final HolonetTicketSubmitter ticketSubmitter;

    public HolonetBugReportSubmitModalAction(HolonetTicketSubmitter ticketSubmitter) {
        this.ticketSubmitter = ticketSubmitter;
    }

    public void handle(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        String title = event.getValue(HolonetBugReportModalButton.INPUT_TITLE).getAsString();
        String description = assembleBugDescription(
                event.getValue(HolonetBugReportModalButton.INPUT_STEPS).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_EXPECTED).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_ACTUAL).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_RECURRENCE).getAsStringList().get(0));
        ticketSubmitter.submit(event, userId, title, description);
    }

    private static String assembleBugDescription(String steps, String expected, String actual, String recurrence) {
        return new StringBuilder()
                .append("**Was hast du vor dem Fehler gemacht?**").append('\n')
                .append(steps).append('\n')
                .append('\n')
                .append("**Was hättest du erwartet?**").append('\n')
                .append(expected).append('\n')
                .append('\n')
                .append("**Was ist stattdessen passiert?**").append('\n')
                .append(actual).append('\n')
                .append('\n')
                .append("**Tritt der Fehler erneut auf?**").append('\n')
                .append(recurrence).append('\n')
                .toString();
    }
}
