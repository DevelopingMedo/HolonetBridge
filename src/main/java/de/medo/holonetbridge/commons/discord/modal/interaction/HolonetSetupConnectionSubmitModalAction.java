package de.medo.holonetbridge.commons.discord.modal.interaction;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.discord.button.HolonetOpenSupportButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetModalFactory;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException;
import de.medo.holonetbridge.commons.provider.HealthCheckResult;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckService;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HolonetSetupConnectionSubmitModalAction {

    private final MessageSource messageSource;
    private final HolonetBotInteractionSessionService botInteractionSessionService;
    private final EncryptionService encryptionService;
    private final ProviderHealthCheckService providerHealthCheckService;

    public HolonetSetupConnectionSubmitModalAction(
            MessageSource messageSource,
            HolonetBotInteractionSessionService botInteractionSessionService,
            EncryptionService encryptionService,
            ProviderHealthCheckService providerHealthCheckService) {
        this.messageSource = messageSource;
        this.botInteractionSessionService = botInteractionSessionService;
        this.encryptionService = encryptionService;
        this.providerHealthCheckService = providerHealthCheckService;
    }

    public void handle(ModalInteractionEvent event) {
        Locale locale = event.getUserLocale().toLocale();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply(messageSource.getMessage("setup.guild-only", null, locale)).setEphemeral(true).queue();
            return;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(messageSource.getMessage("setup.manage-server", null, locale)).setEphemeral(true).queue();
            return;
        }
        TicketProvider ticketProvider = TicketProvider.fromId(event
                .getValue(HolonetModalFactory.STRINGSELECTMENU_ID_PROVIDER)
                .getAsStringList()
                .getFirst());
        Long supportChannelId = event.getValue(HolonetModalFactory.ENTITYSELECTMENU_ID_CHANNEL)
                .getAsLongList()
                .getFirst();
        String baseUrl = event.getValue(HolonetModalFactory.TEXTINPUT_ID_BASE_URL).getAsString().trim();
        String target = event.getValue(HolonetModalFactory.TEXTINPUT_ID_TARGET).getAsString().trim();
        String token = event.getValue(HolonetModalFactory.TEXTINPUT_ID_TOKEN).getAsString();
        HolonetSetupFormData setupFormData = botInteractionSessionService.getOrCreateSetupFormDataSession(
                guild.getIdLong(), member.getIdLong(), locale);

        saveIfChanged(setupFormData, ticketProvider, baseUrl, target, token, supportChannelId);

        event.reply(messageSource.getMessage("setup.healthcheck.checking", null, locale))
                .setEphemeral(true)
                .queue(hook -> CompletableFuture
                        .supplyAsync(() -> checkConnection(ticketProvider, baseUrl, token, locale, guild.getIdLong()))
                        .thenAccept(healthCheckResult -> completeSetup(setupFormData, healthCheckResult, hook, guild, locale)));
    }

    private void saveIfChanged(HolonetSetupFormData form, TicketProvider provider, String baseUrl,
            String target, String token, Long supportChannelId) {
        String savedToken = form.getToken() == null ? null : encryptionService.decrypt(form.getToken());
        if (form.getTicketProvider() == provider
                && Objects.equals(form.getBaseUrl(), baseUrl)
                && Objects.equals(form.getTarget(), target)
                && Objects.equals(savedToken, token)
                && Objects.equals(form.getSupportChannelId(), supportChannelId)) {
            return;
        }
        form.setTicketProvider(provider);
        form.setBaseUrl(baseUrl);
        form.setTarget(target);
        form.setToken(encryptionService.encrypt(token));
        form.setSupportChannelId(supportChannelId);
        botInteractionSessionService.saveSetupFormDataSession(form);
    }

    private void completeSetup(HolonetSetupFormData form, HealthCheckResult healthCheckResult,
            InteractionHook hook, Guild guild, Locale locale) {
        try {
            botInteractionSessionService.completeSetup(form, healthCheckResult);
            if (healthCheckResult.successful()) {
                postSupportPanel(hook, guild, form.getSupportChannelId(), locale);
            }
            hook.editOriginal(healthCheckResult.message()).queue();
        } catch (RuntimeException failure) {
            log.error("Setup für Server {} konnte nicht abgeschlossen werden ({})", guild.getIdLong(),
                    failure.getClass().getSimpleName());
            hook.editOriginal(messageSource.getMessage("setup.healthcheck.failed", null, locale)).queue();
        }
    }

    private void postSupportPanel(InteractionHook hook, Guild guild, long channelId, Locale userLocale) {
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            hook.sendMessage(messageSource.getMessage("setup.panel.channel-unavailable", null, userLocale)).queue();
            return;
        }

        Locale guildLocale = guild.getLocale().toLocale();
        channel.sendMessage(messageSource.getMessage("support.panel-message", null, guildLocale))
                .addComponents(ActionRow.of(Button.success(
                        HolonetOpenSupportButton.BUTTON_ID,
                        messageSource.getMessage("support.open-button", null, guildLocale))))
                .queue(
                        ignored -> {},
                        failure -> hook.sendMessage(messageSource.getMessage("setup.panel.post-failed", null, userLocale))
                                .queue());
    }

    private HealthCheckResult checkConnection(
            TicketProvider provider, String baseUrl, String token, Locale locale, long guildId) {
        String messageKey;
        boolean successful = false;
        try {
            providerHealthCheckService.check(provider, baseUrl, token);
            messageKey = "setup.healthcheck.success";
            successful = true;
        } catch (ProviderHealthCheckException e) {
            messageKey = "setup.healthcheck." + e.getReason().name().toLowerCase(Locale.ROOT).replace('_', '-');
        } catch (RuntimeException e) {
            log.error("Provider healthcheck failed for guild {} ({})", guildId, e.getClass().getSimpleName());
            messageKey = "setup.healthcheck.failed";
        }
        return new HealthCheckResult(
                messageSource.getMessage(messageKey, new Object[] {provider.getDisplayName()}, locale), successful);
    }
}
