package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.i18n.Texts;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

/**
 * Handles the "Idee" choice in the support channel (custom id {@code ticket:kind:feature}): records
 * {@link HolonetTicketKind#FEATURE} on the user's session and opens the idea modal
 * (id {@code ticket:modal:feature}) collecting a title and description.
 *
 * <p>The Discord-visible wording says "Idee" while the backend keeps "Feature" (see
 * {@link HolonetTicketKind}); internal ids and constant names therefore use {@code feature}.
 */
@Component
public class HolonetFeatureModalButton {

    private final Texts texts;

    /** Custom id of the "Idee" choice button. */
    public static final String BUTTON_ID = "ticket:kind:feature";

    /** Custom id of the modal this button opens. */
    public static final String MODAL_ID = "ticket:modal:feature";

    public static final String INPUT_TITLE = "title";
    public static final String INPUT_DESCRIPTION = "description";

    private final HolonetTicketCreationSessionStore sessionStore;

    public HolonetFeatureModalButton(Texts texts, HolonetTicketCreationSessionStore sessionStore) {
        this.texts = texts;
        this.sessionStore = sessionStore;
    }

    public void handle(ButtonInteractionEvent event) {
        sessionStore
                .get(event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getUser().getIdLong())
                .ifPresent(session -> session.kind(HolonetTicketKind.FEATURE));
        event.replyModal(featureModal(event)).queue();
    }

    private Modal featureModal(ButtonInteractionEvent event) {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        TextInput description = TextInput.create(INPUT_DESCRIPTION, TextInputStyle.PARAGRAPH)
                .build();
        return Modal.create(MODAL_ID, texts.user(event, "feature.modal.title"))
                .addComponents(
                        Label.of(texts.user(event, "ticket.title"), texts.user(event, "feature.title-hint"), title),
                        Label.of(texts.user(event, "ticket.description"), texts.user(event, "feature.description-hint"), description))
                .build();
    }
}
