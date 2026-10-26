package de.medo.holonetbridge.commons.domain.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import de.medo.holonetbridge.commons.discord.i18n.*;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.Test;

class HolonetTicketTest {

    @Test
    void bugDescriptionHasFourSectionsAndPreservesRawInput() {
        HolonetTicket ticket = HolonetTicketBug.builder()
                .title("Login schlägt fehl")
                .steps("  1. starte\n* **zweiter Schritt**  ")
                .expected("* soll funktionieren\n  ")
                .actual("`Fehler`\nweiter")
                .recurrence("Jedes Mal")
                .build();

        assertThat(ticket.getTitle()).isEqualTo("Login schlägt fehl");
        assertThat(new TicketDescriptionFormatter(new Texts(new I18nProperties(null)))
                .format(ticket, DiscordLocale.GERMAN))
                .isEqualTo("## Was hast du vor dem Fehler gemacht?\n\n"
                        + "  1. starte\n* **zweiter Schritt**  \n\n"
                        + "## Was hättest du erwartet?\n\n"
                        + "* soll funktionieren\n  \n\n"
                        + "## Was ist stattdessen passiert?\n\n"
                        + "`Fehler`\nweiter\n\n"
                        + "## Tritt der Fehler erneut auf?\n\n"
                        + "Jedes Mal");
    }

    @Test
    void featureDescriptionIsPassedThroughUnchanged() {
        String description = "  **raw Markdown**\nsecond line\n  ";
        HolonetTicket ticket = HolonetTicket.builder()
                .title("Dark mode")
                .description(description)
                .build();

        assertThat(ticket.getTitle()).isEqualTo("Dark mode");
        assertThat(ticket.getDescription()).isSameAs(description);
    }
}
