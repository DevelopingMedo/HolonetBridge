package de.medo.holonetbridge.commons.discord;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Remembers, per Discord guild, which channel currently holds the bot's persistent "Support anfragen" message
 * and that message's id, so {@code /configure-holonet-bridge} only needs to be re-run when the target channel
 * actually changes, not on every application restart.
 */
@Entity
@Table(name = "guild_ticket_channel_configuration")
class GuildTicketChannelConfiguration {

    @Id
    private Long guildId;

    private long channelId;
    private long messageId;

    protected GuildTicketChannelConfiguration() {}

    GuildTicketChannelConfiguration(long guildId, long channelId, long messageId) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.messageId = messageId;
    }

    long guildId() {
        return guildId;
    }

    long channelId() {
        return channelId;
    }

    long messageId() {
        return messageId;
    }
}
