package de.medo.holonetbridge.commons.discord.i18n;

import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.Interaction;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(I18nProperties.class)
public class Texts {

    public static final List<String> LANGUAGES = List.of("en", "de", "hr", "ru");
    private final Map<String, Properties> translations = new HashMap<>();

    public Texts(I18nProperties properties) {
        for (String language : LANGUAGES) {
            Properties messages = load(new ClassPathResource("i18n/" + language + ".yml"));
            if (properties.directory() != null) {
                var override = properties.directory().resolve(language + ".yml");
                if (Files.exists(override)) {
                    messages.putAll(load(new FileSystemResource(override)));
                }
            }
            translations.put(language, messages);
        }
    }

    public String user(Interaction interaction, String key, Object... arguments) {
        return get(interaction == null ? null : interaction.getUserLocale(), key, arguments);
    }

    public String guild(Guild guild, String key, Object... arguments) {
        return get(guild == null ? null : guild.getLocale(), key, arguments);
    }

    public String get(DiscordLocale locale, String key, Object... arguments) {
        return get(locale == null ? "en" : locale.getLocale(), key, arguments);
    }

    public String get(String languageTag, String key, Object... arguments) {
        String language = Locale.forLanguageTag(languageTag).getLanguage();
        Properties messages = translations.getOrDefault(language, translations.get("en"));
        String pattern = messages.getProperty(key, translations.get("en").getProperty(key));
        if (pattern == null) {
            throw new IllegalArgumentException("Unknown translation key: " + key);
        }
        // MessageFormat treats apostrophes as syntax; translators should be able to write normal text.
        return new MessageFormat(pattern.replace("'", "''"), Locale.forLanguageTag(languageTag)).format(arguments);
    }

    private static Properties load(Resource resource) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resource);
        Properties messages = yaml.getObject();
        if (messages == null) {
            throw new IllegalStateException("No translations in " + resource);
        }
        return messages;
    }
}
