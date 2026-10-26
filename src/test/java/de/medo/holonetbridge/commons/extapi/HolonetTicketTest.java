package de.medo.holonetbridge.commons.extapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HolonetTicketTest {

    @Test
    void bugTicketRendersItsFourFieldsAsClassicMarkdown() {
        HolonetTicket ticket = HolonetTicket.bug(
                "Login fails", "Did X then Y", "It should work", "It crashed", "Jedes Mal");

        assertThat(ticket.title()).isEqualTo("Login fails");
        assertThat(ticket.getMarkdownFormat()).isEqualTo("""
                **Was hast du vor dem Fehler gemacht?**
                Did X then Y

                **Was hättest du erwartet?**
                It should work

                **Was ist stattdessen passiert?**
                It crashed

                **Tritt der Fehler erneut auf?**
                Jedes Mal""");
    }

    @Test
    void featureTicketPassesItsDescriptionThroughUnchanged() {
        HolonetTicket ticket = HolonetTicket.feature("Dark mode", "I want a dark theme toggle in settings.");

        assertThat(ticket.title()).isEqualTo("Dark mode");
        assertThat(ticket.getMarkdownFormat()).isEqualTo("I want a dark theme toggle in settings.");
    }
}
