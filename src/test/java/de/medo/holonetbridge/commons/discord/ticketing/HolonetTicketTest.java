package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HolonetTicketTest {

    @Test
    void bugTicketRendersARealisticMarkdownDescription() {
        HolonetTicket ticket = HolonetTicket.builder()
                .title("Login schlägt fehl")
                .h2("Was hast du vor dem Fehler gemacht?")
                .text("1. Ich habe mich mit meinem Konto angemeldet.\n2. Danach habe ich auf „Einstellungen“ geklickt.")
                .h2("Was hättest du erwartet?")
                .text("Die Einstellungen sollten sich öffnen und mein Profil anzeigen.")
                .h2("Was ist stattdessen passiert?")
                .text("Stattdessen erscheint eine leere Seite mit der Fehlermeldung:\n"
                        + "`NullPointerException at com.holonet.SettingsController.load(SettingsController.java:42)`")
                .h2("Tritt der Fehler erneut auf?")
                .text("Jedes Mal")
                .build();

        assertThat(ticket.title()).isEqualTo("Login schlägt fehl");
        assertThat(ticket.getMarkdownFormat()).isEqualTo("""
                ## Was hast du vor dem Fehler gemacht?

                1. Ich habe mich mit meinem Konto angemeldet.
                2. Danach habe ich auf „Einstellungen“ geklickt.

                ## Was hättest du erwartet?

                Die Einstellungen sollten sich öffnen und mein Profil anzeigen.

                ## Was ist stattdessen passiert?

                Stattdessen erscheint eine leere Seite mit der Fehlermeldung:
                `NullPointerException at com.holonet.SettingsController.load(SettingsController.java:42)`

                ## Tritt der Fehler erneut auf?

                Jedes Mal""");
    }

    @Test
    void featureTicketPassesItsDescriptionThroughUnchanged() {
        HolonetTicket ticket = HolonetTicket.builder()
                .title("Dark mode")
                .text("Ich möchte einen Dark-Mode-Schalter in den Einstellungen.")
                .build();

        assertThat(ticket.title()).isEqualTo("Dark mode");
        assertThat(ticket.getMarkdownFormat()).isEqualTo("Ich möchte einen Dark-Mode-Schalter in den Einstellungen.");
    }
}
