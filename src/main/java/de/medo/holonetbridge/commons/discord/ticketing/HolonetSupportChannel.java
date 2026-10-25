package de.medo.holonetbridge.commons.discord.ticketing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A Discord text channel created for a single support request: remembers the channel's id, the requesting user,
 * and the running per-guild number used for its name. The created ticket id is filled in once the ticket has
 * been handed to the provider, so the channel can later be linked to (and cleaned up together with) its ticket.
 */
@Entity
@Table(name = "holonet_support_channel")
class HolonetSupportChannel {

    @Id
    private Long channelId;

    private long guildId;
    private long requesterUserId;
    private String ticketId;
    private long number;

    protected HolonetSupportChannel() {}

    HolonetSupportChannel(long channelId, long guildId, long requesterUserId, long number) {
        this.channelId = channelId;
        this.guildId = guildId;
        this.requesterUserId = requesterUserId;
        this.number = number;
    }

    long channelId() {
        return channelId;
    }

    long guildId() {
        return guildId;
    }

    long requesterUserId() {
        return requesterUserId;
    }

    String ticketId() {
        return ticketId;
    }

    long number() {
        return number;
    }

    void ticketId(String ticketId) {
        this.ticketId = ticketId;
    }
}
