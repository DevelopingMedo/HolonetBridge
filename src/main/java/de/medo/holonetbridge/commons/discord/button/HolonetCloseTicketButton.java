package de.medo.holonetbridge.commons.discord.button;

import de.holonetbridge.commons.message.DiscordMessageCodes;
import de.holonetbridge.commons.message.SupportMessageCodes;
import de.medo.holonetbridge.commons.discord.interaction.HolonetInteractionResponder;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetChannelCoordinator;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketArchiveService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Slf4j
public class HolonetCloseTicketButton {

    public static final String BUTTON_ID = "ticket:close";

    private final MessageSourceAccessor messageSource;
    private final HolonetInteractionResponder interactionResponder;
    private final HolonetChannelCoordinator channels;
    private final HolonetTicketArchiveService archive;

    public HolonetCloseTicketButton(MessageSource messageSource,
                                    HolonetInteractionResponder interactionResponder,
                                    HolonetChannelCoordinator channels,
                                    HolonetTicketArchiveService archive) {
        this.messageSource = new MessageSourceAccessor(messageSource);
        this.interactionResponder = interactionResponder;
        this.channels = channels;
        this.archive = archive;
    }

    public void handle(ButtonInteractionEvent event) {
        Locale userLocale = event.getUserLocale().toLocale();
        if (event.getGuild() == null || event.getMember() == null
                || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(messageSource.getMessage(DiscordMessageCodes.MANAGE_SERVER_REQUIRED, userLocale))
                 .setEphemeral(true).queue();
            return;
        }
        if (event.getChannel().getType() != ChannelType.TEXT) {
            event.reply(messageSource.getMessage(SupportMessageCodes.ACTION_UNAVAILABLE, userLocale))
                 .setEphemeral(true).queue();
            return;
        }
        event.reply(messageSource.getMessage(SupportMessageCodes.CLOSE_IN_PROGRESS, userLocale))
             .setEphemeral(true)
             .queue(replyHook -> closeTicket(event, replyHook),
                    failure -> log.error("Ticket closure progress reply failed; no archive started", failure));
    }

    private void closeTicket(ButtonInteractionEvent event, InteractionHook replyHook) {
        channels.async(event.getChannel().getIdLong(), () -> archive.archive(
                        event.getGuild(), event.getChannel().asTextChannel(), event.getUser().getIdLong()))
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        interactionResponder.updateReplyWithError(replyHook, failure, SupportMessageCodes.CLOSE_FAILURE);
                    } else {
                        replyHook.editOriginal(archiveResultMessage(result, event.getUserLocale().toLocale())).queue();
                    }
                });
    }

    private String archiveResultMessage(HolonetTicketArchiveService.Result result, Locale userLocale) {
        String reply = messageSource.getMessage(result.discordKey(), userLocale);
        if (result.diagnostic() != null) {
            var diagnostic = result.diagnostic();
            if (diagnostic.bot() != null) {
                var missing = diagnostic.bot().missingMessageKeys().stream()
                                        .map(key -> messageSource.getMessage(key, userLocale)).toList();
                if (!missing.isEmpty()) {
                    reply += "\n" + messageSource.getMessage(SupportMessageCodes.CLOSE_MISSING_PERMISSIONS,
                                                           new Object[]{String.join(", ", missing)}, userLocale);
                }
            }
            reply += "\n" + messageSource.getMessage(SupportMessageCodes.CLOSE_DIAGNOSTIC,
                                                   new Object[]{diagnostic.line()}, userLocale);
        }
        return reply;
    }
}
