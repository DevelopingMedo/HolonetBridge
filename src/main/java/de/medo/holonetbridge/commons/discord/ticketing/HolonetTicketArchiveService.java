package de.medo.holonetbridge.commons.discord.ticketing;

import de.holonetbridge.commons.message.DiscordMessageCodes;
import de.holonetbridge.commons.message.SupportMessageCodes;
import java.util.LinkedHashMap;
import java.util.HashSet;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static de.medo.holonetbridge.commons.discord.ticketing.HolonetArchiveDiagnostic.Phase.*;
import static de.medo.holonetbridge.commons.discord.ticketing.HolonetArchiveDiagnostic.at;

@Service
public class HolonetTicketArchiveService {
    private static final Logger LOG = LoggerFactory.getLogger(HolonetTicketArchiveService.class);
    public static final long WRITE_MASK = Permission.getRaw(Permission.MESSAGE_SEND,
            Permission.CREATE_PUBLIC_THREADS, Permission.CREATE_PRIVATE_THREADS, Permission.MESSAGE_SEND_IN_THREADS);
    private static final long BOT_MASK = Permission.getRaw(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS);

    public record Result(String discordKey, HolonetArchiveDiagnostic diagnostic) {}

    private final HolonetDiscordChannelRest discordRest;

    public HolonetTicketArchiveService(HolonetDiscordChannelRest discordRest) {
        this.discordRest = discordRest;
    }

    /** Call while holding the channel coordinator for the full duration, including REST completions. */
    public Result archive(Guild guild, TextChannel discord, long actorId) {
        Member actor = guild.retrieveMemberById(actorId).useCache(false).complete();
        if (!actor.hasPermission(Permission.MANAGE_SERVER)) {
            return new Result(DiscordMessageCodes.MANAGE_SERVER_REQUIRED, null);
        }
        if (discord.getGuild().getIdLong() != guild.getIdLong()) {
            return new Result(SupportMessageCodes.ACTION_UNAVAILABLE, null);
        }

        try {
            protectDiscord(discord);
            return new Result(SupportMessageCodes.CLOSE_DISCORD_SUCCESS, null);
        } catch (HolonetArchiveDiagnostic.Failure failure) {
            var diagnostic = failure.diagnostic();
            LOG.warn("{}", diagnostic.line());
            return new Result(SupportMessageCodes.CLOSE_DISCORD_FAILURE, diagnostic);
        }
    }

    private void protectDiscord(TextChannel discord) {
        Guild guild = discord.getGuild();
        Member bot = at(BOT_PREFLIGHT, () -> {
            Member self = guild.getSelfMember();
            var permissions = new HolonetArchiveDiagnostic.BotPermissions(
                    self.hasPermission(discord, Permission.VIEW_CHANNEL),
                    self.hasPermission(discord, Permission.MANAGE_CHANNEL),
                    self.hasPermission(discord, Permission.MANAGE_PERMISSIONS),
                    self.hasPermission(Permission.ADMINISTRATOR));
            if (!permissions.missingMessageKeys().isEmpty()) throw new HolonetArchiveDiagnostic.Failure(
                    new HolonetArchiveDiagnostic(BOT_PREFLIGHT, "NONE", null, null, permissions, null, null));
            return self;
        });
        // REST completion does not update the gateway cache. Read fresh before replacing the full list,
        // including a watcher successfully added just before this operation acquired the coordinator.
        var snapshot = at(CHANNEL_GET, () -> discordRest.retrieve(discord));
        var original = new LinkedHashMap<Long, HolonetDiscordChannelRest.Overwrite>();
        for (var override : snapshot.overrides()) original.put(override.id(), override);
        original.putIfAbsent(guild.getIdLong(), new HolonetDiscordChannelRest.Overwrite(guild.getIdLong(), false, 0, 0));
        original.putIfAbsent(bot.getIdLong(), new HolonetDiscordChannelRest.Overwrite(bot.getIdLong(), true, 0, 0));
        var desired = new java.util.ArrayList<HolonetDiscordChannelRest.Overwrite>();
        for (var override : original.values()) {
            long allow = override.allow(), deny = override.deny();
            if (override.member() && override.id() == bot.getIdLong()) {
                allow |= BOT_MASK;
                deny &= ~BOT_MASK;
            } else {
                allow &= ~WRITE_MASK;
                deny |= WRITE_MASK;
            }
            desired.add(new HolonetDiscordChannelRest.Overwrite(override.id(), override.member(), allow, deny));
        }
        String name = snapshot.name().startsWith("archived-") ? snapshot.name()
                : "archived-" + snapshot.name().substring(0, Math.min(91, snapshot.name().length()));
        var confirmed = at(CHANNEL_PATCH, () -> discordRest.replace(discord, name, desired));
        boolean nameMatches = name.equals(confirmed.name());
        boolean overridesMatch = new HashSet<>(desired).equals(new HashSet<>(confirmed.overrides()));
        if (!nameMatches || !overridesMatch) {
            throw new HolonetArchiveDiagnostic.Failure(new HolonetArchiveDiagnostic(
                    CONFIRMATION_MISMATCH, "NONE", null, null, null, nameMatches, overridesMatch));
        }
    }
}
