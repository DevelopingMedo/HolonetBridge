package de.medo.holonetbridge.commons.discord.i18n;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import de.medo.holonetbridge.commons.crypto.EncryptionProperties;
import de.medo.holonetbridge.commons.crypto.EncryptionService;
import de.medo.holonetbridge.commons.discord.modal.HolonetModalFactory;
import de.medo.holonetbridge.commons.discord.modal.HolonetTicketReportModalFactory;
import de.medo.holonetbridge.commons.domain.HolonetBotInteractionSession;
import de.medo.holonetbridge.commons.domain.HolonetReportFormData;
import de.medo.holonetbridge.commons.domain.HolonetSetupFormData;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
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
                .setLocalizationFunction(new DiscordCommandLocalization(messageSource));
        var data = command.toData();

        assertThat(data.getString("name")).isEqualTo("setup");
        assertThat(data.getObject("description_localizations").getString("hr"))
                .isEqualTo("Postavi poruku za podršku u kanalu");
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
        HolonetBotInteractionSession session = new HolonetBotInteractionSession();
        session.setLocale(locale.toLocale());
        HolonetSetupFormData formData = new HolonetSetupFormData();
        formData.setSession(session);
        assertThat(new HolonetModalFactory(messageSource, new EncryptionService(new EncryptionProperties("WauOKK6kYn/N+mrkpsvPB2pdVBkPRhuDAyWQyLAsZeA="))).createSetupConnectionModal(formData).getId())
                .isEqualTo(HolonetModalFactory.MODAL_ID_SETUP_HOLONET);
        formData.setTicketProvider(de.medo.holonetbridge.commons.domain.TicketProvider.GITEA);
        var configuredModal = new HolonetModalFactory(messageSource,
                new EncryptionService(new EncryptionProperties("WauOKK6kYn/N+mrkpsvPB2pdVBkPRhuDAyWQyLAsZeA=")))
                .createSetupConnectionModal(formData, locale.toLocale(), formData.getTicketProvider());
        assertThat(configuredModal.toData().toString())
                .contains(messageSource.getMessage("setup.modal.config-found", new Object[] {"Gitea"}, locale.toLocale()));
        var factory = new HolonetTicketReportModalFactory(messageSource);
        var report = new HolonetReportFormData();
        assertThat(factory.createBugReportModal(report, locale.toLocale()).getId())
                .isEqualTo(HolonetTicketReportModalFactory.MODAL_ID_BUG_REPORT);
        assertThat(factory.createFeatureModal(report, locale.toLocale()).getId())
                .isEqualTo(HolonetTicketReportModalFactory.MODAL_ID_FEATURE_REPORT);
    }

    private static Properties catalog(String language) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("i18n/messages" + (language.equals("en") ? "" : "_" + language) + ".yml"));
        return yaml.getObject();
    }
}
