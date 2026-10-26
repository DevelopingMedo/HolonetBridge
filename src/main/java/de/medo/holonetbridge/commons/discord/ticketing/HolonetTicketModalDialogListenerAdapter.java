package de.medo.holonetbridge.commons.discord.ticketing;

import de.medo.holonetbridge.commons.discord.button.HolonetAddWatcherButton;
import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.ticket.TicketDto;
import de.medo.holonetbridge.commons.ticket.TicketProvider;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * JDA {@link ListenerAdapter} for the modal and watcher-selection steps of the ticket flow.
 *
 * <p>A submitted idea or bug-report modal is turned into a ticket at the {@link TicketProvider}; the title is
 * prefixed with {@code [Bug]} or {@code [Feature]} depending on the session's {@link HolonetTicketKind}. The
 * watcher selector grants a chosen member channel access. The button interactions that precede these steps are
 * handled by {@code discord.listener.HolonetOnButtonInteractionListener}; this adapter deliberately only covers
 * the modal and entity-select hooks for now.
 */
@Component
public class HolonetTicketModalDialogListenerAdapter extends ListenerAdapter {

    private final HolonetTicketCreationSessionStore sessionStore;
    private final TicketProvider ticketProvider;
    private final HolonetSupportChannelRepository supportChannelRepository;

    HolonetTicketModalDialogListenerAdapter(
            HolonetTicketCreationSessionStore sessionStore,
            TicketProvider ticketProvider,
            HolonetSupportChannelRepository supportChannelRepository) {
        this.sessionStore = sessionStore;
        this.ticketProvider = ticketProvider;
        this.supportChannelRepository = supportChannelRepository;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case HolonetFeatureModalButton.MODAL_ID -> onFeatureSubmitted(event);
            case HolonetBugReportModalButton.MODAL_ID -> onBugReportSubmitted(event);
            default -> {
                // Not one of ours; ignore.
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!HolonetAddWatcherButton.SELECT_ID.equals(event.getComponentId())) {
            return;
        }

        Member member = event.getMentions().getMembers().stream().findFirst().orElse(null);
        if (member == null) {
            event.reply("Die Person konnte nicht aufgelöst werden.").setEphemeral(true).queue();
            return;
        }
        grantWatcher(event, member);
    }

    private void grantWatcher(EntitySelectInteractionEvent event, Member member) {
        event.deferReply(true).queue();
        event.getChannel()
                .asTextChannel()
                .upsertPermissionOverride(member)
                .grant(
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_SEND,
                        Permission.MESSAGE_HISTORY,
                        Permission.MESSAGE_ATTACH_FILES)
                .queue(
                        success -> event.getHook()
                                .sendMessage(member.getAsMention() + " wurde als Beobachter hinzugefügt.")
                                .queue(),
                        failure -> event.getHook()
                                .sendMessage("Die Rechte konnten nicht vergeben werden.")
                                .queue());
    }

    private void onFeatureSubmitted(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        String title = event.getValue(HolonetFeatureModalButton.INPUT_TITLE).getAsString();
        String description = event.getValue(HolonetFeatureModalButton.INPUT_DESCRIPTION).getAsString();

        sessionStore.get(userId).ifPresentOrElse(
                session -> createTicket(
                        event, userId, titleWithKindPrefix(session.kind(), title), description),
                () -> replySessionExpired(event));
    }

    private void onBugReportSubmitted(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        String title = event.getValue(HolonetBugReportModalButton.INPUT_TITLE).getAsString();
        String description = assembleBugDescription(
                event.getValue(HolonetBugReportModalButton.INPUT_STEPS).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_EXPECTED).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_ACTUAL).getAsString(),
                event.getValue(HolonetBugReportModalButton.INPUT_RECURRENCE).getAsStringList().get(0));

        sessionStore.get(userId).ifPresentOrElse(
                session -> createTicket(
                        event, userId, titleWithKindPrefix(session.kind(), title), description),
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

    private static String assembleBugDescription(String steps, String expected, String actual, String recurrence) {
        return new StringBuilder()
                .append("**Was hast du vor dem Fehler gemacht?**").append('\n')
                .append(steps).append('\n')
                .append('\n')
                .append("**Was hättest du erwartet?**").append('\n')
                .append(expected).append('\n')
                .append('\n')
                .append("**Was ist stattdessen passiert?**").append('\n')
                .append(actual).append('\n')
                .append('\n')
                .append("**Tritt der Fehler erneut auf?**").append('\n')
                .append(recurrence).append('\n')
                .toString();
    }
}
