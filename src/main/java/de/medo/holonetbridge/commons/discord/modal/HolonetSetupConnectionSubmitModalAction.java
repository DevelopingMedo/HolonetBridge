package de.medo.holonetbridge.commons.discord.modal;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import de.medo.holonetbridge.commons.crypto.TokenCryptoService;
import de.medo.holonetbridge.commons.crypto.TokenEncryptionContext;
import de.medo.holonetbridge.commons.discord.button.HolonetOpenSupportButton;
import de.medo.holonetbridge.commons.discord.button.HolonetSetupRetryButton;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServer;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServerRepository;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupSessionStore;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnection;
import de.medo.holonetbridge.commons.provider.HolonetProviderConnectionService;
import de.medo.holonetbridge.commons.provider.HolonetProviderType;
import de.medo.holonetbridge.gitea.GiteaConnectionValidationException;
import de.medo.holonetbridge.gitea.GiteaConnectionValidator;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

/**
 * Handles the submitted Gitea connection modal ({@link HolonetProviderConnectionModal#MODAL_ID}). Runs every
 * validation step - in order, all must pass - before {@link HolonetProviderConnectionService#activate} is
 * ever called, so a failed attempt never touches a guild's previously working connection:
 *
 * <ol>
 *   <li>the invoking user still actually has {@link Permission#MANAGE_SERVER} (re-checked here, not just
 *       trusted from the earlier {@code /setup} invocation or Discord's own default command permission,
 *       which a server admin can loosen independently of this code);
 *   <li>the bot can actually post into the panel channel chosen right here in the modal;
 *   <li>the submitted base URL is syntactically a valid absolute http(s) URL;
 *   <li>the target is SSRF-safe to reach and the (new-or-kept) token can actually read the repository -
 *       {@link GiteaConnectionValidator} proves read access only, never write access, and never creates a
 *       test ticket to find out.
 * </ol>
 *
 * <p>A blank token submission ("keep") is only valid when the target (base URL/owner/repository) is
 * unchanged from the guild's current active connection; there is no anonymous/tokenless mode for Gitea
 * otherwise, since the token is what the validation step above actually exercises.
 *
 * <p>Every submission upserts its non-secret fields into {@link HolonetSetupSessionStore} first, regardless
 * of whether it is a fresh attempt or a resumed one - there is no "expired session" failure mode anymore,
 * since the store no longer depends on an in-memory record created by the earlier {@code /setup} invocation
 * surviving. The stored attempt is only discarded once {@link HolonetProviderConnectionService#activate}
 * actually succeeds.
 */
@Slf4j
@Component
public class HolonetSetupConnectionSubmitModalAction {

    private static final String PANEL_MESSAGE =
            "Brauchst du Unterstützung? Klick unten, um ein Support-Ticket zu erstellen.";

    private final HolonetSetupSessionStore sessionStore;
    private final HolonetProviderConnectionService providerConnectionService;
    private final HolonetDiscordServerRepository discordServerRepository;
    private final TokenCryptoService tokenCryptoService;
    private final GiteaConnectionValidator connectionValidator;

    public HolonetSetupConnectionSubmitModalAction(
            HolonetSetupSessionStore sessionStore,
            HolonetProviderConnectionService providerConnectionService,
            HolonetDiscordServerRepository discordServerRepository,
            TokenCryptoService tokenCryptoService,
            GiteaConnectionValidator connectionValidator) {
        this.sessionStore = sessionStore;
        this.providerConnectionService = providerConnectionService;
        this.discordServerRepository = discordServerRepository;
        this.tokenCryptoService = tokenCryptoService;
        this.connectionValidator = connectionValidator;
    }

    public void handle(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            replyFailureWithoutRetry(event, "/setup kann nur innerhalb eines Servers abgeschlossen werden.");
            return;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            replyFailureWithoutRetry(event, "Dazu fehlt dir die Berechtigung \"Server verwalten\".");
            return;
        }
        long guildId = guild.getIdLong();

        long panelChannelId = event.getValue(HolonetProviderConnectionModal.INPUT_CHANNEL)
                .getAsLongList()
                .get(0);
        TextChannel panelChannel = guild.getTextChannelById(panelChannelId);

        String rawBaseUrl =
                event.getValue(HolonetProviderConnectionModal.INPUT_BASE_URL).getAsString().trim();
        String owner = event.getValue(HolonetProviderConnectionModal.INPUT_OWNER).getAsString().trim();
        String repository =
                event.getValue(HolonetProviderConnectionModal.INPUT_REPOSITORY).getAsString().trim();
        String rawToken = event.getValue(HolonetProviderConnectionModal.INPUT_TOKEN).getAsString();
        String submittedToken = rawToken.isBlank() ? null : rawToken;

        // Upserted before any validation so a failed attempt (or a restart in between) can still be resumed
        // with exactly what was just typed in, never just the previous attempt's values.
        sessionStore.upsert(guildId, userId, panelChannelId, rawBaseUrl, owner, repository);

        if (panelChannel == null
                || !guild.getSelfMember()
                        .hasPermission(panelChannel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
            replyFailureWithRetry(
                    event, "Der gewählte Kanal ist nicht mehr erreichbar oder ich habe dort keine Berechtigung.");
            return;
        }

        URI baseUrl;
        try {
            baseUrl = parseBaseUrl(rawBaseUrl);
        } catch (IllegalArgumentException e) {
            replyFailureWithRetry(event, "Bitte eine gültige http(s)-Basis-URL angeben.");
            return;
        }

        Optional<HolonetProviderConnection> current = providerConnectionService.activeConnection(guildId);
        String effectiveToken = resolveEffectiveToken(submittedToken, current, baseUrl, owner, repository);
        if (effectiveToken == null) {
            replyFailureWithRetry(
                    event,
                    "Für eine neue Verbindung oder eine Änderung von Basis-URL, Owner oder Repository ist "
                            + "ein Token erforderlich.");
            return;
        }

        event.deferReply(true)
                .queue(hook -> validateAndActivate(
                        hook,
                        guild,
                        panelChannel,
                        guildId,
                        baseUrl,
                        owner,
                        repository,
                        submittedToken,
                        effectiveToken,
                        userId));
    }

    /** Resolves which token to actually validate/activate with, or {@code null} if none is usable - see the class Javadoc's "keep" rule. */
    private String resolveEffectiveToken(
            String submittedToken,
            Optional<HolonetProviderConnection> current,
            URI baseUrl,
            String owner,
            String repository) {
        if (submittedToken != null) {
            return submittedToken;
        }
        return current.filter(connection -> connection.targets(baseUrl.toString(), owner, repository))
                .map(connection -> tokenCryptoService.decrypt(
                        connection.encryptedSecret(),
                        new TokenEncryptionContext(connection.guildId(), connection.id().toString())))
                .orElse(null);
    }

    private void validateAndActivate(
            InteractionHook hook,
            Guild guild,
            TextChannel panelChannel,
            long guildId,
            URI baseUrl,
            String owner,
            String repository,
            String submittedToken,
            String effectiveToken,
            long userId) {
        CompletableFuture.runAsync(
                        () -> connectionValidator.verifyReadAccess(baseUrl, effectiveToken, owner, repository))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        reportValidationFailure(hook, failure);
                        return;
                    }
                    try {
                        providerConnectionService.activate(
                                guildId, HolonetProviderType.GITEA, baseUrl.toString(), owner, repository,
                                submittedToken);
                        sessionStore.discard(guildId, userId);
                        upsertPanelAndFinish(hook, guild, panelChannel, guildId);
                    } catch (RuntimeException e) {
                        reportValidationFailure(hook, e);
                    }
                });
    }

    private void upsertPanelAndFinish(InteractionHook hook, Guild guild, TextChannel panelChannel, long guildId) {
        Optional<HolonetDiscordServer> existing = discordServerRepository.findById(guildId);
        if (existing.isPresent() && existing.get().channelId() == panelChannel.getIdLong()) {
            panelChannel
                    .editMessageById(existing.get().messageId(), PANEL_MESSAGE)
                    .setComponents(panelComponents())
                    .queue(
                            message -> finish(hook, panelChannel),
                            failure -> postNewPanel(hook, guild, panelChannel, guildId, existing.get()));
        } else {
            postNewPanel(hook, guild, panelChannel, guildId, existing.orElse(null));
        }
    }

    private void postNewPanel(
            InteractionHook hook, Guild guild, TextChannel panelChannel, long guildId, HolonetDiscordServer previous) {
        panelChannel
                .sendMessage(PANEL_MESSAGE)
                .addComponents(panelComponents())
                .queue(
                        message -> {
                            discordServerRepository.save(new HolonetDiscordServer(
                                    guildId, panelChannel.getIdLong(), message.getIdLong()));
                            deleteStalePanelMessage(guild, panelChannel, previous);
                            finish(hook, panelChannel);
                        },
                        failure -> hook.sendMessage(
                                        "Verbindung erfolgreich eingerichtet, aber die Support-Nachricht konnte "
                                                + "nicht gepostet werden.")
                                .queue());
    }

    /** Best-effort cleanup only: a message that fails to delete (already gone, permissions changed) is not retried. */
    private static void deleteStalePanelMessage(Guild guild, TextChannel newPanelChannel, HolonetDiscordServer previous) {
        if (previous == null || previous.channelId() == newPanelChannel.getIdLong()) {
            return;
        }
        TextChannel oldChannel = guild.getTextChannelById(previous.channelId());
        if (oldChannel != null) {
            oldChannel.deleteMessageById(previous.messageId()).queue(success -> {}, failure -> {});
        }
    }

    private static void finish(InteractionHook hook, TextChannel panelChannel) {
        hook.sendMessage("Verbindung eingerichtet. Die Support-Nachricht steht jetzt in "
                        + panelChannel.getAsMention() + ".")
                .queue();
    }

    private static ActionRow panelComponents() {
        return ActionRow.of(Button.success(HolonetOpenSupportButton.BUTTON_ID, "Support anfragen"));
    }

    /** Logs with a correlation id (never the token) so an unexpected activation failure can be traced. */
    private static void reportValidationFailure(InteractionHook hook, Throwable failure) {
        // CompletableFuture wraps exceptions thrown inside runAsync() in a CompletionException, so the
        // actual validation failure must be unwrapped before the instanceof check below can ever match.
        Throwable actual = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        String message;
        if (actual instanceof GiteaConnectionValidationException validationFailure) {
            message = validationFailure.getMessage();
        } else {
            String correlationId = UUID.randomUUID().toString();
            log.error("Provider-Verbindung konnte nicht eingerichtet werden [correlationId={}]", correlationId, failure);
            message = "Die Verbindung konnte nicht eingerichtet werden. Bitte versuch es erneut (Referenz: %s)."
                    .formatted(correlationId);
        }
        hook.sendMessage(message).addComponents(retryActionRow()).queue();
    }

    private static void replyFailureWithRetry(ModalInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).addComponents(retryActionRow()).queue();
    }

    /** No retry button for failures where retrying would hit the exact same problem (not in a guild, no permission). */
    private static void replyFailureWithoutRetry(ModalInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private static ActionRow retryActionRow() {
        return ActionRow.of(Button.secondary(HolonetSetupRetryButton.BUTTON_ID, "Erneut versuchen"));
    }

    private static URI parseBaseUrl(String raw) {
        URI parsed = URI.create(raw);
        String scheme = parsed.getScheme();
        String normalizedScheme = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        boolean validScheme = normalizedScheme.equals("http") || normalizedScheme.equals("https");
        if (!parsed.isAbsolute() || !validScheme || parsed.getHost() == null) {
            throw new IllegalArgumentException("must be an absolute http(s) URL with a host");
        }
        return parsed;
    }
}
