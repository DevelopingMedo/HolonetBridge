package de.medo.holonetbridge.commons.discord.button;

import java.util.Optional;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketSessionService;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketBug;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
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
 *
 * <p>If a saved, not-yet-submitted idea draft already exists for this channel/user (see
 * {@link HolonetTicketSessionService}) - left over from a failed submission or a process restart - the modal
 * is not opened directly. Instead, {@link #RESUME_BUTTON_ID}/{@link #RESTART_BUTTON_ID} let the user choose
 * between continuing it (a prefilled modal) or discarding it and starting over (an empty modal). A draft of
 * the other kind (left over from a bug report) is not offered here and is simply left in place; it is only
 * ever replaced once a submission actually happens (see {@code HolonetTicketSubmitter}).
 */
@Component
public class HolonetFeatureModalButton {

    /** Custom id of the "Idee" choice button. */
    public static final String BUTTON_ID = "ticket:kind:feature";

    /** Custom id of the "Fortsetzen" button offered when an unfinished idea draft exists. */
    public static final String RESUME_BUTTON_ID = "ticket:feature:resume";

    /** Custom id of the "Neu starten" button offered when an unfinished idea draft exists. */
    public static final String RESTART_BUTTON_ID = "ticket:feature:restart";

    /** Custom id of the modal this button opens. */
    public static final String MODAL_ID = "ticket:modal:feature";

    public static final String INPUT_TITLE = "title";
    public static final String INPUT_DESCRIPTION = "description";

    private static final String TITLE_HINT = "Beschreibe deine Idee in einem kurzen Satz.";
    private static final String DESCRIPTION_HINT =
            "Was soll am Ende möglich sein? Bitte so konkret wie möglich beschreiben.";
    private static final String UNFINISHED_DRAFT_MESSAGE =
            "Du hast einen unvollendeten Vorgang. Möchtest du fortsetzen oder neu starten?";
    private static final String EXPIRED_DRAFT_MESSAGE =
            "Die gespeicherte Idee ist nicht mehr verfügbar. Bitte starte über die Idee-Auswahl neu.";

    private final HolonetTicketCreationSessionStore sessionStore;
    private final HolonetTicketSessionService ticketSessionService;

    public HolonetFeatureModalButton(
            HolonetTicketCreationSessionStore sessionStore, HolonetTicketSessionService ticketSessionService) {
        this.sessionStore = sessionStore;
        this.ticketSessionService = ticketSessionService;
    }

    public void handle(ButtonInteractionEvent event) {
        sessionStore
                .get(event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getUser().getIdLong())
                .ifPresent(session -> session.kind(HolonetTicketKind.FEATURE));

        Optional<HolonetTicket> draft = findFeatureDraft(event);
        if (draft.isPresent()) {
            offerResumeOrRestart(event);
            return;
        }
        event.replyModal(featureModal(null)).queue();
    }

    /** Handles {@value #RESUME_BUTTON_ID}: reopens the modal prefilled with the saved draft's values. */
    public void handleResume(ButtonInteractionEvent event) {
        Optional<HolonetTicket> draft = findFeatureDraft(event);
        if (draft.isEmpty()) {
            event.reply(EXPIRED_DRAFT_MESSAGE).setEphemeral(true).queue();
            return;
        }
        event.replyModal(featureModal(draft.get())).queue();
    }

    /** Handles {@value #RESTART_BUTTON_ID}: discards the saved draft and opens an empty modal. */
    public void handleRestart(ButtonInteractionEvent event) {
        ticketSessionService.discardDraft(event.getChannel().getIdLong(), event.getUser().getIdLong());
        event.replyModal(featureModal(null)).queue();
    }

    /**
     * A feature draft is any saved draft that is not a {@link HolonetTicketBug} - the base
     * {@link HolonetTicket} type itself represents the feature/idea kind, see its class Javadoc.
     */
    private Optional<HolonetTicket> findFeatureDraft(ButtonInteractionEvent event) {
        long channelId = event.getChannel().getIdLong();
        long userId = event.getUser().getIdLong();
        return ticketSessionService.findUnfinishedDraft(channelId, userId).filter(draft -> !(draft instanceof HolonetTicketBug));
    }

    private void offerResumeOrRestart(ButtonInteractionEvent event) {
        event.reply(UNFINISHED_DRAFT_MESSAGE)
                .setEphemeral(true)
                .addComponents(ActionRow.of(
                        Button.primary(RESUME_BUTTON_ID, "Fortsetzen"), Button.secondary(RESTART_BUTTON_ID, "Neu starten")))
                .queue();
    }

    private static Modal featureModal(HolonetTicket prefill) {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .setValue(prefill == null ? null : prefill.getTitle())
                .build();
        TextInput description = TextInput.create(INPUT_DESCRIPTION, TextInputStyle.PARAGRAPH)
                .setValue(prefill == null ? null : prefill.getDescription())
                .build();
        return Modal.create(MODAL_ID, "Neue Idee")
                .addComponents(
                        Label.of("Titel", TITLE_HINT, title),
                        Label.of("Beschreibung", DESCRIPTION_HINT, description))
                .build();
    }
}
