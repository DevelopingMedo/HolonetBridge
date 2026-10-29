package de.medo.holonetbridge.commons.discord.modal;

import java.util.concurrent.CompletableFuture;

import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.provider.HolonetTicketProviderService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class HolonetTicketSubmitter {

    private final HolonetTicketProviderService providers;
    private final MessageSource messageSource;

    public HolonetTicketSubmitter(HolonetTicketProviderService providers, MessageSource messageSource) {
        this.providers = providers;
        this.messageSource = messageSource;
    }

    public void handle(ModalInteractionEvent event, HolonetReportFormData data) {
        var locale = event.getUserLocale().toLocale();
        long guildId = event.getGuild().getIdLong();
        event.deferReply(true).queue(hook -> CompletableFuture.supplyAsync(() -> {
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
