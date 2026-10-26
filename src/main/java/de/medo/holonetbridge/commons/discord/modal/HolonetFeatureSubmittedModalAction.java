package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the submitted idea modal ({@link HolonetFeatureModalButton#MODAL_ID}): reads the title and description
 * into a {@link HolonetTicket} and submits it as a feature ticket.
 */
@Component
public class HolonetFeatureSubmittedModalAction {

    private final HolonetTicketSubmitter ticketSubmitter;

    public HolonetFeatureSubmittedModalAction(HolonetTicketSubmitter ticketSubmitter) {
        this.ticketSubmitter = ticketSubmitter;
    }

    public void handle(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        HolonetTicket ticket = HolonetTicket.builder()
                .title(event.getValue(HolonetFeatureModalButton.INPUT_TITLE).getAsString())
                .description(event.getValue(HolonetFeatureModalButton.INPUT_DESCRIPTION).getAsString())
                .build();
        ticketSubmitter.submit(event, userId, ticket);
    }
}
