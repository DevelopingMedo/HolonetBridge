package de.medo.holonetbridge.commons.discord.ticketing;

/**
 * State of one Discord user's in-progress {@code /ticket} conversation, mutated in place as the user moves
 * through the steps (title, then bug/feature choice and description); {@code title} and {@code kind} are
 * {@code null} until that step has been completed. Once the ticket is created at the provider, the session is
 * discarded.
 */
final class HolonetTicketCreationSession {

    private String title;
    private HolonetTicketKind kind;

    String title() {
        return title;
    }

    void title(String title) {
        this.title = title;
    }

    HolonetTicketKind kind() {
        return kind;
    }

    void kind(HolonetTicketKind kind) {
        this.kind = kind;
    }
}
