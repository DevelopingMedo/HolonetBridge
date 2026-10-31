package de.medo.holonetbridge.commons.discord.select;

import de.holonetbridge.commons.message.SupportMessageCodes;
import de.medo.holonetbridge.commons.discord.interaction.HolonetInteractionResponder;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetChannelCoordinator;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketInteractionGuard;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Slf4j
public class HolonetGrantWatcherAction {

    private final MessageSourceAccessor messageSource;
    private final HolonetInteractionResponder interactionResponder;
    private final HolonetChannelCoordinator channels;
    private final HolonetTicketInteractionGuard guard;

    public HolonetGrantWatcherAction(MessageSource messageSource,
                                     HolonetInteractionResponder interactionResponder,
                                     HolonetChannelCoordinator channels,
                                     HolonetTicketInteractionGuard guard) {
        this.messageSource = new MessageSourceAccessor(messageSource);
        this.interactionResponder = interactionResponder;
        this.channels = channels;
        this.guard = guard;
    }

    public void handle(EntitySelectInteractionEvent event) {
        if (!guard.allowWatcher(event)) return;
        Locale userLocale = event.getUserLocale().toLocale();
        Member member = event.getMentions().getMembers().stream().findFirst().orElse(null);
        if (member == null) {
            event.reply(messageSource.getMessage(SupportMessageCodes.WATCHER_ADD_NOT_FOUND, userLocale))
                 .setEphemeral(true).queue();
            return;
        }
        event.reply(messageSource.getMessage(SupportMessageCodes.WATCHER_ADD_IN_PROGRESS, userLocale))
             .setEphemeral(true)
             .queue(replyHook -> grantWatcher(event, member, replyHook),
                    failure -> log.error("Watcher progress reply failed; no permissions changed", failure));
    }

    private void grantWatcher(EntitySelectInteractionEvent event, Member member, InteractionHook replyHook) {
        long guildId = event.getGuild().getIdLong();
        long channelId = event.getChannel().getIdLong();
        channels.async(channelId, () -> {
            if (!guard.canAccess(event)) return SupportMessageCodes.ACTION_UNAVAILABLE;
            if (member.getGuild().getIdLong() != guildId) return SupportMessageCodes.WATCHER_ADD_NOT_FOUND;
            event.getChannel()
                .asTextChannel()
                .upsertPermissionOverride(member)
                .grant(
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_SEND,
                        Permission.MESSAGE_HISTORY,
                        Permission.MESSAGE_ATTACH_FILES)
                .complete();
            return SupportMessageCodes.WATCHER_ADD_SUCCESS;
        }).whenComplete((key, failure) -> {
            if (failure != null) {
                interactionResponder.updateReplyWithError(replyHook, failure, SupportMessageCodes.WATCHER_ADD_FAILURE);
            } else {
                replyHook.editOriginal(messageSource.getMessage(key, new Object[]{member.getAsMention()},
                                                               event.getUserLocale().toLocale())).queue();
            }
        });
    }
}
