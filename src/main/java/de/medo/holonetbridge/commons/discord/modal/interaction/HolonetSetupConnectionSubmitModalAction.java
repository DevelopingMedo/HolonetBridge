package de.medo.holonetbridge.commons.discord.modal.interaction;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.discord.button.HolonetOpenSupportButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetModalFactory;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import de.medo.holonetbridge.commons.provider.HealthCheckResult;
import de.medo.holonetbridge.commons.provider.HolonetTicketProviderService;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckException;
import de.medo.holonetbridge.commons.provider.ProviderHealthCheckService;
import de.medo.holonetbridge.commons.service.HolonetBotInteractionSessionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.binding.message.Message;
import org.springframework.binding.message.Severity;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Component
public class HolonetSetupConnectionSubmitModalAction {

    private final MessageSource messageSource;
    private final HolonetBotInteractionSessionService botInteractionSessionService;
    private final EncryptionService encryptionService;
    private final ProviderHealthCheckService providerHealthCheckService;
    private final HolonetTicketProviderService ticketProviders;

    public HolonetSetupConnectionSubmitModalAction(
            MessageSource messageSource,
            HolonetBotInteractionSessionService botInteractionSessionService,
            EncryptionService encryptionService,
            ProviderHealthCheckService providerHealthCheckService,
            HolonetTicketProviderService ticketProviders) {
        this.messageSource = messageSource;
        this.botInteractionSessionService = botInteractionSessionService;
        this.encryptionService = encryptionService;
        this.providerHealthCheckService = providerHealthCheckService;
        this.ticketProviders = ticketProviders;
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
        String unencryptedToken = event.getValue(HolonetModalFactory.TEXTINPUT_ID_TOKEN).getAsString();
        HolonetSetupFormData oldForm = botInteractionSessionService.getOrCreateSetupFormDataSession(
                guild.getIdLong(), member.getIdLong(), locale);
        HolonetSetupFormData newForm = createSetupFormData(event, oldForm, unencryptedToken);
        event.deferReply(true)
                .queue(hook -> CompletableFuture.supplyAsync(() -> getHolonetSetupFormData(hook, oldForm, newForm, locale))
                        .thenComposeAsync(form ->
                                completeSetup(event, form, checkConnection(form, unencryptedToken)))

                        .exceptionally(_ -> new Message(null,
                                messageSource.getMessage("setup.failed", null, locale), Severity.ERROR))
                        .thenAccept(message -> hook.editOriginal(message.getText()).queue()));
    }

    private HolonetSetupFormData getHolonetSetupFormData(InteractionHook hook, HolonetSetupFormData oldForm, HolonetSetupFormData newForm, Locale locale) {
        Message saveMessage = saveIfChanged(oldForm, newForm);
        hook.editOriginal(saveMessage.getText() + "\n"
                + messageSource.getMessage("setup.healthcheck.checking", null, locale)).queue();
        return newForm;
    }

    private HolonetSetupFormData createSetupFormData(
            ModalInteractionEvent event, HolonetSetupFormData oldForm, String unencryptedToken) {
        var newForm = new HolonetSetupFormData();
        newForm.setId(oldForm.getId());
        newForm.setSession(oldForm.getSession());
        newForm.setTicketProvider(TicketProvider.fromId(event
                .getValue(HolonetModalFactory.STRINGSELECTMENU_ID_PROVIDER).getAsStringList().getFirst()));
        newForm.setSupportChannelId(event
                .getValue(HolonetModalFactory.ENTITYSELECTMENU_ID_CHANNEL).getAsLongList().getFirst());
        newForm.setBaseUrl(event.getValue(HolonetModalFactory.TEXTINPUT_ID_BASE_URL).getAsString().trim());
        newForm.setTarget(event.getValue(HolonetModalFactory.TEXTINPUT_ID_TARGET).getAsString().trim());
        String encryptedToken = oldForm.getToken();
        if (encryptedToken == null || !unencryptedToken.equals(encryptionService.decrypt(encryptedToken))) {
            encryptedToken = encryptionService.encrypt(unencryptedToken);
        }
        newForm.setToken(encryptedToken);
        return newForm;
    }

    private Message saveIfChanged(HolonetSetupFormData oldForm, HolonetSetupFormData newForm) {
        String messageKey = "setup.form-unchanged";
        if (!oldForm.equals(newForm)) {
            botInteractionSessionService.saveSetupFormDataSession(newForm);
            messageKey = "setup.form-saved";
        }
        return new Message(null,
                messageSource.getMessage(messageKey, null, newForm.getSession().getLocale()), Severity.INFO);
    }

    private CompletableFuture<Message> completeSetup(
            ModalInteractionEvent event, HolonetSetupFormData form, HealthCheckResult healthCheckResult) {
        if (!healthCheckResult.successful()) {
            return CompletableFuture.completedFuture(
                    new Message(null, healthCheckResult.message(), Severity.ERROR));
        }
        var candidate = ticketProviders.candidateFor(form);
        return postSupportPanel(event, form).thenApplyAsync(panelMessage -> {
            if (panelMessage.getSeverity() == Severity.ERROR) {
                return panelMessage;
            }
            ticketProviders.completeSetup(form, healthCheckResult, candidate);
            return new Message(null, messageSource.getMessage(
                    "setup.completed", new Object[]{"<#" + form.getSupportChannelId() + ">"},
                    event.getUserLocale().toLocale()), Severity.INFO);
        });
    }

    private CompletableFuture<Message> postSupportPanel(ModalInteractionEvent event, HolonetSetupFormData form) {
        Guild guild = event.getGuild();
        Locale userLocale = event.getUserLocale().toLocale();
        TextChannel channel = guild.getTextChannelById(form.getSupportChannelId());
        if (channel == null) {
            return CompletableFuture.completedFuture(new Message(null,
                    messageSource.getMessage("setup.panel.channel-unavailable", null, userLocale), Severity.ERROR));
        }

        Locale guildLocale = guild.getLocale().toLocale();
        return channel.sendMessage(messageSource.getMessage("support.panel-message", null, guildLocale))
                .addComponents(ActionRow.of(Button.success(
                        HolonetOpenSupportButton.BUTTON_ID,
                        messageSource.getMessage("support.open-button", null, guildLocale))))
                .submit()
                .handle((postedMessage, failure) -> failure == null
                        ? new Message(null, messageSource.getMessage(
                        "setup.panel.posted", new Object[]{channel.getAsMention()}, userLocale), Severity.INFO)
                        : new Message(null,
                        messageSource.getMessage("setup.panel.post-failed", null, userLocale), Severity.ERROR));
    }

    private HealthCheckResult checkConnection(
            HolonetSetupFormData form, String unencryptedToken) {
        TicketProvider provider = form.getTicketProvider();
        Locale locale = form.getSession().getLocale();
        String messageKey;
        boolean successful = false;
        try {
            providerHealthCheckService.check(provider, form.getBaseUrl(), unencryptedToken);
            messageKey = "setup.healthcheck.success";
            successful = true;
        } catch (ProviderHealthCheckException e) {
            messageKey = "setup.healthcheck." + e.getReason().name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
        return new HealthCheckResult(
                messageSource.getMessage(messageKey, new Object[]{provider.getDisplayName()}, locale), successful);
    }
}
