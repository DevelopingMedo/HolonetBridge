package de.medo.holonetbridge.commons.discord.setup;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A Discord server (guild) that has been set up with the HolonetBridge support button: remembers which channel
 * currently holds the bot's persistent "Support anfragen" message and that message's id, so {@code
 * /setup} only needs to be re-run when the target channel actually changes, not on every
 * application restart.
 */
@Entity
@Table(name = "holonet_discord_server")
public class HolonetDiscordServer {

    @Id
    private Long guildId;

    private long channelId;
    private long messageId;

    protected HolonetDiscordServer() {}

    public HolonetDiscordServer(long guildId, long channelId, long messageId) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.messageId = messageId;
    }

    public long guildId() {
        return guildId;
    }

    public long channelId() {
        return channelId;
    }

    public long messageId() {
        return messageId;
    }
}
