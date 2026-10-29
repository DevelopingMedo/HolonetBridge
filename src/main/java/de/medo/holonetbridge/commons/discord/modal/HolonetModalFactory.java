package de.medo.holonetbridge.commons.discord.modal;

import java.util.Locale;

import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import de.medo.holonetbridge.commons.domain.TicketProvider;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class HolonetModalFactory {

    public static final String MODAL_ID_SETUP_HOLONET = "setup:modal:holonetSetup";
    public static final String ENTITYSELECTMENU_ID_CHANNEL = "channel";
    public static final String STRINGSELECTMENU_ID_PROVIDER = "ticket_provider";
    public static final String TEXTINPUT_ID_BASE_URL = "base_url";
    public static final String TEXTINPUT_ID_TARGET = "target";
    public static final String TEXTINPUT_ID_TOKEN = "token";

    private final MessageSource messageSource;
    private final EncryptionService encryptionService;

    public HolonetModalFactory(MessageSource messageSource, EncryptionService encryptionService) {
        this.messageSource = messageSource;
        this.encryptionService = encryptionService;
    }

    public Modal createSetupConnectionModal(HolonetSetupFormData setupFormData) {
        return createSetupConnectionModal(setupFormData, setupFormData.getSession().getLocale(), null);
    }

    public Modal createSetupConnectionModal(
            HolonetSetupFormData setupFormData, Locale locale, TicketProvider configuredProvider) {
        EntitySelectMenu.Builder channelBuilder = EntitySelectMenu.create(
                        ENTITYSELECTMENU_ID_CHANNEL,
                        EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setRequired(true)
                .setRequiredRange(1, 1);
        if (setupFormData.getSupportChannelId() != null) {
            channelBuilder.setDefaultValues(EntitySelectMenu.DefaultValue.channel(setupFormData.getSupportChannelId()));
        }
        EntitySelectMenu channel = channelBuilder.build();

        StringSelectMenu.Builder stringSelectMenuBuilder = StringSelectMenu.create(STRINGSELECTMENU_ID_PROVIDER)
                .setRequired(true)
                .setRequiredRange(1, 1);
        for (TicketProvider provider : TicketProvider.values()) {
            stringSelectMenuBuilder.addOption(provider.getDisplayName(), provider.getId());
        }
        if (setupFormData.getTicketProvider() != null) {
            stringSelectMenuBuilder.setDefaultValues(setupFormData.getTicketProvider().getId());
        }
        StringSelectMenu stringSelectMenu = stringSelectMenuBuilder.build();

        TextInput baseUrl = TextInput.create(TEXTINPUT_ID_BASE_URL, TextInputStyle.SHORT)
                .setRequiredRange(1, 200)
                .setPlaceholder("https://gitea.example.com")
                .setValue(setupFormData.getBaseUrl())
                .build();
        TextInput target = TextInput.create(TEXTINPUT_ID_TARGET, TextInputStyle.SHORT)
                .setRequiredRange(1, 255)
                .setValue(setupFormData.getTarget())
                .build();
        String encryptedToken = setupFormData.getToken();
        boolean isTokenRequired = encryptedToken == null;
        var tokenBuilder = TextInput.create(TEXTINPUT_ID_TOKEN, TextInputStyle.SHORT)
                .setRequired(isTokenRequired)
                .setRequiredRange(isTokenRequired ? 1 : 0, 200)
                .setPlaceholder(isTokenRequired
                        ? messageSource.getMessage("setup.modal.token-required", null, locale)
                        : messageSource.getMessage("setup.modal.token-keep", null, locale));
        if (!isTokenRequired) {
            tokenBuilder.setValue(encryptionService.decrypt(encryptedToken));
        }
        TextInput token = tokenBuilder.build();

        return Modal.create(MODAL_ID_SETUP_HOLONET, messageSource.getMessage("setup.modal.title", null, locale))
                .addComponents(
                        Label.of(messageSource.getMessage("setup.modal.provider", null, locale),
                                configuredProvider != null
                                        ? messageSource.getMessage("setup.modal.config-found",
                                                new Object[] {configuredProvider.getDisplayName()}, locale) : null,
                                stringSelectMenu),
                        Label.of(messageSource.getMessage("setup.modal.channel", null, locale),
                                messageSource.getMessage("setup.modal.channel-hint", null, locale), channel),
                        Label.of(messageSource.getMessage("setup.modal.base-url", null, locale),
                                messageSource.getMessage("setup.modal.base-url-hint", null, locale), baseUrl),
                        Label.of(messageSource.getMessage("setup.modal.target", null, locale),
                                messageSource.getMessage("setup.modal.target-hint", null, locale), target),
                        Label.of(messageSource.getMessage("setup.modal.token", null, locale), token))
                .build();
    }
}
