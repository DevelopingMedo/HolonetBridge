package de.medo.holonetbridge.commons.discord.button;

import de.holonetbridge.commons.message.SupportMessageCodes;
import de.medo.holonetbridge.commons.discord.i18n.I18nProperties;
import de.medo.holonetbridge.commons.discord.i18n.MessageSourceConfiguration;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;

import static org.assertj.core.api.Assertions.assertThat;

class HolonetButtonFactoryTest {

    @ParameterizedTest
    @CsvSource({
            "en, Request support",
            "de, Support anfragen",
            "hr, Zatraži podršku",
            "ru, Zaprosit podderzhku",
            "fr, Request support"
    })
    void openSupportButtonUsesLocalizedLabelAndPreservesIdentityAndStyle(String language, String expectedLabel) {
        var messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
        var button = new HolonetButtonFactory(messages).createOpenSupportButton(Locale.forLanguageTag(language));

        assertThat(button.getLabel()).isEqualTo(expectedLabel);
        assertThat(button.getCustomId()).isEqualTo(HolonetOpenSupportButton.BUTTON_ID);
        assertThat(button.getStyle()).isEqualTo(ButtonStyle.SUCCESS);
    }

    @Test
    void openSupportButtonResolvesTheNewMessageCodeWithoutTheLegacyKey() {
        var messages = new StaticMessageSource();
        messages.addMessage(SupportMessageCodes.CHANNEL_CREATE_BUTTON_LABEL, Locale.GERMAN, "Support öffnen");

        var button = new HolonetButtonFactory(messages).createOpenSupportButton(Locale.GERMAN);

        assertThat(button.getLabel()).isEqualTo("Support öffnen");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "hr", "ru"})
    void allControlsHaveDistinctStableIdsAndLocalizedLabels(String language) {
        var messages = new MessageSourceConfiguration().messageSource(new I18nProperties(null));
        var factory = new HolonetButtonFactory(messages);
        var locale = Locale.forLanguageTag(language);
        var buttons = List.of(factory.createOpenSupportButton(locale), factory.createReportBugButton(locale),
                factory.createReportFeatureButton(locale), factory.createAddWatcherButton(locale),
                factory.createCloseTicketButton(locale));

        assertThat(buttons).extracting(Button::getCustomId).containsExactly(
                "ticket:open", "ticket:kind:bug", "ticket:kind:feature", "ticket:watcher:add", "ticket:close");
        assertThat(buttons).extracting(Button::getStyle).containsExactly(
                ButtonStyle.SUCCESS, ButtonStyle.DANGER, ButtonStyle.SUCCESS, ButtonStyle.SECONDARY, ButtonStyle.SECONDARY);
        assertThat(buttons).extracting(Button::getLabel).containsExactly(
                messages.getMessage(SupportMessageCodes.CHANNEL_CREATE_BUTTON_LABEL, null, locale),
                messages.getMessage(SupportMessageCodes.REPORT_KIND_BUG_LABEL, null, locale),
                messages.getMessage(SupportMessageCodes.REPORT_KIND_FEATURE_LABEL, null, locale),
                messages.getMessage(SupportMessageCodes.WATCHER_ADD_BUTTON_LABEL, null, locale),
                messages.getMessage(SupportMessageCodes.CLOSE_BUTTON_LABEL, null, locale));
    }
}
