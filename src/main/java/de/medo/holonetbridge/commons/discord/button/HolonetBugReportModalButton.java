package de.medo.holonetbridge.commons.discord.button;

import de.medo.holonetbridge.commons.discord.i18n.Texts;
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

    private final Texts texts;

    /** Custom id of the "Bug" choice button. */
    public static final String BUTTON_ID = "ticket:kind:bug";

    /** Custom id of the modal this button opens. */
    public static final String MODAL_ID = "ticket:modal:bug-report";

    public static final String INPUT_TITLE = "title";
    public static final String INPUT_STEPS = "steps";
    public static final String INPUT_EXPECTED = "expected";
    public static final String INPUT_ACTUAL = "actual";
    public static final String INPUT_RECURRENCE = "recurrence";

    private final HolonetTicketCreationSessionStore sessionStore;

    public HolonetBugReportModalButton(Texts texts, HolonetTicketCreationSessionStore sessionStore) {
        this.texts = texts;
        this.sessionStore = sessionStore;
    }

    public void handle(ButtonInteractionEvent event) {
        sessionStore
                .get(event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getUser().getIdLong())
                .ifPresent(session -> session.kind(HolonetTicketKind.BUG));
        event.replyModal(bugReportModal(event)).queue();
    }

    private Modal bugReportModal(ButtonInteractionEvent event) {
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        TextInput steps = TextInput.create(INPUT_STEPS, TextInputStyle.PARAGRAPH).build();
        TextInput expected = TextInput.create(INPUT_EXPECTED, TextInputStyle.PARAGRAPH).build();
        TextInput actual = TextInput.create(INPUT_ACTUAL, TextInputStyle.PARAGRAPH).build();
        StringSelectMenu recurrence = StringSelectMenu.create(INPUT_RECURRENCE)
                .addOption(texts.user(event, "bug.recurrence-always"), "always")
                .addOption(texts.user(event, "bug.recurrence-sometimes"), "sometimes")
                .addOption(texts.user(event, "bug.recurrence-once"), "once")
                .addOption(texts.user(event, "bug.recurrence-not-retried"), "not-retried")
                .setRequired(true)
                .build();
        return Modal.create(MODAL_ID, texts.user(event, "bug.modal.title"))
                .addComponents(
                        Label.of(texts.user(event, "ticket.title"), texts.user(event, "bug.title-hint"), title),
                        Label.of(texts.user(event, "bug.steps"), texts.user(event, "bug.steps-hint"), steps),
                        Label.of(texts.user(event, "bug.expected"), texts.user(event, "bug.expected-hint"), expected),
                        Label.of(texts.user(event, "bug.actual"), texts.user(event, "bug.actual-hint"), actual),
                        Label.of(texts.user(event, "bug.recurrence"), recurrence))
                .build();
    }
}
