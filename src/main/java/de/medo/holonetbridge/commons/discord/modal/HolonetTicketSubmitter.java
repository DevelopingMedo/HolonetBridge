package de.medo.holonetbridge.commons.discord.modal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannel;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnection;
import de.medo.holonetbridge.commons.provider.ProviderConnectionResolver;
import de.medo.holonetbridge.commons.provider.ResolvedProvider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.springframework.stereotype.Component;

/**
 * Shared final step of the modal flows: turns a submitted {@link HolonetTicket} into a ticket at the guild's
 * resolved provider connection ({@link ProviderConnectionResolver}), links the ticket (and the connection it
 * was created against) back to its support channel, and posts the confirmation.
 *
 * <p>Every guard here is durable, DB-backed state - never the in-memory {@link HolonetTicketCreationSessionStore}
 * - so a button/modal submission resolves correctly even after a process restart has wiped that store:
 * whether this channel is tracked at all, whether it already has a ticket, and which connection to create the
 * ticket against all come from {@link HolonetSupportChannelRepository}/{@link ProviderConnectionResolver}.
 * The session store is kept only for cosmetic bookkeeping (the kind choice shown back to the user while the
 * modal is open) and is cleared here on success.
 */
@Slf4j
@Component
public class HolonetTicketSubmitter {

    private final HolonetTicketCreationSessionStore sessionStore;
    private final ProviderConnectionResolver connectionResolver;
    private final HolonetSupportChannelRepository supportChannelRepository;

    public HolonetTicketSubmitter(
            HolonetTicketCreationSessionStore sessionStore,
            ProviderConnectionResolver connectionResolver,
            HolonetSupportChannelRepository supportChannelRepository) {
        this.sessionStore = sessionStore;
        this.connectionResolver = connectionResolver;
        this.supportChannelRepository = supportChannelRepository;
    }

    public void submit(ModalInteractionEvent event, long userId, HolonetTicketKind kind, HolonetTicket ticket) {
        long channelId = event.getChannel().getIdLong();
        Optional<HolonetSupportChannel> channel = supportChannelRepository.findById(channelId);
        if (channel.isEmpty()) {
            replyNotATrackedSupportChannel(event);
            return;
        }
        if (channel.get().ticketId() != null) {
            replyTicketAlreadyExists(event);
            return;
        }
        createTicket(event, userId, titleWithKindPrefix(kind, ticket.getTitle()), ticket.getDescription());
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
        Optional<ResolvedProvider> resolved = connectionResolver.forGuild(guildId);
        if (resolved.isEmpty()) {
            markBroken(channelId);
            hook.sendMessage("Für diesen Server ist noch kein Ticket-Provider eingerichtet. "
                            + "Ein Serveradmin muss zuerst /setup ausführen.")
                    .queue(success -> {}, hookFailure -> log.warn(
                            "Hinweis auf fehlenden Ticket-Provider konnte nicht an Discord zugestellt werden.",
                            hookFailure));
            return;
        }
        ResolvedProvider provider = resolved.get();
        CompletableFuture.supplyAsync(() -> provider.ticketProvider().create(title, description))
                .whenComplete((ticket, failure) -> {
                    if (failure != null) {
                        reportCreationFailure(hook, failure);
                        return;
                    }
                    try {
                        finalizeTicket(event, hook, channelId, userId, provider.connection(), ticket);
                    } catch (RuntimeException exception) {
                        reportCreationFailure(hook, exception);
                    }
                });
    }

    private void finalizeTicket(
            ModalInteractionEvent event,
            InteractionHook hook,
            long channelId,
            long userId,
            HolonetProviderConnection connection,
            TicketDto ticket) {
        supportChannelRepository.findById(channelId).ifPresent(channel -> {
            channel.ticketCreated(connection.id(), ticket.id(), ticket.url());
            supportChannelRepository.save(channel);
        });
        sessionStore.clear(event.getGuild().getIdLong(), channelId, userId);
        String confirmation = confirmationMessage(ticket, connection);
        TextChannel textChannel = event.getChannel().asTextChannel();
        textChannel.sendMessage(confirmation)
                .queue(success -> {}, deliveryFailure -> handleChannelDeliveryFailure(channelId, deliveryFailure));
        hook.sendMessage(confirmation)
                .queue(success -> {}, hookFailure -> log.warn(
                        "Bestätigung für Ticket #{} konnte nicht an Discord zugestellt werden.",
                        ticket.id(),
                        hookFailure));
    }

    private String confirmationMessage(TicketDto ticket, HolonetProviderConnection connection) {
        String identity = "Ticket #%s wurde angelegt: %s".formatted(ticket.id(), ticket.title());
        return validatedTicketUrl(ticket.url(), connection)
                .map(url -> identity + "\nTicket öffnen: " + url)
                .orElse(identity + "\n(Link derzeit nicht verfügbar.)");
    }

    /**
     * Only ever shows a link whose scheme is http(s) and whose host matches the connection's own base url, so
     * a provider adapter returning a malformed or unrelated url (e.g. from a compromised or misbehaving
     * backend) can never get Discord users to click an attacker-controlled link. The ticket itself still
     * exists either way - a missing or invalid url only hides the link, it never triggers another create call.
     */
    private Optional<String> validatedTicketUrl(String url, HolonetProviderConnection connection) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        URI parsed;
        URI base;
        try {
            parsed = new URI(url);
            base = new URI(connection.baseUrl());
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
        String scheme = parsed.getScheme();
        boolean allowedScheme = scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        String host = parsed.getHost();
        boolean plausibleOrigin = host != null && base.getHost() != null && host.equalsIgnoreCase(base.getHost());
        return parsed.isAbsolute() && allowedScheme && plausibleOrigin ? Optional.of(url) : Optional.empty();
    }

    /**
     * Marks the channel {@code BROKEN} on a confirmed-unreachable Discord delivery failure (deleted channel,
     * or the bot lost access/permissions); any other failure is logged but left {@code OPEN}, since it is not
     * evidence that the channel itself is unusable.
     */
    private void handleChannelDeliveryFailure(long channelId, Throwable failure) {
        if (isUnreachableChannelFailure(failure)) {
            markBroken(channelId);
        }
        log.warn("Ticket-Bestätigung konnte nicht in den Support-Kanal gestellt werden.", failure);
    }

    private static boolean isUnreachableChannelFailure(Throwable failure) {
        if (!(failure instanceof ErrorResponseException errorResponseException)) {
            return false;
        }
        ErrorResponse errorResponse = errorResponseException.getErrorResponse();
        return errorResponse == ErrorResponse.UNKNOWN_CHANNEL
                || errorResponse == ErrorResponse.MISSING_ACCESS
                || errorResponse == ErrorResponse.MISSING_PERMISSIONS;
    }

    private void markBroken(long channelId) {
        supportChannelRepository.findById(channelId).ifPresent(channel -> {
            channel.markBroken();
            supportChannelRepository.save(channel);
        });
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

    private static void replyNotATrackedSupportChannel(ModalInteractionEvent event) {
        event.reply("Dieser Kanal wird nicht als Support-Kanal geführt. "
                        + "Bitte starte eine neue Anfrage über „Support anfragen“.")
                .setEphemeral(true)
                .queue();
    }

    private static void replyTicketAlreadyExists(ModalInteractionEvent event) {
        event.reply("Für diesen Kanal wurde bereits ein Ticket angelegt.")
                .setEphemeral(true)
                .queue();
    }

    private static String titleWithKindPrefix(HolonetTicketKind kind, String title) {
        String prefix = kind == HolonetTicketKind.BUG ? "[Bug] " : "[Feature] ";
        return prefix + title;
    }
}
