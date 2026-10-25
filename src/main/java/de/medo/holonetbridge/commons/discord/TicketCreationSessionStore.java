package de.medo.holonetbridge.commons.discord;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Holds the in-progress {@link TicketCreationSession} for each Discord user currently going through the
 * ticket-creation flow, keyed by Discord user id.
 *
 * <p>In-memory only: sessions are short-lived (a single guided conversation) and are not meant to survive an
 * application restart.
 */
@Component
class TicketCreationSessionStore {

    private final Map<Long, TicketCreationSession> sessions = new ConcurrentHashMap<>();

    TicketCreationSession startOrReset(long userId) {
        TicketCreationSession session = new TicketCreationSession();
        sessions.put(userId, session);
        return session;
    }

    Optional<TicketCreationSession> get(long userId) {
        return Optional.ofNullable(sessions.get(userId));
    }

    void clear(long userId) {
        sessions.remove(userId);
    }
}
