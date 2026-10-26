package de.medo.holonetbridge.commons.domain.ticket;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

/**
 * A ticket value holding a title and a description ready to hand to any provider.
 */
@Getter
@SuperBuilder
public class HolonetTicket {

    @NonNull private final String title;

    @NonNull
    @Builder.Default
    private final String description = "";
}
