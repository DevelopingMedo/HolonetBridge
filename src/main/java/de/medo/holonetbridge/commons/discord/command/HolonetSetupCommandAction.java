package de.medo.holonetbridge.commons.discord.command;

import java.util.Locale;

import de.medo.holonetbridge.commons.discord.modal.HolonetModalFactory;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Handles the admin-only {@code /setup} command. Discord's own default-permission gate on the command
 * ({@code DefaultMemberPermissions}, set in {@code DiscordGatewayConfiguration}) can be loosened per-guild by
 * a server admin, so this re-checks {@link Permission#MANAGE_SERVER} explicitly here rather than relying on
 * it alone.
 *
 */
@Component
public class HolonetSetupCommandAction {

    private final MessageSource messageSource;

    /** Name of the {@code /setup} slash command. */
    public static final String COMMAND_SETUP = "setup";

    /** Name of the {@code /setup} command's single {@code CHANNEL} option. */
    public static final String OPTION_CHANNEL = "channel";
    private final HolonetModalFactory holonetModalFactory;
    private final HolonetBotInteractionSessionService holonetBotInteractionSessionService;


    public HolonetSetupCommandAction(
            MessageSource messageSource,
            HolonetModalFactory holonetModalFactory,
            HolonetBotInteractionSessionService holonetBotInteractionSessionService) {
        this.messageSource = messageSource;
        this.holonetModalFactory = holonetModalFactory;
        this.holonetBotInteractionSessionService = holonetBotInteractionSessionService;
    }

    public void handle(SlashCommandInteractionEvent event) {
        Locale locale = event.getUserLocale().toLocale();

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            replyError(event, messageSource.getMessage("setup.guild-only", null, locale));
            return;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            replyError(event, messageSource.getMessage("setup.manage-server", null, locale));
            return;
        }
        HolonetSetupFormData setupFormData = holonetBotInteractionSessionService.getOrCreateSetupFormDataSession(guild.getIdLong(), member.getIdLong(), locale);

        event.replyModal(holonetModalFactory.createSetupConnectionModal(setupFormData))
                .queue();
    }

    private static void replyError(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }
}
