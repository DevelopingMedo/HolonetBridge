package de.medo.holonetbridge.commons.discord.listener;

import de.medo.holonetbridge.commons.discord.DiscordGatewayConfiguration;
import de.medo.holonetbridge.commons.discord.command.HolonetSetupCommandAction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * JDA {@link ListenerAdapter} for slash commands. It forwards the {@link DiscordGatewayConfiguration#COMMAND_SETUP}
 * command to {@link HolonetSetupCommandAction}; other commands are ignored.
 */
@Component
public class HolonetOnSlashCommandInteractionListener extends ListenerAdapter {

    private final HolonetSetupCommandAction setupCommand;

    public HolonetOnSlashCommandInteractionListener(HolonetSetupCommandAction setupCommand) {
        this.setupCommand = setupCommand;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (DiscordGatewayConfiguration.COMMAND_SETUP.equals(event.getName())) {
            setupCommand.handle(event);
        }
    }
}
