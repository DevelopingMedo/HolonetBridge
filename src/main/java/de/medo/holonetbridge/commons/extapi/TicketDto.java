package de.medo.holonetbridge.commons.extapi;

import java.util.Objects;

/**
 * {@code url} is the provider's browser-facing page for the ticket (e.g. Gitea's {@code html_url}), not its REST
 * resource URL. It may be {@code null} or blank if the provider did not return one; callers must not treat a
 * missing url as the ticket not existing.
 */
public record TicketDto(String id, String title, String description, String url) {

    public TicketDto {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }
}
