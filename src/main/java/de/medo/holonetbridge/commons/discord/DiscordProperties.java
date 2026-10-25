package de.medo.holonetbridge.commons.discord;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The Discord bot/application token used to connect to the gateway. */
@ConfigurationProperties(prefix = "holonet.discord")
@Validated
public record DiscordProperties(@NotBlank String token) {

    @Override
    public String toString() {
        return "DiscordProperties[token=[REDACTED]]";
    }
}
