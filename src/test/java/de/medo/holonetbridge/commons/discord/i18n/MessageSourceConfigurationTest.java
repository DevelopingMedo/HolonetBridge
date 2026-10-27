package de.medo.holonetbridge.commons.discord.i18n;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.io.ClassPathResource;

class MessageSourceConfigurationTest {

    private final MessageSource messageSource = new MessageSourceConfiguration().messageSource(new I18nProperties(null));

    @Test
    void resolvesLanguagesAndFallsBackToEnglishRegardlessOfMachineLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            assertThat(messageSource.getMessage("support.feature", null, Locale.forLanguageTag("hr"))).isEqualTo("Ideja");
            assertThat(messageSource.getMessage("support.feature", null, Locale.GERMANY)).isEqualTo("Idee");
            assertThat(messageSource.getMessage("support.feature", null, Locale.US)).isEqualTo("Idea");
            assertThat(messageSource.getMessage("support.feature", null, Locale.FRENCH)).isEqualTo("Idea");
            assertThat(messageSource.getMessage("support.feature", null, Locale.ROOT)).isEqualTo("Idea");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void externalFilesOverrideIndividualKeysWithoutLosingBundledDefaults(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("messages_de.yml"), "support:\n  feature: \"Meine Idee\"\n");
        MessageSource customized = new MessageSourceConfiguration().messageSource(new I18nProperties(directory));

        assertThat(customized.getMessage("support.feature", null, Locale.GERMANY)).isEqualTo("Meine Idee");
        assertThat(customized.getMessage("support.bug", null, Locale.GERMAN)).isEqualTo("Bug");
        assertThat(customized.getMessage("support.feature", null, Locale.US)).isEqualTo("Idea");
    }

    @Test
    void usesSpringFormattingOnlyWhenArgumentsAreProvided(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("messages.yml"), "plain: \"It's unchanged\"\nformatted: \"It''s ticket {0}: {1}\"\n");
        MessageSource customized = new MessageSourceConfiguration().messageSource(new I18nProperties(directory));

        assertThat(customized.getMessage("plain", null, Locale.US)).isEqualTo("It's unchanged");
        assertThat(customized.getMessage("formatted", new Object[] {"12345", "O'Brien"}, Locale.US))
                .isEqualTo("It's ticket 12345: O'Brien");
        assertThatThrownBy(() -> messageSource.getMessage("does.not.exist", null, Locale.US))
                .isInstanceOf(NoSuchMessageException.class);
    }

    @Test
    void catalogsHaveIdenticalKeysAndPlaceholdersAndRussianUsesLatinScript() {
        Properties english = catalog("en");
        Pattern placeholders = Pattern.compile("\\{\\d+}");
        for (String language : List.of("en", "de", "hr", "ru")) {
            Properties catalog = catalog(language);
            assertThat(catalog.stringPropertyNames()).isEqualTo(english.stringPropertyNames());
            for (String key : english.stringPropertyNames()) {
                String value = catalog.getProperty(key);
                assertThat(value).as(language + ":" + key).isNotBlank();
                assertThat(placeholders.matcher(value).results().map(m -> m.group()).toList())
                        .as(language + ":" + key)
                        .containsExactlyElementsOf(placeholders.matcher(english.getProperty(key)).results().map(m -> m.group()).toList());
                if (language.equals("ru")) {
                    assertThat(value).doesNotContainPattern("\\p{IsCyrillic}");
                }
            }
        }
    }

    @Test
    void jdaSerializesLocalizedCommandAndOptionDescriptions() {
        var command = Commands.slash("setup", messageSource.getMessage("commands.setup.description", null, Locale.ROOT))
                .addOptions(new OptionData(OptionType.CHANNEL, "channel",
                        messageSource.getMessage("commands.setup.options.channel.description", null, Locale.ROOT), true))
                .setLocalizationFunction(new DiscordCommandLocalization(messageSource));
        var data = command.toData();

        assertThat(data.getString("name")).isEqualTo("setup");
        assertThat(data.getObject("description_localizations").getString("hr"))
                .isEqualTo("Postavi poruku za podršku u kanalu");
        assertThat(data.getArray("options").getObject(0).getObject("description_localizations").getString("de"))
                .isEqualTo("Kanal für die Support-Nachricht");
        assertThat(new DiscordCommandLocalization(messageSource).apply("setup.name")).isEmpty();
        // Languages without a dedicated bundle receive English; adding a bundle needs no Java change.
        assertThat(data.getObject("description_localizations").getString("fr"))
                .isEqualTo(messageSource.getMessage("commands.setup.description", null, Locale.ROOT));
        assertThat(new DiscordCommandLocalization(messageSource).apply("setup.description"))
                .doesNotContainKey(DiscordLocale.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "de", "hr", "ru"})
    void allLocalizedModalsCanBeBuiltWithinDiscordLimits(String language) {
        DiscordLocale locale = DiscordLocale.from(language);
        assertThat(HolonetProviderConnectionModal.build(true, null, messageSource, locale.toLocale()).getId())
                .isEqualTo(HolonetProviderConnectionModal.MODAL_ID);
        HolonetProviderConnectionModal.build(false, null, messageSource, locale.toLocale());
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUserLocale()).thenReturn(locale);
        when(event.replyModal(any(Modal.class))).thenReturn(mock(ModalCallbackAction.class));
        HolonetTicketCreationSessionStore sessions = new HolonetTicketCreationSessionStore();
        new HolonetBugReportModalButton(messageSource, sessions).handle(event);
        new HolonetFeatureModalButton(messageSource, sessions).handle(event);
        verify(event, times(2)).replyModal(any(Modal.class));
    }

    private static Properties catalog(String language) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("i18n/messages" + (language.equals("en") ? "" : "_" + language) + ".yml"));
        return yaml.getObject();
    }
}
