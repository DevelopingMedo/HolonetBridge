package de.medo.holonetbridge.commons.discord.listener;

import de.medo.holonetbridge.commons.discord.command.HolonetSetupCommandAction;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * JDA {@link ListenerAdapter} for slash commands. It forwards the {@link HolonetSetupCommandAction#COMMAND_SETUP}
 * command to {@link HolonetSetupCommandAction}; other commands are ignored.
 */
@Component
@Slf4j
public class HolonetOnSlashCommandInteractionListener extends ListenerAdapter {

    private final HolonetSetupCommandAction setupCommand;

    public HolonetOnSlashCommandInteractionListener(HolonetSetupCommandAction setupCommand) {
        this.setupCommand = setupCommand;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case HolonetSetupCommandAction.COMMAND_SETUP -> setupCommand.handle(event);
            default -> log.warn("Nicht registrerter Slash-Command wurde aufgerufen: {}", event.getName());
        }
    }
}
