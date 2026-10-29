package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannel;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class HolonetTicketSubmitter {

    private final MessageSource messageSource;

    private final HolonetTicketCreationSessionStore sessionStore;
    private final HolonetSupportChannelRepository supportChannelRepository;

    public HolonetTicketSubmitter(
            MessageSource messageSource,
            HolonetTicketCreationSessionStore sessionStore,
            HolonetSupportChannelRepository supportChannelRepository) {
        this.messageSource = messageSource;
        this.sessionStore = sessionStore;
        this.supportChannelRepository = supportChannelRepository;
    }

    public void submit(ModalInteractionEvent event, long userId, HolonetTicketKind kind, HolonetTicket ticket) {
        long channelId = event.getChannel().getIdLong();
        Optional<HolonetSupportChannel> channel = supportChannelRepository.findById(channelId);
//        if (channel.isEmpty()) {
//            replyNotATrackedSupportChannel(event);
//            return;
//        }
//        if (channel.get().ticketId() != null) {
//            replyTicketAlreadyExists(event);
//            return;
//        }
//        createTicket(
//                event, userId, titleWithKindPrefix(kind, ticket.getTitle()),
//                ticket.getDescription());
    }

    /**
     * Acknowledges the interaction first, then runs the provider call off the acknowledging thread so a slow
     * provider never delays the "thinking..." confirmation nor blocks other interactions. Every path below -
     * success, a failed future, or an exception thrown while finishing up - must end by completing {@code hook},
     * since an interaction left without a follow-up shows as permanently stuck to the user.
     */
    private void createTicket(ModalInteractionEvent event, long userId, String title, String description) {
        event.deferReply(true).queue(hook -> createTicketAfterDefer(event, hook, userId, title, description));
    }

    private void createTicketAfterDefer(
            ModalInteractionEvent event, InteractionHook hook, long userId, String title, String description) {
        long channelId = event.getChannel().getIdLong();
        long guildId = event.getGuild().getIdLong();
//        Optional<ResolvedProvider> resolved = connectionResolver.forGuild(guildId);
//        if (resolved.isEmpty()) {
//            markBroken(channelId);
//            hook.sendMessage(messageSource.getMessage("ticket.no-provider", null, event.getUserLocale().toLocale()))
//                    .queue(success -> {}, hookFailure -> log.warn(
//                            "Hinweis auf fehlenden Ticket-Provider konnte nicht an Discord zugestellt werden.",
//                            hookFailure));
//            return;
//        }
//        ResolvedProvider provider = resolved.get();
//        CompletableFuture.supplyAsync(() -> provider.ticketProvider().create(title, description))
//                .whenComplete((ticket, failure) -> {
//                    if (failure != null) {
//                        reportCreationFailure(hook, failure);
//                        return;
//                    }
//                    try {
//                        finalizeTicket(event, hook, channelId, userId, provider.connection(), ticket);
//                    } catch (RuntimeException exception) {
//                        reportCreationFailure(hook, exception);
//                    }
//                });
    }


}
