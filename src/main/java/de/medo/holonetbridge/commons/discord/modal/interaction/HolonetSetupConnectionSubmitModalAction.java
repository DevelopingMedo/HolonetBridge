package de.medo.holonetbridge.commons.discord.modal.interaction;

import java.util.Locale;
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
        HolonetSetupFormData oldForm = botInteractionSessionService.getOrCreateSetupFormDataSession(
                guild.getIdLong(), member.getIdLong(), locale);
        HolonetSetupFormData newForm = createSetupFormData(event, oldForm);
        String token = newForm.getToken();
        saveIfChanged(oldForm, newForm);

        event.reply(messageSource.getMessage("setup.healthcheck.checking", null, locale))
                .setEphemeral(true)
                .queue(hook -> CompletableFuture
                        .supplyAsync(() -> checkConnection(newForm, token))
                        .thenAccept(healthCheckResult -> {
                            try {
                                if (healthCheckResult.successful()) {
                                    postSupportPanel(event, newForm);
                                    botInteractionSessionService.completeSetup(newForm, healthCheckResult);
                                }
                                hook.editOriginal(healthCheckResult.message()).queue();
                            } catch (RuntimeException failure) {
                                log.error("Setup could not be completed for guild {} ({})", guild.getIdLong(),
                                        failure.getClass().getSimpleName());
                                hook.editOriginal(messageSource.getMessage("setup.healthcheck.failed", null, locale)).queue();
                            }
                        }));
    }

    private HolonetSetupFormData createSetupFormData(ModalInteractionEvent event, HolonetSetupFormData oldForm) {
        var newForm = new HolonetSetupFormData();
        newForm.setId(oldForm.getId());
        newForm.setSession(oldForm.getSession());
        newForm.setTicketProvider(TicketProvider.fromId(event
                .getValue(HolonetModalFactory.STRINGSELECTMENU_ID_PROVIDER).getAsStringList().getFirst()));
        newForm.setSupportChannelId(event
                .getValue(HolonetModalFactory.ENTITYSELECTMENU_ID_CHANNEL).getAsLongList().getFirst());
        newForm.setBaseUrl(event.getValue(HolonetModalFactory.TEXTINPUT_ID_BASE_URL).getAsString().trim());
        newForm.setTarget(event.getValue(HolonetModalFactory.TEXTINPUT_ID_TARGET).getAsString().trim());
        newForm.setToken(event.getValue(HolonetModalFactory.TEXTINPUT_ID_TOKEN).getAsString());
        return newForm;
    }

    private void saveIfChanged(HolonetSetupFormData oldForm, HolonetSetupFormData newForm) {
        String token = newForm.getToken();
        String savedToken = oldForm.getToken();
        // Reuse the ciphertext for an unchanged token so Lombok's equals can compare the form data.
        newForm.setToken(savedToken != null && token.equals(encryptionService.decrypt(savedToken))
                ? savedToken : encryptionService.encrypt(token));
        if (!oldForm.equals(newForm)) {
            botInteractionSessionService.saveSetupFormDataSession(newForm);
        }
    }

    private void postSupportPanel(ModalInteractionEvent event, HolonetSetupFormData form) {
        Guild guild = event.getGuild();
        Locale userLocale = event.getUserLocale().toLocale();
        TextChannel channel = guild.getTextChannelById(form.getSupportChannelId());
        if (channel == null) {
            event.getHook().sendMessage(messageSource.getMessage("setup.panel.channel-unavailable", null, userLocale)).queue();
            return;
        }

        Locale guildLocale = guild.getLocale().toLocale();
        channel.sendMessage(messageSource.getMessage("support.panel-message", null, guildLocale))
                .addComponents(ActionRow.of(Button.success(
                        HolonetOpenSupportButton.BUTTON_ID,
                        messageSource.getMessage("support.open-button", null, guildLocale))))
                .queue(
                        ignored -> {},
                        failure -> event.getHook().sendMessage(messageSource.getMessage("setup.panel.post-failed", null, userLocale))
                                .queue());
    }

    private HealthCheckResult checkConnection(
            HolonetSetupFormData form, String token) {
        TicketProvider provider = form.getTicketProvider();
        Locale locale = form.getSession().getLocale();
        String messageKey;
        boolean successful = false;
        try {
            providerHealthCheckService.check(provider, form.getBaseUrl(), token);
            messageKey = "setup.healthcheck.success";
            successful = true;
        } catch (ProviderHealthCheckException e) {
            messageKey = "setup.healthcheck." + e.getReason().name().toLowerCase(Locale.ROOT).replace('_', '-');
        } catch (RuntimeException e) {
            log.error("Provider healthcheck failed for guild {} ({})", form.getSession().getGuildId(), e.getClass().getSimpleName());
            messageKey = "setup.healthcheck.failed";
        }
        return new HealthCheckResult(
                messageSource.getMessage(messageKey, new Object[] {provider.getDisplayName()}, locale), successful);
    }
}
