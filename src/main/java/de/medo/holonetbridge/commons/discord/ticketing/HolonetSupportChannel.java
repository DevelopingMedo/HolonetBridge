package de.medo.holonetbridge.commons.discord.ticketing;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A Discord text channel created for a single support request: remembers the channel's id, the requesting
 * user, and the running per-guild number used for its name.
 *
 * <p>{@code connectionId}, {@code ticketId}, and {@code ticketUrl} are only ever filled in together, at the
 * moment the external ticket is actually created (see {@link #ticketCreated}) - never at channel-open time.
 * {@code connectionId} freezes which {@code HolonetProviderConnection} that ticket was created against: it is
 * never re-resolved later, so a guild switching its active connection via {@code /setup} afterwards does not
 * change where this channel's own ticket is looked up. One support channel therefore maps to at most one
 * external ticket, and that ticket number must only ever be looked up paired with this {@code connectionId} -
 * never by the external number alone, since a different connection (a different repository/provider) could
 * coincidentally use the very same ticket number.
 */
@Entity
@Table(name = "holonet_support_channel")
public class HolonetSupportChannel {

    @Id
    private Long channelId;

    private long guildId;
    private long requesterUserId;
    private UUID connectionId;
    private String ticketId;
    private String ticketUrl;
    private long number;

    @Enumerated(EnumType.STRING)
    private HolonetSupportChannelStatus status = HolonetSupportChannelStatus.OPEN;

    protected HolonetSupportChannel() {}

    public HolonetSupportChannel(long channelId, long guildId, long requesterUserId, long number) {
        this.channelId = channelId;
        this.guildId = guildId;
        this.requesterUserId = requesterUserId;
        this.number = number;
        this.status = HolonetSupportChannelStatus.OPEN;
    }

    public long channelId() {
        return channelId;
    }

    public long guildId() {
        return guildId;
    }

    public long requesterUserId() {
        return requesterUserId;
    }

    public UUID connectionId() {
        return connectionId;
    }

    public String ticketId() {
        return ticketId;
    }

    public String ticketUrl() {
        return ticketUrl;
    }

    public long number() {
        return number;
    }

    public HolonetSupportChannelStatus status() {
        return status;
    }

    /**
     * Records the external ticket this channel was just used to create: freezes the connection it was
     * created against, the provider's ticket id, and its browser-facing url, and resets {@code status} back
     * to {@link HolonetSupportChannelStatus#OPEN} - a successful creation always means the assignment is
     * usable again, even if a previous attempt from this channel had left it {@code BROKEN}.
     */
    public void ticketCreated(UUID connectionId, String ticketId, String ticketUrl) {
        this.connectionId = connectionId;
        this.ticketId = ticketId;
        this.ticketUrl = ticketUrl;
        this.status = HolonetSupportChannelStatus.OPEN;
    }

    /** Marks this channel's assignment unusable as of the last attempt (see {@link HolonetSupportChannelStatus}). */
    public void markBroken() {
        this.status = HolonetSupportChannelStatus.BROKEN;
    }

    /**
     * Links a legacy row (one that already had {@code ticketId} set before {@code connectionId} existed) to
     * the connection it was unambiguously created against. Package-private: only the startup backfill in
     * this package may call this, since it alone performs the "exactly one unambiguous candidate" check this
     * link must be based on - everyone else must go through {@link #ticketCreated} instead.
     */
    void linkLegacyConnection(UUID connectionId) {
        this.connectionId = connectionId;
    }
}
