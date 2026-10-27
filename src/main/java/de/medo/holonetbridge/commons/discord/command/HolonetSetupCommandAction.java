package de.medo.holonetbridge.commons.discord.command;

import de.medo.holonetbridge.commons.discord.button.HolonetSetupRestartButton;
import de.medo.holonetbridge.commons.discord.button.HolonetSetupRetryButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupSessionStore;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

/**
 * Handles the admin-only {@code /setup} command. Discord's own default-permission gate on the command
 * ({@code DefaultMemberPermissions}, set in {@code DiscordGatewayConfiguration}) can be loosened per-guild by
 * a server admin, so this re-checks {@link Permission#MANAGE_SERVER} explicitly here rather than relying on
 * it alone.
 *
 * <p>If a previous attempt for this guild/user already reached a modal submission (see {@link
 * HolonetSetupSessionStore}) - e.g. a failed validation never retried, or the bot restarted in between - the
 * command asks whether to resume it (see {@link HolonetSetupRetryButton}) or discard it and start fresh (see
 * {@link HolonetSetupRestartButton}) instead of opening a modal directly. Otherwise, the command's immediate
 * response is always the Gitea connection modal (or, if a precondition fails, a plain ephemeral reply) -
 * never {@code deferReply} - because Discord only accepts a modal as the direct response to the interaction
 * that triggered it.
 */
@Component
public class HolonetSetupCommandAction {

    /** Name of the {@code /setup} slash command. */
    public static final String COMMAND_SETUP = "setup";

    private final HolonetSetupSessionStore sessionStore;
    private final HolonetProviderConnectionService providerConnectionService;

    public HolonetSetupCommandAction(
            HolonetSetupSessionStore sessionStore, HolonetProviderConnectionService providerConnectionService) {
        this.sessionStore = sessionStore;
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

        long guildId = guild.getIdLong();
        long userId = member.getIdLong();
        if (sessionStore.findUnfinished(guildId, userId).isPresent()) {
            askToResumeOrRestart(event);
            return;
        }

        boolean tokenRequired = providerConnectionService.activeConnection(guildId).isEmpty();
        Modal setupModal = HolonetProviderConnectionModal.build(tokenRequired, null);

        event.replyModal(setupModal).queue();
    }

    private static void askToResumeOrRestart(SlashCommandInteractionEvent event) {
        event.reply("Du hast einen unvollendeten Vorgang. Möchtest du fortsetzen oder neu starten?")
                .setEphemeral(true)
                .addComponents(ActionRow.of(
                        Button.primary(HolonetSetupRetryButton.BUTTON_ID, "Fortsetzen"),
                        Button.secondary(HolonetSetupRestartButton.BUTTON_ID, "Neu starten")))
                .queue();
    }

    private static void replyError(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }
}
