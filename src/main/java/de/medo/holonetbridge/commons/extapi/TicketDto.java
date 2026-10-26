package de.medo.holonetbridge.commons.extapi;

import java.util.Objects;

public record TicketDto(String id, String title, String description) {

    public TicketDto {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }
}
