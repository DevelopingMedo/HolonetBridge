package de.medo.holonetbridge.commons.discord.i18n;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

import de.medo.holonetbridge.commons.discord.button.HolonetBugReportModalButton;
import de.medo.holonetbridge.commons.discord.button.HolonetFeatureModalButton;
import de.medo.holonetbridge.commons.discord.modal.HolonetProviderConnectionModal;
import de.medo.holonetbridge.commons.discord.ticketing.HolonetTicketCreationSessionStore;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class TextsTest {

    private final Texts texts = new Texts(new I18nProperties(null));

    @Test
    void personalAndPublicMessagesUseDifferentLocales() {
        Interaction interaction = mock(Interaction.class);
        Guild guild = mock(Guild.class);
        when(interaction.getUserLocale()).thenReturn(DiscordLocale.CROATIAN);
        when(guild.getLocale()).thenReturn(DiscordLocale.GERMAN);

        assertThat(texts.user(interaction, "support.feature")).isEqualTo("Ideja");
        assertThat(texts.guild(guild, "support.feature")).isEqualTo("Idee");
        assertThat(texts.get("en-US", "support.feature")).isEqualTo("Idea");
        assertThat(texts.get("fr", "support.feature")).isEqualTo("Idea");
        assertThat(texts.get(DiscordLocale.UNKNOWN, "support.feature")).isEqualTo("Idea");
    }

    @Test
    void externalFilesOverrideIndividualKeysWithoutLosingBundledDefaults(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("de.yml"), "support:\n  feature: \"Meine Idee\"\n");
        Texts customized = new Texts(new I18nProperties(directory));

        assertThat(customized.get("de", "support.feature")).isEqualTo("Meine Idee");
        assertThat(customized.get("de", "support.bug")).isEqualTo("Bug");
        assertThat(customized.get("en", "support.feature")).isEqualTo("Idea");
    }

    @Test
    void formatsArgumentsAndPreservesLiteralApostrophes(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("en.yml"), "ticket:\n  created: \"It's ticket {0}: {1}\"\n");
        Texts customized = new Texts(new I18nProperties(directory));

        assertThat(customized.get("en", "ticket.created", "12345", "O'Brien"))
                .isEqualTo("It's ticket 12345: O'Brien");
        assertThatThrownBy(() -> texts.get("en", "does.not.exist"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does.not.exist");
    }

    @Test
    void catalogsHaveIdenticalKeysAndPlaceholdersAndRussianUsesLatinScript() {
        Properties english = catalog("en");
        Pattern placeholders = Pattern.compile("\\{\\d+}");
        for (String language : Texts.LANGUAGES) {
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

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "de", "hr", "ru"})
    void allLocalizedModalsCanBeBuiltWithinDiscordLimits(String language) {
        DiscordLocale locale = DiscordLocale.from(language);
        assertThat(HolonetProviderConnectionModal.build(true, null, texts, locale).getId())
                .isEqualTo(HolonetProviderConnectionModal.MODAL_ID);
        HolonetProviderConnectionModal.build(false, null, texts, locale);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getUserLocale()).thenReturn(locale);
        when(event.replyModal(any(Modal.class))).thenReturn(mock(ModalCallbackAction.class));
        HolonetTicketCreationSessionStore sessions = new HolonetTicketCreationSessionStore();
        new HolonetBugReportModalButton(texts, sessions).handle(event);
        new HolonetFeatureModalButton(texts, sessions).handle(event);
        verify(event, times(2)).replyModal(any(Modal.class));
    }

    private static Properties catalog(String language) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("i18n/" + language + ".yml"));
        return yaml.getObject();
    }
}
