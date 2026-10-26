package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
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
 */
@Component
public class HolonetBugReportModalButton {

    /** Custom id of the "Bug" choice button. */
    public static final String BUTTON_ID = "ticket:kind:bug";

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

    private final HolonetTicketCreationSessionStore sessionStore;

    public HolonetBugReportModalButton(HolonetTicketCreationSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public void handle(ButtonInteractionEvent event) {
        sessionStore
                .get(event.getUser().getIdLong())
                .ifPresent(session -> session.kind(HolonetTicketKind.BUG));
        event.replyModal(bugReportModal()).queue();
    }

    private static Modal bugReportModal() {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        TextInput steps = TextInput.create(INPUT_STEPS, TextInputStyle.PARAGRAPH).build();
        TextInput expected = TextInput.create(INPUT_EXPECTED, TextInputStyle.PARAGRAPH).build();
        TextInput actual = TextInput.create(INPUT_ACTUAL, TextInputStyle.PARAGRAPH).build();
        StringSelectMenu recurrence = StringSelectMenu.create(INPUT_RECURRENCE)
                .addOption("Jedes Mal", "Jedes Mal")
                .addOption("Manchmal", "Manchmal")
                .addOption("Bisher nur einmal", "Bisher nur einmal")
                .addOption("Noch nicht erneut ausprobiert", "Noch nicht erneut ausprobiert")
                .setRequired(true)
                .build();
        return Modal.create(MODAL_ID, "Bug melden")
                .addComponents(
                        Label.of("Titel", TITLE_HINT, title),
                        Label.of("Was hast du vor dem Fehler gemacht?", STEPS_HINT, steps),
                        Label.of("Was hättest du erwartet?", EXPECTED_HINT, expected),
                        Label.of("Was ist stattdessen passiert?", ACTUAL_HINT, actual),
                        Label.of("Tritt der Fehler erneut auf?", recurrence))
                .build();
    }
}
