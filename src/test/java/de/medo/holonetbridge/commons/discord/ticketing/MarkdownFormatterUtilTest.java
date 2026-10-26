package de.medo.holonetbridge.commons.discord.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownFormatterUtilTest {

    @Test
    void h2PrefixesAHeadingWithTheLevelTwoMarker() {
        assertThat(MarkdownFormatterUtil.h2("Titel")).isEqualTo("## Titel");
    }

    @Test
    void renderSeparatesBlocksWithBlankLines() {
        assertThat(MarkdownFormatterUtil.render(
                        List.of("## Titel", "Beschreibung", "## Zweiter Abschnitt", "Text")))
                .isEqualTo("## Titel\n\nBeschreibung\n\n## Zweiter Abschnitt\n\nText");
    }
}
