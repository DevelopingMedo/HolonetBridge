package de.medo.holonetbridge.commons.discord.setup;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.bot.HolonetBotInteractionSessionRepository;
import de.medo.holonetbridge.commons.domain.setup.HolonetSetupConnectionModal;
import de.medo.holonetbridge.commons.domain.setup.HolonetSetupConnectionModalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the in-progress {@code /setup} connection flow's non-secret field values across application
 * restarts, keyed by the (guild, Discord user) pair that invoked {@code /setup}, by combining a {@link
 * HolonetBotInteractionSession} (the generic "unfinished flow" record) with its one-to-one {@link
 * HolonetSetupConnectionModal} row (the {@code /setup}-specific fields).
 *
 * <p>Unlike the discarded in-memory {@code HolonetSetupDraftStore} this replaces, a row here is only ever
 * written once a modal has actually been <em>submitted</em> (successfully or not) - never merely opened -
 * so a user who opens {@code /setup}, abandons the modal, and runs {@code /setup} again sees no "unfinished
 * process" prompt; only an attempt that at least reached submission does.
 */
@Component
public class HolonetSetupSessionStore {

    private final HolonetBotInteractionSessionRepository sessionRepository;
    private final HolonetSetupConnectionModalRepository connectionModalRepository;
    private final Clock clock;

    /**
     * Explicitly {@code @Autowired} because this class also declares a package-private, {@code
     * Clock}-taking constructor for tests; without the annotation Spring cannot pick a constructor between
     * the two and falls back to a (nonexistent) no-arg one instead of this public one.
     */
    @Autowired
    public HolonetSetupSessionStore(
            HolonetBotInteractionSessionRepository sessionRepository,
            HolonetSetupConnectionModalRepository connectionModalRepository) {
        this(sessionRepository, connectionModalRepository, Clock.systemUTC());
    }

    HolonetSetupSessionStore(
            HolonetBotInteractionSessionRepository sessionRepository,
            HolonetSetupConnectionModalRepository connectionModalRepository,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.connectionModalRepository = connectionModalRepository;
        this.clock = clock;
    }

    /** The unfinished {@code /setup} attempt for this guild/user, if a modal has already been submitted once. */
    @Transactional(readOnly = true)
    public Optional<HolonetSetupConnectionModal> findUnfinished(long guildId, long userId) {
        return sessionRepository.findAllByDiscordIdAndUserId(guildId, userId).stream()
                .flatMap(session -> connectionModalRepository.findById(session.id()).stream())
                .findFirst();
    }

    /**
     * Records the non-secret fields of a just-submitted modal: reuses the existing unfinished attempt's row
     * for this guild/user if one already exists (so repeated failed submissions update it in place instead
     * of accumulating one row per attempt), otherwise creates a brand-new session and modal row.
     */
    @Transactional
    public HolonetSetupConnectionModal upsert(
            long guildId, long userId, long channelId, String baseUrl, String owner, String repository) {
        HolonetSetupConnectionModal modal = findUnfinished(guildId, userId).orElseGet(() -> startNew(guildId, userId));
        modal.update(channelId, baseUrl, owner, repository);
        return connectionModalRepository.save(modal);
    }

    /** Discards the unfinished attempt for this guild/user, if any - used both on "Neu starten" and after a successful submit. */
    @Transactional
    public void discard(long guildId, long userId) {
        findUnfinished(guildId, userId).ifPresent(modal -> {
            connectionModalRepository.deleteById(modal.sessionId());
            sessionRepository.deleteById(modal.sessionId());
        });
    }

    private HolonetSetupConnectionModal startNew(long guildId, long userId) {
        Instant now = Instant.now(clock);
        UUID sessionId = UUID.randomUUID();
        sessionRepository.save(new HolonetBotInteractionSession(sessionId, guildId, userId, now, now));
        return new HolonetSetupConnectionModal(sessionId);
    }
}
