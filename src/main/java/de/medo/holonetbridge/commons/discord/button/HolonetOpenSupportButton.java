package de.medo.holonetbridge.commons.discord.button;

import java.util.EnumSet;
import java.util.Locale;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetGuildSequenceService;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannel;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Handles the persistent "Support anfragen" button (custom id {@code ticket:open}). It creates a
 * dedicated, private text channel for the requesting user, remembers it in {@link HolonetSupportChannelRepository},
 * starts the user's {@link HolonetTicketCreationSessionStore} session, and posts the bug/idea chooser into the
 * new channel.
 */
@Component
public class HolonetOpenSupportButton {

    private final MessageSource messageSource;

    /** Custom id of the persistent "Support anfragen" button. */
    public static final String BUTTON_ID = "ticket:open";

    private final HolonetTicketCreationSessionStore sessionStore;
    private final HolonetSupportChannelRepository supportChannelRepository;
    private final HolonetGuildSequenceService guildSequenceService;

    public HolonetOpenSupportButton(
            MessageSource messageSource,
            HolonetTicketCreationSessionStore sessionStore,
            HolonetSupportChannelRepository supportChannelRepository,
            HolonetGuildSequenceService guildSequenceService) {
        this.messageSource = messageSource;
        this.sessionStore = sessionStore;
        this.supportChannelRepository = supportChannelRepository;
        this.guildSequenceService = guildSequenceService;
    }

    public void handle(ButtonInteractionEvent event) {
        Locale userLocale = event.getUserLocale().toLocale();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply(messageSource.getMessage("support.guild-only", null, userLocale))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Locale guildLocale = guild.getLocale().toLocale();
        long guildId = guild.getIdLong();
        long userId = member.getIdLong();
        long number = guildSequenceService.nextNumberFor(guildId);
        String name = "holonetbridge-%05d".formatted(number);
        Category parent = event.getChannel().asTextChannel().getParentCategory();

        event.deferReply(true).queue();

        guild.createTextChannel(name, parent)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(
                        member,
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.MESSAGE_SEND,
                                Permission.MESSAGE_HISTORY,
                                Permission.MESSAGE_ATTACH_FILES),
                        null)
                .addPermissionOverride(
                        guild.getSelfMember(),
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.MESSAGE_SEND,
                                Permission.MANAGE_CHANNEL,
                                Permission.MESSAGE_HISTORY),
                        null)
                .queue(channel -> {
                    supportChannelRepository.save(
                            new HolonetSupportChannel(channel.getIdLong(), guildId, userId, number));
                    sessionStore.startOrReset(guildId, channel.getIdLong(), userId);
                    channel.sendMessage(messageSource.getMessage("support.choose-kind", null, guildLocale))
                            .addComponents(
                                    ActionRow.of(
                                            Button.primary(HolonetBugReportModalButton.BUTTON_ID, messageSource.getMessage("support.bug", null, guildLocale)),
                                            Button.success(HolonetFeatureModalButton.BUTTON_ID, messageSource.getMessage("support.feature", null, guildLocale))),
                                    ActionRow.of(
                                            Button.secondary(HolonetAddWatcherButton.BUTTON_ID, messageSource.getMessage("support.add-watcher", null, guildLocale))))
                            .queue();
                    event.getHook()
                            .sendMessage(messageSource.getMessage("support.created", new Object[] {channel.getAsMention()}, userLocale))
                            .queue();
                }, failure -> event.getHook()
                        .sendMessage(messageSource.getMessage("support.failed", null, userLocale))
                        .queue());
    }
}
