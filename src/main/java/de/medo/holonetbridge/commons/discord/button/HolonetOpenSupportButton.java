package de.medo.holonetbridge.commons.discord.button;

import de.holonetbridge.commons.message.SupportMessageCodes;
import de.medo.holonetbridge.commons.discord.interaction.HolonetInteractionResponder;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.jspecify.annotations.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Component
@Slf4j
public class HolonetOpenSupportButton {

    public static final String BUTTON_ID = "ticket:open";
    private final static EnumSet<Permission> TICKET_REPORT_CHANNEL_MEMBER_PERMISSION = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES);
    private final static EnumSet<Permission> TICKET_REPORT_CHANNEL_BOT_PERMISSIONS = EnumSet.of(Permission.VIEW_CHANNEL,
                                                                                                Permission.MESSAGE_SEND,
                                                                                                Permission.MANAGE_CHANNEL,
                                                                                                Permission.MESSAGE_HISTORY);
    private final ConcurrentHashMap<Long, AtomicLong> ticketChannelCounters = new ConcurrentHashMap<>();
    private final MessageSourceAccessor messageSource;
    private final HolonetInteractionResponder interactionResponder;
    private final HolonetButtonFactory buttonFactory;

    public HolonetOpenSupportButton(MessageSource messageSource,
                                    HolonetInteractionResponder interactionResponder,
                                    HolonetButtonFactory buttonFactory) {
        this.messageSource = new MessageSourceAccessor(messageSource);
        this.interactionResponder = interactionResponder;
        this.buttonFactory = buttonFactory;
    }

    public void handle(ButtonInteractionEvent event) {
        Locale userLocale = event.getUserLocale().toLocale();
        event.reply(messageSource.getMessage(SupportMessageCodes.CHANNEL_CREATE_IN_PROGRESS, userLocale))
             .setEphemeral(true)
             .queue(replyHook -> createTicketReportChannel(event, replyHook),
                    failure -> log.error("Support channel progress reply failed; no channel creation started",
                                         failure));
    }

    private void createTicketReportChannel(ButtonInteractionEvent event,
                                           InteractionHook replyHook) {
        Guild guild = event.getGuild();
        Category category = event.getChannel().asTextChannel().getParentCategory();
        guild.createTextChannel(getTicketReportChannelName(guild.getIdLong()), category)
             .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
             .addPermissionOverride(event.getMember(), TICKET_REPORT_CHANNEL_MEMBER_PERMISSION, null)
             .addPermissionOverride(guild.getSelfMember(), TICKET_REPORT_CHANNEL_BOT_PERMISSIONS, null)
             .queue(sendMessageInTicketReportChannel(event, replyHook),
                    failure -> interactionResponder.updateReplyWithError(
                            replyHook, failure, SupportMessageCodes.CHANNEL_CREATE_FAILURE));
    }

    private @NonNull String getTicketReportChannelName(long guildIdLong) {
        long nextChannelSuffixNumber = ticketChannelCounters.computeIfAbsent(guildIdLong, _ -> new AtomicLong())
                                                            .incrementAndGet();
        return "holonetbridge-%07d".formatted(nextChannelSuffixNumber);
    }

    private @NonNull Consumer<TextChannel> sendMessageInTicketReportChannel(ButtonInteractionEvent event,
                                                                            InteractionHook replyHook) {
        return ticketReportChannel -> {
            Locale guildLocale = event.getGuild().getLocale().toLocale();
            String reportTypeSelectionMessage = messageSource.getMessage(
                    SupportMessageCodes.REPORT_TYPE_SELECTION_MESSAGE, guildLocale);
            ticketReportChannel.sendMessage(reportTypeSelectionMessage)
                               .addComponents(
                                       ActionRow.of(buttonFactory.createReportBugButton(guildLocale),
                                                    buttonFactory.createReportFeatureButton(guildLocale)),
                                       ActionRow.of(buttonFactory.createAddWatcherButton(guildLocale),
                                                    buttonFactory.createCloseTicketButton(guildLocale)))
                               .queue(sendTicketReportChannelCreationConfirmation(event, replyHook,
                                                                                  ticketReportChannel),
                                      failure -> interactionResponder.updateReplyWithError(
                                              replyHook, failure, SupportMessageCodes.CHANNEL_CREATE_INCOMPLETE,
                                              ticketReportChannel.getName()));
        };
    }

    private @NonNull Consumer<Message> sendTicketReportChannelCreationConfirmation(ButtonInteractionEvent event,
                                                                                   InteractionHook replyHook,
                                                                                   TextChannel ticketReportChannel) {
        return _ -> {
            Object[] channelName = {ticketReportChannel.getName()};
            Locale locale = event.getUserLocale().toLocale();
            String ticketChannelCreatedMessage = messageSource.getMessage(SupportMessageCodes.CHANNEL_CREATE_SUCCESS,
                                                                          channelName, locale);
            replyHook.editOriginal(ticketChannelCreatedMessage).queue();
        };
    }
}
