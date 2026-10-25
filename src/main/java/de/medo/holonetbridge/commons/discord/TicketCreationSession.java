package de.medo.holonetbridge.commons.discord;

/**
 * State of one Discord user's in-progress {@code /ticket} conversation, mutated in place as the user moves
 * through the steps (title, then bug/feature choice and description); {@code title} and {@code kind} are
 * {@code null} until that step has been completed. Once the ticket is created at the provider, the session is
 * discarded.
 */
final class TicketCreationSession {

    private String title;
    private TicketKind kind;

    String title() {
        return title;
    }

    void title(String title) {
        this.title = title;
    }

    TicketKind kind() {
        return kind;
    }

    void kind(TicketKind kind) {
        this.kind = kind;
    }
}
