package de.medo.holonetbridge.commons.markdown;

import net.steppschuh.markdowngenerator.text.TextBuilder;
import net.steppschuh.markdowngenerator.text.heading.Heading;

/** Builds raw Markdown blocks without normalizing user-provided text. */
public final class MarkdownBuilder {

    private final TextBuilder builder = new TextBuilder();
    private boolean hasBlock;

    public MarkdownBuilder heading2(String text) {
        beginBlock();
        Heading heading = new Heading(text, 2);
        heading.setUnderlineStyle(false);
        builder.append(heading);
        return this;
    }

    public MarkdownBuilder text(String text) {
        beginBlock();
        builder.append(text);
        return this;
    }

    public String build() {
        return builder.toString();
    }

    private void beginBlock() {
        if (hasBlock) {
            builder.append("\n\n");
        }
        hasBlock = true;
    }
}
