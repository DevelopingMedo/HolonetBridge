package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketKind;
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

    /** Custom id of the "Idee" choice button. */
    public static final String BUTTON_ID = "ticket:kind:feature";

    /** Custom id of the modal this button opens. */
    public static final String MODAL_ID = "ticket:modal:feature";

    public static final String INPUT_TITLE = "title";
    public static final String INPUT_DESCRIPTION = "description";

    private static final String TITLE_HINT = "Beschreibe deine Idee in einem kurzen Satz.";
    private static final String DESCRIPTION_HINT =
            "Was soll am Ende möglich sein? Bitte so konkret wie möglich beschreiben.";

    private final HolonetTicketCreationSessionStore sessionStore;

    public HolonetFeatureModalButton(HolonetTicketCreationSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public void handle(ButtonInteractionEvent event) {
        sessionStore
                .get(event.getUser().getIdLong())
                .ifPresent(session -> session.kind(HolonetTicketKind.FEATURE));
        event.replyModal(featureModal()).queue();
    }

    private static Modal featureModal() {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        TextInput description = TextInput.create(INPUT_DESCRIPTION, TextInputStyle.PARAGRAPH)
                .build();
        return Modal.create(MODAL_ID, "Neue Idee")
                .addComponents(
                        Label.of("Titel", TITLE_HINT, title),
                        Label.of("Beschreibung", DESCRIPTION_HINT, description))
                .build();
    }
}
