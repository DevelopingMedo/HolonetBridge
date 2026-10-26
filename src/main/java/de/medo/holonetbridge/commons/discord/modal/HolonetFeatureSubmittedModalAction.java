package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the submitted idea modal ({@link HolonetFeatureModalButton#MODAL_ID}): reads the title and description
 * and submits them as a feature ticket.
 */
@Component
public class HolonetFeatureSubmittedModalAction {

    private final HolonetTicketSubmitter ticketSubmitter;

    public HolonetFeatureSubmittedModalAction(HolonetTicketSubmitter ticketSubmitter) {
        this.ticketSubmitter = ticketSubmitter;
    }

    public void handle(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        String title = event.getValue(HolonetFeatureModalButton.INPUT_TITLE).getAsString();
        String description = event.getValue(HolonetFeatureModalButton.INPUT_DESCRIPTION).getAsString();
        ticketSubmitter.submit(event, userId, title, description);
    }
}
