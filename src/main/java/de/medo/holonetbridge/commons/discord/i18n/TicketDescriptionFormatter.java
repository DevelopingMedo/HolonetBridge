package de.medo.holonetbridge.commons.discord.i18n;

import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketBug;
import de.medo.holonetbridge.commons.markdown.MarkdownBuilder;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.springframework.stereotype.Component;

@Component
public class TicketDescriptionFormatter {

    private final Texts texts;

    public TicketDescriptionFormatter(Texts texts) {
        this.texts = texts;
    }

    public String format(HolonetTicket ticket, DiscordLocale locale) {
        if (!(ticket instanceof HolonetTicketBug bug)) {
            return ticket.getDescription();
        }
        // Old, already-open modals may still submit a German display label instead of a stable ID.
        String recurrence = switch (bug.getRecurrence()) {
            case "always", "Jedes Mal" -> texts.get(locale, "bug.recurrence-always");
            case "sometimes", "Manchmal" -> texts.get(locale, "bug.recurrence-sometimes");
            case "once", "Bisher nur einmal" -> texts.get(locale, "bug.recurrence-once");
            case "not-retried", "Noch nicht erneut ausprobiert" -> texts.get(locale, "bug.recurrence-not-retried");
            default -> bug.getRecurrence();
        };
        return MarkdownBuilder.builder()
                .heading2(texts.get(locale, "bug.steps")).text(bug.getSteps())
                .heading2(texts.get(locale, "bug.expected")).text(bug.getExpected())
                .heading2(texts.get(locale, "bug.actual")).text(bug.getActual())
                .heading2(texts.get(locale, "bug.recurrence")).text(recurrence)
                .build();
    }
}
