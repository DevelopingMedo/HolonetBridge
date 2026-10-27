package de.medo.holonetbridge.commons.domain.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HolonetTicketTest {

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
