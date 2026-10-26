package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.Objects;

/**
 * A ticket value holding a title and a description ready to hand to any provider.
 */
public class HolonetTicket {

    private final String title;
    private final String description;

    protected HolonetTicket(String title, String description) {
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String title() {
        return title;
    }

    /** The description, ready to hand to any provider. */
    public String getDescription() {
        return description;
    }

    public static final class Builder {

        private String title;
        private String description;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public HolonetTicket build() {
            return new HolonetTicket(title, description);
        }
    }
}
