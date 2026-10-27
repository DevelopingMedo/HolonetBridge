package de.medo.holonetbridge.commons.discord.ticketing;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the durable "unfinished bug/feature report" state backing the resume-or-restart choice offered by
 * {@code HolonetBugReportModalButton}/{@code HolonetFeatureModalButton}: a {@link HolonetBotInteractionSession}
 * plus its linked {@link HolonetTicket} draft, scoped by the user's support channel (one ticket per channel,
 * see {@code HolonetSupportChannel}) and their Discord user id. Unlike {@link HolonetTicketCreationSessionStore}
 * this state is backed by the database, so it survives a process restart.
 *
 * <p>At most one draft is ever kept per (channel, user): {@link #saveDraft} always replaces whatever was
 * there before - whether this call is continuing a previous attempt ("Fortsetzen") or starting a different
 * kind of report over ("Neu starten") - so the resume check never has more than one candidate to consider.
 */
@Service
public class HolonetTicketSessionService {

    private final HolonetBotInteractionSessionRepository sessionRepository;
    private final HolonetTicketRepository ticketRepository;
    private final Clock clock;

    /**
     * Explicitly {@code @Autowired} because this class also declares a package-private, {@code Clock}-taking
     * constructor for tests; without the annotation Spring cannot pick a constructor between the two and
     * falls back to a (nonexistent) no-arg one instead of this public one.
     */
    @Autowired
    public HolonetTicketSessionService(
            HolonetBotInteractionSessionRepository sessionRepository, HolonetTicketRepository ticketRepository) {
        this(sessionRepository, ticketRepository, Clock.systemUTC());
    }

    HolonetTicketSessionService(
            HolonetBotInteractionSessionRepository sessionRepository,
            HolonetTicketRepository ticketRepository,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    /** The saved, not-yet-submitted draft for this channel/user, if a previous attempt left one behind. */
    public Optional<HolonetTicket> findUnfinishedDraft(long channelId, long userId) {
        return findSession(channelId, userId).flatMap(session -> ticketRepository.findBySessionId(session.id()));
    }

    /** Discards a previously saved draft and its session, e.g. when the user chooses "Neu starten". */
    @Transactional
    public void discardDraft(long channelId, long userId) {
        findSession(channelId, userId).ifPresent(session -> {
            ticketRepository.deleteBySessionId(session.id());
            sessionRepository.delete(session);
        });
    }

    /**
     * Saves {@code ticket} as the current draft just before attempting the actual provider submission,
     * reusing the existing session if the user is continuing one (a previous failed attempt, or a
     * "Fortsetzen" choice) or starting a new one otherwise, so a crash or failure after this point always
     * leaves something resumable behind instead of silently losing what was just submitted. The previous
     * draft row, if any, is deleted (and flushed) before the replacement is inserted so the two writes never
     * collide on the unique {@code session_id} constraint.
     */
    @Transactional
    public void saveDraft(long channelId, long userId, HolonetTicket ticket) {
        HolonetBotInteractionSession session =
                findSession(channelId, userId).map(this::touch).orElseGet(() -> startSession(channelId, userId));
        ticketRepository.deleteBySessionId(session.id());
        ticketRepository.flush();
        ticket.assignToSession(session.id());
        ticketRepository.save(ticket);
    }

    private Optional<HolonetBotInteractionSession> findSession(long channelId, long userId) {
        return sessionRepository.findFirstByDiscordIdAndUserIdOrderByUpdatedAtDesc(channelId, userId);
    }

    private HolonetBotInteractionSession startSession(long channelId, long userId) {
        Instant now = clock.instant();
        return sessionRepository.save(
                new HolonetBotInteractionSession(UUID.randomUUID(), channelId, userId, now, now));
    }

    private HolonetBotInteractionSession touch(HolonetBotInteractionSession session) {
        Instant now = clock.instant();
        return sessionRepository.save(new HolonetBotInteractionSession(
                session.id(), session.discordId(), session.userId(), session.createdAt(), now));
    }
}
