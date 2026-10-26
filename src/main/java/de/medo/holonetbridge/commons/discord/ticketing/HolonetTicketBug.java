package de.medo.holonetbridge.commons.discord.ticketing;

import de.medo.holonetbridge.commons.markdown.MarkdownBuilder;
import java.util.Objects;

public final class HolonetTicketBug extends HolonetTicket {

    private static final String STEPS_HEADING = "Was hast du vor dem Fehler gemacht?";
    private static final String EXPECTED_HEADING = "Was hättest du erwartet?";
    private static final String ACTUAL_HEADING = "Was ist stattdessen passiert?";
    private static final String RECURRENCE_HEADING = "Tritt der Fehler erneut auf?";

    private final String steps;
    private final String expected;
    private final String actual;
    private final String recurrence;

    public HolonetTicketBug(String title, String steps, String expected, String actual, String recurrence) {
        super(title, "");
        this.steps = Objects.requireNonNull(steps, "steps must not be null");
        this.expected = Objects.requireNonNull(expected, "expected must not be null");
        this.actual = Objects.requireNonNull(actual, "actual must not be null");
        this.recurrence = Objects.requireNonNull(recurrence, "recurrence must not be null");
    }

    @Override
    public String getDescription() {
        return new MarkdownBuilder()
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
