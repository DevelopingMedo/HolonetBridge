package de.medo.holonetbridge.commons.discord.modal;

import de.holonetbridge.commons.message.SupportMessageCodes;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetChannelCoordinator;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketInteractionGuard;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.provider.HolonetTicketProviderService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class HolonetTicketSubmitter {

    private final HolonetTicketProviderService providers;
    private final MessageSource messageSource;
    private final HolonetChannelCoordinator channels;
    private final HolonetTicketInteractionGuard guard;

    public HolonetTicketSubmitter(HolonetTicketProviderService providers, MessageSource messageSource,
            HolonetChannelCoordinator channels, HolonetTicketInteractionGuard guard) {
        this.providers = providers;
        this.messageSource = messageSource;
        this.channels = channels;
        this.guard = guard;
    }

    public void handle(ModalInteractionEvent event, HolonetReportFormData data) {
        if (!guard.allowCreate(event)) return;
        var locale = event.getUserLocale().toLocale();
        long guildId = event.getGuild().getIdLong();
        long channelId = event.getChannel().getIdLong();
        long userId = event.getUser().getIdLong();
        event.deferReply(true).queue(hook -> channels.async(channelId, () -> {
            if (!guard.canAccess(event)
                    || data.getSession() == null || data.getSession().getGuildId() != guildId
                    || data.getSession().getMemberId() != userId) return SupportMessageCodes.ACTION_UNAVAILABLE;
            var provider = providers.providerFor(guildId);
            if (provider.isEmpty()) {
                return "ticket.not-configured";
            }
            provider.get().create(data);
            return "ticket.created";
        }).exceptionally(failure -> "ticket.create-failed")
                .thenAccept(key -> hook.editOriginal(messageSource.getMessage(key, null, locale)).queue()));
    }
}
