package de.medo.holonetbridge.commons.discord;

import java.util.Collections;

import de.medo.holonetbridge.commons.discord.listener.HolonetOnButtonInteractionListener;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupListenerAdapter;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketModalDialogListenerAdapter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds and starts the JDA gateway connection, wires up the bridge's {@link
 * net.dv8tion.jda.api.hooks.ListenerAdapter}s, and registers the {@code
 * /setup} slash command.
 *
 * <p>The bot does not need to see or cache guild/member state for either flow - everything happens through
 * slash-command and component interactions - so {@link JDABuilder#createLight(String, java.util.Collection)} is
 * used with zero gateway intents.
 */
@Configuration
public class DiscordGatewayConfiguration {

    /**
     * Name of the {@code /setup} command, handled by {@link
     * HolonetSetupListenerAdapter}.
     */
    public static final String COMMAND_SETUP = "setup";

    /** Name of {@link #COMMAND_SETUP}'s single {@code CHANNEL} option. */
    public static final String OPTION_CHANNEL = "channel";

    @Bean(destroyMethod = "shutdown")
    JDA jda(
            DiscordProperties discordProperties,
            HolonetOnButtonInteractionListener buttonListener,
            HolonetTicketModalDialogListenerAdapter modalDialogListenerAdapter,
            HolonetSetupListenerAdapter configurationListenerAdapter) {
        JDA jda = JDABuilder.createLight(discordProperties.token(), Collections.emptyList())
                .addEventListeners(buttonListener, modalDialogListenerAdapter, configurationListenerAdapter)
                .build();

        jda.updateCommands()
                .addCommands(
                        Commands.slash(
                                        COMMAND_SETUP,
                                        "Richtet die Support-Ticket-Nachricht in einem Kanal ein")
                                .addOptions(new OptionData(
                                                OptionType.CHANNEL,
                                                OPTION_CHANNEL,
                                                "Kanal für die Support-Nachricht",
                                                true)
                                        .setChannelTypes(ChannelType.TEXT))
                                .setDefaultPermissions(
                                        DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                                .setContexts(InteractionContextType.GUILD))
                .queue();

        return jda;
    }
}
