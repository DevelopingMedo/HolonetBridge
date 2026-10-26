package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

/**
 * Shared final step of the modal flows: turns a submitted {@link HolonetTicket} into a ticket at the
 * {@link TicketProvider}, links the ticket back to its support channel, clears the user's session, and posts the
 * confirmation - or, when the session has already been discarded, asks the user to restart the flow instead.
 */
@Slf4j
@Component
public class HolonetTicketSubmitter {

    private final HolonetTicketCreationSessionStore sessionStore;
    private final TicketProvider ticketProvider;
    private final HolonetSupportChannelRepository supportChannelRepository;

    public HolonetTicketSubmitter(
            HolonetTicketCreationSessionStore sessionStore,
            TicketProvider ticketProvider,
            HolonetSupportChannelRepository supportChannelRepository) {
        this.sessionStore = sessionStore;
        this.ticketProvider = ticketProvider;
        this.supportChannelRepository = supportChannelRepository;
    }

    public void submit(ModalInteractionEvent event, long userId, HolonetTicket ticket) {
        sessionStore.get(userId).ifPresentOrElse(
                session -> createTicket(
                        event,
                        userId,
                        titleWithKindPrefix(session.kind(), ticket.getTitle()),
                        ticket.getDescription()),
                () -> replySessionExpired(event));
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
        CompletableFuture.supplyAsync(() -> ticketProvider.create(title, description))
                .whenComplete((ticket, failure) -> {
                    if (failure != null) {
                        reportCreationFailure(hook, failure);
                        return;
                    }
                    try {
                        finalizeTicket(event, hook, userId, ticket);
                    } catch (RuntimeException exception) {
                        reportCreationFailure(hook, exception);
                    }
                });
    }

    private void finalizeTicket(ModalInteractionEvent event, InteractionHook hook, long userId, TicketDto ticket) {
        long channelId = event.getChannel().getIdLong();
        supportChannelRepository.findById(channelId).ifPresent(channel -> {
            channel.ticketId(ticket.id());
            supportChannelRepository.save(channel);
        });
        sessionStore.clear(userId);
        String confirmation = "Ticket #%s wurde angelegt: %s".formatted(ticket.id(), ticket.title());
        event.getChannel().asTextChannel().sendMessage(confirmation).queue();
        hook.sendMessage(confirmation)
                .queue(success -> {}, hookFailure -> log.warn(
                        "Bestätigung für Ticket #{} konnte nicht an Discord zugestellt werden.",
                        ticket.id(),
                        hookFailure));
    }

    /** Logs with a correlation id (no ticket content, no tokens) so the incident can be traced without secrets. */
    private static void reportCreationFailure(InteractionHook hook, Throwable failure) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Ticket konnte nicht angelegt werden [correlationId={}]", correlationId, failure);
        hook.sendMessage("Das Ticket konnte nicht angelegt werden. Bitte versuch es erneut (Referenz: %s)."
                        .formatted(correlationId))
                .queue(success -> {}, hookFailure -> log.error(
                        "Fehlermeldung zur fehlgeschlagenen Ticket-Erstellung konnte nicht an Discord zugestellt "
                                + "werden [correlationId={}]",
                        correlationId,
                        hookFailure));
    }

    private static void replySessionExpired(ModalInteractionEvent event) {
        event.reply("Die Sitzung ist abgelaufen, bitte klick erneut auf „Support anfragen“.")
                .setEphemeral(true)
                .queue();
    }

    private static String titleWithKindPrefix(HolonetTicketKind kind, String title) {
        String prefix = kind == HolonetTicketKind.BUG ? "[Bug] " : "[Feature] ";
        return prefix + title;
    }
}
