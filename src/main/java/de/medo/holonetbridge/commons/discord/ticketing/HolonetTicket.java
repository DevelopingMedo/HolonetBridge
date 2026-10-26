package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A ticket being created through the Discord flow, holding the title and the description as classic Markdown.
 * Build it with {@link #builder()}: a sequence of {@link Builder#h2(String)} headings and {@link Builder#text(String)}
 * paragraphs is assembled into the Markdown body via {@link MarkdownFormatterUtil}.
 */
public final class HolonetTicket {

    private final String title;
    private final String description;

    private HolonetTicket(String title, String description) {
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String title() {
        return title;
    }

    /** The description as classic Markdown, ready to hand to any provider. */
    public String getMarkdownFormat() {
        return description;
    }

    public static final class Builder {

        private String title;
        private final List<String> blocks = new ArrayList<>();

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder h2(String heading) {
            blocks.add(MarkdownFormatterUtil.h2(heading));
            return this;
        }

        public Builder text(String text) {
            blocks.add(text);
            return this;
        }

        public HolonetTicket build() {
            return new HolonetTicket(title, MarkdownFormatterUtil.render(blocks));
        }
    }
}
