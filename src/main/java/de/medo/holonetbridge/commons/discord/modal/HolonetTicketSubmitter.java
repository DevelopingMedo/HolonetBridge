package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicket;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Shared final step of the modal flows: turns a submitted {@link HolonetTicket} into a ticket at the
 * {@link TicketProvider}, links the ticket back to its support channel, clears the user's session, and posts the
 * confirmation - or, when the session has already been discarded, asks the user to restart the flow instead.
 */
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
                        titleWithKindPrefix(session.kind(), ticket.title()),
                        ticket.getDescription()),
                () -> replySessionExpired(event));
    }

    private void createTicket(ModalInteractionEvent event, long userId, String title, String description) {
        event.deferReply(true).queue();
        TicketDto ticket = ticketProvider.create(title, description);
        long channelId = event.getChannel().getIdLong();
        supportChannelRepository.findById(channelId).ifPresent(channel -> {
            channel.ticketId(ticket.id());
            supportChannelRepository.save(channel);
        });
        sessionStore.clear(userId);
        event.getChannel()
                .asTextChannel()
                .sendMessage("Ticket #%s wurde angelegt: %s".formatted(ticket.id(), ticket.title()))
                .queue();
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
