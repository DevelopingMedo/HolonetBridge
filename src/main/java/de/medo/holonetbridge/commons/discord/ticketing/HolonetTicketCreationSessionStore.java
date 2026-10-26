package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Holds the in-progress {@link HolonetTicketCreationSession} for each Discord user currently going through the
 * ticket-creation flow, keyed by Discord user id.
 *
 * <p>In-memory only: sessions are short-lived (a single guided conversation) and are not meant to survive an
 * application restart.
 */
@Component
public class HolonetTicketCreationSessionStore {

    private final Map<Long, HolonetTicketCreationSession> sessions = new ConcurrentHashMap<>();

    public HolonetTicketCreationSession startOrReset(long userId) {
        HolonetTicketCreationSession session = new HolonetTicketCreationSession();
        sessions.put(userId, session);
        return session;
    }

    public Optional<HolonetTicketCreationSession> get(long userId) {
        return Optional.ofNullable(sessions.get(userId));
    }

    public void clear(long userId) {
        sessions.remove(userId);
    }
}
