package de.medo.holonetbridge.commons.discord;

import java.util.Collections;

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
 * net.dv8tion.jda.api.hooks.ListenerAdapter}s, and registers the {@code /ticket} and {@code
 * /configure-holonet-bridge} slash commands.
 *
 * <p>The bot does not need to see or cache guild/member state for either flow - everything happens through
 * slash-command and component interactions - so {@link JDABuilder#createLight(String, java.util.Collection)} is
 * used with zero gateway intents.
 */
@Configuration
class DiscordGatewayConfiguration {

    @Bean(destroyMethod = "shutdown")
    JDA jda(
            DiscordProperties discordProperties,
            HolonetBridgeListenerAdapter listenerAdapter,
            TicketChannelConfigurationListenerAdapter configurationListenerAdapter) {
        JDA jda = JDABuilder.createLight(discordProperties.token(), Collections.emptyList())
                .addEventListeners(listenerAdapter, configurationListenerAdapter)
                .build();

        jda.updateCommands()
                .addCommands(
                        Commands.slash(
                                HolonetBridgeListenerAdapter.COMMAND_NAME, "Ein Ticket anlegen oder ändern"),
                        Commands.slash(
                                        TicketChannelConfigurationListenerAdapter.COMMAND_NAME,
                                        "Richtet die Support-Ticket-Nachricht in einem Kanal ein")
                                .addOptions(new OptionData(
                                                OptionType.CHANNEL,
                                                TicketChannelConfigurationListenerAdapter.OPTION_CHANNEL,
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
