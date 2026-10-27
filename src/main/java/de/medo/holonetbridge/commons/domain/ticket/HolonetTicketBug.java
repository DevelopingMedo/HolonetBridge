package de.medo.holonetbridge.commons.domain.ticket;

import de.medo.holonetbridge.commons.markdown.MarkdownBuilder;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@Entity
@DiscriminatorValue("BUG")
@Getter
@SuperBuilder
public final class HolonetTicketBug extends HolonetTicket {

    private static final String STEPS_HEADING = "Was hast du vor dem Fehler gemacht?";
    private static final String EXPECTED_HEADING = "Was hättest du erwartet?";
    private static final String ACTUAL_HEADING = "Was ist stattdessen passiert?";
    private static final String RECURRENCE_HEADING = "Tritt der Fehler erneut auf?";

    @NonNull private String steps;
    @NonNull private String expected;
    @NonNull private String actual;
    @NonNull private String recurrence;

    protected HolonetTicketBug() {}

    @Override
    public String getDescription() {
        return MarkdownBuilder.builder()
                .heading2(STEPS_HEADING)
                .text(steps)
                .heading2(EXPECTED_HEADING)
                .text(expected)
                .heading2(ACTUAL_HEADING)
                .text(actual)
                .heading2(RECURRENCE_HEADING)
                .text(recurrence)
                .build();
    }
}
