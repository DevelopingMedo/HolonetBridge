package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HolonetTicketTest {

    @Test
    void bugDescriptionHasFourSectionsAndPreservesRawInput() {
        HolonetTicket ticket = new HolonetTicketBug(
                "Login schlägt fehl",
                "  1. starte\n* **zweiter Schritt**  ",
                "* soll funktionieren\n  ",
                "`Fehler`\nweiter",
                "Jedes Mal");

        assertThat(ticket.title()).isEqualTo("Login schlägt fehl");
        assertThat(ticket.getDescription())
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

        assertThat(ticket.title()).isEqualTo("Dark mode");
        assertThat(ticket.getDescription()).isSameAs(description);
    }
}
