package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Holds the in-progress {@link HolonetTicketCreationSession} for each Discord user currently going through
 * the ticket-creation flow, keyed by guild, channel, and user: a user can in principle have more than one
 * support channel open at once (e.g. a previous one left stuck), so the channel must be part of the key, not
 * just the user.
 *
 * <p>In-memory only, and purely cosmetic bookkeeping now (see {@code HolonetTicketSubmitter}): the ticket
 * kind and whether submission is allowed at all are both derived from durable state (the button/modal custom
 * id, and {@code HolonetSupportChannelRepository}) that survives a restart, since sessions here are not meant
 * to and do not.
 */
@Component
public class HolonetTicketCreationSessionStore {

    private final Map<Key, HolonetTicketCreationSession> sessions = new ConcurrentHashMap<>();

    public HolonetTicketCreationSession startOrReset(long guildId, long channelId, long userId) {
        HolonetTicketCreationSession session = new HolonetTicketCreationSession();
        sessions.put(new Key(guildId, channelId, userId), session);
        return session;
    }

    public Optional<HolonetTicketCreationSession> get(long guildId, long channelId, long userId) {
        return Optional.ofNullable(sessions.get(new Key(guildId, channelId, userId)));
    }

    public void clear(long guildId, long channelId, long userId) {
        sessions.remove(new Key(guildId, channelId, userId));
    }

    private record Key(long guildId, long channelId, long userId) {}
}
