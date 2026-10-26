package de.medo.holonetbridge.commons.discord.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketBug;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TicketDescriptionFormatterTest {

    private final TicketDescriptionFormatter formatter =
            new TicketDescriptionFormatter(new Texts(new I18nProperties(null)));

    @ParameterizedTest
    @CsvSource({
            "en-US, What did you do before the error?, Every time",
            "de, Was hast du vor dem Fehler gemacht?, Jedes Mal",
            "hr, Što si napravio prije greške?, Svaki put",
            "ru, Chto ty delal pered oshibkoy?, Kazhdyy raz"
    })
    void translatesHeadingsAndStableChoiceValuesButPreservesUserInput(String language, String heading, String recurrence) {
        HolonetTicketBug ticket = HolonetTicketBug.builder()
                .title("Original title")
                .steps("  **Original**\nsteps  ")
                .expected("expected")
                .actual("actual")
                .recurrence("always")
                .build();

        assertThat(formatter.format(ticket, DiscordLocale.from(language)))
                .startsWith("## " + heading + "\n\n  **Original**\nsteps  ")
                .endsWith(recurrence);
    }
}
