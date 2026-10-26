package de.medo.holonetbridge.commons.discord.command;

import de.medo.holonetbridge.commons.discord.DiscordGatewayConfiguration;
import de.medo.holonetbridge.commons.discord.button.HolonetOpenSupportButton;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServer;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServerRepository;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the admin-only {@code /setup} command: posts the persistent "Support anfragen" message (the
 * {@link HolonetOpenSupportButton}) into the chosen channel and remembers that channel and message per guild in
 * {@link HolonetDiscordServerRepository}, so the message survives an application restart.
 *
 * <p>Re-running the command against a different channel posts a new message but does not delete the previous one;
 * cleaning up a stale message after reconfiguring is left to the admin for now.
 */
@Component
public class HolonetSetupCommandAction {

    private final HolonetDiscordServerRepository repository;

    public HolonetSetupCommandAction(HolonetDiscordServerRepository repository) {
        this.repository = repository;
    }

    public void handle(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        TextChannel channel = event.getOption(DiscordGatewayConfiguration.OPTION_CHANNEL)
                .getAsChannel()
                .asTextChannel();

        event.deferReply(true).queue();

        channel
                .sendMessage("Brauchst du Unterstützung? Klick unten, um ein Support-Ticket zu erstellen.")
                .addComponents(ActionRow.of(
                        Button.success(HolonetOpenSupportButton.BUTTON_ID, "Support anfragen")))
                .queue(message -> {
                    repository.save(
                            new HolonetDiscordServer(guildId, channel.getIdLong(), message.getIdLong()));
                    event.getHook()
                            .sendMessage("Eingerichtet: Die Support-Nachricht steht jetzt in "
                                    + channel.getAsMention() + ".")
                            .queue();
                });
    }
}
