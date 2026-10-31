package de.medo.holonetbridge.commons.discord.ticketing;

import de.holonetbridge.commons.message.SupportMessageCodes;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

@Component
public class HolonetTicketInteractionGuard {
    private final MessageSourceAccessor messageSource;

    public HolonetTicketInteractionGuard(MessageSource messages) {
        this.messageSource = new MessageSourceAccessor(messages);
    }

    public boolean allowCreate(IReplyCallback event) { return allow(event); }
    public boolean allowWatcher(IReplyCallback event) { return allow(event); }

    public boolean canAccess(IReplyCallback event) {
        return event.getChannel() != null && event.getChannel().getType() == ChannelType.TEXT
                && event.getMember() != null
                && event.getMember().hasPermission(event.getGuildChannel(),
                        Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND);
    }

    private boolean allow(IReplyCallback event) {
        if (canAccess(event)) return true;
        event.reply(messageSource.getMessage(SupportMessageCodes.ACTION_UNAVAILABLE,
                event.getUserLocale().toLocale())).setEphemeral(true).queue();
        return false;
    }
}
