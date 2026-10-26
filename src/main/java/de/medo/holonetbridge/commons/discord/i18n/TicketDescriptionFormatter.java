package de.medo.holonetbridge.commons.discord.i18n;

import java.util.Locale;

import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketBug;
import de.medo.holonetbridge.commons.markdown.MarkdownBuilder;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class TicketDescriptionFormatter {

    private final MessageSource messageSource;

    public TicketDescriptionFormatter(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String format(HolonetTicket ticket, Locale locale) {
        if (!(ticket instanceof HolonetTicketBug bug)) {
            return ticket.getDescription();
        }
        // Old, already-open modals may still submit a German display label instead of a stable ID.
        String recurrence = switch (bug.getRecurrence()) {
            case "always", "Jedes Mal" -> messageSource.getMessage("bug.recurrence-always", null, locale);
            case "sometimes", "Manchmal" -> messageSource.getMessage("bug.recurrence-sometimes", null, locale);
            case "once", "Bisher nur einmal" -> messageSource.getMessage("bug.recurrence-once", null, locale);
            case "not-retried", "Noch nicht erneut ausprobiert" -> messageSource.getMessage("bug.recurrence-not-retried", null, locale);
            default -> bug.getRecurrence();
        };
        return MarkdownBuilder.builder()
                .heading2(messageSource.getMessage("bug.steps", null, locale)).text(bug.getSteps())
                .heading2(messageSource.getMessage("bug.expected", null, locale)).text(bug.getExpected())
                .heading2(messageSource.getMessage("bug.actual", null, locale)).text(bug.getActual())
                .heading2(messageSource.getMessage("bug.recurrence", null, locale)).text(recurrence)
                .build();
    }
}
