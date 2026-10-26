package de.medo.holonetbridge.commons.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MarkdownBuilderTest {

    @Test
    void rendersMinecraftBugReportToExpectedMarkdownFixture() throws IOException {
        String fixture;
        try (InputStream input = getClass().getResourceAsStream("/markdown/bug-0001.md")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(fixture).endsWith("\n");
        // Fixture files conventionally end in one newline; the builder contract does not.
        String expectedMarkdown = fixture.substring(0, fixture.length() - 1);

        String rendered = MarkdownBuilder.builder()
                .heading2("Was hast du vor dem Fehler gemacht?")
                .text(STEPS)
                .heading2("Was hättest du erwartet?")
                .text(EXPECTED)
                .heading2("Was ist stattdessen passiert?")
                .text(ACTUAL)
                .heading2("Tritt der Fehler erneut auf?")
                .text(RECURRENCE)
                .build();

        assertThat(rendered).isEqualTo(expectedMarkdown).doesNotEndWith("\n");
    }

    private static final String STEPS =
            "Ich habe eine Truhe neben einer Wand platziert und mehrere Stacks Cobblestone hineingelegt.\n"
                    + "Danach habe ich die Truhe geöffnet und mit Shift-Klick Items herausgenommen.";
    private static final String EXPECTED =
            "Die ausgewählten Items sollten direkt in mein Inventar verschoben werden.";
    private static final String ACTUAL =
            "Die Items verschwinden aus der Truhe, tauchen aber nicht im Inventar auf.\n"
                    + "Nach erneutem Öffnen sind sie ebenfalls nicht mehr da.";
    private static final String RECURRENCE = "Jedes Mal, wenn ich Items mit Shift-Klick herausnehme.";
}
