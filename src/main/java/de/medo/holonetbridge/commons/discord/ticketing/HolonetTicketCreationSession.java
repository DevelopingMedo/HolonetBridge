package de.medo.holonetbridge.commons.discord.ticketing;

/**
 * State of one Discord user's in-progress {@code /ticket} conversation, mutated in place as the user moves
 * through the steps (bug/idea choice, then a single modal); {@code kind} is {@code null} until the choice has
 * been made. Once the ticket is created at the provider, the session is discarded.
 */
final class HolonetTicketCreationSession {

    private HolonetTicketKind kind;

    HolonetTicketKind kind() {
        return kind;
    }

    void kind(HolonetTicketKind kind) {
        this.kind = kind;
    }
}
