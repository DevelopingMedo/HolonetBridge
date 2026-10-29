package de.medo.holonetbridge.commons.discord.modal.interaction;

import java.util.Locale;

import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketSubmitter;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketBug;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.markdown.MarkdownBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Handles the submitted bug-report modal ({@link HolonetBugReportModalButton#MODAL_ID}): reads the title and the
 * four report fields into a {@link HolonetTicketBug} and submits it as a bug ticket.
 */
@Component
public class HolonetBugReportSubmitModalAction {

    private final HolonetTicketSubmitter ticketSubmitter;
    private final MessageSource messageSource;

    public HolonetBugReportSubmitModalAction(HolonetTicketSubmitter ticketSubmitter, MessageSource messageSource) {
        this.ticketSubmitter = ticketSubmitter;
        this.messageSource = messageSource;
    }

    public void handle(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        Locale locale = event.getGuildLocale().toLocale();
        String steps = event.getValue(HolonetBugReportModalButton.INPUT_STEPS).getAsString();
        String expected = event.getValue(HolonetBugReportModalButton.INPUT_EXPECTED).getAsString();
        String actual = event.getValue(HolonetBugReportModalButton.INPUT_ACTUAL).getAsString();
        String recurrence = event.getValue(HolonetBugReportModalButton.INPUT_RECURRENCE).getAsStringList().get(0);
        // Already-open modals may still submit the previous German display labels.
        String localizedRecurrence = switch (recurrence) {
            case "always", "Jedes Mal" -> messageSource.getMessage("bug.recurrence-always", null, locale);
            case "sometimes", "Manchmal" -> messageSource.getMessage("bug.recurrence-sometimes", null, locale);
            case "once", "Bisher nur einmal" -> messageSource.getMessage("bug.recurrence-once", null, locale);
            case "not-retried", "Noch nicht erneut ausprobiert" -> messageSource.getMessage("bug.recurrence-not-retried", null, locale);
            default -> recurrence;
        };
        String description = MarkdownBuilder.builder()
                .heading2(messageSource.getMessage("bug.steps", null, locale)).text(steps)
                .heading2(messageSource.getMessage("bug.expected", null, locale)).text(expected)
                .heading2(messageSource.getMessage("bug.actual", null, locale)).text(actual)
                .heading2(messageSource.getMessage("bug.recurrence", null, locale)).text(localizedRecurrence)
                .build();
        HolonetTicketBug ticket = HolonetTicketBug.builder()
                .title(event.getValue(HolonetBugReportModalButton.INPUT_TITLE).getAsString())
                .description(description)
                .steps(steps)
                .expected(expected)
                .actual(actual)
                .recurrence(recurrence)
                .build();
        ticketSubmitter.submit(event, userId, HolonetTicketKind.BUG, ticket);
    }
}
