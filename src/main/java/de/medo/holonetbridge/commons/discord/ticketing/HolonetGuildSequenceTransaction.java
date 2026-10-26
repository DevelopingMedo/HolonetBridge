package de.medo.holonetbridge.commons.discord.ticketing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The actual single-transaction attempt behind {@link HolonetGuildSequenceService#nextNumberFor}, split into
 * its own bean so {@code @Transactional} applies through Spring's proxy: a method calling another
 * {@code @Transactional} method on {@code this} would bypass the proxy entirely and run both in whatever
 * transaction (or none) the caller already had open.
 */
@Component
class HolonetGuildSequenceTransaction {

    private final HolonetGuildSequenceRepository repository;

    HolonetGuildSequenceTransaction(HolonetGuildSequenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Ensures a row exists for {@code guildId} (creating one at {@code 0} if not - this insert itself may lose
     * a race against a concurrent first call for the same brand-new guild, surfacing as a
     * {@code DataIntegrityViolationException} that the caller retries), locks that row for the rest of this
     * transaction (see {@link HolonetGuildSequenceRepository#lockForUpdate}), then atomically increments and
     * returns the new value.
     */
    @Transactional
    long incrementAndGet(long guildId) {
        if (!repository.existsById(guildId)) {
            repository.saveAndFlush(new HolonetGuildSequence(guildId, 0L));
        }
        repository.lockForUpdate(guildId);
        int updated = repository.increment(guildId);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Expected to increment exactly one guild sequence row for guildId=" + guildId
                            + " but updated " + updated);
        }
        return repository.findById(guildId).orElseThrow().currentNumber();
    }
}
