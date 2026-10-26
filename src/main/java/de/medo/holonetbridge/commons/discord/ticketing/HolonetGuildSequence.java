package de.medo.holonetbridge.commons.discord.ticketing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One guild's running counter for support-channel numbers. Kept as its own row (instead of deriving the
 * next number from the highest existing {@link HolonetSupportChannel#number()}) so incrementing it is a
 * single atomic {@code UPDATE} - see {@link HolonetGuildSequenceService} - rather than a read-then-write that
 * two concurrent requests could race on and both derive the same "next" number from.
 */
@Entity
@Table(name = "holonet_guild_sequence")
public class HolonetGuildSequence {

    @Id
    private long guildId;

    private long currentNumber;

    protected HolonetGuildSequence() {}

    public HolonetGuildSequence(long guildId, long currentNumber) {
        this.guildId = guildId;
        this.currentNumber = currentNumber;
    }

    public long guildId() {
        return guildId;
    }

    public long currentNumber() {
        return currentNumber;
    }
}
