package de.medo.holonetbridge.commons.discord;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketModalDialogListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Handles the admin-only {@code /configure-holonet-bridge} command, which posts the persistent "Support
 * anfragen" message that end users click to start the {@code /ticket} flow without typing a slash command. The
 * chosen channel and message id are remembered per guild in {@link GuildTicketChannelConfigurationRepository}
 * so the message survives an application restart.
 *
 * <p>Re-running the command against a different channel posts a new message but does not delete the previous
 * one; cleaning up a stale message after reconfiguring is left to the admin for now.
 */
@Component
class TicketChannelConfigurationListenerAdapter extends ListenerAdapter {

    private final GuildTicketChannelConfigurationRepository repository;

    TicketChannelConfigurationListenerAdapter(GuildTicketChannelConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!DiscordGatewayConfiguration.COMMAND_CONFIGURE_HOLONET_BRIDGE.equals(event.getName())) {
            return;
        }

        long guildId = event.getGuild().getIdLong();
        TextChannel channel = event.getOption(DiscordGatewayConfiguration.OPTION_CHANNEL)
                .getAsChannel()
                .asTextChannel();

        event.deferReply(true).queue();

        channel
                .sendMessage("Brauchst du Unterstützung? Klick unten, um ein Support-Ticket zu erstellen.")
                .addComponents(ActionRow.of(
                        Button.success(HolonetTicketModalDialogListenerAdapter.BUTTON_OPEN, "Support anfragen")))
                .queue(message -> {
                    repository.save(new GuildTicketChannelConfiguration(
                            guildId, channel.getIdLong(), message.getIdLong()));
                    event.getHook()
                            .sendMessage("Eingerichtet: Die Support-Nachricht steht jetzt in "
                                    + channel.getAsMention() + ".")
                            .queue();
                });
    }
}
