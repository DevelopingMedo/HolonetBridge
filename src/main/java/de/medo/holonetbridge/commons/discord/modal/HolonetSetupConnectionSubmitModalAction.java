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
import de.medo.holonetbridge.commons.discord.i18n.Texts;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServer;
import de.medo.holonetbridge.commons.discord.setup.HolonetDiscordServerRepository;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraft;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraftStore;
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
 *   <li>the bot can still post into the panel channel chosen back at {@code /setup} time;
 *   <li>the submitted base URL is syntactically a valid absolute http(s) URL;
 *   <li>the target is SSRF-safe to reach and the (new-or-kept) token can actually read the repository -
 *       {@link GiteaConnectionValidator} proves read access only, never write access, and never creates a
 *       test ticket to find out.
 * </ol>
 *
 * <p>A blank token submission ("keep") is only valid when the target (base URL/owner/repository) is
 * unchanged from the guild's current active connection; there is no anonymous/tokenless mode for Gitea
 * otherwise, since the token is what the validation step above actually exercises.
 */
@Slf4j
@Component
public class HolonetSetupConnectionSubmitModalAction {

    private final Texts texts;

    private final HolonetSetupDraftStore draftStore;
    private final HolonetProviderConnectionService providerConnectionService;
    private final HolonetDiscordServerRepository discordServerRepository;
    private final TokenCryptoService tokenCryptoService;
    private final GiteaConnectionValidator connectionValidator;

    public HolonetSetupConnectionSubmitModalAction(
            Texts texts,
            HolonetSetupDraftStore draftStore,
            HolonetProviderConnectionService providerConnectionService,
            HolonetDiscordServerRepository discordServerRepository,
            TokenCryptoService tokenCryptoService,
            GiteaConnectionValidator connectionValidator) {
        this.texts = texts;
        this.draftStore = draftStore;
        this.providerConnectionService = providerConnectionService;
        this.discordServerRepository = discordServerRepository;
        this.tokenCryptoService = tokenCryptoService;
        this.connectionValidator = connectionValidator;
    }

    public void handle(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        Optional<HolonetSetupDraft> maybeDraft = draftStore.get(userId);
        if (maybeDraft.isEmpty()) {
            replyFailureWithoutRetry(event, texts.user(event, "setup.expired"));
            return;
        }
        HolonetSetupDraft draft = maybeDraft.get();

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            replyFailureWithoutRetry(event, texts.user(event, "setup.guild-only"));
            return;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            replyFailureWithoutRetry(event, texts.user(event, "setup.manage-server"));
            return;
        }

        TextChannel panelChannel = guild.getTextChannelById(draft.panelChannelId());
        if (panelChannel == null
                || !guild.getSelfMember()
                        .hasPermission(panelChannel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
            replyFailureWithRetry(
                    event, texts.user(event, "setup.channel-unavailable"));
            return;
        }

        String rawBaseUrl =
                event.getValue(HolonetProviderConnectionModal.INPUT_BASE_URL).getAsString().trim();
        String owner = event.getValue(HolonetProviderConnectionModal.INPUT_OWNER).getAsString().trim();
        String repository =
                event.getValue(HolonetProviderConnectionModal.INPUT_REPOSITORY).getAsString().trim();
        String rawToken = event.getValue(HolonetProviderConnectionModal.INPUT_TOKEN).getAsString();
        String submittedToken = rawToken.isBlank() ? null : rawToken;
        draft.lastSubmittedTarget(rawBaseUrl, owner, repository);

        URI baseUrl;
        try {
            baseUrl = parseBaseUrl(rawBaseUrl);
        } catch (IllegalArgumentException e) {
            replyFailureWithRetry(event, texts.user(event, "setup.invalid-url"));
            return;
        }

        long guildId = draft.guildId();
        Optional<HolonetProviderConnection> current = providerConnectionService.activeConnection(guildId);
        String effectiveToken = resolveEffectiveToken(submittedToken, current, baseUrl, owner, repository);
        if (effectiveToken == null) {
            replyFailureWithRetry(
                    event,
                    texts.user(event, "setup.token-required"));
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
                        draftStore.clear(userId);
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
                    .editMessageById(existing.get().messageId(), texts.guild(guild, "setup.panel.message"))
                    .setComponents(panelComponents(guild))
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
                .sendMessage(texts.guild(guild, "setup.panel.message"))
                .addComponents(panelComponents(guild))
                .queue(
                        message -> {
                            discordServerRepository.save(new HolonetDiscordServer(
                                    guildId, panelChannel.getIdLong(), message.getIdLong()));
                            deleteStalePanelMessage(guild, panelChannel, previous);
                            finish(hook, panelChannel);
                        },
                        failure -> hook.sendMessage(
                                        texts.user(hook.getInteraction(), "setup.panel.failed"))
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

    private void finish(InteractionHook hook, TextChannel panelChannel) {
        hook.sendMessage(texts.user(hook.getInteraction(), "setup.saved", panelChannel.getAsMention()))
                .queue();
    }

    private ActionRow panelComponents(Guild guild) {
        return ActionRow.of(Button.success(HolonetOpenSupportButton.BUTTON_ID, texts.guild(guild, "setup.panel.button")));
    }

    /** Logs with a correlation id (never the token) so an unexpected activation failure can be traced. */
    private void reportValidationFailure(InteractionHook hook, Throwable failure) {
        // CompletableFuture wraps exceptions thrown inside runAsync() in a CompletionException, so the
        // actual validation failure must be unwrapped before the instanceof check below can ever match.
        Throwable actual = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        String message;
        if (actual instanceof GiteaConnectionValidationException validationFailure) {
            String key = switch (validationFailure.reason()) {
                case BLOCKED -> "validation.blocked";
                case UNREACHABLE -> "validation.unreachable";
                case ACCESS -> "validation.access";
                case NOT_FOUND -> "validation.not-found";
                case REJECTED -> "validation.rejected";
            };
            message = texts.user(hook.getInteraction(), key, Integer.toString(validationFailure.status()));
        } else {
            String correlationId = UUID.randomUUID().toString();
            log.error("Provider-Verbindung konnte nicht eingerichtet werden [correlationId={}]", correlationId, failure);
            message = texts.user(hook.getInteraction(), "setup.failed", correlationId);
        }
        hook.sendMessage(message).addComponents(retryActionRow(hook.getInteraction())).queue();
    }

    private void replyFailureWithRetry(ModalInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).addComponents(retryActionRow(event)).queue();
    }

    /** No retry button for failures where retrying would hit the exact same problem (expired/foreign session). */
    private static void replyFailureWithoutRetry(ModalInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private ActionRow retryActionRow(net.dv8tion.jda.api.interactions.Interaction interaction) {
        return ActionRow.of(Button.secondary(HolonetSetupRetryButton.BUTTON_ID, texts.user(interaction, "setup.retry")));
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
