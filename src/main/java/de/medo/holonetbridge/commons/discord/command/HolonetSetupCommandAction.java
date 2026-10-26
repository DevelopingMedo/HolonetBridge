package de.medo.holonetbridge.commons.discord.command;

import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraftStore;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the admin-only {@code /setup} command. Discord's own default-permission gate on the command
 * ({@code DefaultMemberPermissions}, set in {@code DiscordGatewayConfiguration}) can be loosened per-guild by
 * a server admin, so this re-checks {@link Permission#MANAGE_SERVER} explicitly here rather than relying on
 * it alone.
 *
 * <p>The command's immediate response is always the Gitea connection modal (or, if a precondition fails, a
 * plain ephemeral reply) - never {@code deferReply} - because Discord only accepts a modal as the direct
 * response to the interaction that triggered it. The chosen panel channel and guild are stashed in {@link
 * HolonetSetupDraftStore} under the invoking user's id so the later modal submission ({@link
 * de.medo.holonetbridge.commons.discord.modal.HolonetSetupConnectionSubmitModalAction}) knows where to post
 * the support panel without that channel having to be a modal field itself.
 */
@Component
public class HolonetSetupCommandAction {

    /** Name of the {@code /setup} slash command. */
    public static final String COMMAND_SETUP = "setup";

    /** Name of the {@code /setup} command's single {@code CHANNEL} option. */
    public static final String OPTION_CHANNEL = "channel";

    private final HolonetSetupDraftStore draftStore;
    private final HolonetProviderConnectionService providerConnectionService;

    public HolonetSetupCommandAction(
            HolonetSetupDraftStore draftStore, HolonetProviderConnectionService providerConnectionService) {
        this.draftStore = draftStore;
        this.providerConnectionService = providerConnectionService;
    }

    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            replyError(event, "/setup kann nur innerhalb eines Servers ausgeführt werden.");
            return;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            replyError(event, "Dazu fehlt dir die Berechtigung \"Server verwalten\".");
            return;
        }

        TextChannel channel = event.getOption(OPTION_CHANNEL).getAsChannel().asTextChannel();
        if (!guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
            replyError(
                    event,
                    "Ich habe in " + channel.getAsMention()
                            + " keine Berechtigung, die Support-Nachricht zu posten.");
            return;
        }

        draftStore.start(member.getIdLong(), guild.getIdLong(), channel.getIdLong());
        boolean tokenRequired = providerConnectionService.activeConnection(guild.getIdLong()).isEmpty();
        event.replyModal(HolonetProviderConnectionModal.build(tokenRequired, null)).queue();
    }

    private static void replyError(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }
}
