package de.medo.holonetbridge.commons.discord.i18n;

import java.util.EnumMap;
import java.util.Map;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class DiscordCommandLocalization implements LocalizationFunction {

    private final MessageSource messageSource;

    public DiscordCommandLocalization(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public Map<DiscordLocale, String> apply(String key) {
        Map<DiscordLocale, String> translations = new EnumMap<>(DiscordLocale.class);
        for (DiscordLocale locale : DiscordLocale.values()) {
            if (locale == DiscordLocale.UNKNOWN) {
                continue;
            }
            String translation = messageSource.getMessage("commands." + key, null, null, locale.toLocale());
            if (translation != null) {
                translations.put(locale, translation);
            }
        }
        return translations;
    }
}
