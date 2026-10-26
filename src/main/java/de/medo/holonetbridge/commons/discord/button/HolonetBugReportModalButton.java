package de.medo.holonetbridge.commons.discord.button;

import java.util.Locale;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Handles the "Bug" choice in the support channel (custom id {@code ticket:kind:bug}): records
 * {@link HolonetTicketKind#BUG} on the user's session and opens the bug report modal
 * (id {@code ticket:modal:bug-report}) with a title and four report fields.
 */
@Component
public class HolonetBugReportModalButton {

    private final MessageSource messageSource;

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

    public HolonetBugReportModalButton(MessageSource messageSource, HolonetTicketCreationSessionStore sessionStore) {
        this.messageSource = messageSource;
        this.sessionStore = sessionStore;
    }

    public void handle(ButtonInteractionEvent event) {
        sessionStore
                .get(event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getUser().getIdLong())
                .ifPresent(session -> session.kind(HolonetTicketKind.BUG));
        event.replyModal(bugReportModal(event)).queue();
    }

    private Modal bugReportModal(ButtonInteractionEvent event) {
        Locale userLocale = event.getUserLocale().toLocale();
        TextInput title = TextInput.create(INPUT_TITLE, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .build();
        TextInput steps = TextInput.create(INPUT_STEPS, TextInputStyle.PARAGRAPH).build();
        TextInput expected = TextInput.create(INPUT_EXPECTED, TextInputStyle.PARAGRAPH).build();
        TextInput actual = TextInput.create(INPUT_ACTUAL, TextInputStyle.PARAGRAPH).build();
        StringSelectMenu recurrence = StringSelectMenu.create(INPUT_RECURRENCE)
                .addOption(messageSource.getMessage("bug.recurrence-always", null, userLocale), "always")
                .addOption(messageSource.getMessage("bug.recurrence-sometimes", null, userLocale), "sometimes")
                .addOption(messageSource.getMessage("bug.recurrence-once", null, userLocale), "once")
                .addOption(messageSource.getMessage("bug.recurrence-not-retried", null, userLocale), "not-retried")
                .setRequired(true)
                .build();
        return Modal.create(MODAL_ID, messageSource.getMessage("bug.modal.title", null, userLocale))
                .addComponents(
                        Label.of(messageSource.getMessage("ticket.title", null, userLocale), messageSource.getMessage("bug.title-hint", null, userLocale), title),
                        Label.of(messageSource.getMessage("bug.steps", null, userLocale), messageSource.getMessage("bug.steps-hint", null, userLocale), steps),
                        Label.of(messageSource.getMessage("bug.expected", null, userLocale), messageSource.getMessage("bug.expected-hint", null, userLocale), expected),
                        Label.of(messageSource.getMessage("bug.actual", null, userLocale), messageSource.getMessage("bug.actual-hint", null, userLocale), actual),
                        Label.of(messageSource.getMessage("bug.recurrence", null, userLocale), recurrence))
                .build();
    }
}
