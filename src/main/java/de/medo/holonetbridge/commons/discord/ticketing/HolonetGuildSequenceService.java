package de.medo.holonetbridge.commons.discord.ticketing;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Hands out the next per-guild support-channel number, replacing the old
 * {@code findTopByGuildIdOrderByNumberDesc(...).number() + 1} lookup: that pattern reads the current highest
 * number and writes a channel row with "current + 1" in two separate steps, so two concurrent requests for the
 * same guild could both read the same highest number and then both save the same "next" number. This service
 * instead keeps one running counter row per guild ({@link HolonetGuildSequence}) and increments it with a
 * single atomic {@code UPDATE} (see {@link HolonetGuildSequenceRepository#increment}).
 */
@Service
public class HolonetGuildSequenceService {

    /**
     * Only the very first call for a brand-new guild can lose a race (on the row's initial insert); a handful
     * of retries comfortably covers even many concurrent "first ever" callers for the same guild.
     */
    private static final int MAX_ATTEMPTS = 10;

    private final HolonetGuildSequenceTransaction transaction;

    HolonetGuildSequenceService(HolonetGuildSequenceTransaction transaction) {
        this.transaction = transaction;
    }

    /**
     * Returns the next number for {@code guildId}, starting at 1. Deliberately not itself
     * {@code @Transactional}: each attempt below calls through the Spring proxy into a brand-new transaction
     * (see {@link HolonetGuildSequenceTransaction}), so a lost race on the first attempt never leaves this
     * thread holding two database connections at once while it retries - only ever one at a time.
     */
    public long nextNumberFor(long guildId) {
        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.incrementAndGet(guildId);
            } catch (DataIntegrityViolationException raceOnFirstInsert) {
                lastFailure = raceOnFirstInsert;
            }
        }
        throw new IllegalStateException(
                "Could not obtain the next support-channel number for guildId=" + guildId
                        + " after " + MAX_ATTEMPTS + " attempts",
                lastFailure);
    }
}
