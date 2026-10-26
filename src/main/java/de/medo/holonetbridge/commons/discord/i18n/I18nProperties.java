package de.medo.holonetbridge.commons.discord.i18n;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("holonet.i18n")
public record I18nProperties(Path directory) {}
