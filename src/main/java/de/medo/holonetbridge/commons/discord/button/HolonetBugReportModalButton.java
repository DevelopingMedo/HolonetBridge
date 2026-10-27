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
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

/**
 * Handles the "Bug" choice in the support channel (custom id {@code ticket:kind:bug}): records
 * {@link HolonetTicketKind#BUG} on the user's session and opens the bug report modal
 * (id {@code ticket:modal:bug-report}) with a title and four report fields.
 *
 * <p>If a saved, not-yet-submitted bug report draft already exists for this channel/user (see
 * {@link HolonetTicketSessionService}) - left over from a failed submission or a process restart - the modal
 * is not opened directly. Instead, {@link #RESUME_BUTTON_ID}/{@link #RESTART_BUTTON_ID} let the user choose
 * between continuing it (a prefilled modal) or discarding it and starting over (an empty modal). A draft of
 * the other kind (left over from a feature report) is not offered here and is simply left in place; it is
 * only ever replaced once a submission actually happens (see {@code HolonetTicketSubmitter}).
 */
@Component
public class HolonetBugReportModalButton {

    /** Custom id of the "Bug" choice button. */
    public static final String BUTTON_ID = "ticket:kind:bug";

    /** Custom id of the "Fortsetzen" button offered when an unfinished bug report draft exists. */
    public static final String RESUME_BUTTON_ID = "ticket:bug:resume";

    /** Custom id of the "Neu starten" button offered when an unfinished bug report draft exists. */
    public static final String RESTART_BUTTON_ID = "ticket:bug:restart";

    /** Custom id of the modal this button opens. */
    public static final String MODAL_ID = "ticket:modal:bug-report";

    public static final String INPUT_TITLE = "title";
    public static final String INPUT_STEPS = "steps";
    public static final String INPUT_EXPECTED = "expected";
    public static final String INPUT_ACTUAL = "actual";
    public static final String INPUT_RECURRENCE = "recurrence";

    private static final String TITLE_HINT = "Beschreibe das Problem in einem kurzen Satz.";
    private static final String STEPS_HINT =
            "Schritte der Reihe nach, damit jemand sie nachmachen kann. Inkl. Voraussetzungen (Einstellungen).";
    private static final String EXPECTED_HINT = "Was sollte nach diesen Schritten normalerweise passieren?";
    private static final String ACTUAL_HINT =
            "Beschreibe das Ergebnis. Füge wenn möglich die exakte und vollständige Fehlermeldung hier ein.";
    private static final String UNFINISHED_DRAFT_MESSAGE =
            "Du hast einen unvollendeten Vorgang. Möchtest du fortsetzen oder neu starten?";
    private static final String EXPIRED_DRAFT_MESSAGE =
            "Der gespeicherte Bug-Report ist nicht mehr verfügbar. Bitte starte über die Bug-Auswahl neu.";

    private final HolonetTicketCreationSessionStore sessionStore;
    private final HolonetTicketSessionService ticketSessionService;

    public HolonetBugReportModalButton(
            HolonetTicketCreationSessionStore sessionStore, HolonetTicketSessionService ticketSessionService) {
        this.sessionStore = sessionStore;
        this.ticketSessionService = ticketSessionService;
    }

    public void handle(ButtonInteractionEvent event) {
        sessionStore
                .get(event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getUser().getIdLong())
                .ifPresent(session -> session.kind(HolonetTicketKind.BUG));

        Optional<HolonetTicketBug> draft = findBugDraft(event);
        if (draft.isPresent()) {
            offerResumeOrRestart(event);
            return;
        }
        event.replyModal(bugReportModal(null)).queue();
    }

    /** Handles {@value #RESUME_BUTTON_ID}: reopens the modal prefilled with the saved draft's values. */
    public void handleResume(ButtonInteractionEvent event) {
        Optional<HolonetTicketBug> draft = findBugDraft(event);
        if (draft.isEmpty()) {
            event.reply(EXPIRED_DRAFT_MESSAGE).setEphemeral(true).queue();
            return;
        }
        event.replyModal(bugReportModal(draft.get())).queue();
    }

    /** Handles {@value #RESTART_BUTTON_ID}: discards the saved draft and opens an empty modal. */
    public void handleRestart(ButtonInteractionEvent event) {
        ticketSessionService.discardDraft(event.getChannel().getIdLong(), event.getUser().getIdLong());
        event.replyModal(bugReportModal(null)).queue();
    }

    private Optional<HolonetTicketBug> findBugDraft(ButtonInteractionEvent event) {
        long channelId = event.getChannel().getIdLong();
        long userId = event.getUser().getIdLong();
        Optional<HolonetTicket> draft = ticketSessionService.findUnfinishedDraft(channelId, userId);
        return draft.filter(HolonetTicketBug.class::isInstance).map(HolonetTicketBug.class::cast);
    }

    private void offerResumeOrRestart(ButtonInteractionEvent event) {
        event.reply(UNFINISHED_DRAFT_MESSAGE)
                .setEphemeral(true)
                .addComponents(ActionRow.of(
                        Button.primary(RESUME_BUTTON_ID, "Fortsetzen"), Button.secondary(RESTART_BUTTON_ID, "Neu starten")))
                .queue();
    }

    private static Modal bugReportModal(HolonetTicketBug prefill) {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .setValue(prefill == null ? null : prefill.getTitle())
                .build();
        TextInput steps = TextInput.create(INPUT_STEPS, TextInputStyle.PARAGRAPH)
                .setValue(prefill == null ? null : prefill.getSteps())
                .build();
        TextInput expected = TextInput.create(INPUT_EXPECTED, TextInputStyle.PARAGRAPH)
                .setValue(prefill == null ? null : prefill.getExpected())
                .build();
        TextInput actual = TextInput.create(INPUT_ACTUAL, TextInputStyle.PARAGRAPH)
                .setValue(prefill == null ? null : prefill.getActual())
                .build();
        StringSelectMenu.Builder recurrenceBuilder = StringSelectMenu.create(INPUT_RECURRENCE)
                .addOption("Jedes Mal", "Jedes Mal")
                .addOption("Manchmal", "Manchmal")
                .addOption("Bisher nur einmal", "Bisher nur einmal")
                .addOption("Noch nicht erneut ausprobiert", "Noch nicht erneut ausprobiert")
                .setRequired(true);
        if (prefill != null) {
            recurrenceBuilder.setDefaultValues(prefill.getRecurrence());
        }
        return Modal.create(MODAL_ID, "Bug melden")
                .addComponents(
                        Label.of("Titel", TITLE_HINT, title),
                        Label.of("Was hast du vor dem Fehler gemacht?", STEPS_HINT, steps),
                        Label.of("Was hättest du erwartet?", EXPECTED_HINT, expected),
                        Label.of("Was ist stattdessen passiert?", ACTUAL_HINT, actual),
                        Label.of("Tritt der Fehler erneut auf?", recurrenceBuilder.build()))
                .build();
    }
}
