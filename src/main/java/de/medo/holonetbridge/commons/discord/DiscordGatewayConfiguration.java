package de.medo.holonetbridge.commons.discord;

import static de.medo.holonetbridge.commons.discord.command.HolonetSetupCommandAction.COMMAND_SETUP;
import static de.medo.holonetbridge.commons.discord.command.HolonetSetupCommandAction.OPTION_CHANNEL;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import de.medo.holonetbridge.commons.discord.i18n.Texts;
import de.medo.holonetbridge.commons.discord.listener.HolonetOnButtonInteractionListener;
import de.medo.holonetbridge.commons.discord.listener.HolonetOnEntitySelectInteractionListener;
import de.medo.holonetbridge.commons.discord.listener.HolonetOnModalInteractionListener;
import de.medo.holonetbridge.commons.discord.listener.HolonetOnSlashCommandInteractionListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.DiscordLocale;
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

    @Bean(destroyMethod = "shutdown")
    JDA jda(
            DiscordProperties discordProperties,
            Texts texts,
            HolonetOnButtonInteractionListener buttonListener,
            HolonetOnModalInteractionListener modalListener,
            HolonetOnEntitySelectInteractionListener entitySelectListener,
            HolonetOnSlashCommandInteractionListener slashCommandListener) {
        JDA jda = JDABuilder.createLight(discordProperties.token(), Collections.emptyList())
                .addEventListeners(buttonListener, modalListener, entitySelectListener, slashCommandListener)
                .build();

        jda.updateCommands()
                .addCommands(
                        Commands.slash(
                                        COMMAND_SETUP,
                                        texts.get("en", "setup.command.description"))
                                .setDescriptionLocalizations(localizations(texts, "setup.command.description"))
                                .addOptions(new OptionData(
                                                OptionType.CHANNEL,
                                                OPTION_CHANNEL,
                                                texts.get("en", "setup.command.channel"),
                                                true)
                                        .setDescriptionLocalizations(localizations(texts, "setup.command.channel"))
                                        .setChannelTypes(ChannelType.TEXT))
                                .setDefaultPermissions(
                                        DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                                .setContexts(InteractionContextType.GUILD))
                .queue();

        return jda;
    }

    static Map<DiscordLocale, String> localizations(Texts texts, String key) {
        return java.util.Arrays.stream(DiscordLocale.values())
                .filter(locale -> Texts.LANGUAGES.contains(locale.toLocale().getLanguage()))
                .collect(Collectors.toMap(locale -> locale, locale -> texts.get(locale, key)));
    }
}
