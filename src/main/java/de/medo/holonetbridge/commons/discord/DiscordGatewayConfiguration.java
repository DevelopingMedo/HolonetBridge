package de.medo.holonetbridge.commons.discord;

import java.util.Collections;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds and starts the JDA gateway connection, wires up {@link HolonetBridgeListenerAdapter}, and registers
 * the {@code /ticket} slash command.
 *
 * <p>The bot does not need to see or cache guild/member state for this ticket-creation flow - everything happens
 * through slash-command and component interactions - so {@link JDABuilder#createLight(String, java.util.Collection)}
 * is used with zero gateway intents.
 */
@Configuration
class DiscordGatewayConfiguration {

    @Bean(destroyMethod = "shutdown")
    JDA jda(DiscordProperties discordProperties, HolonetBridgeListenerAdapter listenerAdapter) {
        JDA jda = JDABuilder.createLight(discordProperties.token(), Collections.emptyList())
                .addEventListeners(listenerAdapter)
                .build();

        jda.updateCommands()
                .addCommands(Commands.slash(
                        HolonetBridgeListenerAdapter.COMMAND_NAME, "Ein Ticket anlegen oder ändern"))
                .queue();

        return jda;
    }
}
