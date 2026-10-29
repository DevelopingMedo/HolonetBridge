package de.medo.holonetbridge.commons.discord.button;

import java.util.Locale;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;


/**
 * Pressing this button creates a Modal for creating Features to the selected Ticketing Provider.
 */
@Component
public class HolonetFeatureModalButton {

    private final MessageSource messageSource;

    /** Custom id of the "Idee" choice button. */
    public static final String BUTTON_ID = "ticket:kind:feature";

    /** Custom id of the modal this button opens. */
    public static final String MODAL_ID = "ticket:modal:feature";

    public static final String INPUT_TITLE = "title";
    public static final String INPUT_DESCRIPTION = "description";

    private final HolonetTicketCreationSessionStore sessionStore;

    public HolonetFeatureModalButton(MessageSource messageSource, HolonetTicketCreationSessionStore sessionStore) {
        this.messageSource = messageSource;
        this.sessionStore = sessionStore;
    }

    public void handle(ButtonInteractionEvent event) {
        sessionStore
                .get(event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getUser().getIdLong())
                .ifPresent(session -> session.kind(HolonetTicketKind.FEATURE));
        event.replyModal(featureModal(event)).queue();
    }

    private Modal featureModal(ButtonInteractionEvent event) {
        Locale userLocale = event.getUserLocale().toLocale();
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        TextInput description = TextInput.create(INPUT_DESCRIPTION, TextInputStyle.PARAGRAPH)
                .build();
        return Modal.create(MODAL_ID, messageSource.getMessage("feature.modal.title", null, userLocale))
                .addComponents(
                        Label.of(messageSource.getMessage("ticket.title", null, userLocale), messageSource.getMessage("feature.title-hint", null, userLocale), title),
                        Label.of(messageSource.getMessage("ticket.description", null, userLocale), messageSource.getMessage("feature.description-hint", null, userLocale), description))
                .build();
    }
}
