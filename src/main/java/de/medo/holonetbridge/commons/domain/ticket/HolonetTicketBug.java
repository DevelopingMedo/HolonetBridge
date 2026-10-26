package de.medo.holonetbridge.commons.domain.ticket;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public final class HolonetTicketBug extends HolonetTicket {

    @NonNull private final String steps;
    @NonNull private final String expected;
    @NonNull private final String actual;
    @NonNull private final String recurrence;
}
