package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.ProviderConnectionProperties;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetSupportChannelRepository;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicket;
import de.medo.holonetbridge.commons.domain.ticket.HolonetTicketKind;
import de.medo.holonetbridge.commons.extapi.TicketDto;
import de.medo.holonetbridge.commons.extapi.TicketProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Shared final step of the modal flows: turns a submitted {@link HolonetTicket} into a ticket at the
 * {@link TicketProvider}, links the ticket back to its support channel, clears the user's session, and posts the
 * confirmation - or, when the session has already been discarded, asks the user to restart the flow instead.
 *
 * <p>{@link TicketProvider} and {@link ProviderConnectionProperties} are injected as {@link ObjectProvider}s,
 * not directly, because neither is a startup-mandatory bean anymore (see {@code HolonetBridgeApplication}):
 * {@code GiteaConfiguration}'s old global, {@code @ConditionalOnProperty}-gated {@code TicketProvider} bean no
 * longer exists, and provider connections are now per-guild database rows managed through {@code /setup}
 * instead. Wiring this submitter to a guild's actually resolved connection (via {@code
 * ProviderConnectionResolver}) is a later work package ("Zuordnen"); until then, every submission here simply
 * reports "not configured" if no legacy global provider bean happens to be present.
 */
@Slf4j
@Component
public class HolonetTicketSubmitter {

    private final HolonetTicketCreationSessionStore sessionStore;
    private final ObjectProvider<TicketProvider> ticketProvider;
    private final HolonetSupportChannelRepository supportChannelRepository;
    private final ObjectProvider<ProviderConnectionProperties> providerConnectionProperties;

    public HolonetTicketSubmitter(
            HolonetTicketCreationSessionStore sessionStore,
            ObjectProvider<TicketProvider> ticketProvider,
            HolonetSupportChannelRepository supportChannelRepository,
            ObjectProvider<ProviderConnectionProperties> providerConnectionProperties) {
        this.sessionStore = sessionStore;
        this.ticketProvider = ticketProvider;
        this.supportChannelRepository = supportChannelRepository;
        this.providerConnectionProperties = providerConnectionProperties;
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
        TicketProvider provider = ticketProvider.getIfAvailable();
        if (provider == null) {
            hook.sendMessage("Für diesen Server ist noch kein Ticket-Provider eingerichtet. "
                            + "Ein Serveradmin muss zuerst /setup ausführen.")
                    .queue(success -> {}, hookFailure -> log.warn(
                            "Hinweis auf fehlenden Ticket-Provider konnte nicht an Discord zugestellt werden.",
                            hookFailure));
            return;
        }
        CompletableFuture.supplyAsync(() -> provider.create(title, description))
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
        String confirmation = confirmationMessage(ticket);
        event.getChannel().asTextChannel().sendMessage(confirmation).queue();
        hook.sendMessage(confirmation)
                .queue(success -> {}, hookFailure -> log.warn(
                        "Bestätigung für Ticket #{} konnte nicht an Discord zugestellt werden.",
                        ticket.id(),
                        hookFailure));
    }

    private String confirmationMessage(TicketDto ticket) {
        String identity = "Ticket #%s wurde angelegt: %s".formatted(ticket.id(), ticket.title());
        return validatedTicketUrl(ticket.url())
                .map(url -> identity + "\nTicket öffnen: " + url)
                .orElse(identity + "\n(Link derzeit nicht verfügbar.)");
    }

    /**
     * Only ever shows a link whose scheme is http(s) and whose host matches the configured provider's own
     * base url, so a provider adapter returning a malformed or unrelated url (e.g. from a compromised or
     * misbehaving backend) can never get Discord users to click an attacker-controlled link. The ticket itself
     * still exists either way - a missing or invalid url only hides the link, it never triggers another create
     * call.
     */
    private Optional<String> validatedTicketUrl(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        URI parsed;
        try {
            parsed = new URI(url);
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
        String scheme = parsed.getScheme();
        boolean allowedScheme = scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        String host = parsed.getHost();
        ProviderConnectionProperties properties = providerConnectionProperties.getIfAvailable();
        boolean plausibleOrigin =
                host != null && properties != null && host.equalsIgnoreCase(properties.baseUrl().getHost());
        return parsed.isAbsolute() && allowedScheme && plausibleOrigin ? Optional.of(url) : Optional.empty();
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
