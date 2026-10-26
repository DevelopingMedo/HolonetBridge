package de.medo.holonetbridge.commons.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownBuilderTest {

    @Test
    void rendersAtxHeadingsAndRawBlocksWithBlankLineSeparators() {
        String rendered = new MarkdownBuilder()
                .heading2("Section")
                .text("  * raw\n**markdown**  ")
                .heading2("Next")
                .text("value")
                .build();

        assertThat(rendered)
                .isEqualTo("## Section\n\n  * raw\n**markdown**  \n\n## Next\n\nvalue");
    }
}
